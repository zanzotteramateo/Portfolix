import { Navigate, Outlet, useLocation } from 'react-router'
import { useAppSelector } from '../../app/hooks'
import { Splash } from '../../components/Splash'

/** Rutas que necesitan sesión: sin sesión, al login; después del login vuelve a donde quería ir. */
export function RequireAuth() {
  const status = useAppSelector((state) => state.auth.status)
  const location = useLocation()

  if (status === 'unknown') {
    return <Splash />
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <Outlet />
}

/** Login, registro y recuperación: con la sesión ya abierta no tienen sentido, van al inicio. */
export function PublicOnly() {
  const status = useAppSelector((state) => state.auth.status)

  if (status === 'unknown') {
    return <Splash />
  }
  if (status === 'authenticated') {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}
