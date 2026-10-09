import { useMemo } from 'react'
import type { AssetType, Currency, Decimal } from '../api/types'
import { useAppSelector } from '../app/hooks'
import {
  formatCompact,
  formatMoney,
  formatPercent,
  formatPrice,
  formatQuantity,
  HIDDEN,
  type FormatOptions,
} from './format'

/**
 * Los formateadores con las preferencias del usuario ya aplicadas: el separador decimal y "ocultar montos".
 * Con los montos ocultos se tapan los valores de la cartera (montos y cantidades); los precios y los
 * porcentajes se siguen viendo, porque no dicen cuánto tiene.
 */
export function useFormat() {
  const separator = useAppSelector((state) => state.preferences.decimalSeparator)
  const hidden = useAppSelector((state) => state.preferences.hideAmounts)

  return useMemo(
    () => ({
      money: (value: Decimal | number, currency: Currency, options?: FormatOptions) =>
        hidden ? HIDDEN : formatMoney(value, currency, separator, options),
      price: (value: Decimal | number, currency: Currency) => formatPrice(value, currency, separator),
      percent: (value: Decimal | number, options?: FormatOptions) => formatPercent(value, separator, options),
      quantity: (value: Decimal | number, type: AssetType) =>
        hidden ? HIDDEN : formatQuantity(value, type, separator),
      compact: (value: Decimal | number) => (hidden ? '•••' : formatCompact(value, separator)),
      separator,
      hidden,
    }),
    [separator, hidden],
  )
}
