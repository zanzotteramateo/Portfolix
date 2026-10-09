/*
 * Tipos de los DTOs del backend, copiados a mano (decisión de la fase 11: sin generador de tipos).
 * Cada uno lleva el nombre del record de Java. Si cambia un DTO en el backend, hay que actualizarlo acá:
 * TypeScript no se entera solo.
 */

/** ErrorResponse: el formato de todos los errores de la API. */
export interface ErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  /** Solo cuando el error es de un campo del formulario. */
  fieldErrors?: { field: string; message: string }[]
}

/** AuthResponse: lo que devuelven login, Google y refresh. El refresh token viaja aparte, en una cookie. */
export interface AuthResponse {
  accessToken: string
  tokenType: string
  /** Segundos de vida del access token. */
  expiresIn: number
}

/** UserResponse: GET /me y la respuesta del registro. */
export interface UserResponse {
  id: number
  fullName: string
  email: string
  /** false en las cuentas creadas con Google que todavía no tienen contraseña propia. */
  hasPassword: boolean
}

/** RegisterRequest */
export interface RegisterRequest {
  fullName: string
  email: string
  password: string
  acceptedTerms: boolean
}

/** LoginRequest */
export interface LoginRequest {
  email: string
  password: string
  rememberMe: boolean
}

/** GoogleLoginRequest: el ID token que da el botón de Google. */
export interface GoogleLoginRequest {
  idToken: string
  rememberMe: boolean
}

/** ChangePendingEmailRequest: cambiar el mail de una cuenta que todavía no se verificó. */
export interface ChangePendingEmailRequest {
  email: string
  password: string
  newEmail: string
}

/** ResetPasswordRequest */
export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

/** ChangePasswordRequest: PUT /me/password (las sesiones de los otros dispositivos se cierran). */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

/** EmailChangeRequest: POST /me/email-change (el cambio se aplica al confirmar el link del mail nuevo). */
export interface EmailChangeRequest {
  newEmail: string
  currentPassword: string
}

/** DeletionSummaryResponse: GET /me/deletion-summary, lo que se borra con la cuenta. */
export interface DeletionSummaryResponse {
  portfolios: number
  assets: number
  transactions: number
  email: string
}

/** DeleteAccountRequest: DELETE /me. */
export interface DeleteAccountRequest {
  currentPassword: string
}

// ---- Enums (en el backend son enums de Java; en el JSON viajan con su nombre) ----

export type Currency = 'ARS' | 'USD'
export type AssetType = 'STOCK' | 'CEDEAR' | 'CRYPTO'
export type TransactionType = 'BUY' | 'SELL'
export type Theme = 'LIGHT' | 'DARK'
export type DecimalSeparator = 'COMMA' | 'PERIOD'

/*
 * Montos, precios, cantidades y porcentajes viajan como string ("1234.5"), sin ceros de relleno
 * (JacksonConfig del backend). Se pasan a número solo para mostrarlos o calcular algo en pantalla.
 */
export type Decimal = string

/** PreferencesResponse */
export interface PreferencesResponse {
  theme: Theme
  decimalSeparator: DecimalSeparator
  currency: Currency
  hideAmounts: boolean
}

/** PreferencesUpdateRequest: solo los campos que cambian (null o ausente = sin cambios). */
export type PreferencesUpdateRequest = Partial<PreferencesResponse>

/** PortfolioResponse */
export interface PortfolioResponse {
  id: number
  name: string
  createdAt: string
}

/** AssetResponse: el catálogo (GET /assets solo trae los activos activos). */
export interface AssetResponse {
  symbol: string
  name: string
  type: AssetType
  currency: Currency
}

/** DashboardSummaryResponse (montos en la moneda pedida, ya redondeados). */
export interface DashboardSummaryResponse {
  currency: Currency
  currentValue: Decimal
  investedCapital: Decimal
  /** No realizada + realizada. */
  totalPnl: Decimal
  /** Sobre todo lo comprado alguna vez; null sin compras. */
  totalPnlPercent: Decimal | null
  unrealizedPnl: Decimal
  realizedPnl: Decimal
  holdingsCount: number
  /** Siempre los 3 tipos; percent null si no hay capital. */
  distribution: { type: AssetType; value: Decimal; percent: Decimal | null }[]
  /** Dólar usado para convertir: rate = pesos por dólar. */
  fx: { type: string; rate: Decimal }
  /** El precio más viejo que se usó; null sin posiciones. */
  pricesUpdatedAt: string | null
}

/** HoldingResponse: una fila de la tabla de activos (en la moneda pedida). */
export interface HoldingResponse {
  symbol: string
  name: string
  type: AssetType
  quantity: Decimal
  averagePrice: Decimal | null
  investedCapital: Decimal
  currentPrice: Decimal
  currentValue: Decimal
  pnl: Decimal
  pnlPercent: Decimal | null
  realizedPnl: Decimal
  /** Variación de 7 días en la moneda del activo; null si el historial todavía no se cargó. */
  change7dPercent: Decimal | null
  /** Hasta 28 precios de 7 días (moneda del activo); null si todavía no se cargó. */
  sparkline7d: Decimal[] | null
}

/** HoldingsResponse: GET /holdings (posiciones abiertas, por capital actual de mayor a menor). */
export interface HoldingsResponse {
  currency: Currency
  pricesUpdatedAt: string | null
  holdings: HoldingResponse[]
}

/** QuoteResponse: GET /assets/{symbol}/quote. */
export interface QuoteResponse {
  symbol: string
  currency: Currency
  price: Decimal
  change24hPercent: Decimal | null
  updatedAt: string
}

/** HistoryResponse: GET /assets/{symbol}/history (en la moneda del activo). */
export interface HistoryResponse {
  symbol: string
  currency: Currency
  range: '24h' | '7d'
  changePercent: Decimal | null
  points: { time: string; price: Decimal }[]
}

/** TransactionRequest: el formulario de alta (y de edición). */
export interface TransactionRequest {
  portfolioId: number
  assetSymbol: string
  type: TransactionType
  quantity: Decimal
  price: Decimal
  /** AAAA-MM-DD; no puede ser futura (día de Argentina). */
  tradeDate: string
  notes: string | null
}

/** TransactionResponse */
export interface TransactionResponse {
  id: number
  portfolio: { id: number; name: string }
  asset: { symbol: string; name: string; type: AssetType }
  type: TransactionType
  quantity: Decimal
  price: Decimal
  /** cantidad × precio, en la moneda de la transacción. */
  total: Decimal
  currency: Currency
  tradeDate: string
  notes: string | null
  createdAt: string
}

/** TransactionSummary: las tarjetas del historial. Los totales van separados por moneda, y `converted`, en una sola. */
export interface TransactionSummary {
  totalOperations: number
  totalBought: Record<Currency, Decimal>
  totalSold: Record<Currency, Decimal>
  converted: { currency: Currency; totalBought: Decimal; totalSold: Decimal }
}

/** TransactionPageResponse: GET /transactions (paginado, con el resumen de totales). */
export interface TransactionPageResponse {
  content: TransactionResponse[]
  page: { number: number; size: number; totalElements: number; totalPages: number }
  /** No aplica el filtro de tipo (las tarjetas no cambian con Compras/Ventas). */
  summary: TransactionSummary
}
