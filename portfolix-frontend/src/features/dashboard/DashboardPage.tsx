import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { useAppDispatch, useAppSelector } from '../../app/hooks'
import { Alert } from '../../components/Alert'
import { PlusIcon } from '../../components/icons'
import { useAutoRefresh } from '../../hooks/useAutoRefresh'
import { AssetDetailPanel } from '../assets/AssetDetailPanel'
import { createFormOpened } from '../portfolios/portfoliosSlice'
import { DisplayControls } from '../preferences/DisplayControls'
import { RegisterTransactionDialog } from '../transactions/RegisterTransactionDialog'
import { dashboardKey, fetchDashboard } from './dashboardSlice'
import { EmptyState } from './EmptyState'
import { HoldingsTable } from './HoldingsTable'
import { SummaryHeader } from './SummaryHeader'
import styles from './Dashboard.module.css'

/** Cada cuánto se vuelven a pedir los datos: lo mismo que tarda el backend en refrescar los precios. */
const REFRESH_INTERVAL_MS = 5 * 60_000
const SPARKLINE_RETRY_DELAY_MS = 15_000
const SPARKLINE_RETRIES = 3

/** El dashboard de todos los portafolios (/) o de uno (/portfolios/:portfolioId). */
export function DashboardPage() {
  const dispatch = useAppDispatch()
  const { portfolioId: portfolioParam } = useParams()
  const portfolioId = portfolioParam === undefined ? null : Number(portfolioParam)
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedAsset = searchParams.get('asset')
  const [registering, setRegistering] = useState(false)

  const { currency, status: preferencesStatus } = useAppSelector((state) => state.preferences)
  const portfolios = useAppSelector((state) => state.portfolios)
  const portfolio = portfolioId === null ? null : portfolios.items.find((p) => p.id === portfolioId) ?? null
  const entry = useAppSelector((state) => state.dashboard.entries[dashboardKey(portfolioId, currency)])

  const notFound = portfolioId !== null && (Number.isNaN(portfolioId) || (portfolios.status === 'ready' && !portfolio))
  // Se espera a las preferencias: si no, se pediría en ARS y enseguida otra vez en la moneda elegida.
  const canFetch = !notFound && (preferencesStatus === 'ready' || preferencesStatus === 'error')

  useEffect(() => {
    if (canFetch && !entry) {
      dispatch(fetchDashboard({ portfolioId, currency }))
    }
  }, [canFetch, entry, portfolioId, currency, dispatch])

  useAutoRefresh(() => dispatch(fetchDashboard({ portfolioId, currency })), REFRESH_INTERVAL_MS, canFetch)

  // Los minigráficos los carga el backend en segundo plano la primera vez que se piden: si falta alguno,
  // se vuelve a pedir enseguida (hasta 3 veces, por si la fuente de precios está caída).
  const sparklineRetries = useRef(0)
  const missingSparklines = entry?.status === 'ready' && entry.holdings.some((h) => h.sparkline7d === null)
  useEffect(() => {
    if (!missingSparklines || sparklineRetries.current >= SPARKLINE_RETRIES) {
      return
    }
    const timer = window.setTimeout(() => {
      sparklineRetries.current += 1
      dispatch(fetchDashboard({ portfolioId, currency }))
    }, SPARKLINE_RETRY_DELAY_MS)
    return () => window.clearTimeout(timer)
  }, [missingSparklines, entry, portfolioId, currency, dispatch])

  function selectAsset(symbol: string | null) {
    setSearchParams((params) => {
      if (symbol) {
        params.set('asset', symbol)
      } else {
        params.delete('asset')
      }
      return params
    })
  }

  if (notFound) {
    return (
      <div className={styles.page}>
        <Alert>
          No encontramos ese portafolio. <Link to="/">Ver todos los portafolios</Link>
        </Alert>
      </div>
    )
  }

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <h1 className={styles.scope}>{portfolio ? portfolio.name : 'Todos los portafolios'}</h1>
        <div className={styles.headerActions}>
          <DisplayControls />
          <button type="button" className={styles.primaryButton} onClick={() => setRegistering(true)}>
            <PlusIcon size={15} /> Registrar Transacción
          </button>
        </div>
      </header>

      {(!entry || entry.status === 'loading') && <p className={styles.loading}>Cargando tu cartera…</p>}

      {entry?.status === 'error' && (
        <Alert>
          {entry.error?.message ?? 'No pudimos cargar tu cartera.'}{' '}
          <button type="button" className={styles.linkButton} onClick={() => dispatch(fetchDashboard({ portfolioId, currency }))}>
            Reintentar
          </button>
        </Alert>
      )}

      {entry?.status === 'ready' && entry.summary && (
        <>
          <SummaryHeader summary={entry.summary} />
          {entry.error && (
            <p className={styles.staleWarning}>No pudimos actualizar los datos; se muestran los últimos que llegaron.</p>
          )}
          {entry.holdings.length === 0 ? (
            <EmptyState
              portfolioName={portfolio?.name ?? null}
              onRegister={() => setRegistering(true)}
              onCreatePortfolio={() => dispatch(createFormOpened())}
            />
          ) : (
            <HoldingsTable
              holdings={entry.holdings}
              currency={currency}
              pricesUpdatedAt={entry.summary.pricesUpdatedAt}
              onSelect={selectAsset}
            />
          )}
        </>
      )}

      {selectedAsset && (
        <AssetDetailPanel symbol={selectedAsset} portfolioId={portfolioId} onClose={() => selectAsset(null)} />
      )}
      {registering && (
        <RegisterTransactionDialog defaultPortfolioId={portfolioId} onClose={() => setRegistering(false)} />
      )}
    </div>
  )
}
