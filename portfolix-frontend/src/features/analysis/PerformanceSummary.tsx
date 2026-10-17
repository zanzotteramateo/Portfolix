import { useId } from 'react'
import type { AssetPerformance, Currency, PerformanceResponse } from '../../api/types'
import { AssetIcon } from '../../components/AssetBadges'
import { ValueChart } from '../../components/charts/ValueChart'
import { Pill } from '../../components/Pill'
import { CURRENCY_SYMBOLS, tone, toNumber } from '../../format/format'
import { useFormat } from '../../format/useFormat'
import tones from '../../styles/tones.module.css'
import styles from './Analysis.module.css'

const PERCENT_HINT =
  'Rendimiento ponderado por tiempo: tiene en cuenta cuándo entró y salió la plata, no solo cuánto puso.'

/** Las tarjetas de totales, el gráfico de evolución y el mejor y el peor activo. */
export function PerformanceSummary({ performance }: { performance: PerformanceResponse }) {
  const format = useFormat()
  const titleId = useId()
  const { currency } = performance
  const points = performance.series.map((p) => ({
    date: p.date,
    value: toNumber(p.value),
    netInvested: toNumber(p.netInvested),
  }))
  const chartLabel = format.hidden
    ? 'Evolución del valor de tus inversiones en el período'
    : `Evolución del valor de tus inversiones: de ${format.money(performance.startValue, currency)} a ${format.money(performance.endValue, currency)}`

  return (
    <>
      <div className={styles.cards}>
        <section className={styles.card} aria-label="Resultado del período">
          <span className={styles.cardLabel}>Resultado del período</span>
          <span className={styles.resultLine}>
            <span className={`${styles.resultValue} ${tones[tone(performance.pnl)]}`}>
              {format.money(performance.pnl, currency, { sign: true })}
            </span>
            {performance.pnlPercent !== null && (
              <Pill tone={tone(performance.pnlPercent)} title={PERCENT_HINT}>
                {format.percent(performance.pnlPercent, { sign: true })}
              </Pill>
            )}
          </span>
          <span className={styles.cardHint}>Lo que ganaste o perdiste, sin contar la plata que pusiste o sacaste.</span>
        </section>

        <div className={styles.card}>
          <span className={styles.cardLabel}>Valor de tus inversiones</span>
          <span className={styles.cardValue}>
            {format.money(performance.startValue, currency)}
            <span className={styles.arrow} aria-label="a">→</span>
            {format.money(performance.endValue, currency)}
          </span>
          <span className={styles.cardHint}>Al empezar y al terminar el período.</span>
        </div>

        <div className={styles.card}>
          <span className={styles.cardLabel}>Movimientos</span>
          <dl className={styles.movements}>
            <div>
              <dt>Compras</dt>
              <dd>{format.money(performance.bought, currency)}</dd>
            </div>
            <div>
              <dt>Ventas</dt>
              <dd>{format.money(performance.sold, currency)}</dd>
            </div>
          </dl>
        </div>
      </div>

      <div className={styles.chartRow}>
        <section className={styles.chartCard} aria-labelledby={titleId}>
          <header className={styles.chartHeader}>
            <h2 id={titleId} className={styles.sectionTitle}>
              Evolución
            </h2>
            <ul className={styles.chartLegend}>
              <li>
                <span className={styles.legendValue} aria-hidden="true" /> Valor
              </li>
              <li>
                <span className={styles.legendNet} aria-hidden="true" /> Plata puesta
              </li>
            </ul>
          </header>
          <ValueChart
            points={points}
            formatAmount={(value, options) => format.money(value, currency, options)}
            formatAxis={(value) => `${CURRENCY_SYMBOLS[currency]} ${format.compact(value)}`}
            hideAxis={format.hidden}
            label={chartLabel}
          />
          <p className={styles.chartNote}>La distancia entre las dos líneas es lo que llevás ganado (o perdido).</p>
        </section>

        <div className={styles.highlights}>
          <AssetHighlight title="Mejor activo" asset={performance.best} currency={currency} empty="Ningún activo ganó en este período." />
          <AssetHighlight title="Peor activo" asset={performance.worst} currency={currency} empty="Ningún activo perdió en este período." />
        </div>
      </div>
    </>
  )
}

function AssetHighlight({ title, asset, currency, empty }: {
  title: string
  asset: AssetPerformance | null
  currency: Currency
  empty: string
}) {
  const format = useFormat()
  return (
    <section className={styles.card} aria-label={title}>
      <span className={styles.cardLabel}>{title}</span>
      {asset ? (
        <>
          <span className={styles.highlightAsset}>
            <AssetIcon symbol={asset.symbol} />
            <span>
              <span className={styles.symbol}>{asset.symbol}</span>
              <span className={styles.highlightName}>{asset.name}</span>
            </span>
          </span>
          <span className={styles.resultLine}>
            <span className={`${styles.highlightValue} ${tones[tone(asset.pnl)]}`}>
              {format.money(asset.pnl, currency, { sign: true })}
            </span>
            {asset.pnlPercent !== null && (
              <Pill tone={tone(asset.pnlPercent)} title={PERCENT_HINT}>
                {format.percent(asset.pnlPercent, { sign: true })}
              </Pill>
            )}
          </span>
        </>
      ) : (
        <span className={styles.cardHint}>{empty}</span>
      )}
    </section>
  )
}
