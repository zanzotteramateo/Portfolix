import type { AssetType } from '../api/types'
import { TYPE_LABELS } from '../format/assetTypes'
import styles from './AssetBadges.module.css'

export function TypeBadge({ type }: { type: AssetType }) {
  return <span className={`${styles.badge} ${styles[type]}`}>{TYPE_LABELS[type]}</span>
}

const SPECIAL_GLYPHS: Record<string, string> = { BTC: '₿', ETH: 'Ξ' }
// Excepción a "colores solo con las variables": colores para distinguir activos entre sí, iguales en los dos temas.
const ICON_COLORS = ['#58a6ff', '#a371f7', '#3fb950', '#d29922', '#f0883e', '#db61a2', '#39c5cf', '#f85149']

/**
 * El "logo" de un activo: su inicial en un cuadrado de color (decisión del modelo: el ícono lo genera el front).
 * El color sale del símbolo, así cada activo tiene siempre el mismo.
 */
export function AssetIcon({ symbol }: { symbol: string }) {
  const hash = [...symbol].reduce((sum, char) => sum + char.charCodeAt(0), 0)
  const color = ICON_COLORS[hash % ICON_COLORS.length]
  return (
    <span className={styles.icon} style={{ color, borderColor: `${color}55`, background: `${color}1a` }} aria-hidden="true">
      {SPECIAL_GLYPHS[symbol] ?? symbol[0]}
    </span>
  )
}
