import { useEffect } from 'react'
import { useAppSelector } from '../../app/hooks'

/**
 * Pone el tema elegido en <html data-theme="…">: tokens.css redefine los colores con ese atributo. Sin
 * sesión (o antes de cargar las preferencias) queda el default, el claro.
 */
export function ThemeSync() {
  const theme = useAppSelector((state) => state.preferences.theme)

  useEffect(() => {
    document.documentElement.dataset.theme = theme.toLowerCase()
  }, [theme])

  return null
}
