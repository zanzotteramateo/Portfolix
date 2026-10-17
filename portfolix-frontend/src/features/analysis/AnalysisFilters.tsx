import { useId } from 'react'
import type { PortfolioResponse } from '../../api/types'
import { Chip, ChipGroup, ChipSelect } from '../../components/Chips'
import { TYPE_PLURALS } from '../../format/assetTypes'
import { todayInArgentina } from '../../format/format'
import filterStyles from '../../styles/filters.module.css'
import { PRESETS, selectableYears, type AnalysisFilters as Filters } from './analysisQuery'
import styles from './Analysis.module.css'

const TYPE_CHIPS: { value: Filters['type']; label: string }[] = [
  { value: '', label: 'Todos' },
  { value: 'STOCK', label: TYPE_PLURALS.STOCK },
  { value: 'CEDEAR', label: TYPE_PLURALS.CEDEAR },
  { value: 'CRYPTO', label: TYPE_PLURALS.CRYPTO },
]

/**
 * Período (opciones rápidas, un año o fechas a mano), portafolio y tipo de activo. Los tres cambian toda la página
 * (decisión de la 14B). Los errores de las fechas vienen del backend (ej.: "desde" posterior a "hasta").
 */
export function AnalysisFilters({ filters, portfolios, errors, onPeriod, onYear, onCustom, onChange }: {
  filters: Filters
  portfolios: PortfolioResponse[]
  errors: { from?: string; to?: string }
  onPeriod: (period: Filters['period']) => void
  onYear: (year: string) => void
  /** Pasa a "Personalizado" con las fechas del período que se está mirando. */
  onCustom: () => void
  onChange: (changes: Partial<Filters>) => void
}) {
  const yearId = useId()
  const portfolioId = useId()
  const fromId = useId()
  const toId = useId()
  const today = todayInArgentina()

  return (
    <div className={styles.filters}>
      <ChipGroup label="Período">
        {PRESETS.map((preset) => (
          <Chip key={preset.value} active={filters.period === preset.value} onClick={() => onPeriod(preset.value)}>
            {preset.label}
          </Chip>
        ))}
        <label htmlFor={yearId} className="visually-hidden">
          Año
        </label>
        <ChipSelect
          id={yearId}
          active={filters.period === 'year'}
          value={filters.period === 'year' ? filters.year : ''}
          onChange={(e) => e.target.value && onYear(e.target.value)}
        >
          <option value="">Año…</option>
          {selectableYears(today).map((year) => (
            <option key={year} value={year}>
              {year}
            </option>
          ))}
        </ChipSelect>
        <Chip active={filters.period === 'custom'} onClick={onCustom}>
          Personalizado
        </Chip>
      </ChipGroup>

      {filters.period === 'custom' && (
        <div className={filterStyles.row}>
          <div className={filterStyles.field}>
            <label htmlFor={fromId} className={filterStyles.label}>
              Desde
            </label>
            <input
              id={fromId}
              type="date"
              className={`${filterStyles.control} ${errors.from ? filterStyles.invalid : ''}`}
              value={filters.from}
              max={filters.to || today}
              aria-invalid={errors.from ? true : undefined}
              aria-describedby={errors.from ? `${fromId}-error` : undefined}
              onChange={(e) => onChange({ from: e.target.value })}
            />
            {errors.from && <p id={`${fromId}-error`} className={filterStyles.error}>{errors.from}</p>}
          </div>
          <div className={filterStyles.field}>
            <label htmlFor={toId} className={filterStyles.label}>
              Hasta
            </label>
            <input
              id={toId}
              type="date"
              className={`${filterStyles.control} ${errors.to ? filterStyles.invalid : ''}`}
              value={filters.to}
              min={filters.from || undefined}
              max={today}
              aria-invalid={errors.to ? true : undefined}
              aria-describedby={errors.to ? `${toId}-error` : undefined}
              onChange={(e) => onChange({ to: e.target.value })}
            />
            {errors.to && <p id={`${toId}-error`} className={filterStyles.error}>{errors.to}</p>}
          </div>
        </div>
      )}

      <div className={filterStyles.row}>
        <div className={filterStyles.field}>
          <label htmlFor={portfolioId} className={filterStyles.label}>
            Portafolio
          </label>
          <select
            id={portfolioId}
            className={filterStyles.control}
            value={filters.portfolioId}
            onChange={(e) => onChange({ portfolioId: e.target.value })}
          >
            <option value="">Todos los portafolios</option>
            {portfolios.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
        <ChipGroup label="Tipo de activo">
          {TYPE_CHIPS.map((chip) => (
            <Chip key={chip.label} active={filters.type === chip.value} onClick={() => onChange({ type: chip.value })}>
              {chip.label}
            </Chip>
          ))}
        </ChipGroup>
      </div>
    </div>
  )
}
