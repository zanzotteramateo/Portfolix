import { ChartIcon } from './icons'
import styles from './Logo.module.css'

export function Logo({ size = 'large' }: { size?: 'large' | 'small' }) {
  return (
    <span className={`${styles.logo} ${styles[size]}`}>
      <span className={styles.mark}>
        <ChartIcon size={size === 'large' ? 20 : 16} strokeWidth={2.5} />
      </span>
      <span className={styles.name}>Portfolix</span>
    </span>
  )
}
