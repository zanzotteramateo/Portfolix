import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { api, toApiErrorData, type ApiErrorData } from '../../api/client'
import { applyFieldErrors } from '../../api/formErrors'
import type { DeleteAccountRequest, DeletionSummaryResponse, UserResponse } from '../../api/types'
import { useAppDispatch } from '../../app/hooks'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Dialog, DialogBody, DialogFooter } from '../../components/Dialog'
import { TextField } from '../../components/TextField'
import { plural } from '../../format/format'
import { useApiResource } from '../../hooks/useApiResource'
import { logout } from '../auth/authSlice'
import { downloadTransactionsCsv } from '../transactions/downloadTransactionsCsv'
import { NoPasswordNotice } from './NoPasswordNotice'
import styles from './AccountDialogs.module.css'

interface DeleteForm {
  confirmation: string
  currentPassword: string
}

const FIELDS = ['currentPassword'] as const
const CONFIRMATION_WORD = 'ELIMINAR'

/**
 * Eliminar la cuenta: muestra lo que se borra, ofrece el CSV del historial antes, y pide escribir ELIMINAR y
 * la contraseña. Borra todo en cascada (en el backend) y cierra la sesión.
 */
export function DeleteAccountDialog({ user, onClose }: { user: UserResponse; onClose: () => void }) {
  const dispatch = useAppDispatch()
  const summary = useApiResource<DeletionSummaryResponse>(user.hasPassword ? '/me/deletion-summary' : null)
  const [error, setError] = useState<ApiErrorData | null>(null)
  const [exportError, setExportError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<DeleteForm>({ defaultValues: { confirmation: '', currentPassword: '' } })

  async function onSubmit(values: DeleteForm) {
    setError(null)
    const request: DeleteAccountRequest = { currentPassword: values.currentPassword }
    try {
      await api<void>('DELETE', '/me', request)
      dispatch(logout()) // la cookie ya la borró el backend; esto limpia el estado de este navegador
      onClose()
    } catch (e) {
      const data = toApiErrorData(e)
      if (!applyFieldErrors(data, setFieldError, FIELDS)) {
        setError(data)
      }
    }
  }

  async function exportCsv() {
    setExportError(null)
    try {
      await downloadTransactionsCsv()
    } catch (e) {
      setExportError(toApiErrorData(e).message)
    }
  }

  if (!user.hasPassword) {
    return (
      <Dialog title="Eliminar cuenta" onClose={onClose}>
        <DialogBody>
          <NoPasswordNotice purpose="Para eliminar la cuenta" />
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cerrar
          </Button>
        </DialogFooter>
      </Dialog>
    )
  }

  const data = summary.data

  return (
    <Dialog title="Eliminar cuenta" subtitle="Esta acción no se puede deshacer." onClose={onClose} width={520}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogBody>
          {error && <Alert>{error.message}</Alert>}
          {summary.error && <Alert>{summary.error.message}</Alert>}

          <p className={styles.text}>Se van a eliminar de forma definitiva:</p>
          {data ? (
            <ul className={styles.list}>
              <li>
                <strong>{plural(data.portfolios, 'portafolio', 'portafolios')}</strong> y{' '}
                <strong>{plural(data.assets, 'activo', 'activos')}</strong>
              </li>
              <li>
                <strong>{plural(data.transactions, 'transacción registrada', 'transacciones registradas')}</strong>
              </li>
              <li>
                Tus preferencias y tu acceso con <strong>{data.email}</strong>
              </li>
            </ul>
          ) : (
            <p className={styles.hint}>{summary.error ? '' : 'Contando lo que se va a eliminar…'}</p>
          )}

          <div className={styles.block}>
            <Button type="button" variant="secondary" onClick={exportCsv}>
              Descargar CSV del historial
            </Button>
            {exportError && <p className={styles.hint}>{exportError}</p>}
          </div>

          <div className={`${styles.fields} ${styles.block}`}>
            <TextField
              label={`Escribí ${CONFIRMATION_WORD} para confirmar`}
              autoComplete="off"
              error={errors.confirmation?.message}
              {...register('confirmation', {
                validate: (value) => value === CONFIRMATION_WORD || `Escribí ${CONFIRMATION_WORD} tal cual`,
              })}
            />
            <TextField
              label="Contraseña actual"
              type="password"
              autoComplete="current-password"
              error={errors.currentPassword?.message}
              {...register('currentPassword', { required: 'Ingresá tu contraseña actual' })}
            />
          </div>
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" className={styles.danger} loading={isSubmitting}>
            Eliminar mi cuenta
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  )
}
