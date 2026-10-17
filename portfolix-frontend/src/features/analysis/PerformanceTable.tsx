import type { AssetPerformance, Currency, Decimal } from '../../api/types'
import { AssetIcon, TypeBadge } from '../../components/AssetBadges'
import { Pill } from '../../components/Pill'
import { plural, tone, toNumber } from '../../format/format'
import { useFormat } from '../../format/useFormat'
import table from '../../styles/table.module.css'
import tones from '../../styles/tones.module.css'
import styles from './Analysis.module.css'

const ESTIMATED_HINT =
  'Precio estimado: algunos días no hay cotización de mercado (ej.: todavía no cotizaba en la fuente) y se usó el de tu última operación.'

/**
 * Un activo por fila: todos los que tuviste en algún momento del período, incluidos los que vendiste. Vienen
 * ordenados de mayor a menor resultado (el backend). Un clic en la fila abre el detalle del activo.
 */
export function PerformanceTable({ assets, currency, onSelect }: {
  assets: AssetPerformance[]
  currency: Currency
  onSelect: (symbol: string) => void
}) {
  const format = useFormat()
  const anyEstimated = assets.some((a) => a.estimatedPrice)

  return (
    <section aria-label="Resultado por activo">
      <p className={styles.tableInfo}>{plural(assets.length, 'activo', 'activos')} · clic en una fila para ver el detalle</p>
      <div className={table.wrapper}>
        <table className={table.table}>
          <thead>
            <tr>
              <th scope="col">Activo</th>
              <th scope="col">Tipo</th>
              <th scope="col" className={table.numeric}>Al inicio</th>
              <th scope="col" className={table.numeric}>Compras</th>
              <th scope="col" className={table.numeric}>Ventas</th>
              <th scope="col" className={table.numeric}>Al final</th>
              <th scope="col" className={`${table.numeric} ${table.sorted}`}>Resultado</th>
            </tr>
          </thead>
          <tbody>
            {assets.map((a) => (
              <tr key={a.symbol} className={table.clickable} onClick={() => onSelect(a.symbol)}>
                <td>
                  {/* El botón hace la fila accesible con el teclado; el clic en cualquier lado de la fila también abre. */}
                  <button type="button" className={styles.assetButton} aria-label={`Ver el detalle de ${a.symbol}`}>
                    <AssetIcon symbol={a.symbol} />
                    <span>
                      <span className={styles.symbol}>
                        {a.symbol}
                        {a.estimatedPrice && (
                          <span className={styles.estimated} title={ESTIMATED_HINT}>
                            {' '}≈<span className="visually-hidden"> (precio estimado)</span>
                          </span>
                        )}
                      </span>
                      <span className={styles.assetName}>{a.name}</span>
                    </span>
                  </button>
                </td>
                <td>
                  <TypeBadge type={a.type} />
                </td>
                <ValueCell value={a.startValue} quantity={a.startQuantity} asset={a} currency={currency} />
                <td className={`${table.numeric} ${isZero(a.bought) ? table.muted : ''}`}>
                  {isZero(a.bought) ? '—' : format.money(a.bought, currency)}
                </td>
                <td className={`${table.numeric} ${isZero(a.sold) ? table.muted : ''}`}>
                  {isZero(a.sold) ? '—' : format.money(a.sold, currency)}
                </td>
                <ValueCell value={a.endValue} quantity={a.endQuantity} asset={a} currency={currency} />
                <td className={table.numeric}>
                  <span className={`${styles.cellMain} ${tones[tone(a.pnl)]}`}>{format.money(a.pnl, currency, { sign: true })}</span>
                  {a.pnlPercent !== null && (
                    <span className={styles.cellPill}>
                      <Pill tone={tone(a.pnlPercent)}>{format.percent(a.pnlPercent, { sign: true })}</Pill>
                    </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {anyEstimated && <p className={styles.footnote}>≈ {ESTIMATED_HINT}</p>}
    </section>
  )
}

/** Valor con la cantidad debajo; sin tenencia, un guion. */
function ValueCell({ value, quantity, asset, currency }: {
  value: Decimal
  quantity: Decimal
  asset: AssetPerformance
  currency: Currency
}) {
  const format = useFormat()
  if (isZero(quantity)) {
    return <td className={`${table.numeric} ${table.muted}`}>—</td>
  }
  return (
    <td className={table.numeric}>
      <span className={styles.cellMain}>{format.money(value, currency)}</span>
      <span className={styles.cellSub}>{format.quantity(quantity, asset.type)}</span>
    </td>
  )
}

function isZero(value: Decimal): boolean {
  return toNumber(value) === 0
}
