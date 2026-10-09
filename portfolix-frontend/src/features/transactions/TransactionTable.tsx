import type { TransactionResponse } from '../../api/types'
import { AssetIcon } from '../../components/AssetBadges'
import { PencilIcon, TrashIcon } from '../../components/icons'
import { formatDate } from '../../format/format'
import { useFormat } from '../../format/useFormat'
import styles from './TransactionTable.module.css'

/** La tabla de operaciones de una página, con editar y borrar en cada fila. */
export function TransactionTable({ transactions, onEdit, onDelete }: {
  transactions: TransactionResponse[]
  onEdit: (transaction: TransactionResponse) => void
  onDelete: (transaction: TransactionResponse) => void
}) {
  const format = useFormat()

  return (
    <div className={styles.tableWrapper}>
      <table className={styles.table}>
        <caption className={styles.visuallyHidden}>Operaciones</caption>
        <thead>
          <tr>
            <th scope="col">Fecha</th>
            <th scope="col">Activo</th>
            <th scope="col">Tipo</th>
            <th scope="col" className={styles.numeric}>Cantidad</th>
            <th scope="col" className={styles.numeric}>Precio unitario</th>
            <th scope="col" className={styles.numeric}>Monto total</th>
            <th scope="col">Portafolio</th>
            <th scope="col">
              <span className={styles.visuallyHidden}>Acciones</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((tx) => {
            const date = formatDate(tx.tradeDate)
            return (
              <tr key={tx.id}>
                <td>{date}</td>
                <td>
                  <div className={styles.asset}>
                    <AssetIcon symbol={tx.asset.symbol} />
                    <span className={styles.assetText}>
                      <span className={styles.symbol}>{tx.asset.symbol}</span>
                      <span className={styles.assetName}>{tx.asset.name}</span>
                    </span>
                  </div>
                </td>
                <td>
                  <span className={`${styles.typePill} ${tx.type === 'BUY' ? styles.buy : styles.sell}`}>
                    {tx.type === 'BUY' ? '↑ Compra' : '↓ Venta'}
                  </span>
                </td>
                <td className={styles.numeric}>{format.quantity(tx.quantity, tx.asset.type)}</td>
                <td className={styles.numeric}>{format.price(tx.price, tx.currency)}</td>
                <td className={styles.numeric}>{format.money(tx.total, tx.currency)}</td>
                <td className={styles.portfolio} title={tx.portfolio.name}>
                  {tx.portfolio.name}
                </td>
                <td>
                  <div className={styles.actions}>
                    <button
                      type="button"
                      className={styles.iconButton}
                      onClick={() => onEdit(tx)}
                      aria-label={`Editar la operación de ${tx.asset.symbol} del ${date}`}
                      title="Editar"
                    >
                      <PencilIcon />
                    </button>
                    <button
                      type="button"
                      className={`${styles.iconButton} ${styles.deleteButton}`}
                      onClick={() => onDelete(tx)}
                      aria-label={`Eliminar la operación de ${tx.asset.symbol} del ${date}`}
                      title="Eliminar"
                    >
                      <TrashIcon />
                    </button>
                  </div>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
