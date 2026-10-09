import { expect, request as playwrightRequest, type APIRequestContext, type Page } from '@playwright/test'

/*
 * Lo que comparten los tests: cuentas descartables (un correo único cada una, que se borra al terminar), los mails de
 * Mailpit y las acciones que no son lo que se prueba (entrar, crear un portafolio, cargar una operación).
 * La clave de estas cuentas es solo de test: viven en la base de dev y se borran al terminar.
 */

export const API = 'http://localhost:8080/api/v1'
const MAILPIT = 'http://localhost:8025/api/v1'
export const PASSWORD = 'Prueba-E2E2026!'

export interface TestAccount {
  email: string
  password: string
  name: string
}

export function newAccount(label: string): TestAccount {
  const id = Math.random().toString(36).slice(2, 10)
  return { email: `e2e-${label}-${id}@example.com`, password: PASSWORD, name: `E2E ${label}` }
}

/** Un pedido a la API fuera del navegador (para preparar datos y limpiar). Se cierra al terminar. */
export async function withApi<T>(fn: (api: APIRequestContext) => Promise<T>): Promise<T> {
  const api = await playwrightRequest.newContext()
  try {
    return await fn(api)
  } finally {
    await api.dispose()
  }
}

/** Espera el mail con ese asunto que llega a `to` (sale después del commit, en segundo plano) y devuelve su texto. */
export async function waitForMail(api: APIRequestContext, to: string, subject: string): Promise<string> {
  const deadline = Date.now() + 15_000
  while (Date.now() < deadline) {
    const search = await api.get(`${MAILPIT}/search`, { params: { query: `to:${to}` } })
    const { messages } = (await search.json()) as { messages: { ID: string; Subject: string }[] }
    const match = messages.find((message) => message.Subject.includes(subject))
    if (match) {
      const message = await api.get(`${MAILPIT}/message/${match.ID}`)
      return ((await message.json()) as { Text: string }).Text
    }
    await new Promise((resolve) => setTimeout(resolve, 300))
  }
  throw new Error(`No llegó a ${to} el mail "${subject}"`)
}

/** El token del link del mail: …/verify-email?token=… o …/reset-password?token=… */
export function tokenFrom(mailText: string): string {
  const match = /token=([A-Za-z0-9_-]+)/.exec(mailText)
  if (!match) {
    throw new Error('El mail no trae el token del link')
  }
  return match[1]
}

/** Crea una cuenta ya verificada por la API: para los tests que no prueban el registro. */
export async function createVerifiedAccount(api: APIRequestContext, label: string): Promise<TestAccount> {
  const account = newAccount(label)
  const registered = await api.post(`${API}/auth/register`, {
    data: { fullName: account.name, email: account.email, password: account.password, acceptedTerms: true },
  })
  expect(registered.status()).toBe(201)
  const token = tokenFrom(await waitForMail(api, account.email, 'Confirmá tu correo'))
  const verified = await api.post(`${API}/auth/verify-email`, { data: { token } })
  expect(verified.status()).toBe(204)
  return account
}

/** Un token de acceso para llamar a la API como la cuenta (para preparar datos). */
export async function accessTokenFor(api: APIRequestContext, account: TestAccount): Promise<string> {
  const login = await api.post(`${API}/auth/login`, {
    data: { email: account.email, password: account.password, rememberMe: false },
  })
  expect(login.ok()).toBeTruthy()
  return ((await login.json()) as { accessToken: string }).accessToken
}

/** Borra la cuenta y sus datos por la API. Si ya no existe o la clave no coincide, no hace nada. */
export async function deleteAccount(api: APIRequestContext, account: TestAccount): Promise<void> {
  const login = await api.post(`${API}/auth/login`, {
    data: { email: account.email, password: account.password, rememberMe: false },
  })
  if (!login.ok()) {
    return
  }
  const { accessToken } = (await login.json()) as { accessToken: string }
  await api.delete(`${API}/me`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { currentPassword: account.password },
  })
}

/** Una fecha de hace `days` días, en AAAA-MM-DD (siempre pasada: sirve para cargar un precio de otro día). */
export function pastDate(days: number): string {
  return new Date(Date.now() - days * 86_400_000).toISOString().slice(0, 10)
}

/** Entra por la pantalla de login, como lo haría el usuario. */
export async function signIn(page: Page, account: TestAccount): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('Correo electrónico', { exact: true }).fill(account.email)
  await page.getByLabel('Contraseña', { exact: true }).fill(account.password)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByRole('button', { name: 'Opciones de la cuenta' })).toBeVisible()
}

/** Cierra la sesión desde el menú de la cuenta. */
export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Opciones de la cuenta' }).click()
  await page.getByRole('menuitem', { name: 'Cerrar sesión' }).click()
  await expect(page.getByRole('button', { name: 'Iniciar Sesión' })).toBeVisible()
}

/** Crea un portafolio desde el menú lateral. */
export async function createPortfolio(page: Page, name: string): Promise<void> {
  await page.getByRole('button', { name: 'Crear Portafolio' }).click()
  await page.getByLabel('Nombre del portafolio nuevo').fill(name)
  await page.getByLabel('Nombre del portafolio nuevo').press('Enter')
  await expect(page.getByText(name, { exact: true }).first()).toBeVisible()
}

/** Registra una compra o una venta desde el dashboard, con el formulario de la app. */
export async function registerOperation(
  page: Page,
  operation: { asset: string; type: 'BUY' | 'SELL'; quantity: string; price: string; date: string },
): Promise<void> {
  await page.getByRole('button', { name: 'Registrar Transacción', exact: true }).click()
  const dialog = page.getByRole('dialog')
  const asset = dialog.getByRole('combobox', { name: 'Activo' })
  await asset.fill(operation.asset)
  await asset.press('Enter')
  if (operation.type === 'SELL') {
    await dialog.getByRole('button', { name: '↓ Venta' }).click()
  }
  await dialog.getByLabel('Fecha', { exact: true }).fill(operation.date)
  await dialog.getByLabel('Cantidad', { exact: true }).fill(operation.quantity)
  await dialog.getByLabel(/^Precio unitario/).fill(operation.price)
  await dialog.getByRole('button', { name: operation.type === 'BUY' ? 'Registrar compra' : 'Registrar venta' }).click()
}
