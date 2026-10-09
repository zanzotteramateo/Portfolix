import { expect, test } from '@playwright/test'
import { deleteAccount, newAccount, PASSWORD, signIn, signOut, tokenFrom, waitForMail, withApi, type TestAccount } from './support'

test.describe('Registro, verificación y login', () => {
  let account: TestAccount

  test.beforeAll(() => {
    account = newAccount('registro')
  })

  test.afterAll(async () => {
    await withApi((api) => deleteAccount(api, account))
  })

  test('crea la cuenta, la verifica por mail, entra y cierra sesión', async ({ page, request }) => {
    await page.goto('/register')
    await page.getByLabel('Nombre completo', { exact: true }).fill(account.name)
    await page.getByLabel('Correo electrónico', { exact: true }).fill(account.email)
    await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD)
    await page.getByLabel('Confirmar contraseña', { exact: true }).fill(PASSWORD)
    await page.getByRole('checkbox').check()
    await page.getByRole('button', { name: 'Crear Cuenta' }).click()

    await expect(page.getByRole('heading', { name: 'Revisá tu correo' })).toBeVisible()

    // El mail sale en segundo plano: se espera al link y se entra por el mismo camino que el usuario.
    const token = tokenFrom(await waitForMail(request, account.email, 'Confirmá tu correo'))
    await page.goto(`/verify-email?token=${token}`)
    await expect(page.getByRole('heading', { name: '¡Correo confirmado!' })).toBeVisible()

    await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
    await signIn(page, account)
    await expect(page.getByRole('heading', { name: 'Tu cartera está vacía' })).toBeVisible()

    await signOut(page)
  })
})
