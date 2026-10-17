import type { ComponentPropsWithoutRef, ReactNode } from 'react'
import styles from './Chips.module.css'

/** Un grupo de chips de filtro en una fila (ej.: Todos · Acciones · CEDEARs · Cryptos). */
export function ChipGroup({ label, children }: {
  /** Para lectores de pantalla (ej.: "Tipo de activo"). */
  label: string
  children: ReactNode
}) {
  return (
    <div className={styles.group} role="group" aria-label={label}>
      {children}
    </div>
  )
}

/** Un chip: un botón que queda resaltado mientras está elegido. */
export function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      className={`${styles.chip} ${active ? styles.active : ''}`}
      aria-pressed={active}
      onClick={onClick}
    >
      {children}
    </button>
  )
}

/** Un selector con forma de chip, para un filtro con muchas opciones (ej.: el año del análisis). */
export function ChipSelect({ active, ...props }: ComponentPropsWithoutRef<'select'> & { active: boolean }) {
  return <select {...props} className={`${styles.chip} ${styles.select} ${active ? styles.active : ''}`} />
}

/** Una raya vertical para separar dos grupos de chips en la misma fila. */
export function ChipDivider() {
  return <span className={styles.divider} aria-hidden="true" />
}
