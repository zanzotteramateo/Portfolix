import { useEffect, useState } from 'react'
import { api, toApiErrorData, type ApiErrorData } from '../api/client'

interface Result<T> {
  path: string | null
  data: T | null
  error: ApiErrorData | null
}

/**
 * GET con fetch + useEffect para datos que usa una sola pantalla (decisión de la fase 11: lo compartido va en
 * Redux; esto, no). path null = todavía no hay qué pedir.
 * Se guarda para qué path es cada resultado: si el path cambia, lo viejo deja de mostrarse al instante y una
 * respuesta que llega tarde (de un path anterior) se ignora.
 */
export function useApiResource<T>(path: string | null) {
  const [result, setResult] = useState<Result<T>>({ path: null, data: null, error: null })
  // Cambiarlo vuelve a pedir el mismo path (ej.: después de editar una fila) sin borrar lo que ya se ve.
  const [version, setVersion] = useState(0)

  useEffect(() => {
    if (path === null) {
      return
    }
    let ignore = false
    api<T>('GET', path).then(
      (data) => !ignore && setResult({ path, data, error: null }),
      (e: unknown) => !ignore && setResult({ path, data: null, error: toApiErrorData(e) }),
    )
    return () => {
      ignore = true
    }
  }, [path, version])

  const current = result.path === path
  return {
    data: current ? result.data : null,
    error: current ? result.error : null,
    loading: path !== null && !current,
    reload: () => setVersion((v) => v + 1),
  }
}
