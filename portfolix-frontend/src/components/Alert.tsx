import type { ReactNode } from 'react'
import { AlertIcon, CheckCircleIcon } from './icons'
import styles from './Alert.module.css'

/** Aviso dentro de un formulario. Los de error se anuncian solos en los lectores de pantalla (role="alert"). */
export function Alert({ tone = 'error', children }: { tone?: 'error' | 'info' | 'success'; children: ReactNode }) {
  return (
    <div className={`${styles.alert} ${styles[tone]}`} role={tone === 'error' ? 'alert' : 'status'}>
      {tone === 'success' ? <CheckCircleIcon /> : <AlertIcon />}
      <div className={styles.content}>{children}</div>
    </div>
  )
}
