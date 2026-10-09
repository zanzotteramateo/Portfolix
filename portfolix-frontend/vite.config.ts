import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    // El navegador le habla a un solo origen (localhost:5173) y Vite le pasa /api al backend.
    // Así la cookie del refresh token (SameSite=Strict, Path=/api/v1/auth) viaja sin problemas
    // y no hace falta CORS.
    proxy: {
      '/api': 'http://localhost:8080',
    },
    // El botón de Google no anda en localhost sin este encabezado (ver la fase 8C del backend).
    headers: {
      'Referrer-Policy': 'no-referrer-when-downgrade',
    },
  },
})
