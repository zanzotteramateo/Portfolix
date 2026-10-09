import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { api } from '../../api/client'
import type { AssetResponse } from '../../api/types'

/** El catálogo de activos (unos 60, cambia muy de vez en cuando): se pide una vez por sesión. */
export interface AssetsState {
  items: AssetResponse[]
  status: 'idle' | 'loading' | 'ready' | 'error'
}

const initialState: AssetsState = { items: [], status: 'idle' }

export const fetchAssets = createAsyncThunk<AssetResponse[], void, { state: { assets: AssetsState } }>(
  'assets/fetch',
  () => api<AssetResponse[]>('GET', '/assets'),
  { condition: (_, { getState }) => getState().assets.status === 'idle' || getState().assets.status === 'error' },
)

const assetsSlice = createSlice({
  name: 'assets',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchAssets.pending, (state) => {
        state.status = 'loading'
      })
      .addCase(fetchAssets.fulfilled, (state, action) => {
        state.items = action.payload
        state.status = 'ready'
      })
      .addCase(fetchAssets.rejected, (state) => {
        state.status = 'error'
      })
  },
})

export default assetsSlice.reducer
