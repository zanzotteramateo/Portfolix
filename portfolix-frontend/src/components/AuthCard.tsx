import type { ReactNode } from 'react'
import { Logo } from './Logo'
import styles from './AuthCard.module.css'

/** El marco de todas las pantallas de auth: fondo con los brillos del diseño y la tarjeta centrada. */
export function AuthCard({ children }: { children: ReactNode }) {
  return (
    <main className={styles.page}>
      <div className={styles.glowBlue} aria-hidden="true" />
      <div className={styles.glowGreen} aria-hidden="true" />
      <section className={styles.card}>
        <header className={styles.header}>
          <Logo />
          <p className={styles.tagline}>Tu cartera de inversiones en un solo lugar</p>
        </header>
        {children}
      </section>
    </main>
  )
}

/** Ícono, título y descripción de las pantallas de auth que los tienen (recuperar, verificar…). */
export function AuthIntro({ icon, title, children }: { icon?: ReactNode; title: string; children?: ReactNode }) {
  return (
    <div className={styles.intro}>
      {icon && <span className={styles.introIcon}>{icon}</span>}
      <h1 className={styles.title}>{title}</h1>
      {children && <p className={styles.description}>{children}</p>}
    </div>
  )
}
