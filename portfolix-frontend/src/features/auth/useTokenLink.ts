import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { toApiErrorData } from '../../api/client'

export type TokenLinkState = { status: 'pending' } | { status: 'done' } | { status: 'error'; message: string }

/**
 * Las páginas que abre un link de un mail (?token=…): al abrirse, mandan el token al backend.
 * En desarrollo React ejecuta el efecto dos veces y el pedido sale dos veces: no pasa nada, porque el
 * backend responde igual a un link que ya se usó (es idempotente desde la fase 8A justamente por esto).
 */
export function useTokenLink(action: (token: string) => Promise<void>): TokenLinkState {
  const token = useSearchParams()[0].get('token')
  const [state, setState] = useState<TokenLinkState>(
    token ? { status: 'pending' } : { status: 'error', message: 'El enlace no es válido. Abrilo de nuevo desde el correo.' },
  )

  useEffect(() => {
    if (!token) {
      return
    }
    let ignore = false // si el componente se desmontó, la respuesta ya no importa
    action(token)
      .then(() => !ignore && setState({ status: 'done' }))
      .catch((e: unknown) => !ignore && setState({ status: 'error', message: toApiErrorData(e).message }))
    return () => {
      ignore = true
    }
  }, [token, action])

  return state
}
