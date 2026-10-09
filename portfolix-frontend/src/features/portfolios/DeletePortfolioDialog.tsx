import { useEffect, useMemo, useState } from 'react'
import { useMatch, useNavigate } from 'react-router'
import { api } from '../../api/client'
import type { HoldingsResponse, PortfolioResponse, TransactionPageResponse } from '../../api/types'
import { useAppDispatch, useAppSelector } from '../../app/hooks'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Dialog, DialogBody, DialogFooter } from '../../components/Dialog'
import { plural } from '../../format/format'
import { deletePortfolio } from './portfoliosSlice'
import styles from './DeletePortfolioDialog.module.css'

interface Contents {
  assets: number
  transactions: number
}

/**
 * Eliminar un portafolio. Si tiene transacciones, el usuario elige: moverlas a otro portafolio (conserva
 * el historial) o borrarlas. El backend no deja borrar uno con transacciones sin elegir.
 */
export function DeletePortfolioDialog({ portfolio, onClose }: { portfolio: PortfolioResponse; onClose: () => void }) {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const viewedId = useMatch('/portfolios/:portfolioId')?.params.portfolioId
  const items = useAppSelector((state) => state.portfolios.items)
  // El filtro va afuera del selector: si el selector devolviera un array nuevo cada vez, React Redux
  // creería que el estado cambió y volvería a dibujar el componente en cada acción.
  const others = useMemo(() => items.filter((p) => p.id !== portfolio.id), [items, portfolio.id])
  const [contents, setContents] = useState<Contents | null>(null)
  const [choice, setChoice] = useState<'move' | 'delete'>(others.length > 0 ? 'move' : 'delete')
  const [target, setTarget] = useState<number | null>(others[0]?.id ?? null)
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)

  // Cuántos activos y transacciones tiene, para el subtítulo ("Contiene 7 activos y 9 transacciones").
  useEffect(() => {
    let ignore = false
    Promise.all([
      api<HoldingsResponse>('GET', `/holdings?portfolioId=${portfolio.id}`),
      api<TransactionPageResponse>('GET', `/transactions?portfolioId=${portfolio.id}&size=1`),
    ])
      .then(([holdings, page]) => {
        if (!ignore) {
          setContents({ assets: holdings.holdings.length, transactions: page.page.totalElements })
        }
      })
      .catch(() => !ignore && setContents(null))
    return () => {
      ignore = true
    }
  }, [portfolio.id])

  const isEmpty = contents?.transactions === 0
  const transactions = contents?.transactions ?? 0

  async function confirm() {
    setError(null)
    setDeleting(true)
    const moveTo = !isEmpty && choice === 'move' ? target : null
    const result = await dispatch(deletePortfolio({ id: portfolio.id, moveTo }))
    setDeleting(false)
    if (deletePortfolio.rejected.match(result)) {
      setError(result.payload?.message ?? 'No se pudo eliminar el portafolio')
      return
    }
    // Si estaba mirando el que se eliminó, va al destino de las transacciones o a "Todos".
    if (viewedId === String(portfolio.id)) {
      navigate(moveTo !== null ? `/portfolios/${moveTo}` : '/', { replace: true })
    }
    onClose()
  }

  const subtitle = contents
    ? `Contiene ${plural(contents.assets, 'activo', 'activos')} y ${plural(contents.transactions, 'transacción', 'transacciones')}`
    : 'Contando su contenido…'

  return (
    <Dialog title={`Eliminar “${portfolio.name}”`} subtitle={subtitle} onClose={onClose}>
      <DialogBody>
        {error && <Alert>{error}</Alert>}
        {isEmpty ? (
          <p className={styles.text}>El portafolio está vacío: se elimina sin tocar nada más.</p>
        ) : (
          <fieldset className={styles.options} disabled={!contents}>
            <legend className={styles.question}>¿Qué hacemos con sus transacciones?</legend>

            <label className={`${styles.option} ${choice === 'move' ? styles.selected : ''}`}>
              <input
                type="radio"
                name="choice"
                checked={choice === 'move'}
                onChange={() => setChoice('move')}
                disabled={others.length === 0}
              />
              <span className={styles.optionText}>
                <strong>Moverlas a otro portafolio</strong>
                <span className={styles.optionDescription}>
                  {others.length === 0
                    ? 'No tenés otro portafolio adonde moverlas.'
                    : 'Conservás el historial completo: las operaciones pasan al portafolio que elijas.'}
                </span>
                {others.length > 0 && (
                  <select
                    className={styles.select}
                    value={target ?? ''}
                    onChange={(e) => setTarget(Number(e.target.value))}
                    disabled={choice !== 'move'}
                    aria-label="Portafolio de destino"
                  >
                    {others.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                )}
              </span>
            </label>

            <label className={`${styles.option} ${choice === 'delete' ? styles.selectedDanger : ''}`}>
              <input type="radio" name="choice" checked={choice === 'delete'} onChange={() => setChoice('delete')} />
              <span className={styles.optionText}>
                <strong>Eliminarlas junto con el portafolio</strong>
                <span className={styles.optionDescription}>
                  {transactions === 1 ? 'Se borra la transacción' : `Se borran las ${transactions} transacciones`} y
                  no se pueden recuperar.
                </span>
              </span>
            </label>
          </fieldset>
        )}
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="secondary" onClick={onClose}>
          Cancelar
        </Button>
        <Button type="button" className={styles.danger} onClick={confirm} loading={deleting} disabled={!contents}>
          {isEmpty || choice === 'delete' ? 'Eliminar portafolio' : 'Mover y eliminar portafolio'}
        </Button>
      </DialogFooter>
    </Dialog>
  )
}
