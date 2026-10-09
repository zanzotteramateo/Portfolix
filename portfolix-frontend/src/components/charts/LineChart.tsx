import { useId } from 'react'

/**
 * Línea de precios en SVG, sin ejes: los minigráficos de la tabla y las tendencias del detalle de activo.
 * Usa un viewBox fijo que se estira al ancho del contenedor (preserveAspectRatio="none"); el trazo no se
 * deforma gracias a vector-effect="non-scaling-stroke".
 */
export function LineChart({ values, color, height = 28, area = false, label }: {
  values: number[]
  color: string
  height?: number
  /** Relleno degradado debajo de la línea (las tendencias del detalle). */
  area?: boolean
  label: string
}) {
  const gradientId = useId()
  const width = 100
  const padding = 2

  if (values.length < 2) {
    return <svg width="100%" height={height} role="img" aria-label={label} />
  }

  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1 // una línea plana queda al medio
  const x = (i: number) => (i / (values.length - 1)) * width
  const y = (v: number) => (max === min ? height / 2 : padding + (1 - (v - min) / range) * (height - padding * 2))
  const line = values.map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(2)},${y(v).toFixed(2)}`).join(' ')

  return (
    <svg
      width="100%"
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      role="img"
      aria-label={label}
    >
      {area && (
        <>
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor={color} stopOpacity="0.25" />
              <stop offset="1" stopColor={color} stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={`${line} L${width},${height} L0,${height} Z`} fill={`url(#${gradientId})`} />
        </>
      )}
      <path d={line} fill="none" stroke={color} strokeWidth={1.5} strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
    </svg>
  )
}
