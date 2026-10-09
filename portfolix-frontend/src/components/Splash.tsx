import { Logo } from './Logo'
import styles from './Splash.module.css'

/** Mientras la app averigua si hay sesión (al abrir o recargar la página). */
export function Splash() {
  return (
    <div className={styles.splash} role="status">
      <Logo />
      <span className={styles.spinner} aria-hidden="true" />
      <span className="visually-hidden">Cargando…</span>
    </div>
  )
}
