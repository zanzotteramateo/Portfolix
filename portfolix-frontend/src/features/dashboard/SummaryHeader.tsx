import type { AssetType, DashboardSummaryResponse } from '../../api/types'
import { TYPE_COLORS, TYPE_PLURALS } from '../../format/assetTypes'
import { Donut } from '../../components/charts/Donut'
import { tone, toNumber } from '../../format/format'
import { useFormat } from '../../format/useFormat'
import styles from './Dashboard.module.css'

/** El orden de la leyenda del diseño. */
const TYPE_ORDER: AssetType[] = ['CRYPTO', 'CEDEAR', 'STOCK']

/** La cabecera del dashboard: valor actual, ganancia, capital invertido, cantidad de activos y distribución. */
export function SummaryHeader({ summary }: { summary: DashboardSummaryResponse }) {
  const format = useFormat()
  const { currency } = summary
  const other = currency === 'ARS' ? 'USD' : 'ARS'
  const rate = toNumber(summary.fx.rate)
  const value = toNumber(summary.currentValue)
  // El valor en la otra moneda, con el dólar de hoy que usó el backend (pesos por dólar).
  const otherValue = rate > 0 ? (currency === 'ARS' ? value / rate : value * rate) : null
  const distribution = TYPE_ORDER.map((type) => summary.distribution.find((d) => d.type === type)).filter(
    (d): d is DashboardSummaryResponse['distribution'][number] => d !== undefined,
  )

  return (
    <section className={styles.summary} aria-label="Resumen de la cartera">
      <div className={styles.valueBlock}>
        <p className={styles.bigValue}>
          {format.money(value, currency)}
          {otherValue !== null && (
            <span className={styles.otherValue} title={`Dólar ${summary.fx.type.toLowerCase()}: ${format.price(rate, 'ARS')}`}>
              {format.money(otherValue, other)}
            </span>
          )}
        </p>
        <span className={`${styles.pill} ${styles[tone(summary.totalPnlPercent)]}`}>
          {format.percent(summary.totalPnlPercent ?? 0, { sign: true })}
        </span>
      </div>

      <dl className={styles.stats}>
        <div>
          <dt>Ganancia total</dt>
          <dd className={styles[tone(summary.totalPnl)]}>
            {summary.holdingsCount === 0 && toNumber(summary.totalPnl) === 0 ? '—' : format.money(summary.totalPnl, currency, { sign: true })}
          </dd>
        </div>
        <div>
          <dt>Capital invertido</dt>
          <dd>{summary.holdingsCount === 0 ? '—' : format.money(summary.investedCapital, currency)}</dd>
        </div>
        <div>
          <dt>Activos</dt>
          <dd>{summary.holdingsCount === 0 ? '—' : summary.holdingsCount}</dd>
        </div>
      </dl>

      <div className={styles.distribution}>
        <Donut
          label="Distribución por tipo de activo"
          segments={distribution.map((d) => ({ value: toNumber(d.value), color: TYPE_COLORS[d.type] }))}
        >
          <span className={styles.donutValue}>{format.compact(value)}</span>
          <span className={styles.donutCurrency}>{currency}</span>
        </Donut>
        <ul className={styles.legend}>
          {distribution.map((d) => (
            <li key={d.type}>
              <span className={styles.legendDot} style={{ background: TYPE_COLORS[d.type] }} aria-hidden="true" />
              <span className={styles.legendText}>
                <span>
                  {TYPE_PLURALS[d.type]}{' '}
                  <span style={{ color: TYPE_COLORS[d.type] }}>{d.percent === null ? '—' : format.percent(d.percent)}</span>
                </span>
                <span className={styles.legendValue}>{format.money(d.value, currency)}</span>
              </span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}
