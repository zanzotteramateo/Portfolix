import { expect, test } from '@playwright/test'
import { createVerifiedAccount, deleteAccount, signIn, signOut, withApi, type TestAccount } from './support'

test.describe('Cuenta: contraseña y eliminación', () => {
  let account: TestAccount

  test.beforeAll(async () => {
    account = await withApi((api) => createVerifiedAccount(api, 'cuenta'))
  })

  test.afterAll(async () => {
    // Si el test llegó a cambiar la clave, `account.password` ya tiene la nueva (deleteAccount la usa).
    await withApi((api) => deleteAccount(api, account))
  })

  test('cambia la contraseña, entra con la nueva y después elimina la cuenta', async ({ page }) => {
    await signIn(page, account)

    // Cambiar la contraseña desde el menú de la cuenta.
    const nuevaClave = 'Otra-E2E2026!'
    await page.getByRole('button', { name: 'Opciones de la cuenta' }).click()
    await page.getByRole('menuitem', { name: 'Actualizar contraseña' }).click()
    const passwordDialog = page.getByRole('dialog')
    await passwordDialog.getByLabel('Contraseña actual', { exact: true }).fill(account.password)
    await passwordDialog.getByLabel('Contraseña nueva', { exact: true }).fill(nuevaClave)
    await passwordDialog.getByLabel('Confirmar contraseña nueva', { exact: true }).fill(nuevaClave)
    await passwordDialog.getByRole('button', { name: 'Guardar contraseña' }).click()
    await expect(passwordDialog.getByText('Cambiamos tu contraseña')).toBeVisible()
    await passwordDialog.getByRole('button', { name: 'Listo' }).click()
    account.password = nuevaClave

    // Cerrar sesión y entrar con la clave nueva: es la prueba de que el cambio quedó guardado.
    await signOut(page)
    await signIn(page, account)

    // Eliminar la cuenta: escribir ELIMINAR y la clave. Al terminar vuelve al login.
    await page.getByRole('button', { name: 'Opciones de la cuenta' }).click()
    await page.getByRole('menuitem', { name: 'Eliminar cuenta' }).click()
    const deleteDialog = page.getByRole('dialog')
    await deleteDialog.getByLabel('Escribí ELIMINAR para confirmar').fill('ELIMINAR')
    await deleteDialog.getByLabel('Contraseña actual', { exact: true }).fill(account.password)
    await deleteDialog.getByRole('button', { name: 'Eliminar mi cuenta' }).click()

    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('button', { name: 'Iniciar Sesión' })).toBeVisible()
  })
})
