import { useEffect, useId, useMemo, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { api, toApiErrorData, type ApiErrorData } from '../../api/client'
import { applyFieldErrors } from '../../api/formErrors'
import type {
  DecimalSeparator,
  HoldingResponse,
  QuoteResponse,
  TransactionRequest,
  TransactionResponse,
  TransactionType,
} from '../../api/types'
import { useAppDispatch, useAppSelector } from '../../app/hooks'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Dialog, DialogBody, DialogFooter } from '../../components/Dialog'
import { SegmentedControl } from '../../components/SegmentedControl'
import { TextField } from '../../components/TextField'
import { AlertIcon } from '../../components/icons'
import {
  CURRENCY_SYMBOLS,
  formatQuantity,
  parseDecimalInput,
  todayInArgentina,
  toInputDecimal,
} from '../../format/format'
import { useApiResource } from '../../hooks/useApiResource'
import { fetchAssets } from '../assets/assetsSlice'
import { dashboardInvalidated } from '../dashboard/dashboardSlice'
import { createFormOpened } from '../portfolios/portfoliosSlice'
import { AssetPicker } from './AssetPicker'
import styles from './RegisterTransactionDialog.module.css'

interface FormValues {
  type: TransactionType
  assetSymbol: string
  /** Como lo escribe el usuario ("0,05"); se pasa al formato de la API al enviar. */
  quantity: string
  price: string
  /** Cantidad × precio. Se puede escribir (y entonces calcula la cantidad) o se calcula; no se envía a la API. */
  total: string
  /** Lo último que escribió el usuario: qué campo se recalcula cuando cambia el precio. No se envía a la API. */
  lastEdited: 'quantity' | 'total'
  tradeDate: string
  portfolioId: string
  notes: string
}

const FIELDS = ['type', 'assetSymbol', 'quantity', 'price', 'tradeDate', 'portfolioId', 'notes'] as const

/** Los valores del formulario para editar una transacción, en el formato que escribe el usuario. */
function valuesOf(transaction: TransactionResponse, separator: DecimalSeparator): FormValues {
  return {
    type: transaction.type,
    assetSymbol: transaction.asset.symbol,
    quantity: toInputDecimal(transaction.quantity, separator),
    price: toInputDecimal(transaction.price, separator),
    total: toInputDecimal(transaction.total, separator),
    lastEdited: 'quantity',
    tradeDate: transaction.tradeDate,
    portfolioId: String(transaction.portfolio.id),
    notes: transaction.notes ?? '',
  }
}

/** Lo que escribe el usuario como número; null si no es un número válido. */
function amountOf(text: string, separator: DecimalSeparator): number | null {
  const parsed = parseDecimalInput(text, separator)
  return parsed === null ? null : Number(parsed)
}

/** Un número como lo escribe el usuario: con hasta `decimals` decimales y sin ceros de más ("72400", "0,0125"). */
function inputOf(value: number, decimals: number, separator: DecimalSeparator): string {
  return toInputDecimal(value.toFixed(decimals).replace(/\.?0+$/, ''), separator)
}

/** La cantidad que da un total a un precio (total ÷ precio). Vacío si falta uno o no tiene sentido. */
function quantityFor(total: string, price: string, separator: DecimalSeparator): string {
  const amount = amountOf(total, separator)
  const unit = amountOf(price, separator)
  return amount !== null && unit !== null && amount > 0 && unit > 0 ? inputOf(amount / unit, 8, separator) : ''
}

/** El total de una cantidad a un precio (cantidad × precio). Vacío si falta uno o no tiene sentido. */
function totalFor(quantity: string, price: string, separator: DecimalSeparator): string {
  const units = amountOf(quantity, separator)
  const unit = amountOf(price, separator)
  return units !== null && unit !== null && units > 0 && unit > 0 ? inputOf(units * unit, 2, separator) : ''
}

/**
 * "Registrar Transacción": alta de una compra o una venta (POST /transactions). Con `transaction`, el mismo
 * formulario edita una existente (PUT /transactions/{id}).
 */
export function RegisterTransactionDialog({ defaultPortfolioId, transaction, onClose }: {
  /** El portafolio que se está mirando (null = todos): queda elegido de entrada. */
  defaultPortfolioId: number | null
  /** La transacción a editar; sin ella, se registra una nueva. */
  transaction?: TransactionResponse
  onClose: () => void
}) {
  const dispatch = useAppDispatch()
  const portfolios = useAppSelector((state) => state.portfolios.items)
  const catalog = useAppSelector((state) => state.assets.items)
  const separator = useAppSelector((state) => state.preferences.decimalSeparator)
  const [formError, setFormError] = useState<ApiErrorData | null>(null)
  const assetErrorId = useId()

  // Un activo dado de baja ya no está en el catálogo, pero una operación vieja puede ser de ese activo.
  const assets = useMemo(() => {
    if (!transaction || catalog.some((a) => a.symbol === transaction.asset.symbol)) {
      return catalog
    }
    const { symbol, name, type: assetType } = transaction.asset
    return [...catalog, { symbol, name, type: assetType, currency: transaction.currency }]
  }, [catalog, transaction])

  useEffect(() => {
    dispatch(fetchAssets())
  }, [dispatch])

  const {
    register,
    handleSubmit,
    control,
    setValue,
    getValues,
    setError,
    formState: { errors, isSubmitting, isSubmitted },
  } = useForm<FormValues>({
    defaultValues: transaction
      ? valuesOf(transaction, separator)
      : {
          type: 'BUY',
          assetSymbol: '',
          quantity: '',
          price: '',
          total: '',
          lastEdited: 'quantity',
          tradeDate: todayInArgentina(),
          portfolioId: String(defaultPortfolioId ?? portfolios[0]?.id ?? ''),
          notes: '',
        },
  })
  const [type, assetSymbol, price, totalText, tradeDate, portfolioId] = useWatch({
    control,
    name: ['type', 'assetSymbol', 'price', 'total', 'tradeDate', 'portfolioId'],
  })
  const asset = assets.find((a) => a.symbol === assetSymbol)
  // Si el precio lo puso la cotización actual (y no el usuario): solo se vacía solo si cambia la fecha a otro día.
  const [pricePrefilled, setPricePrefilled] = useState(false)

  // Total y cantidad se calculan con el precio. Lo que el usuario escribió por último manda (campo lastEdited del
  // formulario): si escribió el total, la cantidad se recalcula; si escribió la cantidad, o cambió el precio, el total.

  // Al elegir un activo, el precio se precarga con su cotización actual (en la moneda del activo). Solo para una
  // operación de hoy: la cotización de hoy no sirve para otra fecha, y ahí el usuario escribe el precio de ese día.
  useEffect(() => {
    if (!asset || getValues('price') || tradeDate !== todayInArgentina()) {
      return
    }
    let ignore = false
    api<QuoteResponse>('GET', `/assets/${asset.symbol}/quote?currency=${asset.currency}`)
      .then((quote) => {
        if (!ignore && !getValues('price')) {
          const price = toInputDecimal(quote.price, separator)
          setValue('price', price)
          setPricePrefilled(true)
          if (getValues('lastEdited') === 'total') {
            setValue('quantity', quantityFor(getValues('total'), price, separator))
          } else {
            setValue('total', totalFor(getValues('quantity'), price, separator))
          }
        }
      })
      .catch(() => {}) // sin cotización, el usuario la escribe
    return () => {
      ignore = true
    }
  }, [asset, getValues, setValue, separator, tradeDate])

  // En una venta, cuánto tiene de ese activo en el portafolio elegido ("Tenés 0,3421"). Al editar no se muestra:
  // la tenencia de hoy incluye la propia operación, y lo que cuenta es la de su fecha (el backend lo explica si no alcanza).
  const holdingPath =
    !transaction && type === 'SELL' && assetSymbol && portfolioId ? `/holdings/${assetSymbol}?portfolioId=${portfolioId}` : null
  const holding = useApiResource<HoldingResponse>(holdingPath)
  const held = holding.data ? holding.data.quantity : holding.error?.status === 404 ? '0' : null

  const errorCount = Object.keys(errors).length

  async function onSubmit(values: FormValues) {
    setFormError(null)
    const request: TransactionRequest = {
      portfolioId: Number(values.portfolioId),
      assetSymbol: values.assetSymbol,
      type: values.type,
      quantity: parseDecimalInput(values.quantity, separator) ?? values.quantity,
      price: parseDecimalInput(values.price, separator) ?? values.price,
      tradeDate: values.tradeDate,
      notes: values.notes.trim() || null,
    }
    try {
      if (transaction) {
        await api('PUT', `/transactions/${transaction.id}`, request)
      } else {
        await api('POST', '/transactions', request)
      }
      dispatch(dashboardInvalidated()) // las posiciones cambiaron: el dashboard se vuelve a pedir
      onClose()
    } catch (e) {
      const data = toApiErrorData(e)
      if (!applyFieldErrors(data, setError, FIELDS)) {
        setFormError(data)
      }
    }
  }

  if (portfolios.length === 0) {
    return (
      <Dialog title="Registrar Transacción" onClose={onClose}>
        <DialogBody>
          <p className={styles.muted}>Para registrar una operación, primero creá un portafolio donde guardarla.</p>
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            type="button"
            onClick={() => {
              dispatch(createFormOpened())
              onClose()
            }}
          >
            Crear un portafolio
          </Button>
        </DialogFooter>
      </Dialog>
    )
  }

  const validDecimal = (empty: string, notPositive: string) => (value: string) => {
    if (!value.trim()) return empty
    const parsed = parseDecimalInput(value, separator)
    if (parsed === null) return 'Ingresá un número válido'
    return Number(parsed) > 0 || notPositive
  }

  // El total es opcional: si está vacío, la cantidad se escribe a mano.
  const optionalAmount = (value: string) => {
    if (!value.trim()) return true
    const amount = amountOf(value, separator)
    if (amount === null) return 'Ingresá un número válido'
    return amount > 0 || 'El total debe ser mayor a 0'
  }

  return (
    <Dialog
      title={transaction ? 'Editar transacción' : 'Registrar Transacción'}
      subtitle={transaction ? 'Corregí los datos de la operación' : 'Agregá una operación a tu portafolio'}
      onClose={onClose}
      width={520}
    >
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogBody>
          {formError && <Alert>{formError.message}</Alert>}

          <SegmentedControl
            label="Tipo de operación"
            size="large"
            options={[
              { value: 'BUY', label: '↑ Compra', tone: 'gain' },
              { value: 'SELL', label: '↓ Venta', tone: 'loss' },
            ]}
            value={type}
            onChange={(value) => setValue('type', value)}
          />

          <div className={styles.field}>
            <div className={styles.labelRow}>
              <span className={styles.label}>Activo</span>
              {held !== null && asset && (
                <span className={styles.held}>Tenés {formatQuantity(held, asset.type, separator)}</span>
              )}
            </div>
            <Controller
              control={control}
              name="assetSymbol"
              rules={{ required: 'Elegí un activo' }}
              render={({ field }) => (
                <AssetPicker
                  assets={assets}
                  value={field.value}
                  onChange={field.onChange}
                  invalid={!!errors.assetSymbol}
                  describedBy={errors.assetSymbol ? assetErrorId : undefined}
                />
              )}
            />
            {errors.assetSymbol && (
              <p id={assetErrorId} className={styles.error}>
                <AlertIcon size={13} /> {errors.assetSymbol.message}
              </p>
            )}
          </div>

          <div className={styles.row}>
            <TextField
              label="Cantidad"
              inputMode="decimal"
              autoComplete="off"
              placeholder={separator === 'COMMA' ? '0,05' : '0.05'}
              error={errors.quantity?.message}
              {...register('quantity', {
                validate: validDecimal('Ingresá una cantidad', 'La cantidad debe ser mayor a 0'),
                onChange: (event) => {
                  setValue('lastEdited', 'quantity')
                  setValue('total', totalFor(event.target.value, getValues('price'), separator))
                },
              })}
            />
            <TextField
              label={`Precio unitario (${asset ? CURRENCY_SYMBOLS[asset.currency] : '$'})`}
              inputMode="decimal"
              autoComplete="off"
              error={errors.price?.message}
              {...register('price', {
                validate: validDecimal('Ingresá un precio', 'El precio debe ser mayor a 0'),
                onChange: (event) => {
                  const newPrice = event.target.value
                  setPricePrefilled(false) // lo escribió el usuario: ya no se vacía solo
                  if (getValues('lastEdited') === 'total') {
                    setValue('quantity', quantityFor(getValues('total'), newPrice, separator))
                  } else {
                    setValue('total', totalFor(getValues('quantity'), newPrice, separator))
                  }
                },
              })}
            >
              {tradeDate !== todayInArgentina() && !price && (
                <p className={styles.muted}>Es una operación de otro día: escribí el precio de esa fecha.</p>
              )}
            </TextField>
          </div>

          {/* Se puede escribir el total: la cantidad sale de dividirlo por el precio. */}
          <TextField
            label={`Total operación (${asset ? CURRENCY_SYMBOLS[asset.currency] : '$'})`}
            inputMode="decimal"
            autoComplete="off"
            placeholder="Si lo sabés, escribí el total y calculamos la cantidad"
            error={errors.total?.message}
            {...register('total', {
              validate: optionalAmount,
              onChange: (event) => {
                setValue('lastEdited', 'total')
                setValue('quantity', quantityFor(event.target.value, getValues('price'), separator))
              },
            })}
          >
            {(totalText ?? '').trim() !== '' && (amountOf(price, separator) ?? 0) <= 0 && (
              <p className={styles.muted}>Escribí el precio para calcular la cantidad.</p>
            )}
          </TextField>

          <div className={styles.row}>
            <TextField
              label="Fecha"
              type="date"
              max={todayInArgentina()}
              error={errors.tradeDate?.message}
              {...register('tradeDate', {
                required: 'Ingresá la fecha de la operación',
                validate: (value) => value <= todayInArgentina() || 'La fecha no puede ser futura',
                onChange: (event) => {
                  // Un día distinto de hoy: la cotización de hoy que había quedado ya no corresponde.
                  if (event.target.value !== todayInArgentina() && pricePrefilled) {
                    setPricePrefilled(false)
                    setValue('price', '')
                    if (getValues('lastEdited') === 'total') {
                      setValue('quantity', '')
                    } else {
                      setValue('total', '')
                    }
                  }
                },
              })}
            />
            <div className={styles.field}>
              <label className={styles.label} htmlFor="register-portfolio">
                Portafolio
              </label>
              <select
                id="register-portfolio"
                className={`${styles.select} ${errors.portfolioId ? styles.invalid : ''}`}
                aria-invalid={errors.portfolioId ? true : undefined}
                {...register('portfolioId', { required: 'Elegí un portafolio' })}
              >
                {portfolios.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
              {errors.portfolioId && (
                <p className={styles.error}>
                  <AlertIcon size={13} /> {errors.portfolioId.message}
                </p>
              )}
            </div>
          </div>

          <div className={styles.field}>
            <label className={styles.label} htmlFor="register-notes">
              Notas <span className={styles.optional}>(opcional)</span>
            </label>
            <textarea
              id="register-notes"
              className={`${styles.textarea} ${errors.notes ? styles.invalid : ''}`}
              placeholder="Ej: compra en baja, estrategia de largo plazo"
              rows={2}
              {...register('notes', { maxLength: { value: 500, message: 'Las notas pueden tener hasta 500 caracteres' } })}
            />
            {errors.notes && (
              <p className={styles.error}>
                <AlertIcon size={13} /> {errors.notes.message}
              </p>
            )}
          </div>

          {isSubmitted && errorCount > 0 && (
            <p className={styles.note} role="status">
              <AlertIcon size={14} />
              {errorCount === 1
                ? 'Revisá el campo marcado para continuar.'
                : `Revisá los ${errorCount} campos marcados para continuar.`}
            </p>
          )}
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" loading={isSubmitting} className={type === 'SELL' ? styles.sellButton : undefined}>
            {transaction ? 'Guardar cambios' : type === 'BUY' ? 'Registrar compra' : 'Registrar venta'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  )
}
