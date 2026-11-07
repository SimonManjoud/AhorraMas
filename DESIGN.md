---
name: Ahorra+
description: La libreta viva de tu dinero en Bs y USD a tasa BCV, sin custodiar fondos ni pedir claves.
colors:
  vino-tapa: "#5C1726"
  vino-hondo: "#43101C"
  sobre-tapa: "#F7ECEE"
  sobre-tapa-tenue: "#DDBBC3"
  rosa-vino: "#EBD3D8"
  hoja: "#EEF2EE"
  hoja-honda: "#E1E8E2"
  lamina: "#FBFCFA"
  regla: "#B4C2B7"
  regla-suave: "#DCE4DD"
  tinta: "#1B1F1C"
  tinta-gris: "#47524A"
  sello-violeta: "#5A3E9E"
  tinta-roja: "#A3271F"
  tinta-verde: "#2F6B45"
  serie-1: "#2A78D6"
  serie-2: "#EB6834"
  serie-3: "#1BAF7A"
  serie-4: "#EDA100"
  serie-5: "#E87BA4"
  serie-6: "#008300"
  serie-7: "#6250D6"
  serie-8: "#E34948"
typography:
  display:
    fontFamily: "Archivo Condensed, Roboto, sans-serif"
    fontSize: "56sp"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.018em"
    fontFeature: "'tnum' 1"
  headline:
    fontFamily: "Archivo, Roboto, sans-serif"
    fontSize: "26sp"
    fontWeight: 800
    lineHeight: 1.15
    letterSpacing: "-0.02em"
  title:
    fontFamily: "Archivo, Roboto, sans-serif"
    fontSize: "17sp"
    fontWeight: 700
    lineHeight: 1.25
  body:
    fontFamily: "Archivo, Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: 1.4
  label:
    fontFamily: "Archivo, Roboto, sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: 1.3
  cifra:
    fontFamily: "Archivo, Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 600
    lineHeight: 1.3
    fontFeature: "'tnum' 1"
rounded:
  sm: "12px"
  md: "16px"
  lg: "24px"
  xl: "28px"
  full: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  xxl: "28px"
components:
  button-primary:
    backgroundColor: "{colors.vino-tapa}"
    textColor: "{colors.sobre-tapa}"
    typography: "{typography.title}"
    rounded: "{rounded.full}"
    height: "52px"
    padding: "0 24px"
  button-primary-pressed:
    backgroundColor: "{colors.vino-hondo}"
    textColor: "{colors.sobre-tapa}"
  button-secondary:
    backgroundColor: "{colors.lamina}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.full}"
    height: "48px"
    padding: "0 20px"
  lamina:
    backgroundColor: "{colors.lamina}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.lg}"
    padding: "18px"
  tapa-card:
    backgroundColor: "{colors.vino-tapa}"
    textColor: "{colors.sobre-tapa}"
    rounded: "{rounded.xl}"
    padding: "18px 20px"
  input:
    backgroundColor: "{colors.lamina}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.md}"
    padding: "16px 18px"
  day-mark:
    backgroundColor: "{colors.vino-tapa}"
    textColor: "{colors.sobre-tapa}"
    typography: "{typography.cifra}"
    rounded: "9px"
    height: "28px"
  nav-indicator:
    backgroundColor: "{colors.rosa-vino}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.full}"
    height: "32px"
  nav-bar:
    backgroundColor: "{colors.lamina}"
    textColor: "{colors.tinta-gris}"
    height: "72px"
---

<!-- Conciliado con el código el 2026-11-07 (versión 2, "moderna"): tokens de `mobile/ahorra_mas_app/lib/core/theme/app_theme.dart`, `lib/core/widgets/libreta.dart`, `lib/features/consolidacion/presentation/widgets/grafico_posicion.dart` y `lib/features/pagos/presentation/widgets/tarjeta_visual.dart`. Capturas verificadas a 360×800 en claro y oscuro: `.impeccable/review/v2/`. La versión 1 (guilloché, reglas verticales, monoespaciada, sello de tasa) se descartó a pedido del usuario. -->

# Design System: Ahorra+

## Overview

**Creative North Star: "La Libreta Viva"** (versión moderna)

Ahorra+ sigue siendo la libreta de ahorros que todo venezolano reconoce: una tapa vino tinto y hojas claras donde el dinero se lee de un vistazo, en Bs y USD a tasa BCV. En esta versión la libreta se vuelve una app táctil y cálida. El fondo es liso, sin tramas ni rayas. El contenido vive en láminas claras con esquinas de 24px. Los botones son píldoras. Las cifras son grandes, con números tabulares y sin aire de terminal. El color con voz propia aparece donde ayuda a entender: el vino en la tapa y en la acción principal, el violeta del Sello de compromiso, y una paleta categórica viva solo en los gráficos.

Se rechaza explícitamente la estética cripto o de casino (neón, brillos, celebraciones estridentes), la fintech gringa genérica (verde dinero, degradados de saldo en la pantalla principal) y lo infantil (ilustraciones, mascotas, confeti). El único degradado permitido es el de la tarjeta de crédito dibujada, porque representa un objeto físico.

**Key Characteristics:**
- La tapa vino (tarjeta de 28px) lleva la posición global en USD, su equivalente en Bs y la tasa BCV en una píldora.
- Fondo verde pálido liso con láminas claras de 24px encima. Sin guilloché, sin reglas verticales.
- Botones píldora de 52dp; campos rellenos con esquinas de 16px; teclado de PIN en círculos.
- Todo monto en Bs y USD, con la tasa BCV y su fecha a la vista.
- La distribución del dinero se ve en una dona interactiva por cuenta.

## Colors

### Primary
- **Vino Tapa** (#5C1726): la tapa. Tarjeta de posición global, botón primario, logotipo y marcas de racha. En oscuro se aclara a #7A2236.
- **Vino Hondo** (#43101C): estado presionado del botón primario.
- **Sobre Tapa** (#F7ECEE) / **Sobre Tapa Tenue** (#DDBBC3): texto principal y secundario sobre la tapa.
- **Rosa Vino** (#EBD3D8, `primaryContainer`): indicador de la barra de navegación, segmento elegido del selector V/E y avatares de acción. Es la versión suave del vino para elementos seleccionados.

### Tertiary
- **Sello Violeta** (#5A3E9E): solo para el Sello de compromiso, que se dibuja como insignia con candado sobre `tertiaryContainer` (#E6DEF6). Va una por vista. Nunca en botones ni en texto corrido.

### Neutral
- **Hoja** (#EEF2EE): fondo liso de toda la app y de la barra superior.
- **Hoja Honda** (#E1E8E2): pistas de progreso, rellenos de iconos y marcas de día sin aporte.
- **Lámina** (#FBFCFA): láminas, campos, teclas del PIN y barra de navegación.
- **Regla** (#B4C2B7) / **Regla Suave** (#DCE4DD): separadores finos y bordes de campo.
- **Tinta** (#1B1F1C) / **Tinta Gris** (#47524A): texto principal y secundario.
- **Tinta Roja** (#A3271F): errores y fechas de pago urgentes.
- **Tinta Verde** (#2F6B45): confirmaciones (cuota cumplida).

### Paleta de gráficos (categórica)
Ocho colores en orden fijo (azul, naranja, aguamarina, ámbar, rosa, verde, violeta, rojo). Se validaron con el validador de dataviz contra la lámina clara #FBFCFA y la oscura #1A211C: pasan la banda de luminosidad, el croma mínimo, la separación para daltonismo y el umbral de visión normal. El modo oscuro usa sus propios pasos (#3987E5, #D95926, #199E70, #C98500, #D55181, #008300, #9085E9, #E66767). En claro, aguamarina, ámbar y rosa quedan bajo 3:1 de contraste, así que la leyenda-lista con nombre, monto y porcentaje siempre está visible.
- El color sigue a la cuenta, nunca a su tamaño: se asigna por el orden estable de la lista.
- Más de 8 cuentas: la 8.ª en adelante se agrupan en "Otras" en gris (#9AA59D claro, #6B756E oscuro).
- Nunca se usan los colores de serie para texto: las cifras y etiquetas van en Tinta o Tinta Gris.

### Named Rules
**The Un Sello Rule.** Cada vista lleva como máximo una insignia de Sello de compromiso.

**The Dos Monedas Rule.** Ningún monto aparece solo: Bs y USD van juntos, con la tasa BCV y su fecha alcanzables en la misma vista.

**The Fondo Liso Rule.** El fondo no lleva tramas, rayas ni patrones. La textura viene de las láminas y del color, no del papel.

### Libreta de noche (tema oscuro)
El tema oscuro es un esquema propio, no una inversión: hoja #111613, lámina #1A211C, hoja honda #232B25, tinta #E2EAE3, tinta gris #A9B8AD, regla suave #2A322C; tapa #7A2236; sello #A794E8; tinta roja #FFB4A8; tinta verde #8FD3A6. El `primary` oscuro es #F2B7C3 (texto de botones de texto); los botones primarios siguen pintándose con la tapa.

## Typography

**Display Font:** Archivo Condensed (familia `ArchivoCondensado`, estática 600/700/800), solo para la cifra principal.
**Body Font:** Archivo (estática 400–800) para todo lo demás, incluidas las cifras con números tabulares.

Las dos familias van empaquetadas como TTF estáticos en `mobile/ahorra_mas_app/assets/fonts/` (licencia OFL). JetBrains Mono se retiró: daba un aire de terminal.

### Hierarchy
- **Display** (Condensed 700, 56sp, tabular): el total en USD de la tapa y la cifra de días en Racha. Uno por pantalla.
- **Headline** (Archivo 800, 30/26sp; 700 a 22sp): "Hola de nuevo", "Hoy toca apartar $1,47", estados vacíos.
- **Title** (700, 17sp): títulos de lámina ("¿Dónde está tu dinero?"), nombres de metas, barra superior (800 a 22sp).
- **Body** (400, 14sp): explicaciones y avisos, 65–75 caracteres por línea.
- **Label** (500, 12sp, mayúsculas y minúsculas): tipo de cuenta, encabezados de bloque. Ya no hay rúbricas en mayúsculas.
- **Cifra** (Archivo 600–800, tabular): montos, tasas, porcentajes y fechas (07 nov, 15 mar 2027).

## Layout

La app es de una columna en el teléfono, con 16px de margen lateral y láminas separadas por 12–16px. La barra superior va sobre la Hoja con el título en Tinta; en Inicio lleva el logotipo y "Hola, María".

Orden de las pantallas firma:
- **Login, paso 1:** marca, "Hola de nuevo", selector V/E y cédula, "Continuar", registro, regla de oro.
- **Login, paso 2:** volver con la cédula en un chip; "Hola de nuevo, María" (si el equipo la recuerda); desplegable **Mis bancos**; PIN en teclado circular; "Entrar".
- **Inicio:** tapa vino (posición global, USD, Bs, píldora de tasa BCV) → línea de vínculos → lámina "¿Dónde está tu dinero?" (dona, detalle de la porción y lista de cuentas por institución con total) → Ruta de hoy → "Vincular bancos", regla de oro y hora de sincronización.
- **Cofre:** lámina de la meta del día con "Hoy toca apartar" y su botón → lámina de racha → lámina del Espejo de Impacto → "Mis metas" y una lámina por meta.
- **Pagar:** límite diario → una lámina por tarjeta (tarjeta dibujada, estado de cuenta, "Pagar esta tarjeta") → historial.

*Pendiente:* en anchos de tableta, riel de navegación y dos columnas.

## Elevation & Depth

Casi todo es plano y se separa por tono: la lámina clara sobre la hoja. Hay dos excepciones: la barra de navegación lleva una sombra suave hacia arriba (16px, 8 % de negro) y la tarjeta de crédito dibujada lleva sombra de objeto (18px y 4px). Diálogos (28px), hojas inferiores (28px arriba) y snackbars (16px) van sobre la lámina sin tinte de superficie.

## Shapes

- 24px: láminas. 28px: tapa, diálogos y hojas inferiores. 20px: la tarjeta de crédito.
- 16px: campos y snackbars. 12–14px: insignias, filas tocables y logotipo.
- Píldora: botones, chips, etiquetas de estado, píldora de tasa e indicador de navegación.
- Círculo: teclas del PIN, muestras de color y avatares de icono.

## Components

### Buttons
- **Primary:** píldora de 52dp, Vino Tapa con texto Sobre Tapa (Archivo 700, 15sp). Puede llevar el equivalente en Bs al 80 % ("Apartar $1,47 · Bs 733,14"). Uno por vista.
- **Secondary (outlined):** píldora de 48dp, fondo Lámina, borde de 1,5px de Regla Suave.
- **Text:** píldora, texto en `primary` (vino en claro, rosa en oscuro).
- **Etiqueta:** si "Apartar $x" y su equivalente en Bs no caben, se reducen juntos; nunca se cortan.

### Píldora de tasa BCV
Reemplaza al sello circular. Va dentro de la tapa como una píldora translúcida (12 % de blanco) con un icono de cambio de divisas y el texto "1 US$ = **Bs 498,73** · Tasa BCV 07 nov". Fuera de la tapa usa Hoja Honda.

### Dona de posición global (componente firma)
- **Librería:** `fl_chart` 1.3 (PieChart), con anillo de 66px de hueco y 36px de grosor, separación de 3px y esquinas de 6px.
- **Entrada:** barrido de 900ms desde las 12 en sentido horario (easeOutCubic). Sin animación si el sistema pide quitarlas.
- **Selección:** tocar una porción la agranda a 46px y atenúa las demás al 28 % en 280ms. El centro cambia de "En USD / total / N cuentas" a "46 % / Ahorro en divisas". Debajo se abre un panel con el color de la porción y cuatro etiquetas: institución, Banco o "Cripto · solo lectura", tipo de cuenta y moneda con los últimos 4 dígitos. También muestra el monto en USD y Bs, el % del total y un acceso a Movimientos. Tocar otra vez la porción, o la ×, cierra el panel.
- **Leyenda:** la propia lista de cuentas agrupada por institución, cada fila con su muestra de color, monto en USD y porcentaje (o Bs si la cuenta no suma). Tocar una fila abre la cuenta.
- **Accesibilidad:** etiqueta semántica con el reparto completo ("Nómina 7,1 %, Ahorro en divisas 46 %…").

### Tarjeta de crédito dibujada
- Proporción 1,586 y esquinas de 20px, con un degradado del tono del banco a casi negro y dos brillos circulares suaves.
- **Frente:** banco, red en texto espaciado (VISA / MASTERCARD), chip dorado, icono contactless, "••••  ••••  ••••  7781", "VENCE ••/••", titular y logo de la red (VISA en itálica 800; Mastercard con dos círculos #EB001B / #F79E1B y su intersección #FF5F00).
- **Reverso:** banda magnética con trama diagonal, franja de firma, "CVV ***" y "Ahorra+ solo conoce los últimos 4 dígitos de tu tarjeta."
- **Giro:** al tocarla gira en Y durante 700ms (easeInOutCubic, perspectiva 0,0012). Sin animaciones, el cambio es instantáneo.
- **Datos:** la red llega del banco emisor como `marca` (`visa|mastercard`, nullable) en `GET /tarjetas`. Sin marca, no se dibuja logo. El número completo nunca existe en Ahorra+.

### Mis bancos (login)
Es un desplegable en una lámina. La cabecera tiene un icono de banco, "Mis bancos (3)" y un resumen ("Banco Ávila, Banco Orinoco y 1 más"), y el chevron gira al abrir.
- **Sin saldo rápido:** lista los bancos que este equipo recuerda del último ingreso, con "••••" en vez de montos y una nota para activar Saldo rápido.
- **Con saldo rápido activo en este equipo:** consulta `GET /saldo-rapido` (token de solo lectura ligado al dispositivo) y muestra el total y el monto de cada institución en USD y Bs.
- **Otra cédula o equipo nuevo:** no muestra bancos. La lista nunca sale de una consulta pública por cédula. Si entra otra persona, el token de saldo rápido del usuario anterior se borra.

### Sello de compromiso
Es una insignia de 14px de radio en `tertiaryContainer`, con candado violeta, "Sellado" y "$50,00 · 15 mar". Aparece con un leve rebote (0,92 → 1 en 240ms).

### Marcas de racha
- **Aporte:** cuadrado de 28dp y 9px de radio en Vino Tapa, con el día en Sobre Tapa.
- **Día colchón:** el mismo cuadrado con contorno punteado de Tinta; se lee como un día cubierto, no como un fallo.
- **Sin aporte:** cuadrado en Hoja Honda.

### Progreso de meta
Barra redondeada de 10px en Vino Tapa sobre una pista de Hoja Honda, con "$112,40 de $300,00" y la fecha destino.

### Inputs / Fields
- **Estilo:** relleno de Lámina, borde de 1px en Regla Suave y esquinas de 16px.
- **Enfoque:** borde de 2px en `primary`.
- **Error:** borde y mensaje en Tinta Roja. El mensaje dice el problema y cómo resolverlo.

### PIN
Seis puntos de estado y un teclado de círculos de 58dp en Lámina, con onda al tocar.

### Navigation
Barra inferior Material 3 de 72dp sobre Lámina, con sombra suave hacia arriba. El indicador es una píldora Rosa Vino. Los iconos son outlined en reposo y redondeados rellenos al seleccionar.

## Do's and Don'ts

### Do:
- **Do** muestra cada monto en Bs y USD, con la tasa BCV y su fecha en la misma vista.
- **Do** pon el contenido en láminas de 24px sobre el fondo liso.
- **Do** usa la paleta categórica solo en gráficos, en orden fijo, con leyenda-lista visible.
- **Do** pinta los días colchón como días cubiertos (contorno punteado), nunca como error.
- **Do** mantén "Simulación informativa, no asesoría." bajo el Espejo de Impacto y el asistente de IA.
- **Do** muestra solo los últimos 4 dígitos de cualquier tarjeta o cuenta.

### Don't:
- **Don't** vuelvas a poner tramas, guilloché o rayas en el fondo.
- **Don't** uses neón, brillos ni celebraciones estridentes: Ahorra+ no es cripto ni casino.
- **Don't** uses verde dinero ni degradados de saldo en la pantalla principal.
- **Don't** uses ilustraciones, mascotas ni confeti.
- **Don't** muestres montos antes del PIN salvo con saldo rápido activo en el equipo de confianza.
- **Don't** pintes un día sin aporte ni un sello roto en Tinta Roja: romper un sello cuesta el multiplicador, nunca dinero.
