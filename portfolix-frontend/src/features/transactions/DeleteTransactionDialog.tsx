import { useState } from 'react'
import { api, toApiErrorData } from '../../api/client'
import type { TransactionResponse } from '../../api/types'
import { useAppDispatch } from '../../app/hooks'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Dialog, DialogBody, DialogFooter } from '../../components/Dialog'
import { formatDate } from '../../format/format'
import { useFormat } from '../../format/useFormat'
import { dashboardInvalidated } from '../dashboard/dashboardSlice'
import styles from './DeleteTransactionDialog.module.css'

/**
 * Confirmación para borrar una transacción (DELETE /transactions/{id}). Si una venta posterior dependía de
 * ella, el backend lo rechaza con el motivo y el diálogo lo muestra.
 */
export function DeleteTransactionDialog({ transaction, onDeleted, onClose }: {
  transaction: TransactionResponse
  onDeleted: () => void
  onClose: () => void
}) {
  const dispatch = useAppDispatch()
  const format = useFormat()
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)

  const what = transaction.type === 'BUY' ? 'la compra' : 'la venta'

  async function confirm() {
    setError(null)
    setDeleting(true)
    try {
      await api('DELETE', `/transactions/${transaction.id}`)
      dispatch(dashboardInvalidated()) // las posiciones cambiaron: el dashboard se vuelve a pedir
      onDeleted()
    } catch (e) {
      setError(toApiErrorData(e).message)
      setDeleting(false)
    }
  }

  return (
    <Dialog title="Eliminar transacción" onClose={onClose}>
      <DialogBody>
        {error && <Alert>{error}</Alert>}
        <p className={styles.text}>
          Vas a eliminar {what} de{' '}
          <strong>
            {format.quantity(transaction.quantity, transaction.asset.type)} {transaction.asset.symbol}
          </strong>{' '}
          del {formatDate(transaction.tradeDate)} en {transaction.portfolio.name} ({format.money(transaction.total, transaction.currency)}).
        </p>
        <p className={styles.text}>No se puede deshacer.</p>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="secondary" onClick={onClose}>
          Cancelar
        </Button>
        <Button type="button" className={styles.danger} onClick={confirm} loading={deleting}>
          Eliminar transacción
        </Button>
      </DialogFooter>
    </Dialog>
  )
}
