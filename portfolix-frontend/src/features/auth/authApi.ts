import { api, setAccessToken } from '../../api/client'
import type {
  AuthResponse,
  ChangePendingEmailRequest,
  GoogleLoginRequest,
  LoginRequest,
  RegisterRequest,
  ResetPasswordRequest,
  UserResponse,
} from '../../api/types'

/*
 * Pedidos de auth que no son estado compartido: los usa una sola pantalla, que guarda el resultado en su
 * propio estado. Lo que sí cambia la sesión (quién está logueado) termina en el authSlice.
 *
 * Login y Google son funciones y no thunks a propósito: el argumento de un thunk queda registrado en sus
 * acciones de Redux (y se ve en Redux DevTools), y acá ese argumento es la contraseña o el token de Google.
 */

/** Inicia sesión: guarda el access token en memoria (la cookie la guarda el navegador) y trae el usuario. */
export async function login(request: LoginRequest): Promise<UserResponse> {
  const { accessToken } = await api<AuthResponse>('POST', '/auth/login', request)
  setAccessToken(accessToken)
  return api<UserResponse>('GET', '/me')
}

export async function loginWithGoogle(request: GoogleLoginRequest): Promise<UserResponse> {
  const { accessToken } = await api<AuthResponse>('POST', '/auth/oauth/google', request)
  setAccessToken(accessToken)
  return api<UserResponse>('GET', '/me')
}

/** Crea la cuenta sin verificar y manda el mail de verificación. No inicia sesión. */
export function register(request: RegisterRequest): Promise<UserResponse> {
  return api<UserResponse>('POST', '/auth/register', request)
}

export function verifyEmail(token: string): Promise<void> {
  return api<void>('POST', '/auth/verify-email', { token })
}

/** Siempre responde bien (202), haya o no cuenta con ese mail: no revela qué mails están registrados. */
export function resendVerification(email: string): Promise<void> {
  return api<void>('POST', '/auth/verify-email/resend', { email })
}

/** Cambia el mail de una cuenta que todavía no se verificó, y manda el link al mail nuevo. */
export function changePendingEmail(request: ChangePendingEmailRequest): Promise<void> {
  return api<void>('POST', '/auth/verify-email/change-email', request)
}

/** Siempre responde bien (202), como el reenvío. */
export function forgotPassword(email: string): Promise<void> {
  return api<void>('POST', '/auth/password/forgot', { email })
}

export function resetPassword(request: ResetPasswordRequest): Promise<void> {
  return api<void>('POST', '/auth/password/reset', request)
}

export function confirmEmailChange(token: string): Promise<void> {
  return api<void>('POST', '/auth/email-change/confirm', { token })
}
