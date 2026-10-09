import type { ReactNode } from 'react'
import styles from './Donut.module.css'

export interface DonutSegment {
  value: number
  color: string
}

/**
 * Anillo de distribución en SVG, sin librería: cada segmento es el mismo círculo dibujado solo en parte
 * (stroke-dasharray = "largo del segmento, resto del círculo"), corrido para empezar donde terminó el anterior.
 */
export function Donut({ segments, size = 76, thickness = 9, children, label }: {
  segments: DonutSegment[]
  size?: number
  thickness?: number
  /** Lo que va en el centro (ej.: "5,9M"). */
  children?: ReactNode
  /** Descripción para lectores de pantalla. */
  label: string
}) {
  const radius = (size - thickness) / 2
  const circumference = 2 * Math.PI * radius
  const total = segments.reduce((sum, s) => sum + Math.max(s.value, 0), 0)
  const visible = segments.filter((s) => s.value > 0)
  const gap = visible.length > 1 ? 2 : 0 // una separación chiquita entre segmentos

  let offset = 0
  return (
    <div className={styles.donut} style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={label}>
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="var(--border-default)" strokeWidth={thickness} />
        {total > 0 &&
          visible.map((segment, index) => {
            const length = (segment.value / total) * circumference
            const dash = Math.max(length - gap, 0)
            const circle = (
              <circle
                key={index}
                cx={size / 2}
                cy={size / 2}
                r={radius}
                fill="none"
                stroke={segment.color}
                strokeWidth={thickness}
                strokeDasharray={`${dash} ${circumference - dash}`}
                strokeDashoffset={-offset}
                transform={`rotate(-90 ${size / 2} ${size / 2})`}
              />
            )
            offset += length
            return circle
          })}
      </svg>
      {children && <div className={styles.center}>{children}</div>}
    </div>
  )
}
