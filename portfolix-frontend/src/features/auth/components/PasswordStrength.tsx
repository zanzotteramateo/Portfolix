import { CheckCircleIcon, CircleIcon } from '../../../components/icons'
import { checkPassword, passwordStrength } from '../password'
import styles from './PasswordStrength.module.css'

/** La barra de 4 segmentos del diseño: uno por cada regla que la contraseña ya cumple. */
export function PasswordStrength({ password }: { password: string }) {
  if (!password) {
    return null
  }
  const { score, tone, label } = passwordStrength(password)
  return (
    <div className={styles.strength}>
      <div className={styles.bar} aria-hidden="true">
        {[1, 2, 3, 4].map((segment) => (
          <span key={segment} className={`${styles.segment} ${segment <= score ? styles[tone] : ''}`} />
        ))}
      </div>
      <span className={`${styles.label} ${styles[tone]}`}>{label}</span>
    </div>
  )
}

const REQUIREMENTS = [
  { key: 'length', text: 'Mínimo 8 caracteres' },
  { key: 'uppercase', text: 'Una letra mayúscula' },
  { key: 'number', text: 'Al menos un número' },
  { key: 'symbol', text: 'Un símbolo (!, @, #…)' },
] as const

/** La lista de requisitos de "Creá una nueva contraseña": cada uno se marca al cumplirse. */
export function PasswordRequirements({ password }: { password: string }) {
  const checks = checkPassword(password)
  return (
    <ul className={styles.requirements} aria-label="Requisitos de la contraseña">
      {REQUIREMENTS.map(({ key, text }) => (
        <li key={key} className={checks[key] ? styles.met : undefined}>
          {checks[key] ? <CheckCircleIcon size={14} /> : <CircleIcon size={14} />}
          <span>{text}</span>
          <span className="visually-hidden">{checks[key] ? '(cumplido)' : '(pendiente)'}</span>
        </li>
      ))}
    </ul>
  )
}
