import { useNavigate } from 'react-router'
import { Button } from '../../../components/Button'
import * as authApi from '../authApi'
import { ResultCard } from '../components/ResultCard'
import { useTokenLink } from '../useTokenLink'

/** El link del mail de verificación: /verify-email?token=… */
export function VerifyEmailPage() {
  const navigate = useNavigate()
  const link = useTokenLink(authApi.verifyEmail)

  if (link.status === 'pending') {
    return (
      <ResultCard tone="pending" title="Confirmando tu correo…">
        Un segundo.
      </ResultCard>
    )
  }
  if (link.status === 'error') {
    return (
      <ResultCard
        tone="error"
        title="No pudimos confirmar tu correo"
        action={
          <Button variant="secondary" onClick={() => navigate('/check-email')}>
            Pedir un enlace nuevo
          </Button>
        }
      >
        {link.message}
      </ResultCard>
    )
  }
  return (
    <ResultCard
      tone="success"
      title="¡Correo confirmado!"
      action={<Button onClick={() => navigate('/login')}>Iniciar Sesión</Button>}
    >
      Tu cuenta ya está activa. Ya podés iniciar sesión.
    </ResultCard>
  )
}
