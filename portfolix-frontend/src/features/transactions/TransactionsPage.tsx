import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { toApiErrorData } from '../../api/client'
import type { TransactionPageResponse, TransactionResponse } from '../../api/types'
import { useAppSelector } from '../../app/hooks'
import { Alert } from '../../components/Alert'
import { plural } from '../../format/format'
import { useFormat } from '../../format/useFormat'
import { useApiResource } from '../../hooks/useApiResource'
import { DisplayControls } from '../preferences/DisplayControls'
import { DeleteTransactionDialog } from './DeleteTransactionDialog'
import { downloadTransactionsCsv } from './downloadTransactionsCsv'
import { RegisterTransactionDialog } from './RegisterTransactionDialog'
import { TransactionFilters } from './TransactionFilters'
import { TransactionTable } from './TransactionTable'
import { hasFilters, readFilters, readPage, transactionsPath, type HistoryFilters } from './transactionQuery'
import styles from './TransactionsPage.module.css'

/**
 * El historial de operaciones (/transactions): tarjetas de totales, filtros, la tabla paginada y el CSV.
 * Filtros y página viven en la URL. Editar y borrar se hacen desde cada fila. Al cerrar el diálogo de editar se
 * vuelve a pedir la página, falle o no la operación: si otro cambio la tocó, así se ve el estado real.
 */
export function TransactionsPage() {
  const format = useFormat()
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = readFilters(searchParams)
  const page = readPage(searchParams)

  const currency = useAppSelector((state) => state.preferences.currency)
  const preferencesStatus = useAppSelector((state) => state.preferences.status)
  const portfolios = useAppSelector((state) => state.portfolios.items)
  const assets = useAppSelector((state) => state.assets.items)
  // Como en el dashboard: se espera a las preferencias para pedir en la moneda que el usuario eligió.
  const canFetch = preferencesStatus === 'ready' || preferencesStatus === 'error'
  const list = useApiResource<TransactionPageResponse>(canFetch ? transactionsPath(filters, page, currency) : null)

  const [editing, setEditing] = useState<TransactionResponse | null>(null)
  const [deleting, setDeleting] = useState<TransactionResponse | null>(null)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)

  // Al cambiar de filtro o de página se siguen mostrando los datos anteriores, atenuados, hasta que llegan los
  // nuevos (si no, la tabla desaparecería y volvería en cada clic).
  const [lastLoaded, setLastLoaded] = useState<TransactionPageResponse | null>(null)
  if (list.data && list.data !== lastLoaded) {
    setLastLoaded(list.data) // se guarda durante el dibujado (así lo recomienda React), sin un efecto
  }
  const data = list.error ? null : (list.data ?? lastLoaded)
  const refreshing = list.data === null && !list.error && lastLoaded !== null
  const fieldErrors = list.error?.fieldErrors ?? {}
  const hasFieldErrors = Object.keys(fieldErrors).length > 0

  function updateFilters(changes: Partial<HistoryFilters>) {
    setSearchParams((params) => {
      for (const [key, value] of Object.entries(changes)) {
        if (value) {
          params.set(key, value)
        } else {
          params.delete(key)
        }
      }
      params.delete('page') // un filtro nuevo empieza en la primera página
      return params
    })
  }

  function goToPage(number: number) {
    setSearchParams((params) => {
      if (number > 0) {
        params.set('page', String(number))
      } else {
        params.delete('page')
      }
      return params
    })
  }

  function clearFilters() {
    setSearchParams(new URLSearchParams())
  }

  // Si se borró la última operación de una página que no es la primera, se vuelve a la anterior (la nueva
  // página se pide sola al cambiar la URL). Si no, se vuelve a pedir la misma.
  function handleDeleted() {
    const wasLastOnPage = data !== null && data.content.length === 1 && page > 0
    setDeleting(null)
    if (wasLastOnPage) {
      goToPage(page - 1)
    } else {
      list.reload()
    }
  }

  function closeEditor() {
    setEditing(null)
    list.reload()
  }

  async function exportCsv() {
    setExportError(null)
    setExporting(true)
    try {
      await downloadTransactionsCsv()
    } catch (e) {
      setExportError(toApiErrorData(e).message)
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <div>
          <h1 className={styles.title}>Historial de Transacciones</h1>
          <p className={styles.subtitle}>Tus compras y ventas, de la más reciente a la más antigua.</p>
        </div>
        <div className={styles.headerActions}>
          <DisplayControls />
          <button
            type="button"
            className={styles.secondaryButton}
            onClick={exportCsv}
            disabled={exporting}
            aria-busy={exporting || undefined}
            title="Descarga todo el historial, sin los filtros"
          >
            {exporting ? 'Exportando…' : 'Exportar CSV'}
          </button>
        </div>
      </header>

      {exportError && <Alert>{exportError}</Alert>}

      <section className={`${styles.summary} ${refreshing ? styles.refreshing : ''}`} aria-label="Resumen de operaciones">
        <SummaryCard label="Total operaciones" value={data ? String(data.summary.totalOperations) : '…'} />
        <SummaryCard
          label="Total comprado"
          value={data ? format.money(data.summary.converted.totalBought, data.summary.converted.currency) : '…'}
        />
        <SummaryCard
          label="Total vendido"
          value={data ? format.money(data.summary.converted.totalSold, data.summary.converted.currency) : '…'}
        />
      </section>

      <TransactionFilters
        filters={filters}
        portfolios={portfolios}
        assets={assets}
        errors={{ from: fieldErrors.from, to: fieldErrors.to }}
        onChange={updateFilters}
        onClear={clearFilters}
      />

      {data && data.content.length > 0 && (
        <p className={styles.count} role="status">
          {plural(data.page.totalElements, 'operación', 'operaciones')}
        </p>
      )}

      {list.error && !hasFieldErrors && (
        <Alert>
          {list.error.message}{' '}
          <button type="button" className={styles.linkButton} onClick={list.reload}>
            Reintentar
          </button>
        </Alert>
      )}

      {!data && !list.error && <p className={styles.note}>Cargando operaciones…</p>}

      {data && data.content.length === 0 && (
        <EmptyResults
          page={page}
          noFiltersAndNoOperations={!hasFilters(filters) && data.summary.totalOperations === 0}
          onFirstPage={() => goToPage(0)}
          onClearFilters={clearFilters}
        />
      )}

      {data && data.content.length > 0 && (
        <div className={refreshing ? styles.refreshing : undefined}>
          <TransactionTable transactions={data.content} onEdit={setEditing} onDelete={setDeleting} />
          {data.page.totalPages > 1 && (
            <nav className={styles.pagination} aria-label="Páginas">
              <button
                type="button"
                className={styles.secondaryButton}
                disabled={page === 0}
                onClick={() => goToPage(page - 1)}
              >
                Anterior
              </button>
              <span className={styles.pageLabel}>
                Página {page + 1} de {data.page.totalPages}
              </span>
              <button
                type="button"
                className={styles.secondaryButton}
                disabled={page + 1 >= data.page.totalPages}
                onClick={() => goToPage(page + 1)}
              >
                Siguiente
              </button>
            </nav>
          )}
        </div>
      )}

      {editing && (
        <RegisterTransactionDialog defaultPortfolioId={null} transaction={editing} onClose={closeEditor} />
      )}
      {deleting && (
        <DeleteTransactionDialog transaction={deleting} onDeleted={handleDeleted} onClose={() => setDeleting(null)} />
      )}
    </div>
  )
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div className={styles.card}>
      <span className={styles.cardLabel}>{label}</span>
      <span className={styles.cardValue}>{value}</span>
    </div>
  )
}

function EmptyResults({ page, noFiltersAndNoOperations, onFirstPage, onClearFilters }: {
  page: number
  noFiltersAndNoOperations: boolean
  onFirstPage: () => void
  onClearFilters: () => void
}) {
  if (page > 0) {
    return (
      <p className={styles.note}>
        Esta página ya no tiene operaciones.{' '}
        <button type="button" className={styles.linkButton} onClick={onFirstPage}>
          Volver a la primera página
        </button>
      </p>
    )
  }
  if (noFiltersAndNoOperations) {
    return <p className={styles.note}>Todavía no registraste operaciones. Las compras y ventas que cargues aparecen acá.</p>
  }
  return (
    <p className={styles.note}>
      No hay operaciones con estos filtros.{' '}
      <button type="button" className={styles.linkButton} onClick={onClearFilters}>
        Limpiar filtros
      </button>
    </p>
  )
}
