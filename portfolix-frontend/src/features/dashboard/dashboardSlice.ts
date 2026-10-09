import { createAction, createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { api, toApiErrorData, type ApiErrorData } from '../../api/client'
import type { Currency, DashboardSummaryResponse, HoldingResponse, HoldingsResponse } from '../../api/types'
import { logout, sessionExpired } from '../auth/authSlice'
import { deletePortfolio, duplicatePortfolio } from '../portfolios/portfoliosSlice'

/** El resumen y las posiciones de un alcance (todos los portafolios o uno) en una moneda. */
export interface DashboardEntry {
  summary: DashboardSummaryResponse | null
  holdings: HoldingResponse[]
  status: 'loading' | 'ready' | 'error'
  /** Se está volviendo a pedir con datos ya en pantalla (no se muestra "cargando"). */
  refreshing: boolean
  error: ApiErrorData | null
}

export interface DashboardState {
  /** Por alcance y moneda ("all:ARS", "7:USD"): volver a uno ya visto es instantáneo. */
  entries: Record<string, DashboardEntry>
}

const initialState: DashboardState = { entries: {} }

export function dashboardKey(portfolioId: number | null, currency: Currency): string {
  return `${portfolioId ?? 'all'}:${currency}`
}

interface DashboardScope {
  portfolioId: number | null
  currency: Currency
}

/** Pide el resumen (cabecera y dona) y las posiciones (tabla) a la vez. */
export const fetchDashboard = createAsyncThunk<
  { summary: DashboardSummaryResponse; holdings: HoldingResponse[] },
  DashboardScope,
  { rejectValue: ApiErrorData }
>('dashboard/fetch', async ({ portfolioId, currency }, { rejectWithValue }) => {
  const query = new URLSearchParams({ currency })
  if (portfolioId !== null) {
    query.set('portfolioId', String(portfolioId))
  }
  try {
    const [summary, holdings] = await Promise.all([
      api<DashboardSummaryResponse>('GET', `/dashboard/summary?${query}`),
      api<HoldingsResponse>('GET', `/holdings?${query}`),
    ])
    return { summary, holdings: holdings.holdings }
  } catch (e) {
    return rejectWithValue(toApiErrorData(e))
  }
})

/** Algo cambió las posiciones (una operación nueva, un portafolio movido…): lo guardado ya no sirve. */
export const dashboardInvalidated = createAction('dashboard/invalidated')

const reset = () => initialState

const dashboardSlice = createSlice({
  name: 'dashboard',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchDashboard.pending, (state, action) => {
        const key = dashboardKey(action.meta.arg.portfolioId, action.meta.arg.currency)
        const entry = state.entries[key]
        if (entry && entry.summary) {
          entry.refreshing = true
        } else {
          state.entries[key] = { summary: null, holdings: [], status: 'loading', refreshing: false, error: null }
        }
      })
      .addCase(fetchDashboard.fulfilled, (state, action) => {
        const key = dashboardKey(action.meta.arg.portfolioId, action.meta.arg.currency)
        state.entries[key] = { ...action.payload, status: 'ready', refreshing: false, error: null }
      })
      .addCase(fetchDashboard.rejected, (state, action) => {
        const key = dashboardKey(action.meta.arg.portfolioId, action.meta.arg.currency)
        const entry = state.entries[key]
        if (!entry) {
          return // se invalidó mientras tanto
        }
        entry.refreshing = false
        entry.error = action.payload ?? null
        if (!entry.summary) {
          entry.status = 'error' // si ya había datos, se siguen mostrando
        }
      })
      // Cambian las posiciones: duplicar suma transacciones a "Todos"; eliminar puede moverlas a otro portafolio.
      .addCase(dashboardInvalidated, reset)
      .addCase(duplicatePortfolio.fulfilled, reset)
      .addCase(deletePortfolio.fulfilled, reset)
      .addCase(logout.fulfilled, reset)
      .addCase(sessionExpired, reset)
  },
})

export default dashboardSlice.reducer
