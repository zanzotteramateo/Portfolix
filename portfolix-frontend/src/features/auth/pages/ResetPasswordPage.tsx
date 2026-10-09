import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { toApiErrorData, type ApiErrorData } from '../../../api/client'
import { applyFieldErrors } from '../../../api/formErrors'
import { Alert } from '../../../components/Alert'
import { AuthCard, AuthIntro } from '../../../components/AuthCard'
import { Button } from '../../../components/Button'
import { TextField } from '../../../components/TextField'
import { ArrowLeftIcon, LockIcon } from '../../../components/icons'
import * as authApi from '../authApi'
import { PasswordRequirements, PasswordStrength } from '../components/PasswordStrength'
import { ResultCard } from '../components/ResultCard'
import { strongPassword } from '../password'
import styles from './AuthPages.module.css'

interface ResetForm {
  newPassword: string
  confirmPassword: string
}

/** El link del mail de recuperación: /reset-password?token=… */
export function ResetPasswordPage() {
  const navigate = useNavigate()
  const token = useSearchParams()[0].get('token')
  const [done, setDone] = useState(false)
  // El problema es el link (inválido, vencido o ya usado): hay que pedir otro.
  const [linkError, setLinkError] = useState<string | null>(token ? null : 'El enlace no es válido.')
  const [error, setError] = useState<ApiErrorData | null>(null)
  const {
    register,
    handleSubmit,
    control,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<ResetForm>({ defaultValues: { newPassword: '', confirmPassword: '' } })
  const newPassword = useWatch({ control, name: 'newPassword' })

  async function onSubmit({ newPassword }: ResetForm) {
    setError(null)
    try {
      await authApi.resetPassword({ token: token ?? '', newPassword })
      setDone(true)
    } catch (e) {
      const data = toApiErrorData(e)
      if (data.fieldErrors.token) {
        setLinkError(data.fieldErrors.token)
      } else if (!applyFieldErrors(data, setFieldError, ['newPassword'])) {
        setError(data)
      }
    }
  }

  if (done) {
    return (
      <ResultCard
        tone="success"
        title="Contraseña actualizada"
        action={<Button onClick={() => navigate('/login')}>Iniciar Sesión</Button>}
      >
        Ya podés iniciar sesión con tu nueva contraseña. Por seguridad, cerramos la sesión en todos tus dispositivos.
      </ResultCard>
    )
  }

  if (linkError) {
    return (
      <ResultCard
        tone="error"
        title="El enlace no sirve"
        action={<Button onClick={() => navigate('/forgot-password')}>Pedir un enlace nuevo</Button>}
      >
        {linkError.endsWith('.') ? linkError : `${linkError}.`}
      </ResultCard>
    )
  }

  return (
    <AuthCard>
      <AuthIntro icon={<LockIcon size={20} />} title="Creá una nueva contraseña">
        Elegí una contraseña distinta de la que tenías.
      </AuthIntro>
      {error && <Alert>{error.message}</Alert>}
      <form className={styles.form} onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className={styles.fields}>
          <TextField
            label="Nueva contraseña"
            type="password"
            autoComplete="new-password"
            error={errors.newPassword?.message}
            {...register('newPassword', { validate: strongPassword })}
          >
            <PasswordStrength password={newPassword} />
          </TextField>
          <TextField
            label="Confirmar contraseña"
            type="password"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
            {...register('confirmPassword', {
              validate: (value, values) => value === values.newPassword || 'Las contraseñas no coinciden.',
            })}
          />
        </div>
        <PasswordRequirements password={newPassword} />
        <Button type="submit" loading={isSubmitting}>
          Guardar contraseña
        </Button>
      </form>
      <Link to="/login" className={styles.backLink}>
        <ArrowLeftIcon size={12} /> Volver al Inicio de Sesión
      </Link>
    </AuthCard>
  )
}
