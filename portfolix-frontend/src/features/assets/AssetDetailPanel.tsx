import { useEffect, useRef } from 'react'
import type { HistoryResponse, QuoteResponse, TransactionPageResponse } from '../../api/types'
import { useAppSelector } from '../../app/hooks'
import { AssetIcon, TypeBadge } from '../../components/AssetBadges'
import { LineChart } from '../../components/charts/LineChart'
import { CloseIcon } from '../../components/icons'
import { formatDate, tone } from '../../format/format'
import { useFormat } from '../../format/useFormat'
import { useApiResource } from '../../hooks/useApiResource'
import styles from './AssetDetailPanel.module.css'

/**
 * Detalle de un activo: panel lateral sobre el dashboard (?asset=BTC en la URL, así "atrás" lo cierra).
 * Precio actual en la moneda elegida, tendencias de 7 días y 24 horas, y las operaciones del activo
 * (del portafolio que se está mirando, o de todos).
 */
export function AssetDetailPanel({ symbol, portfolioId, onClose }: {
  symbol: string
  portfolioId: number | null
  onClose: () => void
}) {
  const format = useFormat()
  const panel = useRef<HTMLElement>(null)
  const currency = useAppSelector((state) => state.preferences.currency)
  const catalogAsset = useAppSelector((state) => state.assets.items.find((a) => a.symbol === symbol))

  const quote = useApiResource<QuoteResponse>(`/assets/${symbol}/quote?currency=${currency}`)
  const week = useApiResource<HistoryResponse>(`/assets/${symbol}/history?range=7d`)
  const day = useApiResource<HistoryResponse>(`/assets/${symbol}/history?range=24h`)
  const portfolioFilter = portfolioId === null ? '' : `&portfolioId=${portfolioId}`
  const operations = useApiResource<TransactionPageResponse>(`/transactions?assetSymbol=${symbol}${portfolioFilter}&size=20`)

  // El nombre y el tipo: del catálogo, o de una operación (un activo dado de baja ya no está en el catálogo).
  const asset = catalogAsset ?? operations.data?.content[0]?.asset

  useEffect(() => {
    panel.current?.focus() // los lectores de pantalla anuncian el panel al abrirse
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !document.querySelector('dialog[open]')) {
        onClose()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <aside ref={panel} className={styles.panel} tabIndex={-1} aria-label={`Detalle de ${symbol}`}>
      <header className={styles.header}>
        <div className={styles.title}>
          <AssetIcon symbol={symbol} />
          <div>
            <h2>
              Detalle {symbol}
              {asset && <span className={styles.assetName}> ({asset.name})</span>}
            </h2>
            {asset && <TypeBadge type={asset.type} />}
          </div>
        </div>
        <button type="button" className={styles.close} onClick={onClose} aria-label="Cerrar el detalle">
          <CloseIcon />
        </button>
      </header>

      <section className={styles.market} aria-label="Mercado">
        <div className={styles.price}>
          <span className={styles.label}>
            Precio actual {symbol}/{currency}
          </span>
          <span className={styles.priceValue}>
            {quote.data ? format.price(quote.data.price, currency) : quote.error ? 'No disponible' : '…'}
          </span>
          {quote.data?.change24hPercent != null && (
            <span className={styles[tone(quote.data.change24hPercent)]}>
              {format.percent(quote.data.change24hPercent, { sign: true })} hoy
            </span>
          )}
        </div>
        <div className={styles.trends}>
          <TrendCard title="Tendencia (7d)" history={week} />
          <TrendCard title="Tendencia (24h)" history={day} />
        </div>
      </section>

      <section className={styles.history} aria-label={`Historial de operaciones de ${symbol}`}>
        <h3 className={styles.label}>Historial de operaciones {symbol}</h3>
        {operations.loading && <p className={styles.note}>Cargando…</p>}
        {operations.error && <p className={styles.note}>{operations.error.message}</p>}
        {operations.data?.content.length === 0 && <p className={styles.note}>Todavía no operaste este activo.</p>}
        {operations.data && operations.data.content.length > 0 && (
          <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">Fecha</th>
                <th scope="col">Tipo</th>
                <th scope="col" className={styles.numeric}>Cant.</th>
                <th scope="col" className={styles.numeric}>Precio</th>
                <th scope="col" className={styles.numeric}>Monto</th>
                {portfolioId === null && <th scope="col">Portafolio</th>}
              </tr>
            </thead>
            <tbody>
              {operations.data.content.map((op) => (
                <tr key={op.id}>
                  <td>{formatDate(op.tradeDate)}</td>
                  <td>
                    <span className={`${styles.typePill} ${op.type === 'BUY' ? styles.buy : styles.sell}`}>
                      {op.type === 'BUY' ? '↑ Compra' : '↓ Venta'}
                    </span>
                  </td>
                  <td className={styles.numeric}>{format.quantity(op.quantity, op.asset.type)}</td>
                  <td className={styles.numeric}>{format.price(op.price, op.currency)}</td>
                  <td className={styles.numeric}>{format.money(op.total, op.currency)}</td>
                  {portfolioId === null && (
                    <td className={styles.portfolio} title={op.portfolio.name}>
                      {op.portfolio.name}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </section>
    </aside>
  )
}

/** Una tarjeta de tendencia: variación del período y la línea de precios (en la moneda del activo). */
function TrendCard({ title, history }: {
  title: string
  history: { data: HistoryResponse | null; error: { message: string } | null; loading: boolean }
}) {
  const format = useFormat()
  const change = history.data?.changePercent ?? null
  const color = tone(change) === 'loss' ? 'var(--status-loss)' : 'var(--status-gain)'
  return (
    <div className={styles.trend}>
      <div className={styles.trendHeader}>
        <span className={styles.label}>{title}</span>
        {change !== null && <span className={styles[tone(change)]}>{format.percent(change, { sign: true })}</span>}
      </div>
      {history.data ? (
        <LineChart values={history.data.points.map((p) => Number(p.price))} color={color} height={48} area label={title} />
      ) : (
        <p className={styles.note}>{history.error ? 'No disponible' : 'Cargando…'}</p>
      )}
    </div>
  )
}
