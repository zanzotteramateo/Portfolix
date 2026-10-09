import type { ReactNode } from 'react'
import styles from './SegmentedControl.module.css'

/** Botones de opción única pegados (ARS | USD, Compra | Venta). */
export function SegmentedControl<T extends string>({ label, options, value, onChange, size = 'small' }: {
  /** Para lectores de pantalla (ej.: "Moneda"). */
  label: string
  options: { value: T; label: ReactNode; tone?: 'gain' | 'loss' }[]
  value: T
  onChange: (value: T) => void
  size?: 'small' | 'large'
}) {
  return (
    <div className={`${styles.segmented} ${styles[size]}`} role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={option.value === value}
          className={`${styles.option} ${option.value === value ? styles[option.tone ?? 'active'] : ''}`}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
