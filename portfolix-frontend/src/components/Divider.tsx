import styles from './Divider.module.css'

/** Línea con un texto al medio ("o continuá con"). */
export function Divider({ children }: { children: string }) {
  return (
    <div className={styles.divider}>
      <span>{children}</span>
    </div>
  )
}
