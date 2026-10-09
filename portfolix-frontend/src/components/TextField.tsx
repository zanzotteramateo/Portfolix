import { useId, useState, type ComponentPropsWithRef, type ReactNode } from 'react'
import { AlertIcon, EyeIcon, EyeOffIcon } from './icons'
import styles from './TextField.module.css'

type TextFieldProps = ComponentPropsWithRef<'input'> & {
  label: string
  /** Mensaje de error del campo (de la validación del formulario o del backend). */
  error?: string
  /** Contenido extra debajo del input (ej.: la barra de fuerza de la contraseña). */
  children?: ReactNode
}

/**
 * Etiqueta + input + error, conectados para los lectores de pantalla (aria-invalid y aria-describedby).
 * Recibe el ref de React Hook Form (register) y se lo pasa al input.
 */
export function TextField({ label, error, children, type = 'text', ...inputProps }: TextFieldProps) {
  const id = useId()
  const errorId = `${id}-error`
  const [visible, setVisible] = useState(false)
  const isPassword = type === 'password'

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <div className={`${styles.control} ${error ? styles.invalid : ''}`}>
        <input
          id={id}
          type={isPassword && visible ? 'text' : type}
          className={styles.input}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          {...inputProps}
        />
        {isPassword && (
          <button
            type="button"
            className={styles.toggle}
            onClick={() => setVisible((v) => !v)}
            aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          >
            {visible ? <EyeOffIcon /> : <EyeIcon />}
          </button>
        )}
      </div>
      {children}
      {error && (
        <p id={errorId} className={styles.error}>
          <AlertIcon size={13} />
          <span>{error}</span>
        </p>
      )}
    </div>
  )
}
