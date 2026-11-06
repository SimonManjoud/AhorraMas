# Tablero de KPIs del prototipo · Ahorra+

Interfaz web (HTML + CSS + JS, **sin dependencias ni build**) que muestra los cuatro KPIs técnicos del pago de
TDC interbancario medidos en el prototipo:

| KPI | Definición exacta | Fuente en el ledger |
|---|---|---|
| **Tasa de aprobación** | aprobados ÷ (aprobados + rechazados). Aprobado = el suiche aprobó la captura (`CAPTURADO`, `CONCILIADO`, `CON_DIFERENCIA`, `REVERSADO` con captura). Los pagos en curso no cuentan. | `pagos.pago_tdc` |
| **Latencia p95** | percentil 95 (nearest-rank) de `fecha_hora_respuesta − fecha_hora_envio` de cada mensaje al suiche. Los timeouts se cuentan aparte. También p50/p99/máx y latencia extremo a extremo. | `pagos.mensaje_suiche`, `historial_estado_pago` |
| **TPS** | promedio = pagos creados ÷ segundos de la ventana; **pico** = máximo de pagos creados en un mismo segundo (y por minuto, y mensajes/s al suiche). | `pago_tdc.fecha_hora_creacion` |
| **Tasa de reversos** | reversados ÷ aprobados, con el origen del reverso según el historial (`CON_DIFERENCIA`, `EN_VERIFICACION`, `CAPTURADO` → `REVERSADO`). | `pago_tdc`, `historial_estado_pago` |

Las definiciones viven en **`backend/.../pagos/dominio/CalculadoraKpis.java`** (dominio puro, aritmética entera) y
se replican línea a línea en `datos-demo.js`, de modo que el tablero mide exactamente lo mismo en demo y en vivo.

## Cómo abrirlo

```bash
# Opción A · servidor estático (recomendado)
cd tablero-kpis && python -m http.server 8070      # → http://localhost:8070
# Opción B · abrir index.html directamente en el navegador (también funciona)
```

### Modo **Demo** (por defecto)
No necesita backend. Genera pagos sintéticos deterministas con los mismos escenarios del simulador del suiche
(`monto % 100`: `13` timeout → verificación, `51` fondos insuficientes, `77` captura rechazada → reverso de la
autorización, `98`/`99` diferencia en conciliación → reverso), un perfil horario de llegadas y una ráfaga de carga
diaria a las 15:12 (36 pagos en 9 s) para que el pico de TPS sea visible.

### Modo **En vivo**
1. Arranca simuladores y núcleo (`README.md` de la raíz).
2. Pulsa **En vivo**, deja `http://localhost:8080` y entra con un operador semilla
   (`admin@ahorramas.test` / `demo`; también `conciliacion@…`, `soporte@…`).
3. El tablero consume `GET /api/v1/backoffice/kpis?rango=24h&bucket=auto` (requiere JWT de operador; CORS
   habilitado solo para `/api/v1/backoffice/**` y `/api/v1/demo/**`).
4. Genera tráfico con la app Flutter o con `scripts/prueba_e2e.py`, y usa **Avanzar 1 día** (modo demo del núcleo)
   para que corra la conciliación y aparezcan los reversos por diferencia.

Parámetros del endpoint: `rango` = `hoy | 1h | 24h | 7d | 30d` (relativo al reloj del negocio, que en demo puede
estar adelantado) o `desde`/`hasta` ISO-8601; `bucket` = `auto | minuto | hora | dia` (máx. 2 000 intervalos, 92 días).

## Qué hay en la interfaz

* **Fila de KPIs**: valor, semáforo con icono y texto (nunca solo color), variación frente al periodo anterior de
  igual longitud, sparkline de 12 puntos y la meta de referencia aplicada. El TPS lleva un medidor contra la capacidad.
* **Una sola fila de filtros** (ventana e intervalo) que re-calcula todo el tablero sobre la misma rebanada;
  actualización automática cada 30 s opcional; el frame anterior se mantiene atenuado mientras carga.
* **Gráficos SVG propios** (`graficos.js`): líneas con crosshair y tooltip de todas las series, columnas apiladas con
  hueco de superficie, barras ordenadas de un solo tono, barra segmentada de estados y medidor. Marcas finas,
  rejilla hairline, un solo eje por gráfico, etiquetas directas selectivas, foco de teclado equivalente al hover
  (flechas ←/→) y **vista de tabla** gemela en cada tarjeta.
* **Tabla de últimos pagos** con el escenario del simulador, latencia de autorización y latencia total.
* **Sección "Cómo se mide"** con fórmula, inclusiones/exclusiones y tablas fuente de cada KPI.
* **Metas de referencia** editables (⊙): aprobación ≥ 95 %, p95 ≤ 800 ms, capacidad 10 TPS, reversos ≤ 1 %.
  Se guardan en `localStorage` del navegador.
* Tema claro/oscuro (sigue al sistema; conmutable) con paleta validada para daltonismo
  (`dataviz/validate_palette.js`: categórica azul/naranja/aqua, rampa ordinal azul para p50/p95, estados fijos).

## Archivos

| Archivo | Contenido |
|---|---|
| `index.html` | Estructura, textos y definiciones |
| `estilos.css` | Tokens de color por rol (claro/oscuro), tarjetas, filtros, tablas, tooltip |
| `graficos.js` | Mini-librería SVG (líneas, columnas, barras, sparkline, segmentada, medidor, tabla) |
| `datos-demo.js` | Réplica JS de `CalculadoraKpis` + generador determinista de pagos sintéticos |
| `tablero.js` | Estado, filtros, modo demo/en vivo, login de operador, render de KPIs y gráficos |
