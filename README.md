# Ahorra+ - MVP

**Impulsa Tech, C.A.** — Simón Manjoud, Sebastián Pérez, Andrés De Quintal

Ahorra+ consolida en bolívares y dólares (tasa oficial BCV) las cuentas multibanco de una persona,
automatiza su ahorro en divisas y paga tarjetas de crédito banco a banco, **sin custodiar fondos ni
pedir claves bancarias**. Incluye el módulo nuevo **Cofre Protector** (Ruta del día, racha con días
colchón, Espejo de Impacto y Sello de compromiso).

| Carpeta | Qué es |
|---|---|
| `backend/` | Núcleo: **monolito modular** Java 21 + Spring Boot 4.1, **hexagonal** por módulo, PostgreSQL (Flyway), Spring Modulith |
| `mobile/ahorra_mas_app/` | App **Flutter** (Material 3, Riverpod, go_router) |
| `mocks/simuladores/` | **Simuladores** de los sistemas externos: bancos (Open Banking OAuth2), suiche interbancario, BCV, plataformas cripto, KYC, LLM y notificaciones |
| `docs/` | `arquitectura.md` (decisiones, C4 → código, reglas R-01…R-23), `contrato-api.md` (app ↔ núcleo), `contrato-simuladores.md` (núcleo ↔ externos) |
| `scripts/prueba_e2e.py` | Prueba de punta a punta contra los servicios en ejecución |
| `tablero-kpis/` | **Tablero de KPIs del prototipo** (tasa de aprobación, latencia p95, TPS, tasa de reversos): HTML/JS sin dependencias, modo demo y modo en vivo contra `GET /api/v1/backoffice/kpis` |
| `Documentos/` | Documento de concepto y modelos C4 |

## Arranque rápido (sin instalar PostgreSQL ni Docker)

Requisitos: **Java 21**, **Flutter 3.4x**. Maven no hace falta (se incluye `mvnw`).

```bash
# 1) Simuladores de sistemas externos  →  http://localhost:8090  (consola con los OTP y el suiche)
cd mocks/simuladores && ./mvnw spring-boot:run

# 2) Núcleo (perfil "local": PostgreSQL 17 embebido real + modo demo)  →  http://localhost:8080
cd backend && ./mvnw spring-boot:run

# 3) Comprobar todo de punta a punta
python scripts/prueba_e2e.py

# 4) Tablero de KPIs del prototipo  →  http://localhost:8070  (modo demo sin backend; "En vivo" con admin@ahorramas.test / demo)
cd tablero-kpis && python -m http.server 8070

# 5) App móvil
cd mobile/ahorra_mas_app
flutter run                                   # contra el núcleo (emulador Android usa 10.0.2.2)
flutter run --dart-define=FAKE_API=true       # demo sin backend
```

En Windows usa `mvnw.cmd`. Los datos del perfil local quedan en `backend/data/` (bórrala para empezar de cero).

**Emulador Android o teléfono físico**: la app abre la "banca en línea" del banco simulado en el
navegador del teléfono, que debe poder llegar a los servicios. Arranca el núcleo con
```bash
AHORRA_SIMULADORES_URL_PUBLICA=http://10.0.2.2:8090 AHORRA_URL_PUBLICA=http://10.0.2.2:8080 ./mvnw spring-boot:run
```
(o la IP de tu PC en la red local para un teléfono físico, junto con `--dart-define=API_BASE_URL=http://<IP>:8080/api/v1`).

### Con Docker (PostgreSQL real)

```bash
docker compose up --build
```

## Demo guiada

1. **Registro** en la app (cualquier cédula; terminada en `666` → rechazo por listas de sanciones) y KYC (cédula + selfie).
2. **Vincular bancos** (selección múltiple): se abre la banca del simulador; escribe cualquier usuario/clave *allí* y pulsa *Autorizar*. Ahorra+ nunca la ve.
3. **Inicio**: posición global en Bs y USD a la tasa BCV del día.
4. **Cofre**: crea una meta ("Viaje en marzo, $300") → *Ruta del día* ("Hoy toca apartar $2,30…") → aparta la cuota en un toque → racha y puntos. Activa una regla de *10 % de cada ingreso* (PIN) e inyecta una nómina desde la consola del simulador (`http://localhost:8090`) → sincroniza → aporte automático. Sella una parte; si retiras lo sellado, pierdes la racha (no el dinero).
5. **Más → Ajustes → Avanzar 1 día** (modo demo): cierre del día → días colchón, conciliación.
6. **Pagar TDC**: confirma el titular, PIN + OTP (el OTP aparece en la app en modo demo y en la consola del simulador). Montos de prueba según los **céntimos**:

   | Termina en | Escenario |
   |---|---|
   | `,13` | el suiche tarda (timeout) → *en proceso* → se consulta con la misma referencia → aplicado |
   | `,51` | rechazo por fondos insuficientes (sin débito ni comisión) |
   | `,77` | el emisor rechaza la captura → reverso |
   | `,99` / `,98` | diferencia / faltante en la conciliación → reverso + caso en backoffice |

7. **Backoffice**: `http://localhost:8080/backoffice/` — operadores `soporte@`, `conciliacion@`, `cumplimiento@`, `admin@ahorramas.test`, clave `demo`. Pagos, conciliación (hash SHA-256 del archivo del suiche), casos, alertas AML → ROS a la UNIF, límites y auditoría con cadena de hashes.

## Pruebas

```bash
cd backend && ./mvnw test                 # 68 pruebas: arquitectura, dominio, integración y OE4 (500 transacciones)
cd mocks/simuladores && ./mvnw test       # 30 pruebas de los simuladores
cd mobile/ahorra_mas_app && flutter test  # 55 pruebas de la app
```

`ArquitecturaTest` verifica los límites del monolito modular (Spring Modulith), las capas
hexagonales (ArchUnit) y que el dominio no use punto flotante para dinero; genera la documentación de
módulos en `backend/target/spring-modulith-docs`.

> Simulación académica: Ahorra+ no cuenta con autorización de SUDEBAN; los bancos, el suiche, el BCV
> y los demás sistemas externos son simulados. El Espejo de Impacto y el asistente de IA son
> informativos y no constituyen asesoría financiera.
