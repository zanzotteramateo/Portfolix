import type { ReactNode } from 'react'
import styles from './Pill.module.css'

/**
 * Etiqueta con el color de una ganancia o una pérdida: los porcentajes ("+12,5%") y el tipo de operación
 * ("↑ Compra"). El tono de un número sale de tone() de format.ts.
 */
export function Pill({ tone, size = 'normal', title, children }: {
  tone: 'gain' | 'loss' | 'neutral'
  /** small: más chica y sin fuente monoespaciada, para el tipo de operación en las tablas. */
  size?: 'normal' | 'small'
  /** Texto que aparece al pasar el mouse (ej.: cómo se calcula el porcentaje). */
  title?: string
  children: ReactNode
}) {
  return (
    <span className={`${styles.pill} ${styles[size]} ${styles[tone]}`} title={title}>
      {children}
    </span>
  )
}
