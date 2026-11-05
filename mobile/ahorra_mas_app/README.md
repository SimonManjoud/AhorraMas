# Ahorra+ — App móvil (Flutter)

App móvil del MVP de **Ahorra+** (Impulsa Tech, C.A.). Consume el núcleo (monolito modular en
Spring Boot) **exclusivamente** a través del contrato REST de `docs/contrato-api.md`.

Incluye: consolidación multibanco (Bs y USD a tasa BCV), **Cofre Protector** (Ruta del día,
racha con días colchón, Espejo de Impacto, Sello de compromiso, reglas automáticas), pago de TDC
interbancario (F2) con PIN + OTP e idempotencia, saldo rápido (F3), Puntos Ahorra y niveles,
asistente de perfil de riesgo con IA, notificaciones y ajustes de seguridad.

Principios visibles en la UX:

* **Nunca pedimos la clave del banco**: "Vincular bancos" abre la página oficial de cada banco
  (`url_launcher`) y la app solo consulta el estado del consentimiento.
* Todo el dinero se muestra en **Bs y USD a la tasa BCV** del día.
* Operaciones sensibles piden **PIN** (step-up `pin`); los pagos de TDC piden **PIN + OTP**
  (`pin_otp`).
* La IA **nunca actúa sola**: aceptar o rechazar una recomendación exige PIN.
* El Espejo de Impacto y el asistente IA muestran el aviso **"Simulación informativa, no asesoría"**.

## Requisitos

* Flutter 3.47 / Dart 3.13 (probado con Flutter 3.47.9).
* Para Android: Android SDK + emulador. Para escritorio Windows: activar el **Modo de
  desarrollador** de Windows (los plugins necesitan enlaces simbólicos).

## Cómo ejecutar

```bash
cd mobile/ahorra_mas_app
flutter pub get
```

### 1. Modo demo, sin backend (API falsa en memoria)

```bash
flutter run --dart-define=FAKE_API=true                 # dispositivo/emulador conectado
flutter run -d chrome --dart-define=FAKE_API=true       # navegador
flutter run -d windows --dart-define=FAKE_API=true      # escritorio (requiere Modo desarrollador)
```

Datos de demostración:

| Dato | Valor |
|---|---|
| Usuario | **V-12345678**, PIN **1234** (KYC aprobado) |
| Bancos vinculados | Banco Ávila, Banco Orinoco, CriptoAndes (USDT, solo lectura) |
| Metas | "Viaje en marzo" ($300, diaria, con sello de $50) y "Fondo de emergencia" ($500, semanal) |
| Racha | 16 días (2 días colchón usados), multiplicador 1,25x |
| OTP | Se muestra en un banner "Modo demo" dentro de la hoja de confirmación |

Reglas de la API falsa para demostrar estados del pago de TDC:

* Monto con céntimos **77** (p. ej. `1.000,77`) ⇒ `EN_VERIFICACION` ("en proceso"); al tocar
  "Actualizar estado" un par de veces pasa a `CAPTURADO`.
* Monto + comisión mayor al saldo de la cuenta origen ⇒ `RECHAZADO` con motivo.
* Cualquier otro monto ⇒ `CAPTURADO` (+50 puntos por pago puntual).
* La misma `Idempotency-Key` devuelve el mismo pago (nunca duplica).
* "Vincular bancos" simula la autorización del banco al "abrir" el enlace.
* En *Más → Perfil y seguridad → Modo demo* puedes avanzar el reloj 1 día para ver el uso de
  un día colchón.

> La API falsa es de un solo usuario: los datos (bancos, metas) son compartidos aunque
> registres otra cédula.

### 2. Contra el backend real (por defecto)

El backend debe estar corriendo en el puerto 8080 (`backend/`). La URL base se elige así:

| Dónde corre la app | URL por defecto |
|---|---|
| Emulador Android | `http://10.0.2.2:8080/api/v1` |
| Web / Windows / iOS simulador | `http://localhost:8080/api/v1` |

```bash
flutter run                                   # usa la URL por defecto según la plataforma
flutter run --dart-define=API_BASE_URL=http://192.168.1.50:8080/api/v1   # teléfono físico en la LAN
```

El `AndroidManifest.xml` declara `INTERNET` y `usesCleartextTraffic="true"` (HTTP local de
desarrollo); el `Info.plist` de iOS permite red local y cámara/galería para el KYC.

Para la vinculación de bancos con los simuladores (`mocks/`, puerto 8090), el navegador del
dispositivo debe poder abrir la `urlAutorizacion` que devuelve el backend (en el emulador Android
`localhost` no es la máquina anfitriona: el backend debería generar URLs con un host alcanzable).

## Estructura

```
lib/
├── main.dart                 # arranque (fechas en español, ProviderScope sin reintentos)
├── app/                      # MaterialApp, router (go_router) y navegación inferior
├── core/
│   ├── api/                  # ApiClient HTTP (headers, errores {codigo,mensaje,detalles}, reintento seguro)
│   ├── config/               # AppConfig: API_BASE_URL y FAKE_API (--dart-define)
│   ├── di/                   # composición: adaptadores HTTP reales o API falsa
│   ├── money/                # Dinero: céntimos enteros, formato es_VE, conversión BCV, parseo
│   ├── session/              # sesión en memoria (Riverpod Notifier)
│   ├── storage/              # almacén seguro (Keychain/Keystore) abstraído
│   ├── theme/                # Material 3 claro/oscuro: verde azulado + ámbar del Cofre
│   ├── util/                 # fechas
│   └── widgets/              # step-up (PIN / PIN+OTP), teclado PIN, montos, avisos
├── features/<módulo>/
│   ├── domain/               # modelos y puertos (interfaces de repositorio, casos de uso)
│   ├── data/                 # DTOs (JSON del contrato ⇄ dominio) + repositorio HTTP
│   └── presentation/         # pantallas, widgets y providers
│       módulos: auth, consolidacion, cofre, pagos, puntos, asistente, notificaciones, perfil
└── fake/fake_api.dart        # API falsa en memoria que implementa todos los puertos
test/
├── core/dinero_test.dart             # formato, conversión y parseo de montos
├── data/dto_contrato_test.dart       # parseo de los JSON de ejemplo del contrato
├── data/idempotencia_test.dart       # misma Idempotency-Key en reintentos (HTTP y caso de uso)
├── fake/fake_api_test.dart           # reglas de negocio simuladas (R-09, sello, colchón…)
└── widget/                           # login → home, Ruta del día, resultado EN_VERIFICACION,
                                      # recorrido de pantallas, pago completo, KYC
```

Arquitectura: *feature-first* con capas limpias ligeras. La presentación solo conoce los puertos
del dominio; `core/di/providers.dart` decide si inyecta el adaptador HTTP o la API falsa.

### Dinero

* Todos los montos son `int` en **céntimos** (`...Cts`), como en el contrato. Nunca `double`.
* `Dinero.bs(123456)` → `Bs 1.234,56`; `Dinero.usd(1234)` → `$12,34`.
* `Dinero.usdABs` / `bsAUsd` usan la tasa `valorBsX10000` con aritmética entera y redondeo half-up.
* `Dinero.parsear("2,40" | "2.40" | "1.234,56" | "1,234.56")` → céntimos o `null`.

### Idempotencia de pagos

`ServicioPagoTdc` crea un `IntentoPago` con un UUID v4 **una vez por intento**. Si falla la red y
el usuario toca "Reintentar", o el `ApiClient` reintenta solo, se envía **la misma**
`Idempotency-Key`. Cambiar tarjeta/cuenta/monto crea un intento nuevo.

## Paquetes

`flutter_riverpod` (estado), `go_router` (rutas), `http`, `flutter_secure_storage` (token de
saldo rápido, huella del equipo, última cédula), `url_launcher`, `intl` (fechas en español),
`uuid`, `image_picker` (KYC, con imagen de demostración si no hay cámara),
`flutter_localizations`.

## Verificación

```bash
flutter analyze      # sin issues
flutter test         # unitarias + widgets (usan la API falsa)
flutter build web    # compila
```
