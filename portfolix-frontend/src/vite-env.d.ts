/// <reference types="vite/client" />

/** Variables de entorno del front (.env.development y, en producción, las del build). */
interface ImportMetaEnv {
  /** Client ID de Google (no es secreto). Sin él, no se muestra el botón de Google. */
  readonly VITE_GOOGLE_CLIENT_ID?: string
  /** URL base de la API en producción (ej.: https://api.<dominio>/api/v1). Sin ella, /api/v1 (el proxy de Vite). */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
