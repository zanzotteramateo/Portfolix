import type { TransactionResponse } from '../../api/types'
import { AssetIcon } from '../../components/AssetBadges'
import { PencilIcon, TrashIcon } from '../../components/icons'
import { Pill } from '../../components/Pill'
import { formatDate } from '../../format/format'
import { useFormat } from '../../format/useFormat'
import table from '../../styles/table.module.css'
import styles from './TransactionTable.module.css'

/** La tabla de operaciones de una página, con editar y borrar en cada fila. */
export function TransactionTable({ transactions, onEdit, onDelete }: {
  transactions: TransactionResponse[]
  onEdit: (transaction: TransactionResponse) => void
  onDelete: (transaction: TransactionResponse) => void
}) {
  const format = useFormat()

  return (
    <div className={table.wrapper}>
      <table className={table.table}>
        <caption className="visually-hidden">Operaciones</caption>
        <thead>
          <tr>
            <th scope="col">Fecha</th>
            <th scope="col">Activo</th>
            <th scope="col">Tipo</th>
            <th scope="col" className={table.numeric}>Cantidad</th>
            <th scope="col" className={table.numeric}>Precio unitario</th>
            <th scope="col" className={table.numeric}>Monto total</th>
            <th scope="col">Portafolio</th>
            <th scope="col">
              <span className="visually-hidden">Acciones</span>
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
                  <Pill tone={tx.type === 'BUY' ? 'gain' : 'loss'} size="small">
                    {tx.type === 'BUY' ? '↑ Compra' : '↓ Venta'}
                  </Pill>
                </td>
                <td className={table.numeric}>{format.quantity(tx.quantity, tx.asset.type)}</td>
                <td className={table.numeric}>{format.price(tx.price, tx.currency)}</td>
                <td className={table.numeric}>{format.money(tx.total, tx.currency)}</td>
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
