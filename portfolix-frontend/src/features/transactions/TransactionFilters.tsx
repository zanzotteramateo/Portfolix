import { useId } from 'react'
import type { AssetResponse, AssetType, PortfolioResponse } from '../../api/types'
import { TYPE_PLURALS } from '../../format/assetTypes'
import { todayInArgentina } from '../../format/format'
import { hasFilters, type HistoryFilters } from './transactionQuery'
import styles from './TransactionFilters.module.css'

const TYPE_CHIPS: { value: HistoryFilters['type']; label: string }[] = [
  { value: '', label: 'Todas' },
  { value: 'BUY', label: '↑ Compras' },
  { value: 'SELL', label: '↓ Ventas' },
]

const ASSET_TYPES: AssetType[] = ['STOCK', 'CEDEAR', 'CRYPTO']

/**
 * Los filtros del historial: tipo (chips), portafolio, activo y fechas. Cada cambio vuelve a pedir la primera
 * página. Los errores de las fechas vienen del backend (ej.: "desde" posterior a "hasta").
 */
export function TransactionFilters({ filters, portfolios, assets, errors, onChange, onClear }: {
  filters: HistoryFilters
  portfolios: PortfolioResponse[]
  assets: AssetResponse[]
  /** Errores por campo que devolvió el backend. */
  errors: { from?: string; to?: string }
  onChange: (changes: Partial<HistoryFilters>) => void
  onClear: () => void
}) {
  const portfolioId = useId()
  const assetId = useId()
  const fromId = useId()
  const toId = useId()
  const today = todayInArgentina()

  return (
    <div className={styles.filters}>
      <div className={styles.chips} role="group" aria-label="Tipo de operación">
        {TYPE_CHIPS.map((chip) => (
          <button
            key={chip.label}
            type="button"
            className={`${styles.chip} ${filters.type === chip.value ? styles.chipActive : ''}`}
            aria-pressed={filters.type === chip.value}
            onClick={() => onChange({ type: chip.value })}
          >
            {chip.label}
          </button>
        ))}
      </div>

      <div className={styles.fields}>
        <div className={styles.field}>
          <label htmlFor={portfolioId} className={styles.label}>
            Portafolio
          </label>
          <select id={portfolioId} className={styles.select} value={filters.portfolioId} onChange={(e) => onChange({ portfolioId: e.target.value })}>
            <option value="">Todos los portafolios</option>
            {portfolios.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.field}>
          <label htmlFor={assetId} className={styles.label}>
            Activo
          </label>
          <select id={assetId} className={styles.select} value={filters.assetSymbol} onChange={(e) => onChange({ assetSymbol: e.target.value })}>
            <option value="">Todos los activos</option>
            {ASSET_TYPES.map((type) => {
              const group = assets.filter((a) => a.type === type)
              return group.length > 0 ? (
                <optgroup key={type} label={TYPE_PLURALS[type]}>
                  {group.map((a) => (
                    <option key={a.symbol} value={a.symbol}>
                      {a.symbol} · {a.name}
                    </option>
                  ))}
                </optgroup>
              ) : null
            })}
          </select>
        </div>

        <div className={styles.field}>
          <label htmlFor={fromId} className={styles.label}>
            Desde
          </label>
          <input
            id={fromId}
            type="date"
            className={`${styles.select} ${errors.from ? styles.invalid : ''}`}
            value={filters.from}
            max={filters.to || today}
            aria-invalid={errors.from ? true : undefined}
            aria-describedby={errors.from ? `${fromId}-error` : undefined}
            onChange={(e) => onChange({ from: e.target.value })}
          />
          {errors.from && <p id={`${fromId}-error`} className={styles.error}>{errors.from}</p>}
        </div>

        <div className={styles.field}>
          <label htmlFor={toId} className={styles.label}>
            Hasta
          </label>
          <input
            id={toId}
            type="date"
            className={`${styles.select} ${errors.to ? styles.invalid : ''}`}
            value={filters.to}
            min={filters.from || undefined}
            max={today}
            aria-invalid={errors.to ? true : undefined}
            aria-describedby={errors.to ? `${toId}-error` : undefined}
            onChange={(e) => onChange({ to: e.target.value })}
          />
          {errors.to && <p id={`${toId}-error`} className={styles.error}>{errors.to}</p>}
        </div>

        {hasFilters(filters) && (
          <button type="button" className={styles.clear} onClick={onClear}>
            Limpiar filtros
          </button>
        )}
      </div>
    </div>
  )
}
