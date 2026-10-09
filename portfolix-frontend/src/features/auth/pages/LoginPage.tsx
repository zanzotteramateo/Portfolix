import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link } from 'react-router'
import { toApiErrorData, type ApiErrorData } from '../../../api/client'
import { applyFieldErrors, EMAIL_PATTERN } from '../../../api/formErrors'
import { useAppSelector } from '../../../app/hooks'
import { Alert } from '../../../components/Alert'
import { AuthCard } from '../../../components/AuthCard'
import { Button } from '../../../components/Button'
import { Checkbox } from '../../../components/Checkbox'
import { Divider } from '../../../components/Divider'
import { TextField } from '../../../components/TextField'
import { formatCountdown, useCountdown } from '../../../hooks/useCountdown'
import * as authApi from '../authApi'
import { GoogleButton } from '../components/GoogleButton'
import { useCompleteSignIn } from '../useCompleteSignIn'
import styles from './AuthPages.module.css'

interface LoginForm {
  email: string
  password: string
  rememberMe: boolean
}

export function LoginPage() {
  const sessionExpired = useAppSelector((state) => state.auth.expired)
  const completeSignIn = useCompleteSignIn()
  const [error, setError] = useState<ApiErrorData | null>(null)
  const lockout = useCountdown()
  // El bloqueo por intentos es por mail: con otro mail en el campo, se puede volver a intentar.
  const [lockedEmail, setLockedEmail] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    getValues,
    control,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ defaultValues: { email: '', password: '', rememberMe: false } })
  const email = useWatch({ control, name: 'email' })
  const locked = lockout.running && email === lockedEmail

  async function onSubmit(values: LoginForm) {
    setError(null)
    try {
      completeSignIn(await authApi.login(values))
    } catch (e) {
      showError(toApiErrorData(e))
    }
  }

  async function onGoogleCredential(idToken: string) {
    setError(null)
    try {
      completeSignIn(await authApi.loginWithGoogle({ idToken, rememberMe: getValues('rememberMe') }))
    } catch (e) {
      showError(toApiErrorData(e))
    }
  }

  function showError(data: ApiErrorData) {
    if (data.status === 429 && data.retryAfterSeconds) {
      lockout.start(data.retryAfterSeconds)
      setLockedEmail(getValues('email'))
    }
    if (!applyFieldErrors(data, setFieldError, ['email', 'password'])) {
      setError(data)
    }
  }

  return (
    <AuthCard>
      {sessionExpired && !error && <Alert tone="info">Tu sesión venció. Volvé a iniciar sesión para seguir.</Alert>}
      {error && <LoginError error={error} email={email} lockoutRemaining={locked ? lockout.remaining : 0} />}

      <form className={styles.form} onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className={styles.fields}>
          <TextField
            label="Correo electrónico"
            type="email"
            autoComplete="email"
            placeholder="juan@email.com"
            error={errors.email?.message}
            {...register('email', {
              required: 'Ingresá tu correo',
              pattern: { value: EMAIL_PATTERN, message: 'El correo no es válido' },
            })}
          />
          <TextField
            label="Contraseña"
            type="password"
            autoComplete="current-password"
            error={errors.password?.message}
            {...register('password', { required: 'Ingresá tu contraseña' })}
          />
        </div>

        <div className={styles.row}>
          <Checkbox {...register('rememberMe')}>Recuérdame</Checkbox>
          <Link to="/forgot-password">¿Olvidaste tu contraseña?</Link>
        </div>

        <Button type="submit" loading={isSubmitting} disabled={locked}>
          {locked ? `Probá de nuevo en ${formatCountdown(lockout.remaining)}` : 'Iniciar Sesión'}
        </Button>
      </form>

      <Divider>o continuá con</Divider>
      <GoogleButton onCredential={onGoogleCredential} />

      <p className={styles.footer}>
        ¿No tenés cuenta? <Link to="/register">Registrate</Link>
      </p>
    </AuthCard>
  )
}

/** El aviso de error del login, según lo que respondió el backend. */
function LoginError({ error, email, lockoutRemaining }: { error: ApiErrorData; email: string; lockoutRemaining: number }) {
  if (error.status === 403) {
    // Contraseña correcta, pero la cuenta todavía no confirmó el mail.
    return (
      <Alert>
        <span>{error.message}</span>
        <Link to="/check-email" state={{ email }}>
          Reenviar el correo de confirmación
        </Link>
      </Alert>
    )
  }
  if (error.status === 429) {
    return (
      <Alert>
        <span>{error.message}</span>
        {lockoutRemaining > 0 ? (
          <span>Podés volver a intentar en {formatCountdown(lockoutRemaining)}.</span>
        ) : (
          <span>Ya podés volver a intentar.</span>
        )}
        <Link to="/forgot-password">Restablecer mi contraseña</Link>
      </Alert>
    )
  }
  // 401 ("Correo o contraseña incorrectos…", con los intentos que quedan), sin conexión, etc.
  return <Alert>{error.message}</Alert>
}
