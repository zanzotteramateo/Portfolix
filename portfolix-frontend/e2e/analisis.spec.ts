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

test.describe('Análisis', () => {
  let account: TestAccount

  test.beforeAll(async () => {
    // Una compra cargada por la API: 2 KO a 20.000 el día de hace 30 días.
    account = await withApi(async (api) => {
      const created = await createVerifiedAccount(api, 'analisis')
      const token = await accessTokenFor(api, created)
      const headers = { Authorization: `Bearer ${token}` }
      const portfolio = await api.post(`${API}/portfolios`, { headers, data: { name: 'Analisis E2E' } })
      const { id } = (await portfolio.json()) as { id: number }
      const bought = await api.post(`${API}/transactions`, {
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
      expect(bought.status()).toBe(201)
      return created
    })
  })

  test.afterAll(async () => {
    await withApi((api) => deleteAccount(api, account))
  })

  test('"Desde el inicio" da la ganancia total del dashboard, y los filtros van en la URL', async ({ page }) => {
    await signIn(page, account)
    const totalGain = page.locator('dt', { hasText: 'Ganancia total' }).locator('xpath=following-sibling::dd')
    const periodResult = page.getByRole('region', { name: 'Resultado del período' })

    // Con precios reales, el precio de KO puede cambiar entre una pantalla y la otra (el backend lo refresca cada
    // 5 minutos): si justo pasa, se repite la comparación.
    await expect(async () => {
      await page.goto('/')
      const dashboardGain = (await totalGain.textContent())?.trim()
      expect(dashboardGain).toMatch(/\$/)

      await page.getByRole('link', { name: 'Análisis' }).click()
      await page.getByRole('button', { name: 'Desde el inicio' }).click()
      await expect(page).toHaveURL(/period=all/)
      await expect(periodResult).toContainText(dashboardGain!)
    }).toPass({ timeout: 60_000 })

    // KO es un CEDEAR: con el filtro de cripto no queda nada.
    await page.getByRole('button', { name: 'Cryptos' }).click()
    await expect(page).toHaveURL(/type=CRYPTO/)
    await expect(page.getByText('No tuviste inversiones en este período con estos filtros.')).toBeVisible()

    // Fechas a mano: "desde" posterior a "hasta" → el error del backend debajo de "Desde".
    await page.getByRole('button', { name: 'Todos', exact: true }).click()
    await page.getByRole('button', { name: 'Personalizado' }).click()
    await page.getByLabel('Hasta', { exact: true }).fill(pastDate(60))
    await expect(page.getByText('La fecha de inicio no puede ser posterior a la de fin')).toBeVisible()
    await expect(page.getByLabel('Desde', { exact: true })).toHaveAttribute('aria-invalid', 'true')
  })
})
