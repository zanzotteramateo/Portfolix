import type { ReactNode } from 'react'
import { AuthCard } from '../../../components/AuthCard'
import { AlertIcon, CheckIcon, MailIcon } from '../../../components/icons'
import styles from '../pages/AuthPages.module.css'

const ICONS = {
  success: <CheckIcon size={24} strokeWidth={2.5} />,
  error: <AlertIcon size={24} />,
  pending: <MailIcon size={24} />,
}

/** Pantalla de resultado (ej.: "Contraseña actualizada"): ícono grande, título, texto y una acción. */
export function ResultCard({
  tone,
  title,
  children,
  action,
}: {
  tone: 'success' | 'error' | 'pending'
  title: string
  children: ReactNode
  action?: ReactNode
}) {
  return (
    <AuthCard>
      <div className={styles.result} role={tone === 'error' ? 'alert' : 'status'}>
        <span className={`${styles.resultIcon} ${tone !== 'success' ? styles[tone] : ''}`}>{ICONS[tone]}</span>
        <h1 className={styles.resultTitle}>{title}</h1>
        <p className={styles.resultText}>{children}</p>
      </div>
      {action}
    </AuthCard>
  )
}
