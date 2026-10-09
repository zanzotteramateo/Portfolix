import { download } from '../../api/client'

/**
 * Baja el CSV de todo el historial (el endpoint no filtra) con el nombre que manda el backend.
 * Lo usan el historial y el diálogo de eliminar la cuenta (que ofrece el respaldo antes de borrarla).
 */
export async function downloadTransactionsCsv(): Promise<void> {
  const { blob, filename } = await download('/me/export/transactions.csv')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000) // después del clic: el navegador ya empezó a bajarlo
}
