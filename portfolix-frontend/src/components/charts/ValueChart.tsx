import { useId, useLayoutEffect, useRef, useState, type KeyboardEvent, type PointerEvent, type RefObject } from 'react'
import { formatDate, formatDayMonth, formatMonthYear, tone } from '../../format/format'
import styles from './ValueChart.module.css'

export interface ValuePoint {
  /** AAAA-MM-DD */
  date: string
  value: number
  netInvested: number
}

const HEIGHT = 260
const MARGIN = { top: 12, right: 12, bottom: 28, left: 76 }
/** Con un rango de hasta tantos días, el eje muestra días ("16/10"); con más, meses ("oct '26"). */
const DAILY_LABELS_UP_TO = 92

/**
 * La evolución de un período: el valor de las inversiones (línea llena) y la plata puesta (punteada). La distancia
 * entre las dos es lo que se lleva ganado o perdido. Con ejes y, al pasar el mouse (o con las flechas del teclado),
 * el detalle del día. A diferencia de LineChart, se dibuja al ancho real del contenedor (lo mide con un
 * ResizeObserver): con un viewBox estirado, el texto de los ejes se deformaría.
 */
export function ValueChart({ points, formatAmount, formatAxis, hideAxis, label }: {
  points: ValuePoint[]
  /** El monto completo, para el detalle ("$ 1.234.567"). */
  formatAmount: (value: number, options?: { sign?: boolean }) => string
  /** El monto corto, para el eje ("$ 1,2M"). */
  formatAxis: (value: number) => string
  /** "Ocultar montos": sin números en el eje. */
  hideAxis: boolean
  label: string
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const width = useWidth(containerRef)
  const gradientId = useId()
  const [active, setActive] = useState<number | null>(null)

  if (points.length < 2) {
    return <div ref={containerRef} className={styles.chart} style={{ height: HEIGHT }} />
  }

  const plotWidth = Math.max(width - MARGIN.left - MARGIN.right, 1)
  const plotHeight = HEIGHT - MARGIN.top - MARGIN.bottom
  const ticks = niceTicks(points.flatMap((p) => [p.value, p.netInvested]))
  const low = ticks[0]
  const high = ticks[ticks.length - 1]
  const x = (i: number) => MARGIN.left + (i / (points.length - 1)) * plotWidth
  const y = (v: number) => MARGIN.top + (1 - (v - low) / (high - low)) * plotHeight
  const path = (pick: (p: ValuePoint) => number) =>
    points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(pick(p)).toFixed(1)}`).join(' ')
  const valueLine = path((p) => p.value)
  const bottom = MARGIN.top + plotHeight

  const days = (Date.parse(points[points.length - 1].date) - Date.parse(points[0].date)) / 86_400_000
  const dateLabel = days <= DAILY_LABELS_UP_TO ? formatDayMonth : formatMonthYear
  // Las fechas del eje arrancan en el segundo punto: el primero es el valor inicial (el cierre del día anterior
  // al período), y "dic '24" debajo del análisis de 2025 confundiría. El detalle sí muestra su fecha real.
  const firstLabeled = points.length > 2 ? 1 : 0
  const labeled = points.length - firstLabeled
  const labelCount = Math.max(2, Math.min(6, Math.floor(plotWidth / 90), labeled))
  const labelIndexes = [...new Set(Array.from({ length: labelCount }, (_, k) =>
    firstLabeled + Math.round((k * (labeled - 1)) / (labelCount - 1))))]

  function pick(e: PointerEvent<SVGRectElement>) {
    const box = e.currentTarget.getBoundingClientRect()
    const ratio = (e.clientX - box.left) / box.width
    setActive(Math.min(points.length - 1, Math.max(0, Math.round(ratio * (points.length - 1)))))
  }

  function onKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    const step = e.key === 'ArrowLeft' ? -1 : e.key === 'ArrowRight' ? 1 : 0
    if (step === 0) return
    e.preventDefault()
    setActive((current) => Math.min(points.length - 1, Math.max(0, (current ?? points.length - 1) + (current === null ? 0 : step))))
  }

  const point = active === null ? null : points[active]
  const difference = point ? point.value - point.netInvested : 0

  return (
    <div
      ref={containerRef}
      className={styles.chart}
      tabIndex={0}
      role="group"
      aria-label={`${label}. Usá las flechas para recorrer los días.`}
      onKeyDown={onKeyDown}
      onBlur={() => setActive(null)}
    >
      {width > 0 && (
        <svg width={width} height={HEIGHT} role="img" aria-label={label}>
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="var(--accent)" stopOpacity="0.18" />
              <stop offset="1" stopColor="var(--accent)" stopOpacity="0" />
            </linearGradient>
          </defs>

          {ticks.map((tick) => (
            <g key={tick}>
              <line x1={MARGIN.left} x2={MARGIN.left + plotWidth} y1={y(tick)} y2={y(tick)} className={styles.grid} />
              {!hideAxis && (
                <text x={MARGIN.left - 8} y={y(tick)} className={styles.axisY}>
                  {formatAxis(tick)}
                </text>
              )}
            </g>
          ))}
          {labelIndexes.map((i) => (
            <text key={i} x={x(i)} y={HEIGHT - 8} className={styles.axisX} textAnchor={i === firstLabeled ? 'start' : i === points.length - 1 ? 'end' : 'middle'}>
              {dateLabel(points[i].date)}
            </text>
          ))}

          <path d={`${valueLine} L${x(points.length - 1)},${bottom} L${x(0)},${bottom} Z`} fill={`url(#${gradientId})`} />
          <path d={path((p) => p.netInvested)} className={styles.netLine} />
          <path d={valueLine} className={styles.valueLine} />

          {point && active !== null && (
            <g aria-hidden="true">
              <line x1={x(active)} x2={x(active)} y1={MARGIN.top} y2={bottom} className={styles.cursor} />
              <circle cx={x(active)} cy={y(point.netInvested)} r={3.5} className={styles.netDot} />
              <circle cx={x(active)} cy={y(point.value)} r={4} className={styles.valueDot} />
            </g>
          )}
          <rect
            x={MARGIN.left}
            y={MARGIN.top}
            width={plotWidth}
            height={plotHeight}
            fill="transparent"
            onPointerMove={pick}
            onPointerDown={pick}
            onPointerLeave={() => setActive(null)}
          />
        </svg>
      )}

      {point && active !== null && (
        <div
          className={styles.tooltip}
          style={x(active) > width / 2 ? { right: width - x(active) + 10 } : { left: x(active) + 10 }}
          aria-live="polite"
        >
          <span className={styles.tooltipDate}>{formatDate(point.date)}</span>
          <span className={styles.tooltipRow}>
            <span>Valor</span>
            <span>{formatAmount(point.value)}</span>
          </span>
          <span className={styles.tooltipRow}>
            <span>Plata puesta</span>
            <span>{formatAmount(point.netInvested)}</span>
          </span>
          <span className={`${styles.tooltipRow} ${styles[tone(difference)] ?? ''}`}>
            <span>Resultado</span>
            <span>{formatAmount(difference, { sign: true })}</span>
          </span>
        </div>
      )}
    </div>
  )
}

/** El ancho del contenedor, actualizado cuando cambia (ventana, menú lateral). */
function useWidth(ref: RefObject<HTMLDivElement | null>): number {
  const [width, setWidth] = useState(0)
  useLayoutEffect(() => {
    const element = ref.current
    if (!element) return
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width))
    observer.observe(element)
    return () => observer.disconnect()
  }, [ref])
  return width
}

/**
 * Hasta ~5 valores "redondos" (1, 2, 2,5 o 5 por una potencia de 10) que cubren los datos: el eje muestra
 * "$ 1M · $ 1,5M · $ 2M" en vez de "$ 1,03M · $ 1,41M…".
 */
function niceTicks(values: number[]): number[] {
  let min = Math.min(...values)
  let max = Math.max(...values)
  if (min === max) {
    // Una línea plana (o todo en cero): un margen alrededor para que quede en el medio.
    min = min === 0 ? 0 : min * 0.9
    max = max === 0 ? 1 : max * 1.1
  }
  const raw = (max - min) / 4
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const normalized = raw / magnitude
  const step = (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 2.5 ? 2.5 : normalized <= 5 ? 5 : 10) * magnitude
  const start = Math.floor(min / step) * step
  const ticks = [start]
  // Hasta el primer valor redondo que llega al máximo (si no, la línea se saldría por arriba).
  for (let k = 1; ticks[ticks.length - 1] < max; k++) {
    ticks.push(Number((start + k * step).toPrecision(12))) // sin restos de coma flotante (0,30000000000000004)
  }
  return ticks
}
