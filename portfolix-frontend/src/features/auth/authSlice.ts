import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'
import { api, refreshAccessToken, setAccessToken } from '../../api/client'
import type { UserResponse } from '../../api/types'

/**
 * - unknown: la app recién abrió y todavía no sabe si hay sesión (está probando la cookie).
 * - authenticated / anonymous: ya sabe.
 */
export type SessionStatus = 'unknown' | 'authenticated' | 'anonymous'

export interface AuthState {
  status: SessionStatus
  user: UserResponse | null
  /** La sesión se cerró sola (venció): la pantalla de login lo avisa. */
  expired: boolean
  restoring: boolean
}

const initialState: AuthState = { status: 'unknown', user: null, expired: false, restoring: false }

/**
 * Al abrir la app: si la cookie del refresh token sigue viva, recupera la sesión sin pedir login
 * (el access token se perdió al recargar, porque vive en memoria).
 */
export const restoreSession = createAsyncThunk<UserResponse | null, void, { state: { auth: AuthState } }>(
  'auth/restoreSession',
  async () => ((await refreshAccessToken()) ? api<UserResponse>('GET', '/me') : null),
  {
    // En desarrollo React monta los componentes dos veces (modo estricto): que el pedido salga una sola.
    condition: (_, { getState }) => getState().auth.status === 'unknown' && !getState().auth.restoring,
  },
)

/** Cierra la sesión en el backend (revoca el refresh token y borra la cookie) y en este navegador. */
export const logout = createAsyncThunk('auth/logout', async () => {
  try {
    await api<void>('POST', '/auth/logout')
  } catch {
    // Aunque el backend no responda, la sesión se cierra en este navegador.
  }
  setAccessToken(null)
})

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    signedIn(state, action: PayloadAction<UserResponse>) {
      state.status = 'authenticated'
      state.user = action.payload
      state.expired = false
    },
    userUpdated(state, action: PayloadAction<UserResponse>) {
      state.user = action.payload
    },
    /** No se pudo renovar el access token: la sesión venció o se cerró desde otro dispositivo. */
    sessionExpired(state) {
      if (state.status === 'authenticated') {
        state.expired = true
      }
      state.status = 'anonymous'
      state.user = null
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(restoreSession.pending, (state) => {
        state.restoring = true
      })
      .addCase(restoreSession.fulfilled, (state, action) => {
        state.restoring = false
        state.status = action.payload ? 'authenticated' : 'anonymous'
        state.user = action.payload
      })
      .addCase(restoreSession.rejected, (state) => {
        state.restoring = false
        state.status = 'anonymous'
      })
      .addCase(logout.fulfilled, (state) => {
        state.status = 'anonymous'
        state.user = null
        state.expired = false
      })
  },
})

export const { signedIn, userUpdated, sessionExpired } = authSlice.actions
export default authSlice.reducer
