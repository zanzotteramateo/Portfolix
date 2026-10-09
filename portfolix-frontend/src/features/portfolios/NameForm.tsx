import { useState, type FormEvent, type KeyboardEvent } from 'react'
import type { ApiErrorData } from '../../api/client'
import { AlertIcon } from '../../components/icons'
import styles from './PortfolioNav.module.css'

/** Mismo máximo que el backend (PortfolioService.MAX_NAME_LENGTH). */
const MAX_NAME_LENGTH = 50

/**
 * El nombre de un portafolio, editado en el lugar (crear o renombrar), como en el diseño:
 * "Enter guarda, Esc cancela". onSubmit devuelve el error del backend, o null si salió bien.
 */
export function NameForm({ initialName = '', label, onSubmit, onCancel }: {
  initialName?: string
  label: string
  onSubmit: (name: string) => Promise<ApiErrorData | null>
  onCancel: () => void
}) {
  const [name, setName] = useState(initialName)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setError('Ingresá un nombre')
      return
    }
    if (trimmed.length > MAX_NAME_LENGTH) {
      setError(`El nombre puede tener hasta ${MAX_NAME_LENGTH} caracteres`)
      return
    }
    setSaving(true)
    const failure = await onSubmit(trimmed)
    setSaving(false)
    if (failure) {
      // "Ya tenés un portafolio con ese nombre", "Alcanzaste el máximo de 20 portafolios"…
      setError(failure.fieldErrors.name ?? failure.message)
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      onCancel()
    }
  }

  return (
    <form className={styles.nameForm} onSubmit={submit} noValidate>
      <input
        className={`${styles.nameInput} ${error ? styles.nameInputInvalid : ''}`}
        value={name}
        onChange={(e) => {
          setName(e.target.value)
          setError(null)
        }}
        onKeyDown={onKeyDown}
        aria-label={label}
        aria-invalid={error ? true : undefined}
        maxLength={MAX_NAME_LENGTH + 10}
        autoFocus
        // readOnly y no disabled: un campo deshabilitado pierde el foco, y después del error Esc ya no cancelaría.
        readOnly={saving}
      />
      {error && (
        <p className={styles.nameError} role="alert">
          <AlertIcon size={12} /> {error}
        </p>
      )}
      <div className={styles.nameActions}>
        <button type="submit" className={styles.saveButton} disabled={saving}>
          Guardar
        </button>
        <button type="button" className={styles.cancelButton} onClick={onCancel}>
          Cancelar
        </button>
      </div>
      <p className={styles.nameHint}>Enter guarda, Esc cancela</p>
    </form>
  )
}
