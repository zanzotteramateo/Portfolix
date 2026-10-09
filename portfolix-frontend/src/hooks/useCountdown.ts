import { useCallback, useEffect, useState } from 'react'

/**
 * Cuenta regresiva en segundos (ej.: "Reenviar correo en 0:45", o el bloqueo de un 429).
 * Se calcula contra la hora de fin, no restando de a uno: si la pestaña queda en segundo plano y el
 * navegador frena los intervalos, al volver muestra el tiempo real.
 */
export function useCountdown() {
  const [endsAt, setEndsAt] = useState<number | null>(null)
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (endsAt === null) {
      return
    }
    const timer = window.setInterval(() => setNow(Date.now()), 250)
    return () => window.clearInterval(timer)
  }, [endsAt])

  const start = useCallback((seconds: number) => {
    setNow(Date.now())
    setEndsAt(Date.now() + seconds * 1000)
  }, [])

  const remaining = endsAt === null ? 0 : Math.max(0, Math.ceil((endsAt - now) / 1000))
  return { remaining, running: remaining > 0, start }
}

/** 45 → "0:45"; 125 → "2:05". */
export function formatCountdown(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`
}
