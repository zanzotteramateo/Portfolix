import { Alert } from '../../components/Alert'

/**
 * Las cuentas creadas con Google no tienen contraseña, y cambiar el mail, cambiar la contraseña o eliminar la
 * cuenta piden la actual. Por ahora solo se avisa cómo crear una (decisión de la 11D): el aviso no tiene botón,
 * porque "Olvidé mi contraseña" está en la pantalla de login, que solo se ve sin sesión.
 */
export function NoPasswordNotice({ purpose }: { purpose: string }) {
  return (
    <Alert tone="info">
      Esta cuenta entra con Google y todavía no tiene contraseña. {purpose}: cerrá sesión y usá «¿Olvidaste tu
      contraseña?» en la pantalla de inicio de sesión.
    </Alert>
  )
}
