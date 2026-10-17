import type { ReactNode } from 'react'
import styles from './PageHeader.module.css'

/** La cabecera de una página: el título, una línea que explica qué muestra y las acciones de la derecha. */
export function PageHeader({ title, subtitle, children }: { title: string; subtitle: string; children?: ReactNode }) {
  return (
    <header className={styles.header}>
      <div>
        <h1 className={styles.title}>{title}</h1>
        <p className={styles.subtitle}>{subtitle}</p>
      </div>
      {children && <div className={styles.actions}>{children}</div>}
    </header>
  )
}
