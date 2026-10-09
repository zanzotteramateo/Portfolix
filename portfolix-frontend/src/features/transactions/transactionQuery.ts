import type { Currency } from '../../api/types'

/** Operaciones por página (el backend acepta hasta 100). */
export const PAGE_SIZE = 20

/**
 * Los filtros del historial. Viven en la URL con los mismos nombres que la API (?portfolioId=…&type=SELL…),
 * así la página se puede recargar o compartir tal como está. '' = sin filtrar.
 */
export interface HistoryFilters {
  portfolioId: string
  assetSymbol: string
  type: '' | 'BUY' | 'SELL'
  /** AAAA-MM-DD */
  from: string
  to: string
}

export function readFilters(params: URLSearchParams): HistoryFilters {
  const type = params.get('type')
  return {
    portfolioId: params.get('portfolioId') ?? '',
    assetSymbol: params.get('assetSymbol') ?? '',
    type: type === 'BUY' || type === 'SELL' ? type : '',
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
  }
}

/** La página en la URL (?page=2 es la tercera); 0 si falta o no es válida. */
export function readPage(params: URLSearchParams): number {
  const page = Number(params.get('page'))
  return Number.isInteger(page) && page > 0 ? page : 0
}

/** El pedido a GET /transactions para estos filtros, la página y la moneda del resumen. */
export function transactionsPath(filters: HistoryFilters, page: number, currency: Currency): string {
  const query = new URLSearchParams({ currency, page: String(page), size: String(PAGE_SIZE) })
  for (const [key, value] of Object.entries(filters)) {
    if (value) {
      query.set(key, value)
    }
  }
  return `/transactions?${query}`
}

export function hasFilters(filters: HistoryFilters): boolean {
  return Object.values(filters).some(Boolean)
}
