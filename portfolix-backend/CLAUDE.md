# Portfolix — Backend

## Qué es el proyecto
Portfolix es una app web para seguir una cartera de inversiones personal. El usuario registra compras y ventas de **acciones argentinas, CEDEARs y criptomonedas**, las agrupa en **portafolios** (ej.: "Jubilación", "Trading a corto plazo", "Ahorro Crypto") y ve el valor actual, la ganancia/pérdida (P&L), la distribución por tipo de activo y la tendencia de cada activo.

Este módulo es el **backend** (API REST). El front, en React, está en `../portfolix-frontend/` (fase 11, en curso), con su propio CLAUDE.md para lo que es solo del front; el estado del proyecto entero sigue estando acá.

## Stack
- Java (última LTS) + Spring Boot
- Spring Web, Validation, Spring Data JPA, PostgreSQL, Flyway
- Spring Security + OAuth2 Resource Server (JWT propios, firmados con `NimbusJwtEncoder`)
- Java Mail Sender + Thymeleaf (solo para plantillas de mails)
- Spring Cache + Caffeine (cotizaciones y precios)
- springdoc-openapi (Swagger UI)
- Docker Compose para Postgres en desarrollo, Testcontainers para tests de integración
- Package base: `com.portfolix.api`

## Arquitectura
Arquitectura en capas (controller → service → repository), **organizada por funcionalidad**:

```
com.portfolix.api
├── auth/          registro, login, OAuth (Google), tokens de verificación, reset y cambio de mail
├── user/          datos del usuario, GET /me, preferencias
├── account/       operaciones de cuenta que cruzan módulos: cambiar contraseña y mail, eliminar cuenta, export CSV
├── portfolio/     CRUD de portafolios, duplicar, eliminar moviendo o borrando transacciones
├── transaction/   alta y listado de transacciones, export CSV
├── holding/       posiciones y dashboard (se CALCULAN a partir de transacciones, no tienen entidad)
├── asset/         catálogo de activos (símbolo, nombre, tipo)
├── market/        integración con APIs externas de precios y cotización del dólar
├── common/        excepciones, handler global, mail, utilidades
├── security/      SecurityConfig, JwtService, CORS, usuario actual
└── config/        cache, OpenAPI, RestClient
```

Cada módulo contiene su entidad, repository, service, controller, `dto/` y mapper si hace falta.

## Convenciones (respetarlas siempre)
- Los controllers **nunca devuelven entidades**: siempre DTOs (preferir `record`).
- Validación con `@Valid` y anotaciones de Jakarta Validation en los DTOs de entrada.
- La lógica de negocio va en los services. Los controllers solo reciben, delegan y responden.
- Un módulo no usa el repository de otro módulo: se comunica a través del service.
- Errores centralizados en un `@RestControllerAdvice` con un formato `ErrorResponse` único.
- Montos, precios y cantidades con `BigDecimal` en Java y `NUMERIC` en Postgres. Nunca `double`.
- El esquema de la base se maneja **solo con migraciones Flyway** (`ddl-auto=validate`).
- Rutas bajo el prefijo `/api/v1`.
- Todo recurso pertenece a un usuario: cada consulta filtra por el usuario autenticado. Un usuario nunca puede ver ni modificar datos de otro.
- Código, nombres de clases y endpoints en inglés. Mensajes de error visibles al usuario en español.

## Reglas de negocio importantes
- La cuenta se crea sin verificar; hay que activarla con un link por mail que vence a las 24 h. No se puede iniciar sesión sin verificar.
- Contraseña: mínimo 8 caracteres, una mayúscula, un número y un símbolo. Al resetearla no puede ser igual a la actual (decidido en la fase 8: sin historial de contraseñas anteriores).
- Al cambiar o resetear la contraseña se cierran las sesiones de los otros dispositivos.
- El cambio de mail se aplica recién cuando el usuario confirma el link enviado al correo nuevo, y pide la contraseña actual.
- Eliminar cuenta requiere contraseña y borra portafolios, transacciones y preferencias.
- Al eliminar un portafolio, el usuario elige: mover sus transacciones a otro portafolio o borrarlas.
- Una venta no puede superar la cantidad que el usuario tiene de ese activo en ese portafolio (en ninguna fecha). Editar o borrar una transacción tampoco puede dejar a una venta sin tenencia suficiente.
- Cada transacción guarda su moneda (ARS o USD), porque las cripto se operan en USD y los CEDEARs/acciones en ARS. El dashboard puede mostrarse en ARS o USD.
- Endpoints sensibles (login, reenvío de mails, recuperación de contraseña) tienen rate limit.

## Endpoints previstos (resumen)
- **Auth:** register, verify-email (+ resend y cambio de mail antes de verificar), login, oauth/google, refresh, logout, password/forgot, password/reset (oauth/apple quedó afuera: requiere la cuenta paga de Apple Developer)
- **Cuenta:** GET /me, email-change (+ confirm), PUT /me/password, GET /me/deletion-summary, DELETE /me, GET /me/export/transactions.csv
- **Preferencias:** GET/PATCH /me/preferences (tema, separador decimal, moneda, ocultar montos)
- **Portafolios:** GET/POST /portfolios, GET/PATCH/DELETE /portfolios/{id}, POST /portfolios/{id}/duplicate
- **Dashboard:** GET /dashboard/summary, GET /holdings, GET /holdings/{symbol}
- **Transacciones:** GET /transactions (filtros + resumen de totales), POST /transactions, GET/PUT/DELETE /transactions/{id}
- **Activos y mercado:** GET /assets, GET /assets/{symbol}/quote, GET /assets/{symbol}/history, GET /market/fx

## Cómo quiero que trabajes
- Antes de escribir código para una tarea nueva, proponé un plan corto y esperá mi OK.
- Avanzá en pasos chicos que compilen y se puedan probar. No generes todo el proyecto de una.
- Cuando crees un endpoint, agregá al menos un test (unitario del service o de integración).
- Si algo no está definido en este archivo, preguntame en lugar de asumir.
- Explicame brevemente las decisiones importantes: estoy aprendiendo y quiero entender el porqué.

## Roadmap
1. **Setup.** Postgres, Flyway, configuración base y manejo de errores.
2. **Modelo de datos.** Migraciones de las tablas del núcleo: usuarios, portafolios, activos y transacciones. Las tablas de tokens, historial de contraseñas y preferencias se crean en su propia fase. Al borrar un usuario se borran en cascada sus portafolios.
3. **Auth básica.** Registro y login con JWT, **incluyendo refresh token (guardado en la base) y logout**. Sin mails ni OAuth todavía: las cuentas se marcan como verificadas al crearlas. Swagger UI (springdoc) queda configurado acá.
4. **Portafolios.** CRUD completo. Asienta el patrón controller → service → repository → DTO.
5. **Activos y transacciones.** Catálogo de activos cargado con una migración (seed), alta y listado de transacciones con la validación de ventas. Al final de la fase: **eliminar un portafolio eligiendo mover sus transacciones a otro o borrarlas**.
6. **Holdings y dashboard, con precios fijos.** Posiciones, P&L y distribución. Los precios y la cotización del dólar se obtienen a través de interfaces (ej.: `PriceProvider`, `FxRateProvider`) con una implementación de valores fijos.
7. **Datos de mercado.** Implementaciones reales de esas interfaces contra APIs externas, con caché (Caffeine).
8. **Auth completa.** Verificación por mail, recuperación de contraseña, Google, rate limit y bloqueo por intentos fallidos. En tres partes: 8A mails y verificación, 8B contraseña y protección del login, 8C Google. Apple quedó afuera.
9. **Cuenta y extras.** Cambio de mail y contraseña, eliminar cuenta, preferencias, export CSV, duplicar portafolios.

**Segunda etapa** (definida el 03/10/2026). Objetivo: uso personal y mostrarla como proyecto; no se abre a otros usuarios. El front va antes que el deploy, así el deploy se decide una sola vez para el front y la API.

10. **Base para producción (backend).** 10A: CI con GitHub Actions (`./mvnw verify` en cada push), Dependabot y un README. 10B: perfil `prod` (hoy `dev` es el perfil por defecto y trae una clave JWT de desarrollo commiteada: en un servidor no puede quedar activo), conexión a la base y mails por variables de entorno, decidir si Swagger se apaga e imagen Docker probada en local con ese perfil y un límite de memoria.
11. **Front en React, de cero, desde el diseño de Figma.** En `portfolix-frontend/`, con su propio CLAUDE.md. 11A: la base (tipos de los DTOs, cliente HTTP con el access token en memoria y el refresh con la cookie, manejo de `fieldErrors`, 429 y 409, rutas de los links de los mails) y las pantallas de auth, con Google. 11B: dashboard, detalle de activo, portafolios y registrar transacción (se adelantó de la 11C). 11C: historial de transacciones (incluye editar y borrar, que no tienen diseño). 11D: cuenta y ajustes (con el tema claro). 11E: tests end-to-end (ej.: Playwright) del flujo principal. Stack decidido al arrancar: React + Vite + TypeScript, Redux Toolkit, React Router y React Hook Form, CSS Modules, `fetch` + `useEffect`; de cero, con Figma de referencia (detalles en `portfolix-frontend/CLAUDE.md`).
12. **Deploy.** El front y la API tienen que estar en el **mismo sitio**, porque la cookie de refresh es `SameSite=Strict` (con subdominios de plataformas distintas el navegador no la manda): dominio propio con `app.` y `api.`, o el front reenviando `/api` al backend. Elegir hosting, Postgres administrado y proveedor de mails con SPF y DKIM (es camino crítico: el login exige el mail verificado). Configurar `JWT_SECRET`, el Client ID de Google con el origen de producción, `CORS_ALLOWED_ORIGINS`, `FRONTEND_URL` y `server.forward-headers-strategy`. Backups, monitor de uptime y una sola instancia (el rate limit y el bloqueo viven en memoria).
13. **Mostrarla como proyecto.** README con capturas y arquitectura, badge de CI y un usuario demo con datos de ejemplo. Decidir si el registro queda abierto. Si queda, hacen falta dos cosas: una página simple de términos y privacidad, porque el registro exige aceptarlos, y pasar a modo producción la pantalla de consentimiento de Google (en modo prueba solo pueden entrar los usuarios cargados como de prueba).

**Backlog (ideas sin decidir).** Producto: tipo de dólar como preferencia (blue, MEP u oficial); rendimiento real contra la inflación y el dólar; gráfico de la evolución del portafolio (no está en el diseño); bonos, FCI, plazo fijo y dividendos; importar transacciones desde un CSV; alertas de precio por mail. Técnico: catálogo de activos sin migraciones; invalidar los access tokens al cambiar la contraseña; Redis si hay más de una instancia; 2FA; Apple (requiere la cuenta paga).

## Estado actual
**Fase 1 (setup) — completada.**
- Java 25, Spring Boot 4.1, package `com.portfolix.api`.
- Postgres 17 con Docker Compose, levantado automáticamente por `spring-boot-docker-compose` al arrancar la app.
- `application.yml` (común) + `application-dev.yml` (perfil por defecto). Idioma fijo en español para los mensajes de validación.
- Flyway con `V1__init.sql` vacía. Las próximas migraciones arrancan en V2.
- `GlobalExceptionHandler` + `ErrorResponse` en `common/exception`. Errores de negocio (`BusinessException`) → 400, recurso inexistente (`ResourceNotFoundException`) → 404.
- `SecurityConfig` mínimo: stateless, CSRF desactivado, CORS para `http://localhost:5173`, todo `permitAll` hasta la fase de auth.
- La JVM corre en UTC (en el `main` y en Surefire), independientemente de la zona horaria de la máquina.

**Fase 2 (modelo de datos) — completada.**
- Migraciones V2–V5: `users`, `portfolios`, `assets`, `transactions`. Entidades JPA y repositories (vacíos) en cada módulo.
- IDs `BIGINT GENERATED ALWAYS AS IDENTITY`. Enums como `VARCHAR` + `CHECK` (no `ENUM` de Postgres), `@Enumerated(STRING)` en Java.
- Borrado: usuario → portafolios `CASCADE`, portafolio → transacciones `CASCADE` (la opción "mover" la hace el service antes de borrar), activo → transacciones `RESTRICT`.
- Cantidades y precios `NUMERIC(38,18)`. `DatabaseSchemaTest` cubre cascadas, unicidad y restricciones.

**Fase 3 (auth básica) — completada.**
- Endpoints: `POST /auth/register` (201, sin sesión), `/auth/login`, `/auth/refresh`, `/auth/logout`, `GET /me`.
- Access token: JWT HS256 de 15 min (`sub` = id del usuario), en el header `Authorization: Bearer`. Clave en `JWT_SECRET` (Base64, ≥ 32 bytes); en dev tiene default.
- Refresh token: valor aleatorio en cookie `portfolix_refresh` (HttpOnly, Secure, SameSite=Strict, Path=/api/v1/auth). En la base (`refresh_tokens`, V6) solo su hash SHA-256. Con "Recuérdame" dura 30 días (cookie persistente); sin él, 1 día (cookie de sesión).
- Rotación en cada refresh; si se reusa un token ya rotado se revocan todas las sesiones del usuario. `RefreshTokenService.revokeAll(userId)` queda listo para el cambio de contraseña.
- Login con mensaje genérico ("Correo o contraseña incorrectos") y comparación contra un hash falso si el mail no existe (mismo tiempo de respuesta).
- `@StrongPassword` (8–64 caracteres, mayúscula, número, símbolo). Contraseñas con BCrypt (`DelegatingPasswordEncoder`).
- `@CurrentUserId Long userId` en los controllers para obtener el usuario autenticado.
- 401/403 de los filtros de Security con el mismo `ErrorResponse` (`JsonSecurityErrorHandler`). `UnauthorizedException` → 401.
- Swagger UI en `/swagger-ui.html` (springdoc 3.1.1). Bean `Clock` inyectable para tests de tiempo.
- Por ahora las cuentas se crean verificadas (`TODO fase 8` en `UserService` y `AuthService`).

**Fase 4 (portafolios) — completada.**
- `GET/POST /portfolios`, `GET/PATCH/DELETE /portfolios/{id}`. `PortfolioResponse(id, name, createdAt)`; POST devuelve 201 + header `Location`.
- Listado por fecha de creación (el más viejo primero). Máximo 20 portafolios por usuario.
- Nombre: trim, 1–50 caracteres, único por usuario sin distinguir mayúsculas (se puede renombrar cambiando solo mayúsculas).
- **Patrón de pertenencia** (repetirlo en todos los módulos): los repositories buscan por `id` + `userId`; el service expone `getOwned(userId, id)`, que tira 404 si no existe o es de otro usuario (nunca 403, para no revelar que el id existe). Otros módulos usan `PortfolioService.getOwned` para validar el portafolio de una transacción.
- `UserService.getReference(userId)` para asociar el usuario a una entidad sin consultarlo.
- `DELETE` con transacciones: ver Fase 5B.
- Tests de integración autentican con `jwt().jwt(t -> t.subject(userId))` de spring-security-test, creando el usuario con `UserService`.

**Fase 5A (catálogo de activos y alta de transacciones) — completada.**
- `V7__seed_assets.sql`: 58 activos (19 acciones con ticker BYMA, ej. `YPFD`; 25 CEDEARs; 14 cripto). No hay activos creados por usuarios.
- `GET /assets?type=&q=`: activos activos, búsqueda sin mayúsculas ni tildes, filtrada en memoria (catálogo chico).
- `POST /transactions` (201): body con `portfolioId`, `assetSymbol`, `type` (BUY/SELL), `quantity`, `price`, `tradeDate`, `notes`. La moneda se copia del activo.
- **Decimales como string en el JSON** (`JacksonConfig`): todos los `BigDecimal` salen como `"0.05"`, sin ceros de relleno. En la entrada se acepta string o número.
- **"Hoy" = día en Argentina** (`BusinessCalendar`, propiedad `portfolix.business-time-zone`). `@NotFutureDate` lo usa en vez de `@PastOrPresent`.
- **Ventas respetando las fechas** (`SellableQuantity`): la tenencia nunca puede quedar negativa en ningún día. Mensajes: "Supera tu tenencia (X)" o "Supera tu tenencia a esa fecha (X)", con X en formato argentino.
- **Bloqueo del portafolio**: toda operación que escribe transacciones (compra, venta, mover, borrar) lo bloquea antes con `PortfolioService.getOwnedForUpdate` (`SELECT ... FOR UPDATE`, `Propagation.MANDATORY`). Así dos ventas simultáneas se validan de a una, y una compra simultánea a un borrado responde 404 en vez de 500.
- Activo inactivo: no se puede comprar, sí vender.
- `BusinessException(field, message)` → la respuesta incluye `fieldErrors` para que el front marque el campo.

**Fase 5B (listado de transacciones y borrado de portafolios) — completada.**
- `GET /transactions?portfolioId=&assetSymbol=&type=&from=&to=&page=0&size=50` (size máx. 100). Orden: fecha desc, y dentro del día la última cargada primero. Fechas de URL en ISO (`spring.mvc.format.date: iso`).
- Respuesta `{content, page: {number, size, totalElements, totalPages}, summary}`. **El resumen no aplica el filtro de tipo** (las tarjetas no cambian con los chips Compras/Ventas); sí aplica portafolio, activo y fechas.
- **Totales separados por moneda**: `totalBought`/`totalSold` = `{"ARS": "...", "USD": "..."}`, siempre con las dos claves. El total convertido a una sola moneda se agrega en la fase 6.
- Filtros con **Specifications** (`TransactionSpecifications`); resumen con una consulta agregada propia (`TransactionSummaryRepository` + `Impl`, Criteria API) que reusa la misma Specification. `@EntityGraph` en el listado para evitar N+1.
- `DELETE /portfolios/{id}?moveTransactionsTo={id}` mueve y elimina; `?deleteTransactions=true` elimina todo; **sin parámetros solo se puede eliminar un portafolio vacío** (si no, 400 con la cantidad de transacciones). Destino ajeno → 404 "Portafolio de destino no encontrado".
- `PortfolioDeletionService` existe para evitar la dependencia circular PortfolioService ↔ TransactionService. Bloquea origen y destino en orden de id ascendente (evita deadlocks).
- Mover no revalida ventas: si la tenencia de cada portafolio nunca fue negativa, la suma tampoco.
- Tests de integración heredan de `ApiIntegrationTest` (helpers: `createUser`, `authenticatedAs`, `createPortfolio`, `registerTransaction`, `recordTransaction`, `daysAgo`).

**Fase 6A (motor de cálculo y holdings) — completada.**
- `market/`: interfaces `PriceProvider` (precio en la moneda del activo) y `FxRateProvider` (pesos por dólar, hoy y por fecha). `CurrencyConverter` da el factor ARS↔USD. Implementaciones: ver Fase 7A.
- **Dólar: BLUE** (`portfolix.market.fx-type`, se cambia en un solo lugar).
- **Precio promedio ponderado** (`PositionCalculator`, clase pura): vender no cambia el promedio; ganancia realizada = (precio de venta − promedio) × cantidad. Si se vende todo, el invertido queda en 0 exacto.
- **Conversión a la otra moneda con el dólar del día de cada operación**; la tenencia se valoriza con el dólar de hoy.
- **"Todos los portafolios" = suma de cada portafolio calculado por separado** (así los números de cada portafolio suman los del total).
- Redondeo solo al responder: montos 2 decimales, precios unitarios 8, porcentajes 2. `pnl` = capital actual − invertido ya redondeados.
- `GET /holdings?portfolioId=&currency=ARS` (posiciones abiertas, por capital actual desc; filtros/orden de la tabla los hace el front) y `GET /holdings/{symbol}` (incluye activos vendidos por completo, con cantidad 0 y su ganancia realizada; 404 si nunca se operó).

**Fase 6B (dashboard y total convertido del historial) — completada.**
- `GET /dashboard/summary?portfolioId=&currency=ARS` (`DashboardService`, reusa `HoldingService.positions`): `currentValue`, `investedCapital`, `totalPnl`, `totalPnlPercent`, `unrealizedPnl`, `realizedPnl`, `holdingsCount`, `distribution` (siempre los 3 tipos) y `fx` (tipo y cotización, para mostrar el valor en la otra moneda).
- **"Ganancia total" incluye lo ya vendido**: no realizada + realizada. Su porcentaje se calcula sobre todo lo comprado alguna vez (`Position.totalBought`), no sobre el capital invertido actual.
- La cabecera suma los montos ya redondeados de cada activo: capital actual e invertido cierran exactamente con la tabla de `/holdings`.
- Distribución con 1 decimal (como el diseño), repartida con el método del mayor resto para que sume exactamente 100. Sin capital: porcentajes `null` (el front muestra guiones).
- `GET /transactions?currency=ARS`: `summary.converted` = total comprado y vendido en una sola moneda, convirtiendo cada día con su dólar (la consulta agregada agrupa también por `trade_date`).
- `Rounding` (redondeo de montos, precios y porcentajes) vive en `common/`.

**Fase 7A (precios y dólar reales) — completada.**
- `portfolix.market.provider`: `live` (default) usa `LiveMarketData`; `fixed` usa `FixedMarketData` (`resources/market/fixed-prices.yml`). **Todos los tests usan `fixed`** vía `src/test/resources/config/application.properties` (así ningún test sale a internet); `PortfolixBackendApplicationTests` levanta `live` apuntando a `localhost:1` para verificar el cableado.
- Fuentes (gratuitas, sin clave): **Data912** (acciones y CEDEARs en ARS, `/live/arg_stocks` y `/live/arg_cedears`), **CoinGecko** (cripto, `/simple/price?ids=...&vs_currencies=usd&include_24hr_change=true`; USDT se fija en 1 en vez de pedirlo, para que un "stablecoin" no se vea moviéndose unos centavos), **DolarApi** (dólar actual) y **ArgentinaDatos** (dólar histórico, toda la serie en un pedido). Cada una tiene su cliente (`*Client`, `RestClient` de `spring-boot-starter-restclient`, timeouts `spring.http.clients.*`). **Binance se sacó el 10/10/2026** (fase 12): le niega el acceso a su API a pedidos desde EE.UU. por motivos regulatorios, y eso incluye a Render. Mapeo símbolo → id de CoinGecko a mano en `CoinGeckoIds` (ej.: `BTC` → `bitcoin`); una cripto nueva en el catálogo necesita su entrada ahí.
- **Blue de venta** (`FxQuote.rate()`). Histórico: si la fecha no tiene cotización (fin de semana, feriado), se usa la del último día anterior.
- **Caché con Caffeine directo** (no `@Cacheable`): `refreshAfterWrite` sin vencimiento → se sirve el valor viejo mientras se refresca en segundo plano y **si la API falla se conserva el último conocido**. Un símbolo que falta en una respuesta nueva conserva su precio. Solo si una fuente nunca respondió → `ServiceUnavailableException` (503). Refresco: precios 5 min, dólar 15 min, histórico 12 h. Precarga al arrancar (`ApplicationReadyEvent`).
- `GET /assets/{symbol}/quote?currency=ARS` (precio, `change24hPercent`: del día para BYMA, 24 h corridas para cripto) y `GET /market/fx` (compra, venta, `rate`). Los sirve `MarketController` en `market/` (así `asset` no depende de `market`).
- `pricesUpdatedAt` (precio más viejo usado) en `/holdings` y `/dashboard/summary`.
- `V8`: el CEDEAR de Disney es **DISN** en BYMA (no DIS).
- A diferencia de Binance, si CoinGecko no tiene un id del pedido no rechaza el lote entero: devuelve el resto y esa cripto puntual se queda con su último precio conocido (el límite que tenía Binance, anotado en el backlog, ya no aplica).

**Fase 7B (historial de precios y tendencias) — completada.**
- `GET /assets/{symbol}/history?range=7d|24h` (`PriceHistoryProvider`): puntos `{time, price}` en la **moneda del activo** y `changePercent` (primer punto → último). Espera a la fuente si no está cargado.
- Cripto (`LiveMarketHistory`): `/coins/{id}/market_chart?vs_currency=usd&days=N` de CoinGecko (`days=1` para 24 h, `days=7` para 7 d) — CoinGecko ajusta sola la densidad de puntos (~5 min para 1 día, ~1 h para 7 días; no se puede pedir un intervalo exacto como con las velas de Binance). Cada punto ya viene con su precio, a diferencia de las velas: no hace falta el truco de "apertura de la primera + cada cierre".
- Acciones y CEDEARs: cierres diarios de Data912 `/historical/{stocks|cedears}/{ticker}` (~600 KB, sin rango: se guardan solo los últimos 30 días). **No hay datos dentro del día**: el gráfico de 24 h es "cierre anterior → precio actual" (2 puntos); el de 7 d, un punto por día hábil desde el último cierre de hace 7 días + precio actual (`BymaHistories`, cierres ubicados a las 17 h ART).
- `/holdings` trae por fila `change7dPercent` y `sparkline7d` (hasta 28 precios). Se leen **solo del caché** (`cachedHistory`): si no está cargado vienen `null` y se piden en segundo plano; la tabla nunca espera ni falla por un historial.
- Refresco: cripto 24 h 5 min, cripto 7 d 15 min, cierres diarios 6 h. Timeout de lectura 10 s (el histórico de Data912 tarda 2–4 s).
- Los refrescos corren en hilos virtuales (`marketExecutor` en `MarketConfig`), no en el ForkJoinPool común.
- Con `provider=fixed` el historial es una línea plana (variación 0).

**Fase 8A (mails y verificación de cuenta) — completada.**
- **Mailpit** en `compose.yaml` (SMTP en 1025, bandeja en http://localhost:8025); arranca solo con la app. En producción, SMTP estándar por variables de entorno: `MAIL_HOST`, `MAIL_PORT` (587), `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` y `FRONTEND_URL`, con STARTTLS obligatorio. `MAIL_HOST` no tiene default: fuera de dev la app no arranca sin él. El health del mail está desactivado (un SMTP caído no marca la app como caída).
- **`MailService`** (`common/mail`): `send(OutgoingMail)` publica un evento y `deliver` lo manda con `@TransactionalEventListener(fallbackExecution = true)` + `@Async("mailExecutor")` (hilos virtuales): **después del commit y en segundo plano**. Si falla, se loguea. Plantillas Thymeleaf en `templates/mail/` con un layout común (`layout(content)`), estilos en línea y diseño en tablas. Las variables del mail son datos simples (se arma en otro hilo, sin transacción).
- **`email_tokens`** (V9): tokens de un solo uso por propósito (`EMAIL_VERIFICATION`, `PASSWORD_RESET`), solo el hash SHA-256 (`SecureTokens`, compartido con los refresh tokens). Un pendiente por usuario y propósito: emitir uno nuevo borra el anterior. Los usados se conservan. `created_at` lo fija el `Clock`.
- **Los links apuntan al front** (`{frontend-url}/verify-email?token=…`) y el front hace `POST /auth/verify-email {token}` → 204. Es idempotente (link ya usado de una cuenta verificada → 204: doble clic, StrictMode de React). Errores: 400 con `fieldErrors[token]` (inválido o reemplazado, vencido, usado). El link de verificación vence en 24 h.
- `POST /auth/verify-email/resend {email}` → **202 siempre** (no revela qué mails tienen cuenta). No manda nada si no hay cuenta, si ya está verificada o si se le mandó otro hace menos de 60 s (`resend-cooldown`).
- `POST /auth/verify-email/change-email {email, password, newEmail}` → 202. Solo para cuentas sin verificar (si no, 400). Mail tomado → 400 en `newEmail`.
- Registro: cuenta sin verificar + mail, en una transacción. **Login con credenciales correctas pero sin verificar → 403** (`ForbiddenException`); con la contraseña mal sigue el 401 genérico.
- `CredentialsChecker`: chequeo de mail y contraseña (con el hash falso para igualar tiempos) que comparten login y change-email.
- Tests: `TestMailbox` (bean de `TestMailConfiguration`, importado por `ApiIntegrationTest`) reemplaza al `JavaMailSender` y guarda los mails. `mailbox.awaitMail(email)` espera el mail (sale async) y `.token()` saca el token del link; `assertNoNewMail` confirma que no llegó otro. Helpers nuevos en `ApiIntegrationTest`: `register`, `verifyEmail`, `registerVerifiedUser`, `login`, `uniqueEmail`, `PASSWORD`. `createUser` crea cuentas sin verificar y sin mail.

**Fase 8B (recuperación de contraseña y protección del login) — completada.**
- `POST /auth/password/forgot {email}` → **202 siempre** (no revela cuentas). Link al front (`/reset-password?token=…`) que vence en 1 h (`password-reset-ttl`), como mucho uno por minuto por cuenta. Sirve también para cuentas sin verificar.
- `POST /auth/password/reset {token, newPassword}` → 204. `@StrongPassword` y **no puede ser igual a la actual** (400 en `newPassword`; no hay historial). Cierra todas las sesiones (`revokeAll`), marca el mail como verificado y desbloquea el login. No inicia sesión. Es una sola transacción: si la contraseña nueva no sirve, el link no queda gastado. Los access tokens ya emitidos siguen valiendo hasta 15 min (son stateless).
- **Bloqueo por intentos** (`LoginLockout`, en memoria con Caffeine, tiempos con el `Clock`): 5 contraseñas incorrectas seguidas con un mismo mail (**exista o no la cuenta**) → **429 durante 5 min**, aunque después ponga la correcta (ni se compara). El contador vuelve a cero si pasan 5 min sin errores, con la contraseña correcta o con un reset. Con 2 y 1 intentos restantes el 401 avisa ("Te quedan 2 intentos antes de que bloqueemos el acceso por 5 minutos"); bloqueado: "Por seguridad, bloqueamos el acceso… Probá de nuevo en N minutos o restablecé tu contraseña". Lo aplica `CredentialsChecker`: cuenta igual en login y change-email. Config: `portfolix.auth.login-lockout`.
- **Rate limit por IP** (`security/ratelimit`, Bucket4j 8.20 + Caffeine, en memoria): `@RateLimited(política)` en el método del controller; lo aplica `RateLimitInterceptor` (un interceptor de Spring MVC, así el 429 lo responde el `GlobalExceptionHandler`). Políticas en `portfolix.rate-limit.limits`: **LOGIN** 10/min (login), **EMAIL** 10/h compartido (registro, reenvío, olvidé mi contraseña, cambio de mail), **TOKEN** 20/min (verificar, reset). Refresh y logout no tienen límite. Token bucket: las fichas se recargan de a poco (10/min = una cada 6 s). La IP es `getRemoteAddr()`: **al deployar detrás de un proxy hay que configurar `server.forward-headers-strategy`**, o todos los clientes compartirían la IP del proxy.
- 429 = `TooManyRequestsException(message, retryAfter)` → header `Retry-After` en segundos (redondeado para arriba), expuesto por CORS para que el front muestre la cuenta regresiva. `DurationText.roundedUp` arma el "probá de nuevo en N".
- Tests: el rate limit está **apagado en los tests** (`config/application.properties`), porque todos los pedidos de MockMvc vienen de 127.0.0.1. `RateLimitIntegrationTest` lo prende con `@TestPropertySource` (levanta su propio contexto) y usa una IP distinta por test. `MutableClock` es un reloj que se adelanta a mano, para probar vencimientos.

**Fase 8C (login con Google) — completada.**
- **Flujo**: el front usa el botón de Google Identity Services, que le da un **ID token** (JWT firmado por Google), y lo manda a `POST /auth/oauth/google {idToken, rememberMe}`. La respuesta es igual a la del login (access token + cookie). Rate limit LOGIN. No pasa por el bloqueo por intentos (no hay contraseña).
- **Validación** (`auth/oauth/GoogleIdTokenVerifier`): `NimbusJwtDecoder` con las claves públicas de Google (`jwk-set-uri`, se bajan la primera vez y se cachean; `RestTemplate` con los timeouts de `spring.http.clients`). Verifica firma, `iss` (`https://accounts.google.com` o `accounts.google.com`), `aud` = nuestro Client ID, vencimiento y `email_verified`. Token inválido → 401; mail no verificado por Google → 403; sin Client ID o sin poder bajar las claves → 503. El decoder **no es un bean** (Spring Security tendría dos `JwtDecoder` y no sabría cuál usar para los access tokens).
- **Client ID**: `GOOGLE_CLIENT_ID` (no es secreto). En dev tiene default (el de desarrollo, en `application-dev.yml`). El backend no usa el client secret.
- **`user_identities`** (V10): proveedor + `sub` (id fijo de la cuenta de Google), únicos; una cuenta de cada proveedor por usuario. `users.password_hash` pasó a ser opcional.
- **`OAuthAccountService.resolveUser`**: (1) si el `sub` ya está vinculado, ese usuario (aunque haya cambiado el mail en Google); (2) si hay una cuenta con ese mail, la **vincula**; si no estaba verificada, la verifica y **le saca la contraseña** (quien la creó nunca probó ser el dueño del mail); si ya tiene otra cuenta de Google vinculada → 400; (3) si no, **crea** una cuenta verificada, sin contraseña, con el nombre de Google (o lo anterior a la @) y términos aceptados.
- **Cuentas sin contraseña**: el login con contraseña les responde el 401 genérico, y se crean una con "Olvidé mi contraseña" (`changePassword` acepta hash `null`). `CredentialsChecker` exige que la cuenta tenga contraseña propia, y el hash falso es de una contraseña aleatoria por arranque: si no, una cuenta sin contraseña se compararía contra el hash falso y quien conociera esa contraseña entraría.
- Tests: `TestGoogle` firma ID tokens con una clave RSA de prueba y `TestGoogleConfiguration` (importada por `ApiIntegrationTest`) registra un verificador `@Primary` que valida con esa clave: ningún test sale a internet. `GoogleIdTokenVerifierTest` prueba la validación real de Nimbus (emisor, destinatario, vencimiento, firma, claves inalcanzables).
- **Probado con una cuenta de Google real**: una página en `http://localhost:5173` con el botón de Google, que manda el `credential` a la API. Para probar en localhost, el Client ID tiene que tener autorizado ese origen (Google recomienda agregar también `http://localhost` sin puerto) y la página tiene que servirse con `Referrer-Policy: no-referrer-when-downgrade`.
- **Apple quedó afuera** (requiere la cuenta paga de Apple Developer).

**Fase 8 (auth completa) — completada (8A + 8B + 8C).**

**Fase 9A (cuenta) — completada.**
- **Módulo `account/`** (`AccountController` en `/api/v1/me`, `AccountService`): orquesta auth, user, portfolio y transaction. Existe para no crear dependencias circulares (auth ya usa a user), así que estas operaciones no viven en `user/` como decía el plan original.
- **Confirmación con la contraseña actual** (`CredentialsChecker.confirm`): la piden cambiar la contraseña, cambiar el mail y eliminar la cuenta. Error → **400 en `currentPassword`** (no 401: el usuario sigue autenticado). Comparte el bloqueo por intentos con el login (5 errores → 429 también en el login). **Las cuentas sin contraseña (solo Google) primero tienen que crearse una** con "¿Olvidaste tu contraseña?" (decisión de la fase 9); si no, 400 con ese mensaje. `GET /me` devuelve `hasPassword` para que el front lo sepa de antemano.
- **Sesiones**: cada login abre una sesión (`refresh_tokens.session_id`, V11) que se mantiene al rotar el refresh token. El access token la lleva en el claim **`sid`**, y `@CurrentSessionId` la inyecta en el controller.
- `PUT /me/password {currentPassword, newPassword}` → 204. La nueva no puede ser igual a la actual. Cierra las sesiones de los **otros** dispositivos (`revokeOtherSessions`) y deja la actual. Un token sin `sid` (ej.: los `jwt()` de los tests) las cierra todas. Rate limit LOGIN.
- **Cambio de mail**: `POST /me/email-change {newEmail, currentPassword}` → 202 y link al mail **nuevo** (`/confirm-email-change?token=…`, 24 h, `EMAIL_CHANGE` en `email_tokens` con `new_email`, V12). `POST /auth/email-change/confirm {token}` (público) aplica el cambio; es idempotente. Hasta confirmar se sigue entrando con el mail actual. Mail tomado o igual al actual → 400 en `newEmail`; si se ocupó entre el pedido y la confirmación, 400 y el link no se gasta. Sin avisos al mail anterior (decisión de la fase 9).
- **Eliminar cuenta**: `GET /me/deletion-summary` → `{portfolios, assets, transactions, email}` (`assets` = activos distintos que operó alguna vez). `DELETE /me {currentPassword}` → 204, borra todo en cascada desde Postgres y limpia la cookie del refresh token. "Escribí ELIMINAR" lo valida el front.
- `VerifyEmailRequest` pasó a llamarse `TokenRequest` (lo usan la verificación y la confirmación del cambio de mail).
- Tests: `AccountIntegrationTest` hace login de verdad (dos sesiones) para comprobar qué sesiones se cierran.

**Fase 9B (extras) — completada.**
- **Preferencias** (`user/preference`): `GET/PATCH /me/preferences` → `{theme, decimalSeparator, currency, hideAmounts}`. Valores por defecto: **tema claro** (`LIGHT`/`DARK`), coma decimal (`COMMA`/`PERIOD`), `ARS`, montos visibles. Tabla `user_preferences` (V13), una fila por usuario con el id como clave, que se crea recién la primera vez que guarda algo (hasta entonces `GET` devuelve los valores por defecto). `PATCH` cambia solo los campos que vienen (`null` = sin cambios); un valor desconocido → 400. El backend no las aplica por su cuenta: el front sigue mandando `currency`. La única que usa el backend es el separador decimal (para el CSV).
- **Export CSV**: `GET /me/export/transactions.csv` (en `AccountController`; el armado está en `transaction/TransactionCsv`, una clase pura). Todo el historial en orden cronológico, columnas `Fecha;Portafolio;Tipo;Símbolo;Activo;Cantidad;Precio;Moneda;Total;Notas`. **Según el separador decimal del usuario**: coma → `;` y `1234,56` (Excel en español); punto → `,` y `1234.56`. Sin separador de miles, total redondeado a centavos, UTF-8 con BOM, líneas CRLF, celdas con separador/comillas/saltos entre comillas. Al texto del usuario que empieza con `= + - @` se le antepone `'` (evita que Excel lo ejecute como fórmula: "CSV injection"). `Content-Disposition: attachment; filename="portfolix-transacciones-AAAA-MM-DD.csv"` (fecha de Argentina).
- **Duplicar portafolio**: `POST /portfolios/{id}/duplicate {name?}` → 201 + `Location`. Copia el portafolio con **todas sus transacciones** en el mismo orden (`PortfolioDuplicationService`, separado de `PortfolioService` por la misma dependencia circular que el borrado). Bloquea el original mientras copia. Sin nombre: "Nombre (copia)", "Nombre (copia 2)"…, recortando el nombre (no el sufijo) si no entra en 50. Respeta el máximo de 20 y el nombre único; un nombre solo con espacios → 400.

**Roadmap completo (fases 1 a 9).** Lo que sigue está en la segunda etapa del roadmap (fases 10 a 13).

**Después del roadmap: editar y borrar transacciones — completada.**
- `GET /transactions/{id}` (el mismo `TransactionResponse` del listado), `PUT /transactions/{id}` → 200 y `DELETE /transactions/{id}` → 204. Si no existe o es de otro usuario → 404 "Transacción no encontrada".
- **PUT con el formulario completo**: el mismo `TransactionRequest` y las mismas validaciones del alta; `notes: null` = sin notas. Se puede cambiar **todo**, incluso el activo y el portafolio. La moneda se vuelve a copiar del activo (`Transaction.update`; `asset` y `currency` ya no son `updatable = false`, pero siguen sin setter).
- **Ningún cambio puede dejar una venta descubierta**: se arma el historial como quedaría y se busca la primera venta con tenencia negativa (`SellableQuantity.firstUncoveredSale`). Si cambian el activo o el portafolio, se validan los dos historiales (el que pierde la transacción y el que la recibe). 400 sin campo: "No se puede eliminar: la venta de 8 YPFD del 20/09/2026 en Jubilación quedaría sin tenencia suficiente" / "Este cambio deja sin tenencia suficiente a la venta de …". Si la editada es una venta que se pasa, sale el mensaje del alta en `quantity`. Borrar una venta no se valida (nunca descubre otra).
- **Al editar, la transacción conserva su orden de alta**: dentro del día va por id, el mismo orden de las posiciones (no hay una columna de orden aparte). `SellableQuantity.at(otras, posición)` calcula lo disponible en ese lugar.
- **Activo inactivo**: una compra que ya existía de ese activo se puede corregir (cantidad, precio, fecha, notas, portafolio), pero ninguna edición puede crear una compra nueva de él (cambiar el activo por uno inactivo, o pasar una venta a compra). Las ventas se editan siempre.
- **Bloqueo** (`TransactionService.lockAndLoad`): primero se lee solo el id del portafolio de la transacción, después se lo bloquea (con el de destino, en orden de id ascendente) y recién ahí se carga la transacción, así no se valida con datos viejos. Si en el medio otro pedido la borró → 404; si la movió a otro portafolio → **409** (`ConflictException`, nueva) "La transacción cambió mientras tanto…".
- Los historiales se leen **antes** de modificar la entidad (con la entidad ya modificada, Hibernate la guardaría antes de la consulta). No hay `save()`: los cambios se guardan por dirty checking al confirmar, y si la validación falla, el rollback los descarta.
- Tests: `TransactionEditIntegrationTest` (incluye tres borrados simultáneos de los que solo uno puede pasar). `recordTransaction` devuelve el id.

**Fase 10A (CI, Dependabot y README) — completada.**
- **Workflow** `.github/workflows/backend-ci.yml` (en la raíz del repo, donde GitHub lo busca). Corre en cada push a `main` y en cada PR que toque `portfolix-backend/` o el propio workflow, y a mano desde la pestaña Actions. Permisos de solo lectura; un push nuevo a la misma rama cancela la corrida anterior.
- Job **Tests**: `ubuntu-latest`, JDK 25 (Temurin) con caché de Maven y `./mvnw -B -ntp verify` (tests + jar). Si falla, sube `target/surefire-reports` como artifact (7 días). En Windows no serviría: sus runners no corren contenedores de Linux y Testcontainers los necesita.
- Job **Grafo de dependencias** (solo en `main`): `advanced-security/maven-dependency-submission-action` le manda a GitHub el árbol de dependencias resuelto. Hace falta porque Spring Boot fija las versiones desde el pom padre: leyendo solo el `pom.xml`, GitHub no ve las versiones reales ni las transitivas y las alertas casi no detectarían nada. Es el único job con `contents: write`.
- **Dependabot solo para seguridad** (`.github/dependabot.yml`: Maven y GitHub Actions con `open-pull-requests-limit: 0`). No abre PRs de versiones nuevas; las de seguridad llegan si en la configuración del repo están prendidas "Dependabot alerts" y "Dependabot security updates" (lo hace el usuario en GitHub). Para recibir también versiones nuevas, subir el límite.
- `mvnw` quedó ejecutable en git (`100755`): era `100644`, y en Linux `./mvnw` daba "Permission denied".
- `README.md` en la raíz, en español, con el badge del CI. **Sin licencia por ahora** (decisión de la 10A: agregarla después es fácil; sacarla no, porque las copias conservan la que tenían).
- Versiones de las actions (verificadas el 03/10/2026): `checkout@v7`, `setup-java@v6`, `upload-artifact@v7`, `maven-dependency-submission-action@v6`. Para validar el workflow después de tocarlo: `MSYS_NO_PATHCONV=1 docker run --rm -v "<ruta del repo>:/repo" -w /repo rhysd/actionlint:latest`.
- La suite pasa con la JVM en inglés, como en el runner (`JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"`): no depende del idioma de Windows.

**Fase 10B (configuración de producción e imagen Docker) — completada.**
- **Sin perfil por defecto** (decisión de la 10B, en vez de un perfil `prod`): se sacó `spring.profiles.default: dev`. La configuración base (`application.yml`) es la de producción y ahora incluye la conexión a la base (`DB_HOST`, `DB_PORT` 5432, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, sin defaults). `dev` se activa con `./mvnw spring-boot:run` (`<profiles>` del `spring-boot-maven-plugin`) y en los tests (`spring.profiles.active=dev` en `src/test/resources/config/application.properties`; la base la sigue poniendo Testcontainers). Desde el IDE: `SPRING_PROFILES_ACTIVE=dev`. **Un jar o una imagen sin configurar no arranca** (antes arrancaba en `dev`, con la clave JWT commiteada).
- Variables obligatorias en producción: `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `MAIL_HOST`, `MAIL_FROM`, `FRONTEND_URL`. Opcionales: `DB_PORT`, `MAIL_PORT` (587), `MAIL_USERNAME`, `MAIL_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `GOOGLE_CLIENT_ID`. Si falta una, la app corta al arrancar y avisa de a una: las validadas con `@Validated` con un mensaje en español ("Falta la clave de firma de JWT (variable de entorno JWT_SECRET)"), las demás con "Could not resolve placeholder".
- **Swagger queda prendido en producción** (decisión de la 10B: el repo es público, así que no oculta nada, y suma para mostrar el proyecto).
- **`Dockerfile`** (en `portfolix-backend/`, con `.dockerignore`), en dos etapas:
  - Build con `eclipse-temurin:25-jdk`: `dependency:go-offline` en una capa aparte, para que Docker reuse las dependencias mientras no cambie el `pom.xml`; `package -DskipTests` (los tests los corre el CI y adentro de Docker no hay Docker para Testcontainers); `java -Djarmode=tools -jar application.jar extract --layers`.
  - Imagen final `eclipse-temurin:25-jre` con las 4 capas (dependencias 71 MB, app ~300 KB), usuario `portfolix` (uid 10001, nunca root), `JDK_JAVA_OPTIONS=-XX:MaxRAMPercentage=75` y `java -jar application.jar`. Pesa ~184 MB comprimida.
- Probada en local con 512 MB y sin perfil: arranca en ~13 s, corre las migraciones sobre una base vacía y el flujo registro → mail → login → compra → dashboard funciona con precios reales. Usa ~360 MiB (70%). Mailpit no ofrece STARTTLS: para mandarle mails desde la imagen hay que pasarle `--spring.mail.properties.mail.smtp.starttls.enable=false --spring.mail.properties.mail.smtp.starttls.required=false`.
- **CI**: job **Imagen Docker**. Construye la imagen, la arranca con la configuración de producción contra un Postgres de servicio (`--network host`, 512 MB) y espera hasta 2 minutos a que `/actuator/health` diga UP. Siempre muestra los logs de la app.

**Fase 11A (front: base y auth) — completada (04/10/2026).** Lo del front está en `portfolix-frontend/CLAUDE.md`. Del lado del backend:
- **Stack del front** (decisiones de la fase 11): React 19 + Vite 8 + TypeScript, Redux Toolkit (solo lo compartido, con thunks), React Router, React Hook Form y CSS Modules con las variables de Figma. Sin librería de estilos ni de componentes, sin TanStack Query, sin generador de tipos (los DTOs se copian a mano en `src/api/types.ts`) y sin tests unitarios. CI propio: `.github/workflows/frontend-ci.yml` (lint + build).
- **Cambio en la API:** el registro con un mail que ya tiene cuenta ahora asocia el error al campo `email` (`fieldErrors`), así el front lo muestra debajo del campo como pide el diseño. El mensaje no cambió.
- Dependabot vigila también el `package-lock.json` del front (npm, solo seguridad).

**Fase 11B (front: dashboard, detalle de activo, portafolios y registrar transacción) — completada (04/10/2026).** Detalles en `portfolix-frontend/CLAUDE.md`. Decisiones: gráficos en SVG a mano (sin librería) y "registrar transacción" adelantado desde la 11C. Las preferencias de moneda y "ocultar montos" se guardan con `PATCH /me/preferences` desde la cabecera del dashboard. Sin cambios en el backend. Pendiente: adaptar el front a celulares (el diseño es solo de escritorio).

**Fase 11C (front: historial, editar y borrar) — completada (04/10/2026).** Detalles en `portfolix-frontend/CLAUDE.md`. Sin cambios en el backend.

**Fase 11D (front: cuenta y ajustes) — completada (04/10/2026).** Detalles en `portfolix-frontend/CLAUDE.md`. Sin cambios en el backend.

**Fase 11E (front: tests end-to-end con Playwright) — completada (04/10/2026).** Detalles en `portfolix-frontend/CLAUDE.md`. Sin cambios en el backend.

**Fase 12 (deploy gratis en Render) — en curso (09/10/2026).** El plan original era Fly.io con dominio propio (ver más abajo); Fly sacó su capa gratis en 2024 y un Postgres administrado ahí arranca en ~US$38/mes, así que se cambió a una pila sin costo: **Render** (un solo Web Service gratis, front y API desde el mismo origen), **Neon** (Postgres gratis, no expira; el de Render gratis se borra a los 30 días) y **Brevo** para los mails, pero por su **API HTTP en vez de SMTP** (Render bloquea los puertos SMTP en el plan gratis desde 2025). **Sin dominio propio**: alcanza con el subdominio gratis de Render, porque front y API comparten el mismo origen (no dos subdominios `app.`/`api.` como en el plan de Fly), así que la cookie `SameSite=Strict` viaja sin truco.

Lo que hizo el código:
- **`Dockerfile` y `.dockerignore` en la raíz del repo** (no en `portfolix-backend/`, porque necesita ver los dos directorios a la vez): compila el front **sin `VITE_API_URL`** (así `client.ts` llama a rutas relativas, mismo origen) y copia `dist/` dentro de `portfolix-backend/src/main/resources/static/` antes de compilar el backend, así el front queda empaquetado en el jar. `portfolix-backend/src/main/resources/static/` está en `.gitignore`: solo lo llena este Dockerfile, nunca se commitea.
- **`config/SpaWebConfig`**: sirve ese front con el fallback típico de SPA (recargar `/transactions` no da 404: cualquier ruta que no sea un archivo real ni empiece con `/api` devuelve `index.html`) y cachea `/assets/` (tienen hash en el nombre) por un año. En dev y en los tests no hay nada en `static/`, así que esto no hace nada (el front sigue sirviéndose con `npm run dev`).
- **`SecurityConfig`**: `.anyRequest().authenticated()` pasó a `.requestMatchers("/api/**").authenticated()` + `.anyRequest().permitAll()` — sin este cambio, la página ni cargaría sin sesión (exigía JWT hasta para el `index.html`). Se agregó el header `Referrer-Policy` (lo necesita el botón de Google; antes lo ponía el `nginx` del plan de Fly, acá no hay).
- **`common/mail`**: la lógica de SMTP de siempre quedó en `SmtpMailTransport` (interfaz nueva `MailTransport`, sin cambiar su comportamiento) y se agregó `BrevoMailTransport`, que manda por `POST api.brevo.com/v3/smtp/email`. Se elige con `portfolix.mail.provider` (`MAIL_PROVIDER`), default `smtp` (no cambia nada en dev ni en los tests). Variable nueva: `BREVO_API_KEY` (solo hace falta con `MAIL_PROVIDER=brevo-api`).
- Probado en local: `docker build` de la imagen combinada (compila bien) y el contenedor corriendo contra el Postgres de `compose.yaml` — `/` sirve el front real (con sus `<script>`/`<link>` de verdad), `/transactions` cae a `index.html`, `/assets/*.js` se sirve con `Cache-Control: max-age=31536000`, y `/api/v1/**` sigue protegido (401 sin token; 404 de verdad, no el front, si está logueado y la ruta no existe).
- Los archivos del plan anterior (Fly.io: `portfolix-backend/fly.toml`, y en `portfolix-frontend/`: `Dockerfile`, `nginx.conf`, `.dockerignore`, `fly.toml`) siguen en el repo sin tocar, por si más adelante compran un dominio propio y prefieren esa opción (dos apps separadas, mejor aislamiento).

Lo que falta, en orden (lo hace el usuario):
1. ~~Cuentas de Render, Neon y Brevo~~ — hecho (09/10/2026).
2. ~~Proyecto en Neon~~ — hecho: la cadena de conexión **directa** (sin pooler) queda guardada para separarla en `DB_HOST`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` al configurar Render.
3. ~~Remitente verificado y API key en Brevo~~ — hecho.
4. ~~Web Service en Render~~ — hecho. El nombre corto `portfolix` ya estaba tomado: la URL real quedó `https://portfolix-xt5h.onrender.com` (ni bien se sepa, actualizar acá y en `FRONTEND_URL`).
5. ~~Variables de entorno en Render~~ — hecho: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` (de Neon, con la cadena de conexión **directa**, sin `-pooler`: ver más abajo por qué), `JWT_SECRET`, `MAIL_FROM`, `FRONTEND_URL`, `MAIL_PROVIDER=brevo-api`, `BREVO_API_KEY`. `MAIL_HOST` también quedó cargada (`smtp-relay.brevo.com`) aunque no se use con `brevo-api`: la autoconfiguración de mail de Spring la necesita igual para decidir si se activa (ver más abajo). `GOOGLE_CLIENT_ID` y `VITE_GOOGLE_CLIENT_ID` quedan pendientes para cuando agreguen esa URL como origen autorizado en Google Cloud (opcional: sin esto, todo funciona salvo el botón de Google).
6. ~~Deploy~~ — hecho (Render lo hace solo en cada push a `main`, como el CI).
7. ~~Smoke test~~ — hecho: registro, mail por Brevo y verificación, probados con una cuenta descartable.
8. El plan de Fly con dominio propio (pasos 1-8 de antes, incluido comprar el dominio, SPF/DKIM de Brevo por dominio y `fly deploy`) queda como alternativa para más adelante, no se hace ahora.

**Problemas del primer deploy, y cómo se resolvieron:**
- **`FRONTEND_URL` con el nombre corto** (`portfolix.onrender.com`, que no es de esta app): los links de los mails apuntaban a un sitio ajeno, que a su vez dormido o inexistente hacía que la pantalla "despertando" de Render reintentara para siempre. Se corrigió a la URL real.
- **`MAIL_HOST` sin valor**: aunque no se usa con `MAIL_PROVIDER=brevo-api`, la autoconfiguración de mail de Spring Boot igual necesita resolver `spring.mail.host` para decidir si arma el bean — con el placeholder `${MAIL_HOST}` sin valor, la app ni arrancaba. Se solucionó cargando la variable igual (con el host real de Brevo, sin uso).
- **Arranque lento con poca CPU**: con la CPU limitada del plan gratis, crear los ~200 beans de la app de entrada tardaba más de 6 minutos (medido localmente con `docker run --cpus=0.1`), más de lo que Render espera para ver el puerto abierto. Se activó `spring.main.lazy-initialization=true` **solo en producción** (`application-dev.yml` lo desactiva, así que en desarrollo y en los tests no cambia nada): los beans se crean de a uno, cuando se piden, no todos al arrancar.
- **Binance bloqueaba a Render** (451, por la región EE.UU.): se reemplazó por **CoinGecko** para precio e historial de cripto (ver fase 7A y 7B). Mismo origen del problema que si en algún momento se vuelve al plan de Fly con otra región: CoinGecko no tiene esa restricción, así que no hace falta revertir esto.

Una sola instancia: el rate limit y el bloqueo de login viven en memoria. Con el plan gratis, el servicio de Render se duerme a los 15 min sin visitas (tarda ~1 min en responder la primera vez) y el cómputo de Neon se suspende solo (se reactiva con la próxima consulta, sin intervención).

## Entorno de desarrollo (Windows)
- El `JAVA_HOME` del sistema apunta a JDK 21, pero el proyecto usa **JDK 25**. Antes de `./mvnw` en Git Bash: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"` (en el IDE, elegir el JDK 25).
- **Docker Desktop tiene que estar corriendo**: los tests usan Testcontainers y la app levanta Postgres con `compose.yaml`. Si después de reiniciar la PC fallan todos los tests de integración, es eso.
- Tests: `./mvnw test` (~360 tests, un par de minutos; no salen a internet ni mandan mails). App: `./mvnw spring-boot:run` (perfil `dev`, precios reales; levanta Postgres y Mailpit). Swagger: http://localhost:8080/swagger-ui.html. Mails: http://localhost:8025.
- Probando a mano: en dev el rate limit está prendido (ej.: 10 logins por minuto desde la misma máquina); se apaga con la variable `PORTFOLIX_RATELIMIT_ENABLED=false`. El primer mail después de arrancar tarda alrededor de un segundo en llegar a Mailpit.
- Hay un usuario de prueba verificado en la base de dev. Su contraseña no va en el repo: si hace falta, se resetea con "Olvidé mi contraseña" y el link se lee en Mailpit. Si se cargan datos de prueba a mano, borrarlos al terminar (y vaciar Mailpit).
- Si la app corre en segundo plano y se corta el `mvnw`, la JVM puede quedar viva con el puerto 8080 tomado: cerrarla por PID y hacer `docker compose stop`.
- El repo está en git: la raíz es `Portfolix/` y la rama principal, `main`. Es **público** en GitHub.
- **CI**: GitHub Actions corre `./mvnw verify` y arranca la imagen Docker en cada push a `main` (ver fases 10A y 10B); el estado se ve en el badge del README y en la pestaña Actions.
- Desde el IDE, la app necesita `SPRING_PROFILES_ACTIVE=dev` en la configuración de ejecución (no hay perfil por defecto). Imagen de producción: `docker build -t portfolix-backend .` en `portfolix-backend/` (variables en el README).

## Diseño de Figma
Links: [diseño](https://www.figma.com/design/6koBSUX2RXWaLGdDdgbbSV/Portfolix?node-id=0-1) y [prototipo en Figma Make](https://www.figma.com/make/IXHrPRNMyxla9dBC8eiX62/Dashboard-Financiero-Prototipo) (falta el tema claro). El diseño tiene 24 pantallas y la API las cubre todas (revisado el 03/10/2026). Lo único sin diseño es editar y borrar transacciones: el front tiene que sumarlo.

Lo relevante de las pantallas de las fases 8 y 9:
- **Login fallido**: "Correo o contraseña incorrectos. Te quedan 2 intentos antes de que bloqueemos el acceso por 15 minutos." → bloqueo temporal por intentos fallidos, informando los intentos que quedan. **Decisión de la fase 8: el bloqueo es de 5 minutos** (el texto del front se corrige). Login y registro tienen "Continuar con Google" y "Continuar con Apple" (Apple quedó afuera: el front oculta ese botón).
- **Actualizar contraseña** (modal): contraseña actual, nueva y confirmación (el indicador de fuerza es del front), link "¿Olvidaste tu contraseña actual?", aviso "Al actualizarla cerramos la sesión en tus otros dispositivos".
- **Cambiar correo** (modal): nuevo correo + contraseña actual ("Para confirmar que sos vos"); "Te enviaremos un enlace al nuevo correo. El cambio se aplica cuando lo confirmes; hasta entonces seguís entrando con el actual."
- **Eliminar cuenta** (modal): resumen "Se van a eliminar de forma definitiva: N portafolios y M activos · K transacciones registradas · tus preferencias y tu acceso con <mail>", botón "Descargar CSV" del historial, escribir ELIMINAR (lo valida el front) + contraseña.
- **Ajustes de la aplicación**: tema claro/oscuro y separador decimal (coma "1.234,56" o punto "1,234.56"). En la cabecera del dashboard: selector ARS/USD y ojo para ocultar montos (las preferencias del roadmap).
- **Menú de un portafolio**: Renombrar, Duplicar como nuevo, Eliminar.

## Cosas a tener en cuenta
- **No hay perfil por defecto** (fase 10B): sin perfil, la app usa la configuración de producción y no arranca sin sus variables. El perfil `dev` trae valores de desarrollo (entre ellos una clave JWT commiteada) y nunca tiene que activarse en un servidor. Lo activan `./mvnw spring-boot:run` y los tests; desde el IDE, `SPRING_PROFILES_ACTIVE=dev`. Una propiedad nueva que dependa de una variable de entorno necesita su valor de desarrollo en `application-dev.yml`.
- **Dependabot no puede arreglar las versiones que fija Spring Boot** (las del pom padre, como Jackson): solo sabe subir el parent, y si todavía no salió un Spring Boot con el arreglo, la actualización de seguridad falla (aparece en Actions como una corrida de Dependabot fallida). En ese caso se pisa la versión con la propiedad del BOM en el `pom.xml` (ej.: `jackson-bom.version`) y se deja un comentario para sacarla después. Hoy: Jackson 3.1.7 y 2.21.7 (03/10/2026; Spring Boot 4.1.1 trae 3.1.5 y 2.21.5) y Tomcat 11.0.26 (04/10/2026; trae 11.0.24).
- **Jackson 3 (Boot 4) rechaza el JSON si falta un campo primitivo** (`boolean`, `int`...). En los DTOs de entrada usar wrappers (`Boolean`, `Integer`) para campos opcionales.
- `@Transactional(noRollbackFor = ...)` cuando un service guarda algo y después lanza una excepción que no debe deshacerlo (ej.: revocar sesiones al detectar reuso de refresh token).
- En Git Bash de Windows, `curl -d` con tildes manda el texto mal codificado: para probar con acentos usar `--data-binary @archivo.json` guardado en UTF-8.
- Los mails se mandan después del commit: un mail pedido dentro de una transacción que después falla no sale. En los tests llegan un instante después de la respuesta HTTP: leerlos siempre con `mailbox.awaitMail(...)`, nunca directo.
- Todo endpoint público nuevo de auth lleva `@RateLimited` con la política que corresponda. Todo endpoint que reciba una contraseña sin sesión tiene que pasar por `CredentialsChecker`, así comparte el bloqueo por intentos.
- El bloqueo por intentos vive en memoria y el contexto de Spring se comparte entre los tests de integración: usar siempre mails únicos (`uniqueEmail()`), para que los errores de un test no bloqueen a otro.

## Decisiones de modelo (tomadas a partir del diseño de Figma)
- Usuario: `full_name` (un solo campo), mail guardado en minúsculas y único, `terms_accepted_at` (el registro exige aceptar términos).
- El login es **solo por mail** (el texto "Correo electrónico / usuario" del diseño se corrige en el front).
- Portafolio: solo `name`, **único por usuario sin distinguir mayúsculas**.
- Transacción: tipo, activo, cantidad, precio unitario, `trade_date` (solo día, no puede ser futura: se valida en el service), notas opcionales. Sin comisión. El monto total se calcula, no se guarda.
- La moneda de la transacción **se copia de la moneda del activo** (el formulario no la pide): cripto en USD, acciones y CEDEARs en ARS.
- Activo: `symbol`, `name`, `type`, `currency`, `active`. El ícono lo genera el front con la inicial.
- Pendiente para fases futuras: "Recuérdame" define la duración del refresh token (fase 3); bloqueo de 5 min tras 5 intentos fallidos de login (hecho en la fase 8B); preferencias incluyen tema claro/oscuro (fase 9).
