import { useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router'
import type { UserResponse } from '../../api/types'
import { useAppDispatch } from '../../app/hooks'
import { signedIn } from './authSlice'

/**
 * Después de un login (con contraseña o con Google): guarda la sesión en Redux y vuelve a la pantalla
 * que pidió el login (RequireAuth la deja en location.state.from), o al inicio.
 */
export function useCompleteSignIn() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  return useCallback(
    (user: UserResponse) => {
      dispatch(signedIn(user))
      navigate(from, { replace: true })
    },
    [dispatch, navigate, from],
  )
}
