import { useMemo, useState } from 'react'
import type { AssetType, Currency, HoldingResponse } from '../../api/types'
import { AssetIcon, TypeBadge } from '../../components/AssetBadges'
import { TYPE_PLURALS } from '../../format/assetTypes'
import { LineChart } from '../../components/charts/LineChart'
import { formatAgo, tone, toNumber } from '../../format/format'
import { useFormat } from '../../format/useFormat'
import styles from './Dashboard.module.css'

type TypeFilter = AssetType | 'ALL'
type SortKey = 'capital' | 'gain' | 'loss'

const FILTERS: { value: TypeFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'STOCK', label: TYPE_PLURALS.STOCK },
  { value: 'CEDEAR', label: TYPE_PLURALS.CEDEAR },
  { value: 'CRYPTO', label: TYPE_PLURALS.CRYPTO },
]

const SORTS: { value: SortKey; label: string }[] = [
  { value: 'gain', label: '↑ Mayor ganancia' },
  { value: 'loss', label: '↓ Mayor pérdida' },
  { value: 'capital', label: '◈ Mayor capital' },
]

const COMPARATORS: Record<SortKey, (a: HoldingResponse, b: HoldingResponse) => number> = {
  capital: (a, b) => toNumber(b.currentValue) - toNumber(a.currentValue),
  gain: (a, b) => toNumber(b.pnl) - toNumber(a.pnl),
  loss: (a, b) => toNumber(a.pnl) - toNumber(b.pnl),
}

/**
 * La tabla de activos. Filtrar y ordenar se hace acá, en el front (el backend devuelve todas las posiciones,
 * por capital actual): son pocas filas y así cambiar de filtro es instantáneo.
 */
export function HoldingsTable({ holdings, currency, pricesUpdatedAt, onSelect }: {
  holdings: HoldingResponse[]
  currency: Currency
  pricesUpdatedAt: string | null
  onSelect: (symbol: string) => void
}) {
  const format = useFormat()
  const [filter, setFilter] = useState<TypeFilter>('ALL')
  const [sort, setSort] = useState<SortKey>('capital')

  const rows = useMemo(
    () => holdings.filter((h) => filter === 'ALL' || h.type === filter).sort(COMPARATORS[sort]),
    [holdings, filter, sort],
  )
  const sortedBy = sort === 'capital' ? 'capital' : 'pnl'

  return (
    <section aria-label="Activos">
      <div className={styles.toolbar}>
        <div className={styles.chips} role="group" aria-label="Filtrar por tipo">
          {FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              className={`${styles.chip} ${filter === f.value ? styles.chipActive : ''}`}
              aria-pressed={filter === f.value}
              onClick={() => setFilter(f.value)}
            >
              {f.label}
            </button>
          ))}
          <span className={styles.chipDivider} aria-hidden="true" />
          {SORTS.map((s) => (
            <button
              key={s.value}
              type="button"
              className={`${styles.chip} ${sort === s.value && s.value !== 'capital' ? styles.chipActive : ''}`}
              aria-pressed={sort === s.value}
              onClick={() => setSort(sort === s.value ? 'capital' : s.value)}
            >
              {s.label}
            </button>
          ))}
        </div>
        <p className={styles.toolbarInfo}>
          {rows.length === 1 ? '1 activo' : `${rows.length} activos`} · clic en fila para detalles
          {pricesUpdatedAt && <> · precios de {formatAgo(pricesUpdatedAt)}</>}
        </p>
      </div>

      <div className={styles.tableWrapper}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col">Símbolo</th>
              <th scope="col">Nombre</th>
              <th scope="col">Tipo</th>
              <th scope="col" className={styles.numeric}>Capital invertido</th>
              <th scope="col" className={styles.numeric}>Precio prom.</th>
              <th scope="col" className={styles.chartColumn}>Gráfico (7D)</th>
              <th scope="col" className={`${styles.numeric} ${sortedBy === 'pnl' ? styles.sortedColumn : ''}`}>P&amp;L</th>
              <th scope="col" className={styles.numeric}>Cantidad</th>
              <th scope="col" className={`${styles.numeric} ${sortedBy === 'capital' ? styles.sortedColumn : ''}`}>
                Capital actual
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((h) => (
              <tr key={h.symbol} className={styles.row} onClick={() => onSelect(h.symbol)}>
                <td>
                  {/* El botón hace la fila accesible con el teclado; el clic en cualquier lado de la fila también abre. */}
                  <button type="button" className={styles.symbolButton} aria-label={`Ver el detalle de ${h.symbol}`}>
                    <AssetIcon symbol={h.symbol} />
                    <span className={styles.symbol}>{h.symbol}</span>
                  </button>
                </td>
                <td className={styles.assetName}>{h.name}</td>
                <td>
                  <TypeBadge type={h.type} />
                </td>
                <td className={styles.numeric}>{format.money(h.investedCapital, currency)}</td>
                <td className={`${styles.numeric} ${styles.muted}`}>
                  {h.averagePrice === null ? '—' : format.price(h.averagePrice, currency)}
                </td>
                <td className={styles.chartColumn}>
                  <Sparkline holding={h} />
                </td>
                <td className={styles.numeric}>
                  {h.pnlPercent === null ? (
                    '—'
                  ) : (
                    <span className={`${styles.pill} ${styles[tone(h.pnlPercent)]}`}>
                      {format.percent(h.pnlPercent, { sign: true })}
                    </span>
                  )}
                </td>
                <td className={`${styles.numeric} ${styles.muted}`}>{format.quantity(h.quantity, h.type)}</td>
                <td className={styles.numeric}>
                  <span className={styles.currentValue}>{format.money(h.currentValue, currency)}</span>
                  <span className={`${styles.subValue} ${styles[tone(h.pnl)]}`}>
                    {format.money(h.pnl, currency, { sign: true })}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && <p className={styles.noRows}>No tenés activos de este tipo.</p>}
      </div>
    </section>
  )
}

/** El minigráfico de 7 días con su variación. Si el backend todavía no lo cargó, un lugar vacío. */
function Sparkline({ holding }: { holding: HoldingResponse }) {
  const format = useFormat()
  if (!holding.sparkline7d || holding.change7dPercent === null) {
    return <span className={styles.sparklinePending} title="El gráfico se está cargando" />
  }
  const trend = tone(holding.change7dPercent)
  const color = trend === 'loss' ? 'var(--status-loss)' : 'var(--status-gain)'
  return (
    <span className={styles.sparkline}>
      <LineChart values={holding.sparkline7d.map(Number)} color={color} height={24} label={`Precio de ${holding.symbol} en 7 días`} />
      <span className={styles[trend]}>{format.percent(holding.change7dPercent, { sign: true })}</span>
    </span>
  )
}
