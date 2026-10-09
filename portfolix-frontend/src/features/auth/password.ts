/*
 * Las mismas reglas que @StrongPassword en el backend (StrongPasswordValidator): de 8 a 64 caracteres,
 * una mayúscula, un número y un símbolo (cualquier cosa que no sea letra, número ni espacio).
 * El backend vuelve a validar; acá es para avisar mientras se escribe.
 */

export const PASSWORD_RULES_MESSAGE =
  'La contraseña debe tener entre 8 y 64 caracteres, una mayúscula, un número y un símbolo'

export interface PasswordChecks {
  length: boolean
  uppercase: boolean
  number: boolean
  symbol: boolean
}

export function checkPassword(password: string): PasswordChecks {
  return {
    length: password.length >= 8 && password.length <= 64,
    uppercase: /\p{Lu}/u.test(password),
    number: /\p{Nd}/u.test(password),
    symbol: /[^\p{L}\p{Nd}\s]/u.test(password),
  }
}

export function isStrongPassword(password: string): boolean {
  return Object.values(checkPassword(password)).every(Boolean)
}

export type StrengthTone = 'weak' | 'medium' | 'strong'

export interface PasswordStrength {
  /** Cuántas de las 4 reglas cumple (los segmentos llenos de la barra). */
  score: number
  tone: StrengthTone
  label: string
}

export function passwordStrength(password: string): PasswordStrength {
  const score = Object.values(checkPassword(password)).filter(Boolean).length
  if (score === 4) {
    return { score, tone: 'strong', label: 'Contraseña fuerte' }
  }
  if (score >= 2) {
    return { score, tone: 'medium', label: 'Podría ser más segura' }
  }
  return { score, tone: 'weak', label: 'Contraseña débil' }
}

/** Validación para React Hook Form (register('password', { validate: strongPassword })). */
export function strongPassword(password: string): true | string {
  return isStrongPassword(password) || PASSWORD_RULES_MESSAGE
}
