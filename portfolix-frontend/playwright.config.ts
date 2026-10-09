import { defineConfig, devices } from '@playwright/test'

/*
 * Tests end-to-end (fase 11E): el front de verdad contra el backend de verdad y Mailpit. Hace falta Docker
 * corriendo (Postgres y Mailpit) y JAVA_HOME con JDK 25 (como para ./mvnw). Playwright levanta el backend y el
 * front; si ya están corriendo, los reutiliza (salvo en CI). Ojo: si reutiliza un backend con el rate limit
 * prendido, los tests fallan con 429; levantarlo con PORTFOLIX_RATELIMIT_ENABLED=false.
 */

// En cmd el directorio actual no está en el PATH: el wrapper de Maven se llama con ".\" en Windows.
const backendCommand = process.platform === 'win32' ? '.\\mvnw.cmd spring-boot:run' : './mvnw spring-boot:run'

export default defineConfig({
  testDir: './e2e',
  globalTeardown: './e2e/global-teardown.ts',
  // Un solo worker: el bloqueo por intentos de login y Mailpit se comparten entre todos los tests.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: backendCommand,
      cwd: '../portfolix-backend',
      url: 'http://localhost:8080/actuator/health',
      env: { PORTFOLIX_RATELIMIT_ENABLED: 'false' },
      reuseExistingServer: !process.env.CI,
      timeout: 300_000,
    },
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: !process.env.CI,
    },
  ],
})
