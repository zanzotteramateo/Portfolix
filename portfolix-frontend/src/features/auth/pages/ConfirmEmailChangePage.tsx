import { useEffect } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../../../api/client'
import type { UserResponse } from '../../../api/types'
import { useAppDispatch, useAppSelector } from '../../../app/hooks'
import { Button } from '../../../components/Button'
import * as authApi from '../authApi'
import { userUpdated } from '../authSlice'
import { ResultCard } from '../components/ResultCard'
import { useTokenLink } from '../useTokenLink'

/** El link del mail de cambio de correo (llega al correo nuevo): /confirm-email-change?token=… */
export function ConfirmEmailChangePage() {
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const signedIn = useAppSelector((state) => state.auth.status === 'authenticated')
  const link = useTokenLink(authApi.confirmEmailChange)

  // Si hay sesión abierta en este navegador, el mail que se muestra en la app tiene que ser el nuevo.
  useEffect(() => {
    if (link.status === 'done' && signedIn) {
      api<UserResponse>('GET', '/me')
        .then((user) => dispatch(userUpdated(user)))
        .catch(() => {})
    }
  }, [link.status, signedIn, dispatch])

  const goOn = signedIn ? (
    <Button onClick={() => navigate('/')}>Ir a Portfolix</Button>
  ) : (
    <Button onClick={() => navigate('/login')}>Iniciar Sesión</Button>
  )

  if (link.status === 'pending') {
    return (
      <ResultCard tone="pending" title="Confirmando tu correo nuevo…">
        Un segundo.
      </ResultCard>
    )
  }
  if (link.status === 'error') {
    return (
      <ResultCard tone="error" title="No pudimos cambiar tu correo" action={goOn}>
        {link.message}
      </ResultCard>
    )
  }
  return (
    <ResultCard tone="success" title="Tu correo se actualizó" action={goOn}>
      Desde ahora iniciás sesión con tu correo nuevo.
    </ResultCard>
  )
}
