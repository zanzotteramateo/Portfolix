import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { RouterProvider } from 'react-router/dom'
import { setSessionExpiredHandler } from './api/client'
import { store } from './app/store'
import { restoreSession, sessionExpired } from './features/auth/authSlice'
import { ThemeSync } from './features/preferences/ThemeSync'
import { router } from './router'
import './styles/global.css'

// Si el cliente HTTP no puede renovar el access token, la sesión venció: las rutas privadas mandan al login.
setSessionExpiredHandler(() => store.dispatch(sessionExpired()))

// Al abrir o recargar la página: si la cookie del refresh token sigue viva, se recupera la sesión.
store.dispatch(restoreSession())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <ThemeSync />
      <RouterProvider router={router} />
    </Provider>
  </StrictMode>,
)
