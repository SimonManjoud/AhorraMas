# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

La app móvil es Flutter con un único lenguaje Material 3 en Android e iOS (Android es el teléfono dominante del segmento y la demo corre en emulador Android). Superficies secundarias, internas y web: el backoffice del núcleo (`/backoffice/`) y el tablero de KPIs del prototipo (`tablero-kpis/`).

## Users

**Usuario principal:** persona natural bancarizada de 22 a 40 años que vive en Venezuela (Caracas, Valencia, Maracaibo en el primer año), cobra su ingreso principal en bolívares, intenta ahorrar en dólares y maneja entre dos y cuatro bancos, a menudo con saldos en una plataforma cripto (USDT). Profesionales jóvenes, independientes y empleados del sector privado que usan Pago Móvil a diario, con ingresos irregulares y márgenes estrechos (ENCOVI 2025: pobreza de ingresos 68,5 %). Desconfía de las apps financieras por miedo al fraude y al phishing.

**Su trabajo:** saber cuánto tiene en total, en qué moneda y cuánto vale en USD a la tasa BCV del día; apartar dinero en divisas antes de que la devaluación se lo coma; pagar su tarjeta de crédito desde otro banco sin entregar claves.

**Audiencias secundarias:**
- Operadores internos del backoffice (soporte, conciliación, cumplimiento, admin): revisan pagos, conciliaciones, casos, alertas AML y auditoría.
- Evaluación académica (curso Desarrollo de Software de Fintech, prof. José Leonardo González Villanueva): las decisiones se optimizan **por igual** para el usuario real y para la demo; el usuario real es el norte, pero cada pantalla clave debe poder explicarse en vivo en minutos.

## Product Purpose

Ahorra+ consolida en una sola posición global, en Bs y USD a la tasa oficial BCV, las cuentas multibanco, en divisas y cripto (solo lectura) de una persona; automatiza su ahorro hacia metas en dólares; y paga tarjetas de crédito banco a banco por el suiche interbancario. Todo **sin custodiar fondos ni pedir claves bancarias**.

Frase de valor: *ver todo tu dinero en un solo lugar y ahorrar en dólares sin pensar, sin entregar nunca tus claves.*

Éxito del MVP (objetivos específicos): consolidar al menos tres bancos simulados en Bs y USD a tasa BCV; gestionar metas con tres reglas de ahorro automático; pago interbancario de TDC con autorización, captura, reverso y conciliación, con 100 % de transacciones conciliadas y cero duplicados.

## Positioning

- **Nunca pide claves bancarias.** El acceso es por consentimiento revocable otorgado en la propia banca del banco (OAuth2); el dinero siempre viaja de banco a banco. Convierte la barrera del miedo en el diferenciador.
- **Bimonetarismo nativo.** Todo monto se muestra en Bs y USD a la tasa BCV del día; ninguna alternativa (app de cada banco, hoja de cálculo, exchange) lo hace consolidado.
- **Cofre Protector – Ahorro con Impacto Real**, imposible de copiar sin entender Venezuela:
  1. **Ruta del día:** la meta emocional con plazo y monto en USD se baja a una cuota del día/semana con la tasa BCV viva ("Hoy toca apartar $2,40").
  2. **Racha con días colchón:** cada aporte, aunque sea el redondeo, mantiene la racha y desbloquea multiplicadores de puntos; un día sin aporte no la rompe, porque los ingresos son irregulares.
  3. **Espejo de Impacto:** cuánto poder de compra salvó el cofre este mes en divisas vs. haberlo dejado en bolívares ("Tu cofre de $120 te protegió de perder Bs X este mes"). Solo lee del ledger inmutable y la tasa BCV.
  4. **Sello de compromiso:** sellar voluntariamente una porción hasta la fecha de la meta; romperlo antes cuesta el multiplicador de racha, **nunca dinero**. El sello es lógico; el dinero sigue en el banco.

## Operating Context

- Uso móvil diario, en un entorno donde el 98 % de las transacciones son digitales y Pago Móvil es cotidiano; el usuario no necesita ser convencido de mover dinero por teléfono, necesita una capa de inteligencia y confianza encima.
- La tasa BCV cambia a diario y la inflación/devaluación es alta (inflación 2025: 475,28 %; devaluación del bolívar 2025: 82,7 %, de 52,02 a 301,37 Bs/USD).
- Vincular bancos abre la banca en línea del banco (simulada) en el navegador del teléfono; Ahorra+ solo consulta el estado del consentimiento.
- La demo guiada recorre: registro + KYC → vincular bancos → inicio consolidado → Cofre (meta, Ruta del día, racha, regla 10 % de ingreso, sello) → avanzar 1 día (modo demo) → pago de TDC con PIN + OTP y escenarios por céntimos → backoffice.
- Modo demo sin backend (`FAKE_API=true`) con datos de prueba (V-12345678, PIN 1234).

## Capabilities and Constraints

**Dentro del alcance (Fase 1):** registro, KYC y autenticación con huella o PIN; saldo visible en inicio sin clave (saldo rápido), autenticación para todo lo demás; vinculación multibanco por selección múltiple; consolidado Bs/divisas/cripto (solo lectura); metas en USD con reglas de porcentaje de ingreso, redondeo y aporte fijo quincenal; Cofre Protector; pago de TDC interbancario contra suiche simulado; asistente de perfil de riesgo con IA que explica cada recomendación y exige aprobación (PIN); Puntos Ahorra con ledger inmutable y niveles Gold y Platinum; notificaciones; backoffice de soporte, conciliación y alertas AML.

**Fuera del alcance:** custodia de fondos o cuentas propias; integración productiva con bancos o suiche reales; compra/venta/transferencia de cripto; crédito y analítica (Fase 2); tokenización (Fase 3); versión web para usuarios finales; captura de números de tarjeta; autorización ITFB ante SUDEBAN.

**Reglas de seguridad visibles:** operaciones sensibles piden PIN (step-up); pagos de TDC piden PIN + OTP; confirmación del titular antes de pagar; idempotencia (nunca duplicar un pago); la IA nunca actúa sola.

**Estados de pago que la UI debe expresar:** en proceso / en verificación, capturado, rechazado (sin débito ni comisión), reverso, diferencia en conciliación.

**Técnica:** monolito modular hexagonal (Java 21, Spring Boot, Spring Modulith, PostgreSQL); app Flutter (Material 3, Riverpod, go_router) que consume solo el contrato REST (`docs/contrato-api.md`); sistemas externos simulados en `mocks/simuladores/`. El dinero nunca usa punto flotante.

**Terminología:** Cofre, meta, Ruta del día, cuota, racha, días colchón, multiplicador, Espejo de Impacto, Sello de compromiso, posición global, tasa BCV, consentimiento, suiche, Puntos Ahorra, niveles Gold y Platinum.

## Brand Commitments

- **Nombre canónico: Ahorra+** (no "Ahorro+", aunque aparezca en los C4). Empresa: Impulsa Tech, C.A.
- **Voz:** tuteo cercano, español venezolano neutro, cálido y directo. Ejemplo de registro: "Hoy toca apartar $2,40". Empático con el ingreso irregular: nunca castigar al que tiene un mes malo.
- **Formato de dinero:** convención venezolana (coma decimal, punto de miles: `1.000,77`), siempre Bs y USD a tasa BCV.
- **Avisos obligatorios:** el Espejo de Impacto y el asistente IA muestran "Simulación informativa, no asesoría". Simulación académica: Ahorra+ no cuenta con autorización de SUDEBAN; bancos, suiche, BCV y demás sistemas externos son simulados.

## Evidence on Hand

- Documento de concepto con problema, mercado, propuesta de valor, alcance y datos citados: `Documentos/Ahorra+ - Impulsa Tech, C.A - Simón Manjoud, Sebastián Pérez, Andrés De Quintal.pdf`.
- Modelos C4 (contexto, contenedores, flujos de datos), especificación OpenAPI y plan de certificación en `Documentos/`.
- Arquitectura y contratos: `docs/arquitectura.md`, `docs/contrato-api.md`, `docs/contrato-simuladores.md`.
- Datos de demo (no reales): bancos simulados Banco Ávila, Banco Orinoco, CriptoAndes; metas "Viaje en marzo" ($300) y "Fondo de emergencia" ($500); racha de 16 días.
- Tablero de KPIs del prototipo: `tablero-kpis/` (aprobación, latencia p95, TPS, reversos).
- **No existen:** usuarios reales, testimonios, bancos aliados firmados, métricas de adopción, resultados de la encuesta OE1 ni logotipo definitivo. No fabricarlos.

## Product Principles

1. **Confianza antes que funcionalidad.** Cada pantalla debe hacer evidente que Ahorra+ no ve claves ni toca el dinero; lo sensible pide PIN y lo explica.
2. **Dos monedas, una verdad.** Ningún monto aparece sin su equivalente Bs/USD a la tasa BCV del día y su fecha.
3. **Del objetivo a la acción de hoy.** El ahorro se presenta como una tarea pequeña y concreta, no como una meta abstracta.
4. **Empatía con el ingreso irregular.** Los mecanismos de hábito (racha, sello) premian y nunca castigan con dinero; un día malo no borra el progreso.
5. **Informar, no asesorar.** Simulaciones e IA se etiquetan como informativas, explican su razonamiento y nunca actúan sin aprobación.

## Accessibility & Inclusion

Sin estándar formal establecido. Usuarios de teléfonos Android de gama variada y conectividad irregular; el saldo rápido y los estados de pago deben ser comprensibles sin conocimiento financiero ni técnico.
