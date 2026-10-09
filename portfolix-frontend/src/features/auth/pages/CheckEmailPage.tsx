import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation } from 'react-router'
import { toApiErrorData, type ApiErrorData } from '../../../api/client'
import { applyFieldErrors, EMAIL_PATTERN } from '../../../api/formErrors'
import { Alert } from '../../../components/Alert'
import { AuthCard, AuthIntro } from '../../../components/AuthCard'
import { Button } from '../../../components/Button'
import { TextField } from '../../../components/TextField'
import { ArrowLeftIcon, MailIcon } from '../../../components/icons'
import { formatCountdown, useCountdown } from '../../../hooks/useCountdown'
import * as authApi from '../authApi'
import styles from './AuthPages.module.css'

/** Los mismos 60 s que el backend (portfolix.auth.email-token.resend-cooldown): antes, no reenvía. */
const RESEND_COOLDOWN_SECONDS = 60

interface CheckEmailState {
  email?: string
  /** Se acaba de mandar un mail (recién registrado): el reenvío arranca bloqueado. */
  justSent?: boolean
}

/**
 * "Revisá tu correo": después de registrarse, o al intentar entrar sin haber confirmado el mail.
 * Permite reenviar el mail y corregir la dirección si se escribió mal.
 * Si se llega sin un mail (ej.: recargando la página), lo pide.
 */
export function CheckEmailPage() {
  const state = (useLocation().state ?? {}) as CheckEmailState
  const [email, setEmail] = useState(state.email ?? '')
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<ApiErrorData | null>(null)
  const [changingEmail, setChangingEmail] = useState(false)
  const [sending, setSending] = useState(false)
  const cooldown = useCountdown()
  const { start: startCooldown } = cooldown

  useEffect(() => {
    if (state.justSent) {
      startCooldown(RESEND_COOLDOWN_SECONDS)
    }
  }, [state.justSent, startCooldown])

  async function resend(to = email) {
    setError(null)
    setNotice(null)
    setSending(true)
    try {
      await authApi.resendVerification(to)
      setNotice('Te reenviamos el correo.')
      startCooldown(RESEND_COOLDOWN_SECONDS)
    } catch (e) {
      setError(toApiErrorData(e))
    } finally {
      setSending(false)
    }
  }

  function emailChanged(newEmail: string) {
    setEmail(newEmail)
    setChangingEmail(false)
    setNotice(`Listo: te mandamos el enlace a ${newEmail}.`)
    startCooldown(RESEND_COOLDOWN_SECONDS)
  }

  if (!email) {
    // Lo pidió explícitamente: el reenvío sale en el momento.
    return (
      <AskEmail
        onEmail={(typed) => {
          setEmail(typed)
          void resend(typed)
        }}
      />
    )
  }

  return (
    <AuthCard>
      <AuthIntro icon={<MailIcon size={20} />} title="Revisá tu correo">
        Te enviamos un correo a <span className={styles.email}>{email}</span> con un enlace para activar tu
        cuenta. El enlace vence en 24 horas.
      </AuthIntro>

      <ol className={styles.steps}>
        <li>
          <span className={styles.stepNumber}>1</span>Abrí el correo de Portfolix
        </li>
        <li>
          <span className={styles.stepNumber}>2</span>Tocá “Confirmar mi correo”
        </li>
        <li>
          <span className={styles.stepNumber}>3</span>Volvé acá e iniciá sesión
        </li>
      </ol>

      {notice && <Alert tone="success">{notice}</Alert>}
      {error && <Alert>{error.message}</Alert>}

      {changingEmail ? (
        <ChangeEmailForm currentEmail={email} onChanged={emailChanged} onCancel={() => setChangingEmail(false)} />
      ) : (
        <>
          <Button variant="secondary" onClick={() => resend()} loading={sending} disabled={cooldown.running}>
            {cooldown.running ? `Reenviar correo en ${formatCountdown(cooldown.remaining)}` : 'Reenviar correo'}
          </Button>
          <p className={styles.hint}>¿No te llegó? Revisá la carpeta de spam.</p>
          <hr className={styles.separator} />
          <div className={styles.row}>
            <button type="button" className={styles.linkButton} onClick={() => setChangingEmail(true)}>
              Cambiar correo electrónico
            </button>
            <Link to="/login" className={styles.backLink}>
              <ArrowLeftIcon size={12} /> Iniciar Sesión
            </Link>
          </div>
        </>
      )}
    </AuthCard>
  )
}

interface ChangeEmailFormValues {
  newEmail: string
  password: string
}

/** Corrige el mail de una cuenta sin verificar. Pide la contraseña para confirmar que es el dueño. */
function ChangeEmailForm({
  currentEmail,
  onChanged,
  onCancel,
}: {
  currentEmail: string
  onChanged: (newEmail: string) => void
  onCancel: () => void
}) {
  const [error, setError] = useState<ApiErrorData | null>(null)
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<ChangeEmailFormValues>({ defaultValues: { newEmail: '', password: '' } })

  async function onSubmit({ newEmail, password }: ChangeEmailFormValues) {
    setError(null)
    try {
      await authApi.changePendingEmail({ email: currentEmail, password, newEmail })
      onChanged(newEmail.trim().toLowerCase())
    } catch (e) {
      const data = toApiErrorData(e)
      if (!applyFieldErrors(data, setFieldError, ['newEmail', 'password'])) {
        setError(data)
      }
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit(onSubmit)} noValidate>
      {error && <Alert>{error.message}</Alert>}
      <div className={styles.fields}>
        <TextField
          label="Correo correcto"
          type="email"
          autoComplete="email"
          error={errors.newEmail?.message}
          {...register('newEmail', {
            required: 'Ingresá el correo nuevo',
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
      <Button type="submit" loading={isSubmitting}>
        Cambiar y reenviar
      </Button>
      <button type="button" className={styles.linkButton} onClick={onCancel}>
        Cancelar
      </button>
    </form>
  )
}

/** Si se llega a "Revisá tu correo" sin saber el mail, se lo pide para poder reenviar. */
function AskEmail({ onEmail }: { onEmail: (email: string) => void }) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<{ email: string }>({ defaultValues: { email: '' } })

  return (
    <AuthCard>
      <AuthIntro icon={<MailIcon size={20} />} title="Confirmá tu correo">
        Ingresá el correo con el que te registraste y te reenviamos el enlace para activar tu cuenta.
      </AuthIntro>
      <form className={styles.form} onSubmit={handleSubmit(({ email }) => onEmail(email.trim()))} noValidate>
        <TextField
          label="Correo electrónico"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email', {
            required: 'Ingresá tu correo',
            pattern: { value: EMAIL_PATTERN, message: 'El correo no es válido' },
          })}
        />
        <Button type="submit">Continuar</Button>
      </form>
      <Link to="/login" className={styles.backLink}>
        <ArrowLeftIcon size={12} /> Volver al Inicio de Sesión
      </Link>
    </AuthCard>
  )
}
