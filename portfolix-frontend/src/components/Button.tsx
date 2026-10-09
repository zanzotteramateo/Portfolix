import type { ComponentPropsWithRef } from 'react'
import styles from './Button.module.css'

type ButtonProps = ComponentPropsWithRef<'button'> & {
  variant?: 'primary' | 'secondary'
  /** Muestra un indicador y deshabilita el botón mientras espera la respuesta. */
  loading?: boolean
}

export function Button({ variant = 'primary', loading = false, disabled, children, className, ...props }: ButtonProps) {
  return (
    <button
      className={`${styles.button} ${styles[variant]} ${className ?? ''}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <span className={styles.spinner} aria-hidden="true" />}
      {children}
    </button>
  )
}
