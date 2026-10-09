import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { api, toApiErrorData, type ApiErrorData } from '../../api/client'
import { applyFieldErrors, EMAIL_PATTERN } from '../../api/formErrors'
import type { EmailChangeRequest, UserResponse } from '../../api/types'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Dialog, DialogBody, DialogFooter } from '../../components/Dialog'
import { TextField } from '../../components/TextField'
import { NoPasswordNotice } from './NoPasswordNotice'
import styles from './AccountDialogs.module.css'

interface EmailForm {
  newEmail: string
  currentPassword: string
}

const FIELDS = ['newEmail', 'currentPassword'] as const

/**
 * "Cambiar correo electrónico": el link de confirmación va al mail nuevo, y hasta confirmarlo se sigue entrando
 * con el actual. Pide la contraseña para confirmar que es el dueño de la cuenta.
 */
export function ChangeEmailDialog({ user, onClose }: { user: UserResponse; onClose: () => void }) {
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [error, setError] = useState<ApiErrorData | null>(null)
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<EmailForm>({ defaultValues: { newEmail: '', currentPassword: '' } })

  async function onSubmit(values: EmailForm) {
    setError(null)
    const newEmail = values.newEmail.trim()
    const request: EmailChangeRequest = { newEmail, currentPassword: values.currentPassword }
    try {
      await api<void>('POST', '/me/email-change', request)
      setSentTo(newEmail)
    } catch (e) {
      const data = toApiErrorData(e)
      if (!applyFieldErrors(data, setFieldError, FIELDS)) {
        setError(data)
      }
    }
  }

  if (!user.hasPassword) {
    return (
      <Dialog title="Cambiar correo electrónico" onClose={onClose}>
        <DialogBody>
          <NoPasswordNotice purpose="Para cambiar el correo" />
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cerrar
          </Button>
        </DialogFooter>
      </Dialog>
    )
  }

  if (sentTo) {
    return (
      <Dialog title="Revisá tu correo nuevo" onClose={onClose}>
        <DialogBody>
          <Alert tone="success">
            Te enviamos un enlace a <strong>{sentTo}</strong>. El cambio se aplica cuando lo confirmes; hasta entonces
            seguís entrando con <strong>{user.email}</strong>.
          </Alert>
        </DialogBody>
        <DialogFooter>
          <Button type="button" onClick={onClose}>
            Listo
          </Button>
        </DialogFooter>
      </Dialog>
    )
  }

  return (
    <Dialog
      title="Cambiar correo electrónico"
      subtitle="Te enviaremos un enlace al nuevo correo. El cambio se aplica cuando lo confirmes."
      onClose={onClose}
    >
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogBody>
          {error && <Alert>{error.message}</Alert>}
          <div className={styles.fields}>
            <TextField
              label="Correo nuevo"
              type="email"
              autoComplete="email"
              error={errors.newEmail?.message}
              {...register('newEmail', {
                required: 'Ingresá el correo nuevo',
                pattern: { value: EMAIL_PATTERN, message: 'Ingresá un correo válido' },
              })}
            />
            <TextField
              label="Contraseña actual"
              type="password"
              autoComplete="current-password"
              error={errors.currentPassword?.message}
              {...register('currentPassword', { required: 'Ingresá tu contraseña actual' })}
            />
          </div>
          <p className={styles.hint}>Para confirmar que sos vos.</p>
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" loading={isSubmitting}>
            Enviar enlace
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  )
}
