import { useState } from 'react'
import { useAppDispatch, useAppSelector } from '../../app/hooks'
import { Menu, type MenuItem } from '../../components/Menu'
import { KeyIcon, LogOutIcon, MailIcon, SettingsIcon, TrashIcon } from '../../components/icons'
import { logout } from '../auth/authSlice'
import { AppSettingsDialog } from './AppSettingsDialog'
import { ChangeEmailDialog } from './ChangeEmailDialog'
import { ChangePasswordDialog } from './ChangePasswordDialog'
import { DeleteAccountDialog } from './DeleteAccountDialog'
import styles from './AccountControls.module.css'

type OpenDialog = 'email' | 'password' | 'delete' | 'settings' | null

/** Iniciales para el avatar: "Juan Pérez" → "JP". */
function initials(fullName: string): string {
  const words = fullName.trim().split(/\s+/)
  return ((words[0]?.[0] ?? '') + (words.length > 1 ? (words.at(-1)?.[0] ?? '') : '')).toUpperCase()
}

/**
 * El pie del menú lateral: el perfil (abre el menú de la cuenta: correo, contraseña, cerrar sesión y eliminar)
 * y el engranaje (ajustes de la aplicación). Los diálogos viven acá, así el menú lateral no sabe de ellos.
 */
export function AccountControls() {
  const dispatch = useAppDispatch()
  const user = useAppSelector((state) => state.auth.user)
  const [open, setOpen] = useState<OpenDialog>(null)

  if (!user) {
    return null
  }

  const items: MenuItem[] = [
    { label: 'Cambiar correo electrónico', icon: <MailIcon size={14} />, onSelect: () => setOpen('email') },
    { label: 'Actualizar contraseña', icon: <KeyIcon size={14} />, onSelect: () => setOpen('password') },
    { label: 'Cerrar sesión', icon: <LogOutIcon size={14} />, onSelect: () => dispatch(logout()) },
    { label: 'Eliminar cuenta', icon: <TrashIcon size={14} />, onSelect: () => setOpen('delete'), danger: true },
  ]
  const close = () => setOpen(null)

  return (
    <>
      <div className={styles.user}>
        <Menu
          label="Opciones de la cuenta"
          className={styles.profile}
          items={items}
          trigger={
            <>
              <span className={styles.avatar} aria-hidden="true">
                {initials(user.fullName)}
              </span>
              <span className={styles.userText}>
                <span className={styles.userName}>{user.fullName}</span>
                <span className={styles.userEmail}>{user.email}</span>
              </span>
            </>
          }
        />
        <button
          type="button"
          className={styles.iconButton}
          onClick={() => setOpen('settings')}
          aria-label="Ajustes de la aplicación"
          title="Ajustes de la aplicación"
        >
          <SettingsIcon />
        </button>
      </div>

      {open === 'email' && <ChangeEmailDialog user={user} onClose={close} />}
      {open === 'password' && <ChangePasswordDialog user={user} onClose={close} />}
      {open === 'delete' && <DeleteAccountDialog user={user} onClose={close} />}
      {open === 'settings' && <AppSettingsDialog onClose={close} />}
    </>
  )
}
