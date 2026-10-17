import { Link } from 'react-router'
import type { PerformanceResponse } from '../../api/types'
import { useAppSelector } from '../../app/hooks'
import { Alert } from '../../components/Alert'
import { PageHeader } from '../../components/PageHeader'
import { formatAgo, formatDate, todayInArgentina } from '../../format/format'
import { useApiResource } from '../../hooks/useApiResource'
import { useUrlParams } from '../../hooks/useUrlParams'
import { AssetDetailPanel } from '../assets/AssetDetailPanel'
import { DisplayControls } from '../preferences/DisplayControls'
import { AnalysisFilters } from './AnalysisFilters'
import { periodRange, performancePath, readAnalysisFilters, type AnalysisFilters as Filters } from './analysisQuery'
import { PerformanceSummary } from './PerformanceSummary'
import { PerformanceTable } from './PerformanceTable'
import styles from './Analysis.module.css'

/**
 * Cómo te fue en un período (/analysis): resultado en $ y %, la evolución y el resultado de cada activo.
 * Período, portafolio y tipo viven en la URL (se recarga y se comparte igual) y cambian toda la página. Como el
 * historial, al cambiar un filtro se ven los datos anteriores atenuados hasta que llegan los nuevos.
 */
export function AnalysisPage() {
  const today = todayInArgentina()
  const [params, updateParams] = useUrlParams()
  const filters = readAnalysisFilters(params, today)
  const selectedAsset = params.get('asset')

  const currency = useAppSelector((state) => state.preferences.currency)
  const preferencesStatus = useAppSelector((state) => state.preferences.status)
  const portfolios = useAppSelector((state) => state.portfolios.items)
  // Como en el dashboard: se espera a las preferencias para pedir en la moneda que el usuario eligió.
  const canFetch = preferencesStatus === 'ready' || preferencesStatus === 'error'
  const result = useApiResource<PerformanceResponse>(canFetch ? performancePath(filters, currency, today) : null, {
    keepPrevious: true,
  })
  const { data, refreshing } = result
  const fieldErrors = result.error?.fieldErrors ?? {}
  const hasFieldErrors = Object.keys(fieldErrors).length > 0

  function choosePeriod(period: Filters['period']) {
    updateParams({ period, year: '', from: '', to: '' })
  }

  // "Personalizado" arranca con las fechas del período que se está mirando, así se ajusta a partir de ahí.
  function chooseCustom() {
    const range = periodRange(filters, today)
    updateParams({ period: 'custom', year: '', from: range.from ?? data?.from ?? '', to: range.to ?? today })
  }

  function selectAsset(symbol: string | null) {
    updateParams({ asset: symbol ?? '' })
  }

  const noFilters = filters.period === 'all' && !filters.portfolioId && !filters.type

  return (
    <div className={styles.page}>
      <PageHeader title="Análisis" subtitle="Cómo te fue en un período: cuánto ganaste o perdiste, en total y por activo.">
        <DisplayControls />
      </PageHeader>

      <AnalysisFilters
        filters={filters}
        portfolios={portfolios}
        errors={{ from: fieldErrors.from, to: fieldErrors.to }}
        onPeriod={choosePeriod}
        onYear={(year) => updateParams({ period: 'year', year, from: '', to: '' })}
        onCustom={chooseCustom}
        onChange={(changes) => updateParams(changes)}
      />

      {result.error && !hasFieldErrors && (
        <Alert>
          {result.error.message}{' '}
          <button type="button" className={styles.linkButton} onClick={result.reload}>
            Reintentar
          </button>
        </Alert>
      )}

      {!data && !result.error && (
        <p className={styles.note} role="status">
          Calculando tu rendimiento… La primera vez puede tardar unos segundos, mientras bajamos los precios históricos.
        </p>
      )}

      {data && (
        <div className={`${styles.results} ${refreshing ? styles.refreshing : ''}`} aria-busy={refreshing || undefined}>
          {data.assets.length > 0 && (
            <p className={styles.periodInfo}>
              Del {formatDate(data.from)} al {formatDate(data.to)}
              {data.pricesUpdatedAt && <> · precios de {formatAgo(data.pricesUpdatedAt)}</>}
            </p>
          )}

          {data.assets.length === 0 ? (
            <p className={styles.note}>
              {noFilters ? (
                <>
                  Todavía no registraste operaciones. <Link to="/">Cargá la primera desde el dashboard</Link>.
                </>
              ) : (
                'No tuviste inversiones en este período con estos filtros.'
              )}
            </p>
          ) : (
            <>
              <PerformanceSummary performance={data} />
              <PerformanceTable assets={data.assets} currency={data.currency} onSelect={selectAsset} />
            </>
          )}
        </div>
      )}

      {selectedAsset && (
        <AssetDetailPanel
          symbol={selectedAsset}
          portfolioId={filters.portfolioId ? Number(filters.portfolioId) : null}
          onClose={() => selectAsset(null)}
        />
      )}
    </div>
  )
}
