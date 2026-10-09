import { expect, test } from '@playwright/test'
import {
  createPortfolio,
  createVerifiedAccount,
  deleteAccount,
  pastDate,
  registerOperation,
  signIn,
  withApi,
  type TestAccount,
} from './support'

test.describe('Operaciones y dashboard', () => {
  let account: TestAccount

  test.beforeAll(async () => {
    account = await withApi((api) => createVerifiedAccount(api, 'operaciones'))
  })

  test.afterAll(async () => {
    await withApi((api) => deleteAccount(api, account))
  })

  test('carga una compra con precio de otro día y la ve en el dashboard', async ({ page }) => {
    await signIn(page, account)
    await createPortfolio(page, 'Jubilación E2E')

    // 2 × 20.000 = 40.000 de capital invertido, con el precio que se escribió (no el de hoy).
    await registerOperation(page, { asset: 'KO', type: 'BUY', quantity: '2', price: '20000', date: pastDate(30) })

    const row = page.getByRole('row', { name: /\bKO\b/ })
    await expect(row).toContainText('$ 40.000')
  })

  test('una venta sin tenencia se rechaza dentro del diálogo', async ({ page }) => {
    await signIn(page, account)

    // La cuenta no tiene AAPL: el backend responde 400 con cuánto tiene, y el diálogo lo muestra.
    await registerOperation(page, { asset: 'AAPL', type: 'SELL', quantity: '5', price: '10000', date: pastDate(1) })

    await expect(page.getByRole('dialog').getByText(/Supera tu tenencia/)).toBeVisible()
  })
})
