# Contrato de la API del Núcleo Ahorra+ (app móvil ↔ backend)

Base URL: `http://<host>:8080/api/v1` (en el emulador Android: `http://10.0.2.2:8080/api/v1`).

## Convenciones

* JSON camelCase. **Montos enteros en céntimos** (`...Cts`, enteros de 64 bits). El cliente nunca
  hace aritmética de punto flotante con dinero: solo formatea (`cts / 100` para mostrar).
* Tasa BCV: `valorBsX10000` (Bs por USD × 10⁴). Porcentajes: `...Pb` (puntos básicos, 10 000 = 100 %).
  Multiplicadores: `multiplicadorX100` (150 = 1,5x).
* Fechas `yyyy-MM-dd`; fecha-hora ISO-8601 con offset (`America/Caracas`).
* Autenticación: `Authorization: Bearer <accessToken>` salvo donde se indique.
* Operaciones sensibles requieren además `X-Step-Up: <stepUpToken>` (ver §1.4):
  * nivel `pin` → aprobar reglas, sellar/romper sello, retirar del cofre, aceptar recomendación IA,
    activar saldo rápido.
  * nivel `pin_otp` → pagos de TDC.
* Pagos: header **`Idempotency-Key`** obligatorio (UUID generado por la app **una vez por intento
  de pago**; si la app reintenta por red, reusa la misma clave).
* Errores (`4xx/5xx`):
  ```json
  {"codigo": "LIMITE_EXCEDIDO", "mensaje": "Texto para mostrar al usuario", "detalles": {}}
  ```
  Códigos usados: `NO_AUTENTICADO`(401), `STEP_UP_REQUERIDO`(401), `PROHIBIDO`(403),
  `NO_ENCONTRADO`(404), `VALIDACION`(400), `CONFLICTO`(409), `REGLA_NEGOCIO`(422),
  `KYC_PENDIENTE`(403), `LIMITE_EXCEDIDO`(422), `IDEMPOTENCY_KEY_REQUERIDA`(400),
  `SERVICIO_EXTERNO`(502), `PIN_INVALIDO`(401, `detalles.intentosRestantes`),
  `DISPOSITIVO_REVOCADO`(401/403), `ANTIFRAUDE`(422), y `R-xx`(422) cuando se viola una regla de negocio del E-R
  (p. ej. `R-12` sello, `R-16` pago). La app no debe cerrar sesión ante `PIN_INVALIDO` en `/auth/step-up`.

---

## 1. Identidad (M1)

### 1.1 Registro — `POST /auth/registro` (público)
```json
{"nacionalidadDoc": "V", "cedula": 12345678, "primerNombre": "María", "segundoNombre": null,
 "primerApellido": "Pérez", "segundoApellido": null, "fechaNacimiento": "1995-04-12",
 "correo": "maria@correo.com", "telefonoMovil": "04141234567", "pin": "1234",
 "dispositivo": {"huella": "<hash estable del equipo>", "plataforma": "Android", "modelo": "Pixel 8"}}
```
`201` → `Sesion` (ver 1.2). Valida: mayor de edad, cédula/correo/teléfono únicos (`409`), PIN de 4–6 dígitos.

### 1.2 Login — `POST /auth/login` (público)
```json
{"nacionalidadDoc": "V", "cedula": 12345678, "pin": "1234", "huellaDispositivo": "<hash>",
 "plataforma": "Android", "modelo": "Pixel 8"}
```
`200` → `Sesion`
```json
{"accessToken": "eyJ...", "expiraEn": "2026-11-04T11:15:00-04:00",
 "usuario": {"idUsuario": 1, "primerNombre": "María", "primerApellido": "Pérez",
             "kycAprobado": false, "estatus": "activo"},
 "idDispositivo": 3}
```
* 3 PIN fallidos seguidos ⇒ dispositivo revocado (`motivo 3_fallas`), saldo rápido anulado y `401`.
* Un dispositivo nuevo se registra solo y se envía una notificación de seguridad.

### 1.3 Perfil — `GET /me` → `{"idUsuario","nacionalidadDoc","cedula","primerNombre","primerApellido","correo","telefonoMovil","kycAprobado","estatus","fechaRegistro"}`

### 1.4 Step-up (huella/PIN y OTP)
* `POST /auth/otp` `{"proposito": "pago"}` → `{"idDesafio": "uuid", "expiraEn": "...", "otpDemo": "482913"}`
  Envía el OTP por SMS (simulador). `otpDemo` solo viene cuando el backend corre en modo demo.
* `POST /auth/step-up` `{"pin": "1234"}` → nivel `pin`; o
  `{"pin": "1234", "idDesafio": "uuid", "otp": "482913"}` → nivel `pin_otp`.
  Respuesta: `{"stepUpToken": "eyJ...", "nivel": "pin|pin_otp", "expiraEn": "..."}` (vida 5 min).

### 1.5 KYC — `POST /kyc/verificaciones`
```json
{"documentos": [{"tipo": "cedula", "contenidoBase64": "..."}, {"tipo": "selfie", "contenidoBase64": "..."}]}
```
→ `{"idVerificacion": 1, "resultado": "aprobado|rechazado|pendiente", "pruebaVidaSuperada": true,
"coincideListaSanciones": false, "motivoRechazo": null}`. Las fotos van cifradas al almacén de
objetos; la BD guarda solo referencia + SHA-256. Sin KYC aprobado, vincular bancos, metas y
pagos responden `403 KYC_PENDIENTE`.

### 1.6 Saldo rápido (F3)
* `POST /auth/saldo-rapido` (Bearer + `X-Step-Up` nivel `pin`) → `{"tokenSaldo": "eyJ...", "expiraEn": "..."}`
  Token de alcance `saldo:leer`, ligado al dispositivo. La app lo guarda en Keychain/Keystore.
* `GET /saldo-rapido` con `Authorization: Bearer <tokenSaldo>` y `X-Dispositivo: <huella>` →
  `{"totalBsCts": 152030044, "totalUsdCts": 304512, "actualizadoEn": "..."}` (sin detalle por banco).
* `DELETE /auth/saldo-rapido` → `204`, desactiva y revoca (no requiere step-up: reduce privilegios).
* `POST /dispositivos/actual/revocar` `{"motivo": "cambio_sim|root|robo|usuario"}` → anula token y sesión.

---

## 2. Consolidación multibanco (M2)

* `GET /instituciones` → `[{"idInstitucion":1,"codigo":"0901","nombre":"Banco Ávila","tipo":"banco|plataforma_cripto","estatusIntegracion":"sandbox"}]`
* `POST /consentimientos` (selección múltiple)
  ```json
  {"instituciones": [1, 2, 5], "permiteDebitoTdc": true}
  ```
  → `201 [{"idConsentimiento": 10, "idInstitucion": 1, "institucion": "Banco Ávila", "estatus": "pendiente",
  "urlAutorizacion": "http://localhost:8090/ob/0901/oauth/authorize?..."}]`
  La app abre cada `urlAutorizacion` en el navegador. El banco redirige a
  `GET /consentimientos/callback?code=..&state=..` (público, responde HTML "Listo, vuelve a Ahorra+").
  La app consulta `GET /consentimientos` hasta que pasen a `activo`.
  `permiteDebitoTdc` se ignora para plataformas cripto (R-03/R-04).
* `GET /consentimientos` → `[{"idConsentimiento","idInstitucion","institucion","estatus":"pendiente|activo|revocado|vencido","permiteLectura":true,"permiteDebitoTdc":true,"fechaOtorgamiento","fechaVencimiento"}]`
* `DELETE /consentimientos/{id}` → revoca (desactiva cuentas de esa institución).
* `POST /consolidado/sincronizar` → `202` (sincroniza saldos, movimientos y tarjetas ahora).
* `GET /consolidado` →
  ```json
  {"tasa": {"fecha": "2026-11-04", "valorBsX10000": 4987321},
   "totalBsCts": 152030044, "totalUsdCts": 304831, "actualizadoEn": "...",
   "instituciones": [{"idInstitucion": 1, "nombre": "Banco Ávila", "tipo": "banco",
      "totalBsCts": 100000000, "totalUsdCts": 200500,
      "cuentas": [{"idCuenta": 7, "alias": "Nómina", "ultimos4": "4821", "tipo": "corriente",
                   "moneda": "VES", "saldoCts": 1543022, "saldoBsCts": 1543022, "saldoUsdCts": 3094,
                   "soloLectura": false}]}]}
  ```
* `PATCH /cuentas/{id}` `{"alias": "Nómina"}`
* `GET /cuentas/{id}/movimientos` → `[{"idMovimiento","fechaHora","naturaleza","clasificacion","montoCts","moneda","descripcion"}]`
* `GET /tasas/hoy` → `{"fecha","valorBsX10000","fuente":"BCV"}`

---

## 3. Cofre Protector (M5)

### 3.1 Metas
* `POST /metas`
  ```json
  {"nombre": "Viaje en marzo", "categoria": "viaje|emergencia|estudio|salud|hogar|otro",
   "montoObjetivoUsdCts": 30000, "fechaObjetivo": "2027-03-15", "frecuenciaCuota": "diaria|semanal"}
  ```
  → `201 Meta`
* `GET /metas` → `Meta[]`; `GET /metas/{id}` → `Meta`
  ```json
  {"idMeta": 1, "nombre": "Viaje en marzo", "categoria": "viaje", "montoObjetivoUsdCts": 30000,
   "fechaObjetivo": "2027-03-15", "frecuenciaCuota": "diaria", "estatus": "activa",
   "acumuladoUsdCts": 1200, "progresoPb": 400, "fechaCreacion": "...",
   "sello": {"idSello": 2, "montoSelladoUsdCts": 1000, "fechaLiberacion": "2027-03-15", "estatus": "activo"} ,
   "cuotaHoy": RutaDelDia}
  ```
  (`sello` puede ser `null`.)
* `PATCH /metas/{id}` `{"estatus": "pausada|activa|cancelada"}`

### 3.2 Ruta del día
* `GET /metas/{id}/ruta-del-dia` y `GET /ruta-del-dia` (todas las metas activas) → `RutaDelDia` / `RutaDelDia[]`
  ```json
  {"idMeta": 1, "nombreMeta": "Viaje en marzo", "fecha": "2026-11-04",
   "montoSugeridoUsdCts": 240, "montoSugeridoBsCts": 119696, "tasaValorBsX10000": 4987321,
   "aportadoHoyUsdCts": 0, "estatus": "pendiente|cumplida|parcial|colchon|omitida",
   "periodosRestantes": 131, "mensaje": "Hoy toca apartar $2,40 (Bs 1.196,96) para «Viaje en marzo»"}
  ```
  `PATCH /metas/{id}` devuelve la `Meta`; `PATCH /reglas/{id}` devuelve la `Regla`; `PATCH /cuentas/{id}` → `204`.
  Fórmula (R-09): `(objetivo − acumulado) / periodos restantes`, redondeo hacia arriba al céntimo;
  convertida a Bs con la tasa BCV del día.

### 3.3 Aportes y retiros (ledger del cofre, inmutable)
* `POST /metas/{id}/aportes` `{"montoUsdCts": 240, "origen": "manual|cuota_del_dia", "idCuentaOrigen": 7}`
  (`idCuentaOrigen` es opcional e informativo: el cofre es lógico, el dinero queda en esa cuenta del banco)
  → `201`
  ```json
  {"aporte": {"idAporte": 15, "fechaHora": "...", "tipo": "aporte", "origen": "cuota_del_dia",
              "montoUsdCts": 240, "montoBsCts": 119696},
   "meta": Meta, "racha": Racha, "puntosGanados": 36}
  ```
* `POST /metas/{id}/retiros` (`X-Step-Up` pin) `{"montoUsdCts": 500}` → `{"aporte": .., "meta": .., "selloRoto": false}`
  Si el retiro deja el acumulado por debajo del monto sellado antes de la fecha de liberación,
  el sello se rompe y la racha se cierra (se pierde el multiplicador, **nunca dinero**).
* `GET /metas/{id}/aportes` → `Aporte[]`

### 3.4 Reglas de ahorro automático (3 tipos)
* `POST /metas/{id}/reglas` (`X-Step-Up` pin)
  ```json
  {"tipo": "pct_ingreso", "porcentajePb": 1000}
  {"tipo": "redondeo", "multiploRedondeoCts": 10000}
  {"tipo": "fijo_quincenal", "montoFijoUsdCts": 2500}
  ```
  → `201 {"idRegla","idMeta","tipo","porcentajePb","multiploRedondeoCts","montoFijoUsdCts","estatus":"activa","fechaAprobacion"}`
* `GET /metas/{id}/reglas` → `Regla[]`; `PATCH /reglas/{id}` `{"estatus": "activa|pausada|finalizada"}`
* Ejecución (worker): `pct_ingreso` sobre cada movimiento `ingreso`; `redondeo` sobre cada `gasto`
  en Bs (redondea al múltiplo y aparta la diferencia); `fijo_quincenal` los días 15 y último del mes.

### 3.5 Racha con días colchón
* `GET /racha` →
  ```json
  {"idRacha": 3, "fechaInicio": "2026-10-20", "diasConsecutivos": 16, "diasAporte": 14,
   "diasColchonUsados": 2, "colchonesDisponibles": 1, "multiplicadorX100": 125,
   "siguienteTramo": {"diasMinimos": 30, "multiplicadorX100": 150},
   "aporteHoy": true,
   "ultimosDias": [{"fecha": "2026-11-04", "tipo": "aporte|colchon|sin_aporte"}]}
  ```
  `null`-safe: si no hay racha activa, `diasConsecutivos = 0`.

### 3.6 Sello de compromiso
* `POST /metas/{id}/sello` (`X-Step-Up` pin) `{"montoSelladoUsdCts": 1000}` → `Sello` (monto ≤ acumulado; R-12)
* `POST /metas/{id}/sello/romper` (`X-Step-Up` pin) → `Sello` con `estatus: "roto"` (racha cerrada).

### 3.7 Espejo de Impacto
* `GET /espejo-impacto?anio=2026&mes=11` (por defecto mes actual) →
  ```json
  {"anio": 2026, "mes": 11, "provisional": true,
   "saldoCofresUsdCts": 12000, "perdidaEvitadaBsCts": 450385, "perdidaEvitadaUsdCts": 903,
   "tasaInicio": {"fecha": "2026-11-01", "valorBsX10000": 4612000},
   "tasaFin": {"fecha": "2026-11-04", "valorBsX10000": 4987321},
   "devaluacionPb": 814, "inflacionMensualPb": 1240,
   "totalBsCts": 152030044, "totalUsdCts": 304831,
   "frase": "Tu cofre de $120,00 te protegió de perder Bs 4.503,85 este mes.",
   "disclaimer": "Simulación informativa basada en la tasa oficial BCV; no constituye asesoría financiera."}
  ```
  Cálculo: por cada aporte vivo del cofre, `usd × (tasaFin − tasaReferencia)`, con
  `tasaReferencia = tasa BCV del día del aporte o del 1.º del mes, la que sea posterior`.
  Al cierre de mes se persiste una sola vez en `REPORTE_MENSUAL`.

---

## 4. Pago de TDC interbancario (M3)

* `GET /tarjetas` → `[{"idTarjeta": 4, "idInstitucion": 2, "banco": "Banco Orinoco", "ultimos4": "7781", "alias": null,
  "titularConfirmado": "MARIA PEREZ", "esPropia": true,
  "estadoCuenta": {"fechaCorte","fechaLimitePago","saldoTotalCts","pagoMinimoCts"}}]`
* `POST /tarjetas/{id}/beneficiario` → `{"titular": "MARIA PEREZ", "ultimos4": "7781"}` (consulta al suiche; paso 2 de F2)
* `GET /pagos-tdc/cuentas-origen` → cuentas en VES con consentimiento de débito activo:
  `[{"idCuenta","banco","ultimos4","alias","saldoCts"}]`
* `GET /pagos-tdc/cotizacion?montoCts=1500000` → `{"montoCts":1500000,"comisionCts":3000,"totalCts":1503000}`
* `POST /pagos-tdc` — headers `Idempotency-Key`, `X-Step-Up` (nivel `pin_otp`)
  ```json
  {"idTarjeta": 4, "idCuentaOrigen": 7, "montoCts": 1500000, "beneficiarioConfirmado": true}
  ```
  → `201` (nuevo) o `200` (misma `Idempotency-Key`: devuelve el pago existente, **nunca duplica**)
  ```json
  {"idPago": 21, "estado": "CAPTURADO", "montoCts": 1500000, "comisionCts": 3000,
   "referenciaSuiche": "S7B-...", "codigoAutorizacion": "AUT...", "esPuntual": true,
   "motivoRechazo": null, "fechaHoraCreacion": "...",
   "mensaje": "Pago aplicado a tu TDC terminada en 7781",
   "historial": [{"secuencia": 1, "estadoAnterior": null, "estadoNuevo": "PENDIENTE", "fechaHora": "...", "actor": "usuario"}]}
  ```
  Estados (R-14): `PENDIENTE → AUTORIZADO → CAPTURADO → CONCILIADO`; alternos `RECHAZADO`,
  `EN_VERIFICACION` (la app muestra "en proceso", no "fallido"), `REVERSADO`, `CON_DIFERENCIA`.
* `GET /pagos-tdc` → `Pago[]` (sin historial); `GET /pagos-tdc/{id}` → `Pago` con historial.
* `GET /limites` → `{"montoMaxDiarioCts": 50000000, "maxOperacionesDiarias": 5, "usadoHoyCts": 1500000, "operacionesHoy": 1}`

---

## 5. Puntos Ahorro y niveles (M6)

* `GET /puntos` →
  `{"saldo": 1240, "puntosGanadosTotal": 1500, "nivel": {"nombre": "Base", "desde": "..."},
  "siguienteNivel": {"nombre": "Gold", "puntosMinimos": 5000, "puntosFaltantes": 3500}}`
* `GET /puntos/movimientos` → `[{"idTransaccion","fechaHora","concepto":"meta|pago_puntual|racha|referido|canje|reverso","descripcion","puntos": 36}]` (puntos con signo para el usuario)
* `GET /recompensas` → `[{"idRecompensa","descripcion","costoPuntos","comercio","nivelMinimo":"Base|Gold|Platinum","stockDisponible"}]`
* `POST /recompensas/{id}/canjes` → `201 {"idCanje","codigoCanje","puntosCanjeados","estatus":"emitido","fechaHora"}`
* `GET /canjes` → `Canje[]`

---

## 6. Asistente de perfil de riesgo — IA (M4)

* `POST /perfil/evaluaciones`
  `{"objetivoPrincipal": "proteger|meta_corta|crecer", "horizonteMeses": 6, "toleranciaRiesgo": "baja|media|alta"}`
  →
  ```json
  {"idEvaluacion": 2, "perfilResultante": "conservador|moderado|dinamico",
   "recomendacion": {"idRecomendacion": 5, "estatus": "propuesta",
      "explicacion": "Texto redactado por el LLM con datos seudonimizados...",
      "distribucion": [{"instrumento": "USD", "porcentajePb": 6000, "nivelRiesgo": "bajo"}],
      "reglaSugerida": {"tipo": "pct_ingreso", "porcentajePb": 1000},
      "versionModelo": "reglas-1.2 / modelo-0.3",
      "disclaimer": "Recomendación orientativa..."}}
  ```
* `POST /recomendaciones/{id}/decision` (`X-Step-Up` pin)
  `{"aceptar": true, "entendioExplicacion": true, "idMeta": 1}` → `{"estatus": "aceptada", "reglaCreada": Regla|null}`
  La IA **nunca** ejecuta nada sola: solo al aceptar con PIN se crea la regla sugerida en la meta.
* `GET /recomendaciones` → historial: lista de objetos `recomendacion` (mismo formato que arriba) con además
  `perfilResultante` y `fechaHora`; `estatus` puede ser `expirada` (vigencia 7 días).

---

## 7. Notificaciones

* `GET /notificaciones` → `[{"idNotificacion","canal","tipo","titulo","mensaje","fechaHoraEnvio","estatus","leida"}]`
* `POST /notificaciones/{id}/leida` → `204`

## 8. Modo demo (solo perfil `demo`)

* `POST /demo/reloj/avanzar` `{"dias": 1}` → mueve el reloj del sistema y ejecuta el cierre de día
  (evaluación de racha/colchón, cuotas omitidas, reglas quincenales, conciliación del día anterior).
  Respuesta: `{"fechaActual": "2026-11-05"}`
* `GET /demo/reloj` → `{"fechaActual": "..."}`

## 9. Backoffice (operadores; JWT con rol)

Prefijo `/backoffice`. Login simulado de SSO: `POST /backoffice/auth/login`
`{"correoCorporativo": "conciliacion@ahorramas.test", "clave": "demo"}` → `{"accessToken", "rol"}`.
Roles: `soporte`, `conciliacion`, `oficial_cumpl`, `admin`. UI web mínima en `http://localhost:8080/backoffice/`.

* `GET /backoffice/pagos?estado=EN_VERIFICACION`
* `POST /backoffice/conciliaciones` `{"fecha": "2026-11-04"}` → ejecuta conciliación; `GET /backoffice/conciliaciones?fecha=` → archivo + resultados
* `GET /backoffice/casos`, `PATCH /backoffice/casos/{id}` `{"estatus","resolucion"}`
* `GET /backoffice/alertas-aml`, `PATCH /backoffice/alertas-aml/{id}` `{"estatus":"en_revision|descartada|escalada","justificacion"}`
* `POST /backoffice/reportes-regulatorios` `{"idAlerta": 3}` → ROS en borrador (solo desde alerta escalada);
  `POST /backoffice/reportes-regulatorios/{id}/enviar` → `enviado` con `numeroAcuse`; `GET /backoffice/reportes-regulatorios`
* `GET /backoffice/usuarios/{id}/limite`, `PUT /backoffice/usuarios/{id}/limite` `{"montoMaxDiarioCts","maxOperacionesDiarias","motivoCambio"}`
* `GET /backoffice/auditoria?limite=100`
* `GET /backoffice/kpis?rango=24h&bucket=auto` → tablero de KPIs del prototipo (cualquier rol de operador).
  `rango` = `hoy|1h|24h|7d|30d` relativo al reloj del negocio, o `desde`/`hasta` ISO-8601 (máx. 92 días);
  `bucket` = `auto|minuto|hora|dia` (máx. 2 000 intervalos). Respuesta `TableroKpis`: `ventana`, `resumen` y `resumenPrevio`
  (`pagos`, `aprobados`, `rechazados`, `enCurso`, `reversados`, `timeouts`, `tasaAprobacionPb`, `tasaReversosPb`,
  `tasaTimeoutsPb` en puntos básicos, `latenciaSuiche` y `latenciaExtremoAExtremo` con `p50Ms/p95Ms/p99Ms/maxMs`,
  `pagosSegundoPico`, `pagosMinutoPico`, `mensajesSegundoPico`, `montoTotalCts`, `comisionTotalCts`), `latenciasPorTipo`,
  `series` (un `Bucket` por intervalo), `estados`, `motivosRechazo`, `origenReversos`, `pagosRecientes`.
  Definiciones en `pagos/dominio/CalculadoraKpis.java`; interfaz web en `tablero-kpis/`.
