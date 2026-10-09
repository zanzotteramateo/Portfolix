import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import type { ApiErrorData } from './client'

/**
 * Pone cada error de campo del backend (fieldErrors) en su campo del formulario de React Hook Form.
 * Devuelve true si el error correspondía a algún campo del formulario; si no, la pantalla lo muestra
 * como aviso general.
 */
export function applyFieldErrors<T extends FieldValues>(
  error: ApiErrorData,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): boolean {
  let applied = false
  for (const [field, message] of Object.entries(error.fieldErrors)) {
    if ((fields as readonly string[]).includes(field)) {
      setError(field as Path<T>, { type: 'server', message }, { shouldFocus: !applied })
      applied = true
    }
  }
  return applied
}

/** Una validación de mail suficiente para el formulario; la definitiva la hace el backend. */
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
