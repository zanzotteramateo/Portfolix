import type { AssetType, Currency, Decimal, DecimalSeparator } from '../api/types'

/*
 * Formato de números según la preferencia del usuario (separador decimal):
 * - COMMA: "1.234,56" (el uso en Argentina)
 * - PERIOD: "1,234.56"
 * Los valores llegan del backend como string ("1234.56") y se pasan a número solo para mostrarlos.
 */

const LOCALES: Record<DecimalSeparator, string> = { COMMA: 'es-AR', PERIOD: 'en-US' }

/** Como en el diseño: "$ 5.947.000" y "u$s 4.757,60". */
export const CURRENCY_SYMBOLS: Record<Currency, string> = { ARS: '$', USD: 'u$s' }

/** Lo que se ve en lugar de un monto con "ocultar montos" prendido. */
export const HIDDEN = '••••••'

const formatters = new Map<string, Intl.NumberFormat>()

/** Los Intl.NumberFormat son caros de crear: se reusan (la tabla formatea cientos de números). */
function formatter(separator: DecimalSeparator, options: Intl.NumberFormatOptions): Intl.NumberFormat {
  const key = separator + JSON.stringify(options)
  let format = formatters.get(key)
  if (!format) {
    // useGrouping 'always': en español algunos navegadores no separan los miles de los números de 4 cifras.
    format = new Intl.NumberFormat(LOCALES[separator], { useGrouping: 'always', ...options })
    formatters.set(key, format)
  }
  return format
}

export function toNumber(value: Decimal | number): number {
  return typeof value === 'number' ? value : Number(value)
}

export interface FormatOptions {
  /** Muestra "+" en los positivos (ganancias, variaciones). */
  sign?: boolean
}

/** Desde cuánto un monto se muestra sin centavos: en los montos grandes, los centavos son ruido. */
const WITHOUT_CENTS_FROM: Record<Currency, number> = { ARS: 1_000, USD: 10_000 }

/**
 * Montos: pesos sin centavos desde $ 1.000 y dólares desde u$s 10.000 ("$ 5.947.000", "u$s 4.757,60",
 * "u$s 68.200"). El signo va antes del símbolo: "+$ 291.600", "-$ 17.980".
 */
export function formatMoney(value: Decimal | number, currency: Currency, separator: DecimalSeparator,
                            { sign = false }: FormatOptions = {}): string {
  const amount = toNumber(value)
  const decimals = amount === 0 || Math.abs(amount) >= WITHOUT_CENTS_FROM[currency] ? 0 : 2
  const digits = formatter(separator, { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
    .format(Math.abs(amount))
  const prefix = amount < 0 ? '-' : sign && amount > 0 ? '+' : ''
  return `${prefix}${CURRENCY_SYMBOLS[currency]} ${digits}`
}

/** Precios unitarios: como los montos, pero los menores a 1 conservan hasta 6 decimales (ej.: ADA a u$s 0,3812). */
export function formatPrice(value: Decimal | number, currency: Currency, separator: DecimalSeparator): string {
  const price = toNumber(value)
  if (Math.abs(price) >= 1 || price === 0) {
    return formatMoney(price, currency, separator)
  }
  const digits = formatter(separator, { minimumFractionDigits: 2, maximumFractionDigits: 6 }).format(price)
  return `${CURRENCY_SYMBOLS[currency]} ${digits}`
}

/** Porcentajes con un decimal, como en el diseño: "+12,5%", "-3,2%", "31,7%". */
export function formatPercent(value: Decimal | number, separator: DecimalSeparator,
                              { sign = false }: FormatOptions = {}): string {
  return formatter(separator, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
    signDisplay: sign ? 'exceptZero' : 'auto',
  }).format(toNumber(value)) + '%'
}

/** Cantidades: cripto con 4 a 8 decimales ("0,3421", "18,6000"); acciones y CEDEARs, sin ceros de más ("82"). */
export function formatQuantity(value: Decimal | number, type: AssetType, separator: DecimalSeparator): string {
  const options = type === 'CRYPTO'
    ? { minimumFractionDigits: 4, maximumFractionDigits: 8 }
    : { minimumFractionDigits: 0, maximumFractionDigits: 8 }
  return formatter(separator, options).format(toNumber(value))
}

/** Abreviado para el centro de la dona: "5,9M", "820K", "950". */
export function formatCompact(value: Decimal | number, separator: DecimalSeparator): string {
  const amount = toNumber(value)
  const one = formatter(separator, { maximumFractionDigits: 1 })
  if (Math.abs(amount) >= 1e9) return `${one.format(amount / 1e9)}B`
  if (Math.abs(amount) >= 1e6) return `${one.format(amount / 1e6)}M`
  if (Math.abs(amount) >= 1e3) return `${one.format(amount / 1e3)}K`
  return formatter(separator, { maximumFractionDigits: 0 }).format(amount)
}

/** plural(1, 'activo', 'activos') → "1 activo"; plural(9, …) → "9 activos". */
export function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`
}

/** Un número con dos decimales fijos, para la vista previa de los ajustes: "1.234.567,89". */
export function formatWithCents(value: number, separator: DecimalSeparator): string {
  return formatter(separator, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
}

/** "2026-09-25" → "25/09/2026" (las fechas de operación son días, sin hora ni zona). */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

/** Hace cuánto: "hace un momento", "hace 3 min", "hace 2 h". */
export function formatAgo(isoInstant: string, now = Date.now()): string {
  const minutes = Math.floor((now - new Date(isoInstant).getTime()) / 60000)
  if (minutes < 1) return 'hace un momento'
  if (minutes < 60) return `hace ${minutes} min`
  return `hace ${Math.floor(minutes / 60)} h`
}

/** Signo de un valor, para elegir el color: verde ganancia, rojo pérdida. */
export function tone(value: Decimal | number | null): 'gain' | 'loss' | 'neutral' {
  const n = value === null ? 0 : toNumber(value)
  return n > 0 ? 'gain' : n < 0 ? 'loss' : 'neutral'
}

/**
 * Lo que el usuario escribe en un campo de número ("1.234,5", "0,05", "0.05") pasado al formato de la API
 * ("1234.5"); null si no es un número válido. Acepta las dos costumbres sin confundirlas:
 * - con los dos signos, el último es el decimal ("1.234,5" o "1,234.5");
 * - con uno solo, el separador decimal del usuario es decimal, y el otro solo cuenta como separador de miles
 *   si forma grupos de 3 cifras ("1.234" = 1234 con coma; "0.05" = 0,05 igual).
 */
export function parseDecimalInput(text: string, separator: DecimalSeparator): string | null {
  const value = text.trim().replace(/\s/g, '')
  if (!/^[\d.,]+$/.test(value)) {
    return null
  }
  const decimalMark = separator === 'COMMA' ? ',' : '.'
  const groupMark = separator === 'COMMA' ? '.' : ','
  let normalized: string
  if (value.includes(',') && value.includes('.')) {
    const last = Math.max(value.lastIndexOf(','), value.lastIndexOf('.'))
    normalized = value.slice(0, last).replace(/[.,]/g, '') + '.' + value.slice(last + 1)
  } else if (value.includes(decimalMark)) {
    normalized = value.replace(decimalMark, '.')
  } else if (value.includes(groupMark)) {
    const isGrouping = (groupMark === '.' ? /^\d{1,3}(\.\d{3})+$/ : /^\d{1,3}(,\d{3})+$/).test(value)
    normalized = isGrouping ? value.split(groupMark).join('') : value.replace(groupMark, '.')
  } else {
    normalized = value
  }
  return /^\d+(\.\d+)?$/.test(normalized) ? normalized : null
}

/** Un número de la API ("72400.5") escrito como lo escribiría el usuario ("72400,5"), para precargar un campo. */
export function toInputDecimal(value: Decimal, separator: DecimalSeparator): string {
  return separator === 'COMMA' ? value.replace('.', ',') : value
}

/** El día de hoy en Argentina, en AAAA-MM-DD: el mismo "hoy" que usa el backend para las fechas de operación. */
export function todayInArgentina(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Argentina/Buenos_Aires' }).format(new Date())
}
