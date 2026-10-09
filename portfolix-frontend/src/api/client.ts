import type { AuthResponse, ErrorResponse } from './types'

// En desarrollo, Vite reenvía /api al backend. En producción, el build apunta a la API de su dominio (VITE_API_URL,
// por ejemplo https://api.<dominio>/api/v1), que está en otro subdominio y por eso el backend tiene CORS con credenciales.
const BASE_URL = import.meta.env.VITE_API_URL || '/api/v1'

const NETWORK_ERROR_MESSAGE = 'No pudimos conectarnos con el servidor. Revisá tu conexión e intentá de nuevo.'

/** Los datos de un error de la API, en un objeto plano (Redux solo guarda datos serializables). */
export interface ApiErrorData {
  /** Código HTTP; 0 si ni siquiera hubo respuesta (sin conexión). */
  status: number
  message: string
  /** Mensaje por campo del formulario (ej.: { email: 'Ya existe una cuenta…' }). */
  fieldErrors: Record<string, string>
  /** Segundos a esperar, en los 429 (encabezado Retry-After). */
  retryAfterSeconds: number | null
}

/** Error de la API con el formato ErrorResponse del backend. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>
  readonly retryAfterSeconds: number | null

  constructor(data: ApiErrorData) {
    super(data.message)
    this.name = 'ApiError'
    this.status = data.status
    this.fieldErrors = data.fieldErrors
    this.retryAfterSeconds = data.retryAfterSeconds
  }

  toData(): ApiErrorData {
    return {
      status: this.status,
      message: this.message,
      fieldErrors: this.fieldErrors,
      retryAfterSeconds: this.retryAfterSeconds,
    }
  }
}

/** Convierte cualquier error en ApiErrorData (para los rejectWithValue de los thunks). */
export function toApiErrorData(error: unknown): ApiErrorData {
  if (error instanceof ApiError) {
    return error.toData()
  }
  return { status: 0, message: NETWORK_ERROR_MESSAGE, fieldErrors: {}, retryAfterSeconds: null }
}

// ---- Access token ----
// Vive solo en memoria, nunca en localStorage: un script inyectado en la página podría leerlo de ahí.
// Al recargar la página se pierde, y se pide uno nuevo con la cookie del refresh token (refreshAccessToken).

let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

/** Lo que hace la app cuando la sesión venció del todo (no se pudo renovar). Lo configura main.tsx. */
let onSessionExpired: () => void = () => {}

export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler
}

// ---- Renovación del access token ----

let pendingRefresh: Promise<boolean> | null = null

/**
 * Pide un access token nuevo con la cookie del refresh token. Devuelve false si no hay sesión.
 *
 * El backend rota el refresh token en cada uso y, si recibe uno ya usado, lo toma como robado y cierra
 * TODAS las sesiones del usuario. Por eso dos renovaciones nunca pueden salir juntas:
 * - en esta pestaña, si llegan varios 401 a la vez, todos esperan la misma renovación (pendingRefresh);
 * - entre pestañas, Web Locks las pone en fila: la segunda sale cuando la primera ya guardó la cookie nueva.
 */
export function refreshAccessToken(): Promise<boolean> {
  pendingRefresh ??= lockAcrossTabs(requestNewAccessToken).finally(() => {
    pendingRefresh = null
  })
  return pendingRefresh
}

async function requestNewAccessToken(): Promise<boolean> {
  try {
    const response = await fetch(`${BASE_URL}/auth/refresh`, { method: 'POST', credentials: 'include' })
    if (!response.ok) {
      accessToken = null
      return false
    }
    accessToken = ((await response.json()) as AuthResponse).accessToken
    return true
  } catch {
    return false
  }
}

function lockAcrossTabs(task: () => Promise<boolean>): Promise<boolean> {
  return navigator.locks ? navigator.locks.request('portfolix-refresh', task) : task()
}

// ---- Pedidos ----

/**
 * Hace un pedido a la API y devuelve el cuerpo de la respuesta (o undefined si no tiene).
 * Si el access token venció (401), lo renueva una vez y reintenta. Si no se puede renovar, avisa que la
 * sesión venció. Cualquier respuesta de error termina en un ApiError.
 */
export async function api<T>(method: string, path: string, body?: unknown): Promise<T> {
  const response = await requestWithRenewal(method, path, body)
  const isJson = response.headers.get('Content-Type')?.includes('application/json')
  return (isJson ? await response.json() : undefined) as T
}

/** Como api(), pero para un archivo (el CSV del historial): devuelve el contenido y el nombre que manda el backend. */
export async function download(path: string): Promise<{ blob: Blob; filename: string }> {
  const response = await requestWithRenewal('GET', path)
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const filename = /filename="([^"]+)"/.exec(disposition)?.[1] ?? 'portfolix.csv'
  return { blob: await response.blob(), filename }
}

async function requestWithRenewal(method: string, path: string, body?: unknown): Promise<Response> {
  let response = await send(method, path, body)

  // Los endpoints de /auth responden 401 por credenciales incorrectas, no por un token vencido.
  const canRenew = response.status === 401 && !path.startsWith('/auth/')
  if (canRenew) {
    if (await refreshAccessToken()) {
      response = await send(method, path, body)
    } else {
      onSessionExpired()
    }
  }

  if (!response.ok) {
    throw await toApiError(response)
  }
  return response
}

async function send(method: string, path: string, body: unknown): Promise<Response> {
  const headers: Record<string, string> = {}
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }
  try {
    return await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'include', // manda la cookie del refresh token a /auth (login, refresh, logout)
    })
  } catch {
    // fetch solo falla así sin respuesta: sin conexión, o el backend apagado.
    throw new ApiError({ status: 0, message: NETWORK_ERROR_MESSAGE, fieldErrors: {}, retryAfterSeconds: null })
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: Partial<ErrorResponse> = {}
  try {
    body = (await response.json()) as ErrorResponse
  } catch {
    // Sin JSON: no respondió el backend sino algo en el medio (ej.: el proxy de Vite con el backend apagado).
    if (response.status >= 500) {
      body = { message: NETWORK_ERROR_MESSAGE }
    }
  }
  const fieldErrors: Record<string, string> = {}
  for (const error of body.fieldErrors ?? []) {
    fieldErrors[error.field] ??= error.message // si un campo tiene varios errores, el primero
  }
  const retryAfter = Number(response.headers.get('Retry-After'))
  return new ApiError({
    status: response.status,
    message: body.message ?? 'Ocurrió un error inesperado. Intentá de nuevo más tarde',
    fieldErrors,
    retryAfterSeconds: retryAfter > 0 ? retryAfter : null,
  })
}
