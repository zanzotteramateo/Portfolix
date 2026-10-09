import type { ComponentPropsWithRef, ReactNode } from 'react'
import { CheckIcon } from './icons'
import styles from './Checkbox.module.css'

type CheckboxProps = Omit<ComponentPropsWithRef<'input'>, 'type'> & { children: ReactNode }

/** Casilla nativa (accesible con teclado y lectores de pantalla) con el estilo del diseño. */
export function Checkbox({ children, className, ...inputProps }: CheckboxProps) {
  return (
    <label className={`${styles.checkbox} ${className ?? ''}`}>
      <input type="checkbox" className={styles.input} {...inputProps} />
      <span className={styles.box} aria-hidden="true">
        <CheckIcon size={11} strokeWidth={3} />
      </span>
      <span>{children}</span>
    </label>
  )
}
