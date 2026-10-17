import type { AssetType, Currency } from '../../api/types'

/** Los períodos del selector. 'year' usa `year`; 'custom', `from` y `to`. */
export type Period = '1m' | '3m' | '6m' | 'ytd' | '1y' | 'all' | 'year' | 'custom'

/** Lo que se ve al entrar (decisión de la 14B): los últimos 12 meses. */
export const DEFAULT_PERIOD: Period = '1y'

export const PRESETS: { value: Period; label: string }[] = [
  { value: '1m', label: '1M' },
  { value: '3m', label: '3M' },
  { value: '6m', label: '6M' },
  { value: 'ytd', label: 'En el año' },
  { value: '1y', label: '1 año' },
  { value: 'all', label: 'Desde el inicio' },
]

/** Primer año del selector: desde ahí el backend tiene cierres de acciones y CEDEARs. Antes, "Personalizado". */
const FIRST_YEAR = 2020

/**
 * Los filtros del análisis. Viven en la URL (?period=year&year=2025&portfolioId=…&type=CRYPTO), así la página se
 * puede recargar o compartir tal como está. '' = sin filtrar.
 */
export interface AnalysisFilters {
  period: Period
  year: string
  /** AAAA-MM-DD, solo con period=custom. */
  from: string
  to: string
  portfolioId: string
  type: '' | AssetType
}

const PERIODS: Period[] = ['1m', '3m', '6m', 'ytd', '1y', 'all', 'year', 'custom']
const TYPES: AssetType[] = ['STOCK', 'CEDEAR', 'CRYPTO']

export function readAnalysisFilters(params: URLSearchParams, today: string): AnalysisFilters {
  const period = params.get('period') as Period | null
  const type = params.get('type') as AssetType | null
  return {
    period: period && PERIODS.includes(period) ? period : DEFAULT_PERIOD,
    year: params.get('year') ?? today.slice(0, 4),
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
    portfolioId: params.get('portfolioId') ?? '',
    type: type && TYPES.includes(type) ? type : '',
  }
}

/** Los años del selector, del actual para atrás. */
export function selectableYears(today: string): string[] {
  const years: string[] = []
  for (let year = Number(today.slice(0, 4)); year >= FIRST_YEAR; year--) {
    years.push(String(year))
  }
  return years
}

/**
 * Las fechas que se le piden al backend. "1 mes" es desde el día siguiente al de hace un mes: el backend toma como
 * valor inicial el cierre del día anterior a `from`, así que se compara contra el valor de exactamente un mes
 * atrás. Sin `from`, el backend arranca en la primera operación; sin `to`, termina hoy.
 */
export function periodRange(filters: AnalysisFilters, today: string): { from?: string; to?: string } {
  switch (filters.period) {
    case '1m':
      return { from: addDays(addMonths(today, -1), 1) }
    case '3m':
      return { from: addDays(addMonths(today, -3), 1) }
    case '6m':
      return { from: addDays(addMonths(today, -6), 1) }
    case '1y':
      return { from: addDays(addMonths(today, -12), 1) }
    case 'ytd':
      return { from: `${today.slice(0, 4)}-01-01` }
    case 'all':
      return {}
    case 'year': {
      const end = `${filters.year}-12-31`
      return { from: `${filters.year}-01-01`, to: end < today ? end : today }
    }
    case 'custom':
      return { from: filters.from || undefined, to: filters.to || undefined }
  }
}

/** El pedido a GET /performance para estos filtros y la moneda elegida. */
export function performancePath(filters: AnalysisFilters, currency: Currency, today: string): string {
  const query = new URLSearchParams({ currency })
  const { from, to } = periodRange(filters, today)
  if (from) query.set('from', from)
  if (to) query.set('to', to)
  if (filters.portfolioId) query.set('portfolioId', filters.portfolioId)
  if (filters.type) query.set('type', filters.type)
  return `/performance?${query}`
}

/** Fechas AAAA-MM-DD como días UTC: así sumar meses o días no depende de la zona horaria del navegador. */
function toDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day))
}

function toIso(date: Date): string {
  return date.toISOString().slice(0, 10)
}

export function addDays(iso: string, days: number): string {
  const date = toDate(iso)
  date.setUTCDate(date.getUTCDate() + days)
  return toIso(date)
}

/** Si el día no existe en el mes de destino, el último del mes: 31/03 menos un mes es 28/02 (o 29/02). */
export function addMonths(iso: string, months: number): string {
  const date = toDate(iso)
  const day = date.getUTCDate()
  date.setUTCDate(1)
  date.setUTCMonth(date.getUTCMonth() + months)
  const lastDay = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0)).getUTCDate()
  date.setUTCDate(Math.min(day, lastDay))
  return toIso(date)
}
