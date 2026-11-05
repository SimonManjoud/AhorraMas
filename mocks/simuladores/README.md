# Simuladores de sistemas externos — Ahorra+ MVP

Un solo servicio Spring Boot (puerto **8090**) que reemplaza en el MVP a los sistemas externos
del modelo C4: **BCV**, **instituciones financieras (Open Banking + OAuth2)**, **suiche
interbancario**, **proveedor KYC**, **proveedor de IA (LLM)** y **pasarela de notificaciones**.
El núcleo (`backend/`) los consume solo a través de sus adaptadores de salida.

El contrato obligatorio está en [`docs/contrato-simuladores.md`](../../docs/contrato-simuladores.md).
Este proyecto lo implementa tal cual (rutas, campos JSON, códigos de estado y de error).

> Todo es ficticio: las instituciones (Banco Ávila 0901, etc.) no son bancos reales, ningún dato sale
> de la máquina y nada se persiste. Al reiniciar se pierde el estado mutable (saldos modificados,
> operaciones del suiche, tokens, notificaciones); los datos base se regeneran idénticos.

## Cómo ejecutarlo

Requisitos: Java 21. No hace falta tener Maven instalado (se incluye el wrapper).

```bash
cd mocks/simuladores
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
# o bien
./mvnw package && java -jar target/simuladores-0.1.0-SNAPSHOT.jar
```

Abrir la consola: <http://localhost:8090/>

Pruebas: `./mvnw test`

Configuración (`src/main/resources/application.yml`, se puede sobrescribir con `--clave=valor`):

| Propiedad | Por defecto | Uso |
|---|---|---|
| `server.port` | `8090` | Puerto HTTP |
| `simulador.suiche.retardo-timeout-ms` | `8000` | Retardo del escenario de timeout (`% 100 == 13`) |
| `simulador.cedulas-demo` | `V12345678,V87654321` | Cédulas cuyos datos bancarios se generan al arrancar |

Ejemplo para una demo más ágil: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--simulador.suiche.retardo-timeout-ms=3000`

## Estructura

Paquete base `ve.impulsatech.ahorramas.simuladores`, un paquete por sistema simulado
(controlador + servicio en memoria):

| Paquete | Sistema | Rutas |
|---|---|---|
| `bcv` | Banco Central: tasa oficial e inflación | `/bcv/**`, `/simulador/bcv/tasa-hoy` |
| `instituciones` | Bancos y plataformas cripto: OAuth2 + datos | `/ob/**`, `/simulador/ob/{codigo}/movimientos` |
| `suiche` | Suiche interbancario (pago de TDC) | `/suiche/**` |
| `kyc` | Proveedor de verificación de identidad | `/kyc/verificaciones` |
| `llm` | Proveedor de IA con guardia de PII | `/llm/v1/explicaciones` |
| `notificaciones` | Push, SMS y correo | `/notificaciones/**` |
| `consola` | Página web de la demo | `/`, `/consola/datos` |
| `comun` | Errores comunes, reloj (America/Caracas), utilidades de hash | — |

Convenciones: JSON camelCase; montos siempre `long` en céntimos (`...Cts`); tasas en
`valorBsX10000`; fechas `yyyy-MM-dd` y fecha-hora ISO-8601 con offset de Caracas; errores
`{"codigo": "TEXTO_MAYUS", "mensaje": "..."}`.

## Qué hace cada simulador

### 1. BCV
* `GET /bcv/tasas/hoy?moneda=USD|USDT` y `GET /bcv/tasas?moneda=USD&desde=..&hasta=..` (una tasa por día, ascendente).
* Serie determinista: **301,37 Bs/USD el 2025-12-31**, devaluación compuesta de 0,18 % por día calendario
  y ruido determinista por fecha de ±0,08 % (por eso la serie es casi monótona). Sábado y domingo repiten
  la tasa del viernes (y su `fechaHoraPublicacion`). USDT = misma tasa que USD.
* `GET /bcv/inflacion?anio=2026&mes=10` y `GET /bcv/inflacion?desde=2026-01`: entre 800 y 1 800 pb.
  Cada mes se publica el día 3 del mes siguiente; el mes en curso nunca está publicado (`404 INFLACION_NO_PUBLICADA`).

### 2. Instituciones (Open Banking)
* `GET /ob/instituciones`: catálogo fijo (0901 Banco Ávila, 0902 Banco Orinoco, 0903 Banco Catatumbo,
  0904 Banco Roraima, C001 CriptoAndes Exchange, C002 Billetera Tepuy).
* **OAuth2 authorization code**: `GET /ob/{codigo}/oauth/authorize?...` muestra una página HTML de
  "banca en línea" (rotulada **SIMULADOR**) con el alcance en lenguaje simple, la cédula (pre-llenada con
  `login_hint`), usuario/clave (no se validan ni se guardan) y botones **Autorizar** / **Rechazar**.
  El formulario hace `POST` a la misma ruta y responde `302` a `redirect_uri?code=..&state=..` o
  `redirect_uri?error=access_denied&state=..`.
* `POST /ob/{codigo}/oauth/token` (form) exige el header `X-Client-Cert-Fingerprint` (mTLS simulado,
  si falta `401 CERTIFICADO_REQUERIDO`); el código es de un solo uso (`400 CODIGO_INVALIDO`) y expira en 10 min.
* `POST /ob/{codigo}/oauth/revoke` (form `token=..`) → `200 {}`.
* Datos con `Authorization: Bearer ..` (`401 TOKEN_INVALIDO` si no existe, se revocó o es de otra institución):
  cuentas, saldo, movimientos (últimos 60 días: nómina los días 15 y último de cada mes + 6–12 gastos/mes) y
  tarjetas (solo 0902 y 0904 emiten TDC; cripto → `[]`).
* Datos deterministas por (institución, cédula): mismo titular, mismas cuentas, mismos movimientos.
  Cada banco da una cuenta en VES (corriente o ahorro) y ~50 % una de `divisas` en USD; cada plataforma
  cripto da una `billetera` en USDT.

### 3. Suiche
* Consulta de beneficiario, autorización, captura, reverso, consulta de estado y archivo de liquidación.
* **Idempotencia** por `referencia` en cada POST: repetir devuelve exactamente la misma respuesta
  (mismo `fechaHora`) sin repetir efectos ni el retardo.
* La **captura** debita la cuenta origen en el simulador de bancos (con un movimiento `pago_tdc`) y
  reduce `saldoTotalCts` de la TDC; el **reverso** lo deshace.
* `GET /suiche/liquidaciones/{fecha}`: operaciones capturadas/reversadas del día (zona Caracas).
  `hashArchivo` = SHA-256 (hex minúsculas) del **JSON canónico** del arreglo `registros`: claves en
  orden alfabético (`estado, fechaHoraOperacion, linea, montoCts, referencia, referenciaSuiche`),
  sin espacios, UTF-8.

### 4. KYC
`POST /kyc/verificaciones`: aprueba salvo los escenarios de la tabla; sin documento `cedula` o `selfie`
(con su `hashSha256`) → `400 DOCUMENTOS_INCOMPLETOS`.

### 5. IA (LLM)
`POST /llm/v1/explicaciones`: devuelve 4–5 oraciones en español simple, deterministas según perfil,
objetivo, horizonte, distribución y volatilidad, terminando con "Esto es una simulación informativa, no
una asesoría financiera." `modelo = sim-llm-1.0`.
**Guardia de seudonimización** → `422 PII_DETECTADA` si el cuerpo trae:
* una secuencia de 7 o más dígitos seguidos en cualquier valor o clave, salvo `idSeudonimo` en la raíz;
* un `@` en cualquier parte;
* una clave que contenga `cedula`, `nombre`, `correo` o `telefono` (sin distinguir mayúsculas ni acentos;
  p. ej. `primerNombre` también se rechaza).

### 6. Notificaciones
`POST /notificaciones/{push|sms|correo}` guarda la notificación en memoria y responde
`{"id","estatus":"enviada"}`. `GET /notificaciones?destino=..` devuelve las últimas 100 (la más
reciente primero; sin `destino`, de todos).

### 7. Consola (`GET /`)
Tasa del día, últimas notificaciones (los **OTP** resaltados), operaciones del suiche, cuentas generadas
con su saldo, y formularios para fijar la tasa e inyectar movimientos. Los datos se refrescan cada 5 s
sin recargar la página (no se pierde lo que se está escribiendo en los formularios).

## Tabla de escenarios deterministas

| Sistema | Disparador | Resultado |
|---|---|---|
| Suiche | `montoCts % 100 == 13` | **Timeout**: queda `autorizado` *antes* del retardo; la respuesta tarda `retardo-timeout-ms`. El núcleo debe consultar `GET /suiche/operaciones/{referencia}`. |
| Suiche | `montoCts % 100 == 51` | Autorización rechazada: `51` "Fondos insuficientes". |
| Suiche | `montoCts % 100 == 77` | Autoriza, pero la captura se rechaza (`05` "Operación no permitida por el emisor"); la operación sigue `autorizado` y el núcleo debe reversar. |
| Suiche | `montoCts % 100 == 98` | Captura bien, pero **no aparece** en el archivo de liquidación. |
| Suiche | `montoCts % 100 == 99` | Captura bien, pero el archivo de liquidación trae **+100 cts**. |
| Suiche | cuenta origen conocida con disponible < monto | `51` "Fondos insuficientes". |
| Suiche | `bancoOrigen`/`bancoEmisor` que no es un banco del catálogo | `91` "Institución no disponible en el suiche". |
| OAuth | institución cripto + alcance `debito:tdc` | `400 ALCANCE_NO_PERMITIDO`. |
| KYC | cédula terminada en `666` | `rechazado`, `coincideListaSanciones: true`. |
| KYC | cédula terminada en `000` | `rechazado`, `pruebaVidaSuperada: false`. |
| LLM | PII en el cuerpo | `422 PII_DETECTADA`. |

## Controles de demo

```bash
# Forzar la tasa de hoy (Bs 510,00 por USD)
curl -X POST localhost:8090/simulador/bcv/tasa-hoy -H 'Content-Type: application/json' \
     -d '{"valorBsX10000": 5100000}'
# Volver a la tasa generada (extra, no está en el contrato)
curl -X DELETE localhost:8090/simulador/bcv/tasa-hoy

# Inyectar un movimiento (ajusta el saldo) para disparar reglas de ahorro
curl -X POST localhost:8090/simulador/ob/0901/movimientos -H 'Content-Type: application/json' \
     -d '{"cedula":"V12345678","refCuenta":"0901-AH-f5832b","naturaleza":"credito","montoCts":500000,
          "descripcion":"PAGO NOMINA","clasificacion":"ingreso"}'
```

Ambos controles también están en la consola web.

## Decisiones ante ambigüedades del contrato

* **Cédula en OAuth**: los datos se generan por la cédula del formulario de autorización (pre-llenado con
  `login_hint`). Formato aceptado `V12345678`/`E12345678` (solo dígitos ⇒ se asume `V`); otro → `400 CEDULA_INVALIDA`.
* **Titular**: se elige de una lista fija por hash de la cédula (igual en todas las instituciones).
* **`client_id`** no se valida; `redirect_uri` es obligatorio (`400 SOLICITUD_INVALIDA`) y, si se envía en
  `/token`, debe coincidir con el del código. Alcances desconocidos → `400 ALCANCE_NO_PERMITIDO`; sin `scope`
  se asume `saldos:leer movimientos:leer`.
* **mTLS** (`X-Client-Cert-Fingerprint`) solo se exige en `/oauth/token` (es donde lo indica el contrato).
* **Alcances en los datos**: no se restringe qué endpoint permite cada alcance; basta un token válido de esa institución.
* **Movimientos**: orden ascendente por `fechaHora`; `desde` anterior a hoy−60 se recorta a 60 días.
  `refCuenta` en el control de inyección es opcional (vacío ⇒ primera cuenta).
* **TDC**: `fechaCorte` = último día 28; `fechaLimitePago` = corte + 15 días; `pagoMinimoCts` = 10 % del saldo y
  baja a medida que se paga.
* **Suiche con referencias desconocidas**: si `refCuentaOrigen`/`refTarjeta` no existen en el simulador (p. ej.
  tras reiniciarlo) la operación igual se procesa, solo que sin efecto sobre saldos. Para que existan desde
  el arranque, las cédulas de `simulador.cedulas-demo` se generan al iniciar.
* **Captura/reverso de una referencia inexistente** → `404 OPERACION_NO_EXISTE`. Una captura con un
  `codigoAutorizacion` distinto se rechaza (`05`) sin guardarse, para permitir reintentar con el correcto.
  Reverso de una operación `rechazado` → `rechazado` `05`.
* **`motivo`** es `null` en las respuestas aprobadas. `codigoAutorizacion` es `null` si la autorización se rechazó.
* **Liquidación**: una operación entra el día de su captura (o el día del reverso si nunca se capturó) con su
  estado actual (`capturado`/`reversado`). `montoTotalCts` = suma de los `montoCts` de los registros del archivo
  (incluidos los reversados y el +100 del escenario 99). Una fecha sin operaciones devuelve un archivo vacío.
* **`Operacion.fechaHora`** = momento del último cambio de estado. Si la referencia no existe:
  `200 {"referencia": "..", "estado": "no_existe"}` con el resto en `null`.
* **Notificaciones**: `destino` y `mensaje` son obligatorios; `tipo` es opcional, pero si viene debe estar en la lista del contrato.
