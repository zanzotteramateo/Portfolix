import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { api } from '../../api/client'
import type { PreferencesResponse, PreferencesUpdateRequest } from '../../api/types'
import { logout, sessionExpired } from '../auth/authSlice'

/** Los mismos valores por defecto que el backend (mientras no se cargan, y si el usuario nunca guardó nada). */
const DEFAULTS: PreferencesResponse = { theme: 'LIGHT', decimalSeparator: 'COMMA', currency: 'ARS', hideAmounts: false }

export interface PreferencesState extends PreferencesResponse {
  status: 'idle' | 'loading' | 'ready' | 'error'
  /** Lo último que confirmó el backend: si guardar un cambio falla, se vuelve a esto. */
  saved: PreferencesResponse
}

const initialState: PreferencesState = { ...DEFAULTS, status: 'idle', saved: DEFAULTS }

export const fetchPreferences = createAsyncThunk<PreferencesResponse, void, { state: { preferences: PreferencesState } }>(
  'preferences/fetch',
  () => api<PreferencesResponse>('GET', '/me/preferences'),
  { condition: (_, { getState }) => getState().preferences.status === 'idle' },
)

/** Guarda solo lo que cambia. El cambio se ve al instante (sin esperar al backend) y se deshace si falla. */
export const updatePreferences = createAsyncThunk<PreferencesResponse, PreferencesUpdateRequest>(
  'preferences/update',
  (changes) => api<PreferencesResponse>('PATCH', '/me/preferences', changes),
)

const preferencesSlice = createSlice({
  name: 'preferences',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchPreferences.pending, (state) => {
        state.status = 'loading'
      })
      .addCase(fetchPreferences.fulfilled, (state, action) => {
        Object.assign(state, action.payload)
        state.saved = action.payload
        state.status = 'ready'
      })
      .addCase(fetchPreferences.rejected, (state) => {
        state.status = 'error' // se sigue con los valores por defecto
      })
      .addCase(updatePreferences.pending, (state, action) => {
        Object.assign(state, action.meta.arg)
      })
      .addCase(updatePreferences.fulfilled, (state, action) => {
        state.saved = action.payload
      })
      .addCase(updatePreferences.rejected, (state) => {
        Object.assign(state, state.saved)
      })
      .addCase(logout.fulfilled, () => initialState)
      .addCase(sessionExpired, () => initialState)
  },
})

export default preferencesSlice.reducer
