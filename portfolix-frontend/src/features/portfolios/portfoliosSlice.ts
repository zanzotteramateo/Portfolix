import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { api, toApiErrorData, type ApiErrorData } from '../../api/client'
import type { PortfolioResponse } from '../../api/types'
import { logout, sessionExpired } from '../auth/authSlice'

export interface PortfoliosState {
  items: PortfolioResponse[]
  status: 'idle' | 'loading' | 'ready' | 'error'
  /** El formulario de "Crear portafolio" del menú lateral está abierto (lo abren también otras pantallas). */
  createFormOpen: boolean
}

const initialState: PortfoliosState = { items: [], status: 'idle', createFormOpen: false }

type ThunkConfig = { rejectValue: ApiErrorData; state: { portfolios: PortfoliosState } }

/*
 * Cada thunk devuelve el error de la API como dato (rejectWithValue), para que el formulario lo muestre.
 * Ojo: rejectWithValue hay que DEVOLVERLO (return), no lanzarlo, para que la acción termine en "rejected".
 */

export const fetchPortfolios = createAsyncThunk<PortfolioResponse[], void, ThunkConfig>(
  'portfolios/fetch',
  async (_, { rejectWithValue }) => {
    try {
      return await api<PortfolioResponse[]>('GET', '/portfolios')
    } catch (e) {
      return rejectWithValue(toApiErrorData(e))
    }
  },
  { condition: (_, { getState }) => getState().portfolios.status === 'idle' },
)

export const createPortfolio = createAsyncThunk<PortfolioResponse, string, ThunkConfig>(
  'portfolios/create',
  async (name, { rejectWithValue }) => {
    try {
      return await api<PortfolioResponse>('POST', '/portfolios', { name })
    } catch (e) {
      return rejectWithValue(toApiErrorData(e))
    }
  },
)

export const renamePortfolio = createAsyncThunk<PortfolioResponse, { id: number; name: string }, ThunkConfig>(
  'portfolios/rename',
  async ({ id, name }, { rejectWithValue }) => {
    try {
      return await api<PortfolioResponse>('PATCH', `/portfolios/${id}`, { name })
    } catch (e) {
      return rejectWithValue(toApiErrorData(e))
    }
  },
)

/** "Duplicar como nuevo": el backend le pone el nombre ("Jubilación (copia)") y copia todas sus transacciones. */
export const duplicatePortfolio = createAsyncThunk<PortfolioResponse, number, ThunkConfig>(
  'portfolios/duplicate',
  async (id, { rejectWithValue }) => {
    try {
      return await api<PortfolioResponse>('POST', `/portfolios/${id}/duplicate`)
    } catch (e) {
      return rejectWithValue(toApiErrorData(e))
    }
  },
)

/** Elimina un portafolio: moviendo sus transacciones a otro (moveTo) o borrándolas (moveTo null). */
export const deletePortfolio = createAsyncThunk<number, { id: number; moveTo: number | null }, ThunkConfig>(
  'portfolios/delete',
  async ({ id, moveTo }, { rejectWithValue }) => {
    const query = moveTo === null ? 'deleteTransactions=true' : `moveTransactionsTo=${moveTo}`
    try {
      await api<void>('DELETE', `/portfolios/${id}?${query}`)
      return id
    } catch (e) {
      return rejectWithValue(toApiErrorData(e))
    }
  },
)

const portfoliosSlice = createSlice({
  name: 'portfolios',
  initialState,
  reducers: {
    createFormOpened(state) {
      state.createFormOpen = true
    },
    createFormClosed(state) {
      state.createFormOpen = false
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchPortfolios.pending, (state) => {
        state.status = 'loading'
      })
      .addCase(fetchPortfolios.fulfilled, (state, action) => {
        state.items = action.payload
        state.status = 'ready'
      })
      .addCase(fetchPortfolios.rejected, (state) => {
        state.status = 'error'
      })
      .addCase(createPortfolio.fulfilled, (state, action) => {
        state.items.push(action.payload)
        state.createFormOpen = false
      })
      .addCase(duplicatePortfolio.fulfilled, (state, action) => {
        state.items.push(action.payload)
      })
      .addCase(renamePortfolio.fulfilled, (state, action) => {
        const index = state.items.findIndex((p) => p.id === action.payload.id)
        if (index >= 0) {
          state.items[index] = action.payload
        }
      })
      .addCase(deletePortfolio.fulfilled, (state, action) => {
        state.items = state.items.filter((p) => p.id !== action.payload)
      })
      .addCase(logout.fulfilled, () => initialState)
      .addCase(sessionExpired, () => initialState)
  },
})

export const { createFormOpened, createFormClosed } = portfoliosSlice.actions
export default portfoliosSlice.reducer
