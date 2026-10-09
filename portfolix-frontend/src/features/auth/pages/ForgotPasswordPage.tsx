import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { toApiErrorData, type ApiErrorData } from '../../../api/client'
import { applyFieldErrors, EMAIL_PATTERN } from '../../../api/formErrors'
import { Alert } from '../../../components/Alert'
import { AuthCard, AuthIntro } from '../../../components/AuthCard'
import { Button } from '../../../components/Button'
import { TextField } from '../../../components/TextField'
import { ArrowLeftIcon, KeyIcon } from '../../../components/icons'
import * as authApi from '../authApi'
import { ResultCard } from '../components/ResultCard'
import styles from './AuthPages.module.css'

export function ForgotPasswordPage() {
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [error, setError] = useState<ApiErrorData | null>(null)
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<{ email: string }>({ defaultValues: { email: '' } })

  async function onSubmit({ email }: { email: string }) {
    setError(null)
    try {
      await authApi.forgotPassword(email)
      setSentTo(email.trim())
    } catch (e) {
      const data = toApiErrorData(e)
      if (!applyFieldErrors(data, setFieldError, ['email'])) {
        setError(data)
      }
    }
  }

  if (sentTo) {
    // El backend responde igual haya o no una cuenta con ese mail, para no revelar cuáles existen.
    return (
      <ResultCard
        tone="pending"
        title="Revisá tu correo"
        action={
          <Link to="/login" className={styles.backLink}>
            <ArrowLeftIcon size={12} /> Volver al Inicio de Sesión
          </Link>
        }
      >
        Si hay una cuenta con <span className={styles.email}>{sentTo}</span>, te enviamos un enlace para crear una
        contraseña nueva. Vence en 1 hora. ¿No te llegó? Revisá la carpeta de spam.
      </ResultCard>
    )
  }

  return (
    <AuthCard>
      <AuthIntro icon={<KeyIcon size={20} />} title="¿Olvidaste tu contraseña?">
        Ingresá tu correo y te enviamos un enlace para crear una contraseña nueva.
      </AuthIntro>
      {error && <Alert>{error.message}</Alert>}
      <form className={styles.form} onSubmit={handleSubmit(onSubmit)} noValidate>
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
        <Button type="submit" loading={isSubmitting}>
          Enviar enlace de recuperación
        </Button>
      </form>
      <Link to="/login" className={styles.backLink}>
        <ArrowLeftIcon size={12} /> Volver al Inicio de Sesión
      </Link>
    </AuthCard>
  )
}
