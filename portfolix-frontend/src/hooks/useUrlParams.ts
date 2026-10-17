import { useSearchParams } from 'react-router'

/**
 * Los parámetros de la URL y una función para cambiarlos: los filtros del historial y del análisis viven ahí,
 * así la página se recarga o se comparte tal como está. Un valor vacío (o null) borra el parámetro.
 *
 * Los cambios parten de la URL actual del navegador y no de la del último dibujado: con dos clics seguidos,
 * antes de que la página se vuelva a dibujar, el segundo pisaría al primero.
 */
export function useUrlParams() {
  const [params, setParams] = useSearchParams()

  function update(changes: Record<string, string | null | undefined>) {
    setParams(() => {
      const next = new URLSearchParams(window.location.search)
      for (const [key, value] of Object.entries(changes)) {
        if (value) {
          next.set(key, value)
        } else {
          next.delete(key)
        }
      }
      return next
    })
  }

  return [params, update] as const
}
