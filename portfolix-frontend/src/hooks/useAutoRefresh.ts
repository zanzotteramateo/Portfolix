import { useEffect, useRef } from 'react'

/**
 * Llama a refresh cada intervalMs, solo mientras la pestaña está visible: una pestaña en segundo plano no
 * le pide nada al backend, y al volver a ella se actualiza enseguida si ya pasó el intervalo.
 */
export function useAutoRefresh(refresh: () => void, intervalMs: number, enabled = true) {
  // La última versión de refresh, sin reiniciar el intervalo cada vez que el componente se dibuja.
  const latest = useRef(refresh)
  useEffect(() => {
    latest.current = refresh
  })

  useEffect(() => {
    if (!enabled) {
      return
    }
    let last = Date.now()
    const tick = () => {
      if (document.visibilityState === 'visible' && Date.now() - last >= intervalMs) {
        last = Date.now()
        latest.current()
      }
    }
    const timer = window.setInterval(tick, 30_000)
    document.addEventListener('visibilitychange', tick)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', tick)
    }
  }, [intervalMs, enabled])
}
