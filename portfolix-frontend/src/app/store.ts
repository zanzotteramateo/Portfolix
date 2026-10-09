import { configureStore } from '@reduxjs/toolkit'
import assetsReducer from '../features/assets/assetsSlice'
import authReducer from '../features/auth/authSlice'
import dashboardReducer from '../features/dashboard/dashboardSlice'
import portfoliosReducer from '../features/portfolios/portfoliosSlice'
import preferencesReducer from '../features/preferences/preferencesSlice'

/*
 * El estado compartido de la app (decisión de la fase 11): la sesión, las preferencias y los datos del
 * backend que usan varias pantallas. Lo que usa una sola pantalla queda en su propio estado.
 */
export const store = configureStore({
  reducer: {
    auth: authReducer,
    preferences: preferencesReducer,
    portfolios: portfoliosReducer,
    dashboard: dashboardReducer,
    assets: assetsReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
