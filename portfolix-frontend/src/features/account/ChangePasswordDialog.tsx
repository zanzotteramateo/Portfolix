import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { api, toApiErrorData, type ApiErrorData } from '../../api/client'
import { applyFieldErrors } from '../../api/formErrors'
import type { ChangePasswordRequest, UserResponse } from '../../api/types'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Dialog, DialogBody, DialogFooter } from '../../components/Dialog'
import { TextField } from '../../components/TextField'
import { forgotPassword } from '../auth/authApi'
import { PasswordRequirements, PasswordStrength } from '../auth/components/PasswordStrength'
import { strongPassword } from '../auth/password'
import { NoPasswordNotice } from './NoPasswordNotice'
import styles from './AccountDialogs.module.css'

interface PasswordForm {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

const FIELDS = ['currentPassword', 'newPassword'] as const

/** "Actualizar contraseña": pide la actual y la nueva. Al cambiarla se cierran las otras sesiones. */
export function ChangePasswordDialog({ user, onClose }: { user: UserResponse; onClose: () => void }) {
  const [done, setDone] = useState(false)
  const [error, setError] = useState<ApiErrorData | null>(null)
  const [reset, setReset] = useState<{ sent: boolean; message: string } | null>(null)
  const {
    register,
    handleSubmit,
    control,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<PasswordForm>({ defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' } })
  const newPassword = useWatch({ control, name: 'newPassword' })

  async function onSubmit(values: PasswordForm) {
    setError(null)
    const request: ChangePasswordRequest = { currentPassword: values.currentPassword, newPassword: values.newPassword }
    try {
      await api<void>('PUT', '/me/password', request)
      setDone(true)
    } catch (e) {
      const data = toApiErrorData(e)
      if (!applyFieldErrors(data, setFieldError, FIELDS)) {
        setError(data)
      }
    }
  }

  // "Olvidé mi contraseña" desde adentro: el mismo mail que en el login, sin cerrar la sesión.
  async function sendResetLink() {
    try {
      await forgotPassword(user.email)
      setReset({ sent: true, message: `Te enviamos un enlace a ${user.email}.` })
    } catch (e) {
      setReset({ sent: false, message: toApiErrorData(e).message })
    }
  }

  if (!user.hasPassword) {
    return (
      <Dialog title="Actualizar contraseña" onClose={onClose}>
        <DialogBody>
          <NoPasswordNotice purpose="Para crear una contraseña" />
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cerrar
          </Button>
        </DialogFooter>
      </Dialog>
    )
  }

  if (done) {
    return (
      <Dialog title="Actualizar contraseña" onClose={onClose}>
        <DialogBody>
          <Alert tone="success">
            Cambiamos tu contraseña. Cerramos la sesión en tus otros dispositivos; esta sigue abierta.
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
      title="Actualizar contraseña"
      subtitle="Al actualizarla cerramos la sesión en tus otros dispositivos."
      onClose={onClose}
    >
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogBody>
          {error && <Alert>{error.message}</Alert>}
          <div className={styles.fields}>
            <TextField
              label="Contraseña actual"
              type="password"
              autoComplete="current-password"
              error={errors.currentPassword?.message}
              {...register('currentPassword', { required: 'Ingresá tu contraseña actual' })}
            />
            <TextField
              label="Contraseña nueva"
              type="password"
              autoComplete="new-password"
              error={errors.newPassword?.message}
              {...register('newPassword', { validate: strongPassword })}
            >
              <PasswordStrength password={newPassword} />
            </TextField>
            <TextField
              label="Confirmar contraseña nueva"
              type="password"
              autoComplete="new-password"
              error={errors.confirmPassword?.message}
              {...register('confirmPassword', {
                validate: (value, values) => value === values.newPassword || 'Las contraseñas no coinciden.',
              })}
            />
          </div>
          <div className={styles.block}>
            <PasswordRequirements password={newPassword} />
          </div>
          <div className={styles.block}>
            <button type="button" className={styles.linkButton} onClick={sendResetLink} disabled={reset?.sent}>
              ¿Olvidaste tu contraseña actual?
            </button>
          </div>
          {reset && (
            <p className={styles.hint} role="status">
              {reset.message}
            </p>
          )}
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" loading={isSubmitting}>
            Guardar contraseña
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  )
}
