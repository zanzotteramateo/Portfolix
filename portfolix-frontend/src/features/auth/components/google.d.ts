/*
 * Lo mínimo de Google Identity Services que usa GoogleButton (el script se carga de Google, no de npm,
 * así que no trae tipos).
 */
interface GoogleCredentialResponse {
  /** El ID token (un JWT firmado por Google). */
  credential: string
}

interface GoogleButtonOptions {
  theme?: 'outline' | 'filled_blue' | 'filled_black'
  size?: 'large' | 'medium' | 'small'
  text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin'
  shape?: 'rectangular' | 'pill' | 'circle' | 'square'
  logo_alignment?: 'left' | 'center'
  width?: number
  locale?: string
}

interface Window {
  google: {
    accounts: {
      id: {
        initialize(config: { client_id: string; callback: (response: GoogleCredentialResponse) => void }): void
        renderButton(parent: HTMLElement, options: GoogleButtonOptions): void
      }
    }
  }
}
