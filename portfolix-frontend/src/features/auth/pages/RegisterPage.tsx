import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { toApiErrorData, type ApiErrorData } from '../../../api/client'
import { applyFieldErrors, EMAIL_PATTERN } from '../../../api/formErrors'
import { Alert } from '../../../components/Alert'
import { AuthCard } from '../../../components/AuthCard'
import { Button } from '../../../components/Button'
import { Checkbox } from '../../../components/Checkbox'
import { Divider } from '../../../components/Divider'
import { TextField } from '../../../components/TextField'
import { AlertIcon } from '../../../components/icons'
import * as authApi from '../authApi'
import { GoogleButton } from '../components/GoogleButton'
import { PasswordStrength } from '../components/PasswordStrength'
import { strongPassword } from '../password'
import { useCompleteSignIn } from '../useCompleteSignIn'
import styles from './AuthPages.module.css'

interface RegisterForm {
  fullName: string
  email: string
  password: string
  confirmPassword: string
  acceptedTerms: boolean
}

export function RegisterPage() {
  const navigate = useNavigate()
  const completeSignIn = useCompleteSignIn()
  const [error, setError] = useState<ApiErrorData | null>(null)
  const {
    register,
    handleSubmit,
    control,
    setError: setFieldError,
    formState: { errors, isSubmitting, isSubmitted },
  } = useForm<RegisterForm>({
    defaultValues: { fullName: '', email: '', password: '', confirmPassword: '', acceptedTerms: false },
  })
  const password = useWatch({ control, name: 'password' })
  const hasFieldErrors = Object.keys(errors).length > 0

  async function onSubmit({ fullName, email, password, acceptedTerms }: RegisterForm) {
    setError(null)
    try {
      // confirmPassword no viaja: es solo para el formulario.
      await authApi.register({ fullName, email, password, acceptedTerms })
      // La cuenta nace sin verificar: no hay sesión hasta confirmar el mail.
      navigate('/check-email', { state: { email, justSent: true } })
    } catch (e) {
      const data = toApiErrorData(e)
      if (!applyFieldErrors(data, setFieldError, ['fullName', 'email', 'password', 'acceptedTerms'])) {
        setError(data)
      }
    }
  }

  async function onGoogleCredential(idToken: string) {
    setError(null)
    try {
      completeSignIn(await authApi.loginWithGoogle({ idToken, rememberMe: false }))
    } catch (e) {
      setError(toApiErrorData(e))
    }
  }

  return (
    <AuthCard>
      <h1 className={styles.title}>Crear Cuenta</h1>
      {error && <Alert>{error.message}</Alert>}

      <form className={styles.form} onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className={styles.fields}>
          <TextField
            label="Nombre completo"
            autoComplete="name"
            placeholder="Juan Pérez"
            error={errors.fullName?.message}
            {...register('fullName', {
              validate: (value) => value.trim() !== '' || 'Ingresá tu nombre',
              maxLength: { value: 100, message: 'El nombre puede tener hasta 100 caracteres' },
            })}
          />
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
            {errors.email?.type === 'server' && (
              // El backend solo rechaza un mail con formato válido si ya tiene cuenta.
              <p className={styles.fieldHint}>
                ¿Es tuya? <Link to="/login">Iniciá sesión</Link> o{' '}
                <Link to="/forgot-password">recuperá tu contraseña</Link>.
              </p>
            )}
          </div>
          <TextField
            label="Contraseña"
            type="password"
            autoComplete="new-password"
            error={errors.password?.message}
            {...register('password', { validate: strongPassword })}
          >
            <PasswordStrength password={password} />
          </TextField>
          <TextField
            label="Confirmar contraseña"
            type="password"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
            {...register('confirmPassword', {
              validate: (value, values) => value === values.password || 'Las contraseñas no coinciden.',
            })}
          />
        </div>

        <div className={styles.fields}>
          <Checkbox
            aria-invalid={errors.acceptedTerms ? true : undefined}
            {...register('acceptedTerms', { validate: (v) => v || 'Tenés que aceptar los términos y condiciones' })}
          >
            Acepto los Términos y Condiciones
          </Checkbox>
          {errors.acceptedTerms && (
            <p className={styles.fieldError}>
              <AlertIcon size={13} /> {errors.acceptedTerms.message}
            </p>
          )}
        </div>

        <Button type="submit" loading={isSubmitting}>
          Crear Cuenta
        </Button>
        {isSubmitted && hasFieldErrors && (
          <p className={styles.formError}>Corregí los campos marcados y volvé a intentar.</p>
        )}
      </form>

      <Divider>o registrate con</Divider>
      <GoogleButton onCredential={onGoogleCredential} />

      <p className={styles.footer}>
        ¿Ya tenés cuenta? <Link to="/login">Iniciar Sesión</Link>
      </p>
    </AuthCard>
  )
}
