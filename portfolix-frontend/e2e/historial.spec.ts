import { expect, test } from '@playwright/test'
import {
  accessTokenFor,
  API,
  createVerifiedAccount,
  deleteAccount,
  pastDate,
  signIn,
  withApi,
  type TestAccount,
} from './support'

test.describe('Historial: editar y borrar', () => {
  let account: TestAccount

  test.beforeAll(async () => {
    // Una compra cargada por la API (para no repetir el formulario): 2 KO a 20.000 el día de hace 30 días.
    account = await withApi(async (api) => {
      const created = await createVerifiedAccount(api, 'historial')
      const token = await accessTokenFor(api, created)
      const headers = { Authorization: `Bearer ${token}` }
      const portfolio = await api.post(`${API}/portfolios`, { headers, data: { name: 'Historial E2E' } })
      const { id } = (await portfolio.json()) as { id: number }
      await api.post(`${API}/transactions`, {
        headers,
        data: {
          portfolioId: id,
          assetSymbol: 'KO',
          type: 'BUY',
          quantity: '2',
          price: '20000',
          tradeDate: pastDate(30),
          notes: null,
        },
      })
      return created
    })
  })

  test.afterAll(async () => {
    await withApi((api) => deleteAccount(api, account))
  })

  test('edita la cantidad de una operación y después la borra', async ({ page }) => {
    await signIn(page, account)
    await page.goto('/transactions')

    // Editar: 3 KO a 20.000 = 60.000. La fila muestra el monto nuevo.
    await page.getByRole('button', { name: /^Editar la operación de KO/ }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('heading', { name: 'Editar transacción' })).toBeVisible()
    await dialog.getByLabel('Cantidad', { exact: true }).fill('3')
    await dialog.getByRole('button', { name: 'Guardar cambios' }).click()
    await expect(page.getByRole('dialog')).toBeHidden()
    await expect(page.getByRole('row', { name: /\bKO\b/ })).toContainText('$ 60.000')

    // Borrar: la única operación de la cuenta, así que el historial queda vacío.
    await page.getByRole('button', { name: /^Eliminar la operación de KO/ }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Eliminar transacción' }).click()
    await expect(page.getByText('Todavía no registraste operaciones')).toBeVisible()
  })
})
