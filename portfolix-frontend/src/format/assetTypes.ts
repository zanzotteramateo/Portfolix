import type { AssetType } from '../api/types'

/** Los colores de cada tipo de activo: los mismos en las etiquetas, la dona y la leyenda. */
export const TYPE_COLORS: Record<AssetType, string> = {
  CRYPTO: 'var(--accent)',
  CEDEAR: 'var(--status-warning)',
  STOCK: 'var(--status-gain)',
}

export const TYPE_LABELS: Record<AssetType, string> = { CRYPTO: 'Crypto', CEDEAR: 'CEDEAR', STOCK: 'Acción' }

/** Plural, para filtros y leyendas. */
export const TYPE_PLURALS: Record<AssetType, string> = { CRYPTO: 'Cryptos', CEDEAR: 'CEDEARs', STOCK: 'Acciones' }
