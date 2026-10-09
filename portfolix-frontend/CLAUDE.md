# Portfolix — Front

El front en React de Portfolix. El contexto del proyecto (qué es, roadmap, estado de cada fase, cómo quiere trabajar
el usuario, decisiones del backend) está en `../portfolix-backend/CLAUDE.md`: leerlo primero. Este archivo cubre
solo lo propio del front.

## Stack (decisiones de la fase 11)
- React 19 + Vite 8 + TypeScript 6 (`strict`, `erasableSyntaxOnly`: sin `enum` ni propiedades en el constructor). Lint con Oxlint (el de la plantilla de Vite).
- **Redux Toolkit** (+ react-redux), **React Router 8** y **React Hook Form**. Nada más en runtime: el usuario eligió "solo React + Vite + Redux Toolkit".
- **Sin** librería de estilos ni de componentes (ni Tailwind ni Radix), **sin** TanStack Query (datos con `fetch` + `useEffect`), **sin** generador de tipos y **sin** tests unitarios (ni Vitest). Los end-to-end quedan para la 11D.
- Estilos con **CSS Modules** (vienen con Vite): un `.module.css` por componente.
- **Gráficos en SVG a mano** (decisión de la 11B): la dona (`charts/Donut`) y la línea (`charts/LineChart`, para los minigráficos y las tendencias). Sin librería.

## Estructura
```
src/
├── api/          client.ts (todos los pedidos), types.ts (DTOs a mano), formErrors.ts
├── app/          store.ts, hooks.ts (useAppDispatch / useAppSelector)
├── components/   UI compartida: TextField, Button, Alert, Checkbox, Divider, Dialog, Menu, SegmentedControl,
│                 AssetBadges (ícono y tipo), AuthCard, Logo, Splash, icons, charts/ (Donut, LineChart)
├── features/     por funcionalidad: auth/, shell/ (AppLayout), account/ (perfil, ajustes y diálogos de la cuenta), preferences/ (moneda, montos, tema), portfolios/ (menú lateral, modal de
│                 eliminar), dashboard/ (página, resumen, tabla), assets/ (catálogo, panel de detalle),
│                 transactions/ (historial, modal de registrar y editar, borrar, buscador de activos)
├── format/       format.ts (montos, precios, %, cantidades, fechas, números escritos), useFormat, assetTypes
├── hooks/        useCountdown, useAutoRefresh, useApiResource (con `reload`)
├── styles/       tokens.css (variables del diseño), global.css
├── router.tsx    rutas
└── main.tsx      store, restaurar sesión, router
```

## Convenciones (respetarlas siempre)
- **Todos los pedidos pasan por `api()` de `src/api/client.ts`**, nunca `fetch` directo: pone el access token, renueva la sesión ante un 401 y convierte los errores en `ApiError` (o `ApiErrorData`, el mismo objeto plano para Redux). Un archivo (el CSV del historial) pasa por `download()`: un `<a href>` no sirve porque el endpoint pide el access token.
- **Tipos de los DTOs a mano en `src/api/types.ts`**, con el nombre del record de Java. Si cambia un DTO en el backend, actualizarlo ahí (TypeScript no se entera solo).
- **Redux solo para lo compartido** (sesión, preferencias, portafolios, dashboard y catálogo de activos), con `createAsyncThunk` que llama a `api()`; las pantallas lo despachan en un `useEffect`. Lo que usa una sola pantalla (un formulario, el panel de detalle de un activo) queda en su estado local, con **`useApiResource(path)`** (`fetch` + `useEffect`, ignora respuestas viejas; `reload()` vuelve a pedir el mismo path sin borrar lo que se ve).
- Thunks: el error se **devuelve** con `return rejectWithValue(toApiErrorData(e))` (no se lanza), así la acción termina en `rejected` con el error como dato. Todo slice vuelve a su estado inicial con `logout.fulfilled` y `sessionExpired` (otra cuenta no ve datos de la anterior).
- **Un selector nunca devuelve un objeto o array nuevo** (`state.x.filter(…)` adentro de `useAppSelector`): React Redux creería que cambió y volvería a dibujar en cada acción. Filtrar afuera, con `useMemo`.
- **Números siempre con `useFormat()`** (o las funciones de `format.ts`), nunca `toFixed` ni `toLocaleString`: respeta el separador decimal del usuario y "ocultar montos". Reglas: pesos sin centavos desde $ 1.000 y dólares desde u$s 10.000 (el cero, "$ 0"); precios menores a 1 con hasta 6 decimales; porcentajes con 1 decimal; cantidades de cripto con 4 a 8 decimales. Con "ocultar montos" se tapan montos y cantidades; precios y porcentajes se ven.
- Lo que escribe el usuario en un campo de número se lee con `parseDecimalInput` (acepta "0,05", "0.05" y "1.234,5" sin confundirlas) y viaja a la API como string.
- Modales con `Dialog` (el `<dialog>` nativo: foco, Esc y fondo resueltos) y menús con `Menu` (el atributo `popover`: se cierra solo al hacer clic afuera o con Esc).
- **La contraseña y el token de Google nunca pasan por Redux**: el argumento de un thunk queda en sus acciones (y en Redux DevTools). Por eso `login` y `loginWithGoogle` son funciones de `authApi`, y Redux solo recibe el usuario (`signedIn`).
- **Errores de formulario**: `applyFieldErrors(error, setError, campos)` pone los `fieldErrors` del backend en sus campos; si no corresponden a ninguno, la pantalla muestra un `Alert` con el mensaje. Las validaciones del front copian las del backend (mismos mensajes).
- React Hook Form: `useWatch` en vez de `watch` (Oxlint avisa que `watch` rompe la memoización).
- **Colores solo con las variables de `tokens.css`** (`var(--surface-1)`…), nunca un hex en un componente: hay dos paletas en `tokens.css`: la clara (`:root`, el default) y la oscura (`:root[data-theme='dark']`, la de Figma). Las sombras y el fondo de los modales también son variables (`--shadow-lg`, `--shadow-xl`, `--overlay`, `--row-hover`). Única excepción: la paleta de los íconos de activos (`AssetBadges`), igual en los dos temas.
- Un archivo de componentes exporta solo componentes (si no, la recarga en caliente de Vite deja de andar con ese archivo; Oxlint lo marca): las constantes y funciones van aparte (ej.: `format/assetTypes.ts`).
- Textos en español rioplatense (voseo); código en inglés.
- Accesibilidad: cada input con su `label`, errores con `aria-invalid` + `aria-describedby`, avisos de error con `role="alert"`, foco visible con el teclado. Para modales, el `<dialog>` nativo.

## Sesión (lo delicado)
- **Access token solo en memoria** (`client.ts`), nunca en `localStorage`: un script inyectado podría leerlo. Al recargar se pierde y `restoreSession` (en `main.tsx`) pide otro con la cookie del refresh token. La cookie es `HttpOnly`: JavaScript no la ve.
- **Una sola renovación a la vez.** El backend rota el refresh token y, si recibe uno ya usado, cierra todas las sesiones. Por eso los 401 simultáneos comparten la misma renovación (`pendingRefresh`), y entre pestañas la ordena Web Locks (`navigator.locks`). Probado: con el token vencido, 3 pedidos a la vez → 1 renovación → 3 reintentos bien.
- Si la renovación falla, `sessionExpired`: las rutas privadas mandan al login y el login avisa "Tu sesión venció".
- **En desarrollo, Vite reenvía `/api` a `localhost:8080`** (`vite.config.ts`): el navegador ve un solo origen, así que la cookie (`SameSite=Strict`, `Path=/api/v1/auth`) funciona sin CORS.
- React (en modo estricto, en dev) monta todo dos veces: `restoreSession` tiene un `condition` para salir una vez, y las páginas de links de mails (`useTokenLink`) se apoyan en que el backend es idempotente.

## Rutas
- Públicas, solo sin sesión (`PublicOnly`): `/login`, `/register`, `/forgot-password`.
- Públicas siempre: `/check-email` (recibe el mail por `state`; si no, lo pide), y las de los links de los mails, que **tienen que coincidir con las del backend**: `/verify-email`, `/reset-password`, `/confirm-email-change` (`?token=…`).
- Con sesión (`RequireAuth` → `AppLayout`): `/` (dashboard de todos los portafolios), `/portfolios/:portfolioId` (dashboard de uno) y `/transactions` (el historial). El detalle de un activo es `?asset=BTC` sobre el dashboard: "atrás" lo cierra y el link se puede compartir. Los filtros y la página del historial van en la URL con los nombres de la API (`?portfolioId=&assetSymbol=&type=&from=&to=&page=`): se recarga y se comparte igual.

## Desarrollo
- `npm install`, después `npm run dev` (http://localhost:5173; necesita el backend levantado en el 8080). `npm run lint`, `npm run build` (incluye el chequeo de tipos).
- `.env.development`: `VITE_GOOGLE_CLIENT_ID` (el mismo Client ID de desarrollo del backend; no es secreto). Sin él no se muestra el botón de Google. En localhost, Google exige el encabezado `Referrer-Policy: no-referrer-when-downgrade` (ya está en `vite.config.ts`).
- Producción (fase 12): `VITE_API_URL` (la API del dominio, con `/api/v1`) y `VITE_GOOGLE_CLIENT_ID` se fijan en el build. Sin `VITE_API_URL` queda `/api/v1` (el proxy de Vite). El `Dockerfile`, `nginx.conf` y `fly.toml` de este directorio lo despliegan: ver el CLAUDE del backend, fase 12.
- Tests end-to-end: `npm run test:e2e` (con Docker corriendo y `JAVA_HOME` con JDK 25; ver la fase 11E).
- CI: `.github/workflows/frontend-ci.yml` (lint con `--deny-warnings` + build), solo si cambia `portfolix-frontend/`.
- En el navegador integrado, con la pestaña oculta, no llegan las capturas ni los eventos `close` de `<dialog>` (ni en un `<dialog>` mínimo): probar leyendo el DOM (`get_page_text`) y disparando los clics por JS.
- Para probar a mano: el navegador integrado contra `npm run dev`. Los mails se leen en Mailpit (http://localhost:8025, o su API `/api/v1/search?query=to:…`). Borrar los usuarios de prueba al terminar.

## Diseño
- Figma (links en el CLAUDE.md del backend). Las 10 variables del diseño están en `tokens.css` con el mismo nombre (`bg/base` → `--bg-base`). Derivadas propias: `--accent-strong` (#1f6feb, fondo de los botones principales: con texto blanco se lee mejor que `--accent`), `--status-warning` y los fondos suaves.
- Tipografías del sistema (sin fuentes externas): sans para textos, mono para números.
- **El conector de Figma del plan Starter tiene un límite de usos** y se agotó el 04/10/2026. La 11A se armó con el árbol del diseño (estructura, textos, medidas), las variables y una captura del dashboard. Si el límite vuelve a cortar, pedirle al usuario capturas de las pantallas.
- En el árbol de Figma los textos largos vienen cortados a 30 caracteres; los de auth y los de la 11B (los pasos del estado vacío, las descripciones del modal de eliminar, el ejemplo de las notas) se completaron a mano (ej.: "Tu cartera de inversiones en un solo lugar"). Los textos se pasaron todos a voseo (el diseño mezclaba "Regístrate" con "tenés"). El paso 2 de "Revisá tu correo" dice "Confirmar mi correo", que es el botón real del mail (el diseño decía "Activar mi cuenta"). El login dice solo "Correo electrónico" (el login es solo por mail) y el botón de Apple no se muestra.

## Estado
**Fase 11A (base y auth) — completada (04/10/2026).** Login (con intentos restantes y bloqueo con cuenta regresiva, ligado al mail bloqueado), crear cuenta (fuerza de contraseña, términos, mail ya registrado en su campo), "Revisá tu correo" (reenviar con cuenta regresiva de 60 s y corregir el mail), confirmación del mail, olvidé mi contraseña, nueva contraseña (requisitos en vivo; link usado o vencido), confirmación de cambio de mail, botón de Google y esqueleto de la app logueada con cerrar sesión. Todo probado en el navegador contra el backend real, salvo Google (necesita una cuenta real: lo prueba el usuario).

**Fase 11B (dashboard, detalle de activo, portafolios y registrar transacción) — completada (04/10/2026).**
- **Preferencias** (`preferencesSlice`): se cargan al entrar. El selector ARS/USD y el ojo de "ocultar montos" de la cabecera las cambian al instante y las guardan con `PATCH /me/preferences` (si falla, vuelven atrás). El dashboard espera a tenerlas antes de pedir datos; si no, pediría en ARS y enseguida en la moneda guardada.
- **Dashboard** (`dashboardSlice`, guardado por alcance y moneda: volver a uno ya visto es instantáneo): cabecera con el valor actual (y en la otra moneda, con el dólar que trae el resumen), ganancia total, capital invertido, cantidad de activos y la dona de distribución; tabla con filtros por tipo y orden (en el front), minigráfico de 7 días, P&L y capital actual. Se vuelve a pedir cada 5 minutos con la pestaña visible (`useAutoRefresh`) y a los 15 s si faltan minigráficos (el backend los carga en segundo plano; hasta 3 veces). Se invalida al registrar una operación y al duplicar o eliminar un portafolio.
- **Detalle de activo**: panel lateral de 440 px con el precio actual (en la moneda elegida), las tendencias de 7 días y 24 horas (en la moneda del activo) y el historial de operaciones de ese activo (del portafolio que se mira, o de todos).
- **Portafolios en el menú lateral**: crear y renombrar en el lugar (Enter guarda, Esc cancela; mientras guarda, el campo usa `readOnly` y no `disabled`, para no perder el foco) y menú "⋯" con renombrar, duplicar (lleva a la copia) y eliminar (modal que cuenta activos y transacciones; mover a otro portafolio o borrarlas).
- **Registrar transacción** (se adelantó de la 11C, decisión de la 11B): compra o venta, buscador de activos (combobox accesible, sin tildes ni mayúsculas), precio precargado con la cotización actual (solo si la fecha es hoy; si se cambia la fecha a otro día, un precio que vino de la cotización se vacía, y uno que escribió el usuario se mantiene), "Tenés X" en las ventas, total que se puede escribir (si se escribe, la cantidad sale de dividirlo por el precio; si se escribe la cantidad o cambia el precio, el total se recalcula, y gana lo último que se editó), fecha de hoy en Argentina y el portafolio que se está mirando. Sin portafolios, ofrece crear uno.
- Todo probado en el navegador contra el backend real, con una cuenta de prueba con datos y otra vacía.
- **Celular (09/10/2026, sin diseño: lo propuse yo).** Debajo de **860px** el menú lateral fijo (`AppLayout`) pasa a ser un cajón: una barra superior fija con el logo y un botón de hamburguesa (`MenuIcon`/`CloseIcon`) lo abre superpuesto al contenido, con fondo semitransparente que lo cierra al tocarlo, con Escape o al navegar a otra pantalla (comparando `location.pathname` durante el render, no en un efecto, para no disparar el aviso de Oxlint de `set-state-in-effect`). `RegisterTransactionDialog`: las filas Cantidad/Precio y Fecha/Portafolio pasan a una columna debajo de 480px. El resto (diálogos, tablas, panel de detalle de activo, `AuthCard`) ya se adaptaba bien. Probado en 375×812 y repasado 768–1320px (no rompe los breakpoints existentes del dashboard ni del historial).

**Fase 11C (historial de transacciones, editar y borrar) — completada (04/10/2026).** Sin cambios en el backend.
- **Historial** (`/transactions`): tarjetas de totales (`summary.converted`, en la moneda elegida; no cambian con el filtro de tipo, decisión del backend), filtros (tipo en chips, portafolio, activo agrupado por tipo, desde y hasta; el error de "desde posterior a hasta" viene del backend en el campo) y tabla paginada de 20 por página. "Exportar CSV" baja todo el historial, sin filtros. Al cambiar filtro o página se ven los datos anteriores atenuados (si no, la tabla desaparecía en cada clic).
- **Editar**: `RegisterTransactionDialog` con `transaction` (`PUT`). Al editar no se muestra "Tenés X": la tenencia de hoy incluye la propia operación. Si el activo está dado de baja, se agrega al selector solo en esa edición.
- **Borrar**: `DeleteTransactionDialog` con el resumen de la operación. Si era la última fila de una página que no es la primera, vuelve a la anterior.
- **Errores**: el 400 (una venta que queda sin cubrir) y el 409 o 404 (la operación cambió mientras tanto) se muestran dentro del diálogo. Al cerrar el de editar se vuelve a pedir la página.
- `DisplayControls` (moneda y ocultar montos) está en `features/preferences/` y lo usan el dashboard y el historial. `PlaceholderPage` se borró (ya no tenía uso).
- Probado en el navegador contra el backend real con cuentas nuevas: filtros, paginado (y el caso de borrar la única fila de la página 2), edición válida y edición rechazada, borrado, CSV (200), USD, ocultar montos, estado vacío y error de fechas.

**Fase 11D (cuenta y ajustes) — completada (04/10/2026).** Sin cambios en el backend (salvo los de la 11C ya hechos).
- **Pie del menú lateral** (`AccountControls`): el perfil (avatar, nombre y mail) abre el menú de la cuenta: cambiar correo, actualizar contraseña, cerrar sesión y eliminar cuenta. El engranaje abre los ajustes. El menú se abre hacia arriba si no entra abajo (`Menu`).
- **Ajustes de la aplicación**: es un diálogo, no un menú popover como en Figma (tiene dos secciones). Modo oscuro (switch) y separador decimal con vista previa. Se aplican al instante y se guardan con `PATCH /me/preferences`; si el backend los rechaza, vuelven atrás y avisa.
- **Tema**: `ThemeSync` (en `main.tsx`) pone `data-theme` en `<html>` según la preferencia. Sin sesión, o antes de cargar las preferencias, queda el claro (el default del backend). El claro lo propuse yo: Figma solo tiene el oscuro.
- **Actualizar contraseña**: actual, nueva (con fuerza y requisitos) y confirmación. El backend cierra las otras sesiones. "¿Olvidaste tu contraseña actual?" manda el mismo mail de `forgot` sin salir de la sesión (sin esto, el link de login no serviría con sesión abierta).
- **Cambiar correo**: correo nuevo y contraseña actual. Después aparece "Revisá tu correo nuevo"; hasta confirmar se entra con el actual.
- **Eliminar cuenta**: resumen de `deletion-summary`, botón de CSV (`downloadTransactionsCsv`, compartido con el historial), escribir ELIMINAR y la contraseña. Al borrar, `logout` limpia el estado de este navegador.
- **Cuentas sin contraseña (Google)**: en los tres diálogos solo se avisa cómo crear una (decisión tomada en la 11D: el aviso no tiene botón). Se detecta con `user.hasPassword`.
- `plural` y `formatWithCents` salieron a `format/format.ts`.
- Probado en el navegador contra el backend real: validaciones y errores del backend en cada campo, cambio de contraseña, cambio de correo (llega el mail al correo nuevo), modo oscuro y separador guardados en la base y recargados, aviso sin contraseña (cuenta simulada en la base de dev), eliminar con CSV y confirmación, y el 401 al entrar con la cuenta borrada.
- **No verificado a ojo**: como las capturas no funcionan en el navegador integrado, el tema claro se revisó solo con estilos calculados (colores de fondo). Hace falta mirarlo en un navegador normal.
- **Limitación conocida**: con el tema oscuro, al recargar se ve un destello claro hasta que llegan las preferencias. Se arregla guardando el tema en `localStorage` como caché (no se hizo).

**Fase 11E (tests end-to-end con Playwright) — completada (04/10/2026).** Sin cambios en el backend.
- **Dónde**: `e2e/` (los tests), `playwright.config.ts`, `tsconfig.e2e.json` (lo tipa `npm run build`). Script: `npm run test:e2e`.
- **Cómo corre**: Playwright levanta el backend (con `PORTFOLIX_RATELIMIT_ENABLED=false`) y el front, o reutiliza los que ya estén corriendo (salvo en CI). Hace falta Docker corriendo (Postgres y Mailpit) y `JAVA_HOME` con JDK 25. Si reutiliza un backend con el rate limit prendido, los tests fallan con 429.
- **Qué prueban** (cinco tests, un worker porque comparten Mailpit y el bloqueo por intentos): registro, verificación por el link del mail, login y dashboard vacío; crear un portafolio y cargar una compra con precio de otro día; una venta sin tenencia rechazada dentro del diálogo; editar y borrar una operación en el historial; cambiar la contraseña, entrar con la nueva y eliminar la cuenta.
- **Cuentas**: cada test crea cuentas descartables con un correo único (verificadas por la API cuando el test no prueba el registro) y las borra por la API al terminar. Al final de la corrida se vacía Mailpit (`e2e/global-teardown.ts`).
- **Pendiente**: no corren en CI. Eso pide levantar Postgres y Mailpit en el workflow; se decide aparte.

**Próximo paso: fase 12 (deploy).** Antes hay que decidir hosting, Postgres administrado y proveedor de mails (ver la fase 12 del roadmap).
