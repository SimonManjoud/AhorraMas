# Contrato de los simuladores (mocks) — Ahorra+ MVP

Los sistemas externos del C4 se reemplazan en el MVP por **un solo servicio Spring Boot**
ubicado en `mocks/simuladores` (puerto **8090**). El núcleo (`backend/`) solo los consume a
través de sus adaptadores de salida (capa anticorrupción / Hub de integraciones).

Convenciones (iguales que el núcleo):

* JSON camelCase. Montos **siempre enteros en céntimos** (`...Cts`, `long`). Nunca `double`.
* Tasas: `valorBsX10000` = Bs por 1 USD × 10⁴ (ej. 301,37 Bs/USD → `3013700`).
* Fechas `yyyy-MM-dd`; fecha-hora ISO-8601 con offset (`2026-11-04T10:15:30-04:00`), zona `America/Caracas`.
* Errores: `{"codigo": "TEXTO_MAYUS", "mensaje": "..."}` con el status HTTP adecuado.
* Datos **deterministas**: misma cédula ⇒ mismas cuentas/tarjetas (semilla = hash de la cédula).
  El estado mutable (saldos tras débitos, operaciones del suiche, notificaciones) vive en memoria.

---

## 1. BCV — tasa oficial e inflación

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/bcv/tasas/hoy?moneda=USD` | `Tasa` |
| GET | `/bcv/tasas?moneda=USD&desde=2026-09-01&hasta=2026-11-04` | `Tasa[]` (una por día, ascendente) |
| GET | `/bcv/inflacion?anio=2026&mes=10` | `Inflacion` (404 si el mes no está publicado) |
| GET | `/bcv/inflacion?desde=2026-01` | `Inflacion[]` |

```json
// Tasa
{"moneda": "USD", "fecha": "2026-11-04", "valorBsX10000": 4987321,
 "fuente": "BCV", "fechaHoraPublicacion": "2026-11-04T08:00:00-04:00"}
// Inflacion
{"anio": 2026, "mes": 10, "variacionMensualPb": 1240, "fechaPublicacion": "2026-11-03", "fuente": "BCV"}
```

* `moneda` admite `USD` y `USDT` (USDT = misma tasa que USD).
* Serie generada: 301,37 Bs/USD el 2025-12-31 y devaluación compuesta ≈ 0,18 %/día
  (con ruido determinista por fecha). Fines de semana repiten la tasa del viernes.
* Inflación mensual determinista entre 800 y 1 800 pb (8 %–18 %). El mes en curso no está publicado.
* Control demo: `POST /simulador/bcv/tasa-hoy {"valorBsX10000": 5100000}` fuerza la tasa del día.

## 2. Instituciones (bancos y plataformas cripto) — Open Banking simulado

Catálogo fijo (códigos ficticios, **no** son bancos reales):

| codigo | nombre | tipo | tipoPlataforma |
|---|---|---|---|
| `0901` | Banco Ávila | banco | — |
| `0902` | Banco Orinoco | banco | — |
| `0903` | Banco Catatumbo | banco | — |
| `0904` | Banco Roraima | banco | — |
| `C001` | CriptoAndes Exchange | plataforma_cripto | exchange |
| `C002` | Billetera Tepuy | plataforma_cripto | billetera |

`GET /ob/instituciones` → `[{"codigo","nombre","tipo","tipoPlataforma"}]`

### 2.1 OAuth2 (authorization code). Ahorra+ nunca ve las claves.

1. `GET /ob/{codigo}/oauth/authorize?client_id=ahorra-mas&redirect_uri=<url>&state=<opaco>&scope=<scopes>&login_hint=<V12345678>`
   → **HTML** que simula la banca en línea de la institución: muestra nombre del banco, el
   alcance pedido en lenguaje simple, un campo "usuario/clave del banco" (no se valida, cualquier
   valor sirve; nunca sale del simulador) y botones **Autorizar** / **Rechazar**.
   * Autorizar → `302` a `redirect_uri?code=<code>&state=<state>`
   * Rechazar → `302` a `redirect_uri?error=access_denied&state=<state>`
   * Scopes: `saldos:leer`, `movimientos:leer`, `debito:tdc`. Una institución `plataforma_cripto`
     **rechaza** `debito:tdc` (`400` `ALCANCE_NO_PERMITIDO`).
2. `POST /ob/{codigo}/oauth/token` (`application/x-www-form-urlencoded`):
   `grant_type=authorization_code&code=..&redirect_uri=..&client_id=ahorra-mas`
   → `{"access_token":"..","token_type":"Bearer","expires_in":7776000,"scope":"saldos:leer movimientos:leer"}`
   El código es de un solo uso (`400 CODIGO_INVALIDO` si se reutiliza).
   El header `X-Client-Cert-Fingerprint` simula mTLS; si falta → `401`.
3. `POST /ob/{codigo}/oauth/revoke` form `token=..` → `200 {}`

### 2.2 Datos (Bearer `access_token`; `401 TOKEN_INVALIDO` si está revocado o no existe)

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/ob/{codigo}/cuentas` | `Cuenta[]` |
| GET | `/ob/{codigo}/cuentas/{refCuenta}/saldo` | `Saldo` |
| GET | `/ob/{codigo}/cuentas/{refCuenta}/movimientos?desde=2026-10-01` | `Movimiento[]` |
| GET | `/ob/{codigo}/tarjetas` | `Tarjeta[]` (solo bancos; cripto → `[]`) |

```json
// Cuenta
{"refCuenta": "0901-AH-7f3a9c", "ultimos4": "4821", "tipo": "corriente|ahorro|divisas|billetera",
 "moneda": "VES|USD|USDT", "titular": "MARIA PEREZ"}
// Saldo
{"refCuenta": "0901-AH-7f3a9c", "moneda": "VES", "saldoCts": 1543022, "saldoDisponibleCts": 1500000,
 "fechaHora": "2026-11-04T10:00:00-04:00"}
// Movimiento
{"refMovimiento": "MV-0901-000123", "fechaHora": "...", "naturaleza": "credito|debito",
 "montoCts": 250000, "descripcion": "PAGO NOMINA", "clasificacionSugerida": "ingreso|gasto|transf_propia|pago_tdc"}
// Tarjeta (nunca PAN: solo alias opaco + últimos 4)
{"refTarjeta": "TDC-0902-91ab", "ultimos4": "7781", "titular": "MARIA PEREZ",
 "estadoCuenta": {"fechaCorte": "2026-10-28", "fechaLimitePago": "2026-11-12",
                  "saldoTotalCts": 18450000, "pagoMinimoCts": 1845000}}
```

* Cada banco genera 1–2 cuentas por cédula (al menos una en VES; ~50 % también `divisas` en USD).
  Cada plataforma cripto genera 1 cuenta `billetera` en `USDT`.
* Bancos `0902` y `0904` emiten una TDC al usuario.
* Movimientos: últimos 60 días; nómina (`ingreso`) los días 15 y último de cada mes y 6–12 gastos/mes.
* Control demo: `POST /simulador/ob/{codigo}/movimientos`
  `{"cedula":"V12345678","refCuenta":"..","naturaleza":"credito","montoCts":500000,"descripcion":"PAGO NOMINA","clasificacion":"ingreso"}`
  inyecta un movimiento (y ajusta el saldo) para disparar reglas de ahorro.

## 3. Suiche interbancario simulado

Todas las operaciones se identifican con `referencia` = clave de idempotencia del pago en
Ahorra+. Repetir una petición con la misma `referencia` **devuelve el mismo resultado** y nunca
duplica (idempotencia del lado del suiche).

| Método | Ruta | Cuerpo → Respuesta |
|---|---|---|
| POST | `/suiche/beneficiarios/consulta` | `{"bancoEmisor":"0902","refTarjeta":".."}` → `{"titular":"MARIA PEREZ","ultimos4":"7781","refTarjeta":".."}` (404 `TARJETA_NO_EXISTE`) |
| POST | `/suiche/autorizaciones` | `Autorizacion` → `RespuestaSuiche` |
| POST | `/suiche/capturas` | `{"referencia":"..","codigoAutorizacion":".."}` → `RespuestaSuiche` |
| POST | `/suiche/reversos` | `{"referencia":"..","motivo":".."}` → `RespuestaSuiche` |
| GET | `/suiche/operaciones/{referencia}` | `Operacion` (consulta de estado; `estado:"no_existe"` si no llegó) |
| GET | `/suiche/liquidaciones/{fecha}` | `ArchivoLiquidacion` |

```json
// Autorizacion
{"referencia": "a1b2-...", "bancoOrigen": "0901", "refCuentaOrigen": "0901-AH-7f3a9c",
 "bancoEmisor": "0902", "refTarjeta": "TDC-0902-91ab", "montoCts": 1500000, "moneda": "VES"}
// RespuestaSuiche
{"referencia": "..", "resultado": "aprobado|rechazado", "codigoRespuesta": "00|51|05|91",
 "codigoAutorizacion": "AUT123456", "referenciaSuiche": "S7B-20261104-000123",
 "motivo": "Fondos insuficientes", "fechaHora": "..."}
// Operacion
{"referencia": "..", "estado": "no_existe|autorizado|capturado|rechazado|reversado",
 "referenciaSuiche": "..", "codigoAutorizacion": "..", "montoCts": 1500000, "fechaHora": "..."}
// ArchivoLiquidacion
{"fechaLiquidacion": "2026-11-04", "nombreArchivo": "LIQ_S7B_20261104.json", "hashArchivo": "<sha256>",
 "totalRegistros": 2, "montoTotalCts": 3000000,
 "registros": [{"linea": 1, "referencia": "..", "referenciaSuiche": "..", "montoCts": 1500000,
                "estado": "capturado|reversado", "fechaHoraOperacion": "..."}]}
```

**Escenarios deterministas** según los dos últimos dígitos de `montoCts` (`montoCts % 100`):

| `% 100` | Escenario |
|---|---|
| `13` | **Timeout**: la autorización se procesa (queda `autorizado`) pero la respuesta tarda `simulador.suiche.retardo-timeout-ms` (8 000 ms por defecto) — el núcleo corta antes y debe **consultar** con la misma referencia. |
| `51` | **Rechazo** en autorización: `codigoRespuesta 51`, "Fondos insuficientes". |
| `77` | Autoriza pero **rechaza la captura** (`05`, "Operación no permitida por el emisor") — el núcleo debe reversar. |
| `99` | Captura bien, pero el archivo de liquidación trae el **monto distinto** (+100 cts) → diferencia en conciliación. |
| `98` | Captura bien, pero la operación **no aparece** en el archivo de liquidación (`falta_suiche`). |
| otro | Camino feliz. |

* La captura debita el saldo de la cuenta origen (en el simulador de bancos) y abona la TDC
  (reduce `saldoTotalCts`). El reverso lo deshace.
* Un reverso sobre una operación `capturado` o `autorizado` la deja `reversado`.
* El archivo de liquidación de una fecha incluye todas las operaciones capturadas/reversadas de
  ese día (zona Caracas), salvo las alteradas por los escenarios 98/99.

## 4. KYC

`POST /kyc/verificaciones`
```json
{"nacionalidad": "V", "cedula": 12345678, "primerNombre": "MARIA", "primerApellido": "PEREZ",
 "fechaNacimiento": "1995-04-12",
 "documentos": [{"tipo": "cedula", "hashSha256": ".."}, {"tipo": "selfie", "hashSha256": ".."}]}
```
→ `{"refProveedor": "KYC-..", "resultado": "aprobado|rechazado", "pruebaVidaSuperada": true,
"coincideListaSanciones": false, "motivo": null}`

* Cédula terminada en `666` → `coincideListaSanciones: true`, `rechazado`.
* Cédula terminada en `000` → `pruebaVidaSuperada: false`, `rechazado`.
* Falta selfie o cédula → `400 DOCUMENTOS_INCOMPLETOS`.

## 5. Proveedor de IA (LLM) simulado

`POST /llm/v1/explicaciones`
```json
{"idSeudonimo": 834201, "perfil": "conservador|moderado|dinamico",
 "rasgos": {"rangoIngreso": "R2", "volatilidadSaldoPb": 3400, "objetivoPrincipal": "proteger",
            "horizonteMeses": 6, "toleranciaRiesgo": "baja", "metasActivas": 1},
 "distribucion": [{"instrumento": "USD", "porcentajePb": 6000}, {"instrumento": "Bs", "porcentajePb": 1000}]}
```
→ `{"texto": "explicación en español simple (3–5 oraciones)", "modelo": "sim-llm-1.0"}`

* **Guardia de seudonimización**: si el cuerpo contiene algo que parezca PII (secuencia de 7–9
  dígitos fuera de `idSeudonimo`, un `@`, o claves `cedula`, `nombre`, `correo`, `telefono`)
  responde `422 PII_DETECTADA`. Sirve para demostrar que el núcleo solo envía datos seudonimizados.

## 6. Notificaciones (push, SMS, correo)

| Método | Ruta | |
|---|---|---|
| POST | `/notificaciones/{canal}` (`push`/`sms`/`correo`) | `{"destino":"..","titulo":"..","mensaje":"..","tipo":"otp|pago|meta|racha|cuota|seguridad|movimiento"}` → `{"id":"..","estatus":"enviada"}` |
| GET | `/notificaciones?destino=..` | últimas 100 enviadas |

## 7. Consola del simulador

`GET /` → página HTML sencilla que muestra: últimos SMS/push (para ver los **OTP** en la demo),
operaciones del suiche, tasa del día y formularios para los controles demo (inyectar movimiento,
fijar tasa). Se refresca sola cada 5 s.

## 8. Configuración

```yaml
server.port: 8090
simulador.suiche.retardo-timeout-ms: 8000
```
