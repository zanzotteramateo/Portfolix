import { useEffect, useRef, useState } from 'react'
import styles from './GoogleButton.module.css'

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID
const SCRIPT_URL = 'https://accounts.google.com/gsi/client'

let scriptLoading: Promise<void> | null = null

/** Carga una sola vez el script de Google Identity Services, aunque haya varios botones. */
function loadGoogleScript(): Promise<void> {
  scriptLoading ??= new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SCRIPT_URL
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      scriptLoading = null // permite reintentar al volver a montar el botón
      reject(new Error('No se pudo cargar el script de Google'))
    }
    document.head.appendChild(script)
  })
  return scriptLoading
}

/**
 * "Continuar con Google". Google no deja pedir el ID token con un botón propio: hay que usar el que dibuja
 * su script (Google Identity Services). Al elegir la cuenta, llama a onCredential con el ID token, que el
 * backend valida en POST /auth/oauth/google.
 * Sin VITE_GOOGLE_CLIENT_ID no se muestra.
 */
export function GoogleButton({ onCredential }: { onCredential: (idToken: string) => void }) {
  const container = useRef<HTMLDivElement>(null)
  const callback = useRef(onCredential)
  const [failed, setFailed] = useState(false)

  // El último onCredential, sin volver a dibujar el botón cada vez que cambia (ej.: "Recuérdame").
  useEffect(() => {
    callback.current = onCredential
  })

  useEffect(() => {
    if (!CLIENT_ID) {
      return
    }
    let cancelled = false
    loadGoogleScript()
      .then(() => {
        if (cancelled || !container.current) {
          return
        }
        window.google.accounts.id.initialize({
          client_id: CLIENT_ID,
          callback: (response) => callback.current(response.credential),
        })
        window.google.accounts.id.renderButton(container.current, {
          theme: 'filled_black',
          size: 'large',
          text: 'continue_with',
          shape: 'rectangular',
          logo_alignment: 'center',
          width: container.current.offsetWidth,
          locale: 'es',
        })
      })
      .catch(() => setFailed(true))
    return () => {
      cancelled = true
    }
  }, [])

  if (!CLIENT_ID) {
    return null
  }
  if (failed) {
    return <p className={styles.unavailable}>El inicio de sesión con Google no está disponible en este momento.</p>
  }
  return <div ref={container} className={styles.button} />
}
