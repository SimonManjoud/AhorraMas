/* =====================================================================================
   Gráficos SVG sin dependencias para el tablero de KPIs.
   Reglas aplicadas (método dataviz): un solo eje por gráfico, marcas finas (línea 2 px, columna ≤ 24 px, extremo
   redondeado 4 px), rejilla hairline sólida, hueco de 2 px entre segmentos, texto siempre en tokens de tinta,
   crosshair + tooltip con todas las series, foco de teclado equivalente al hover, y vista de tabla gemela.
   ===================================================================================== */
const Graficos = (() => {
  const NS = 'http://www.w3.org/2000/svg';

  function el(tag, attrs = {}, parent) {
    const n = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attrs)) if (v !== undefined && v !== null) n.setAttribute(k, v);
    if (parent) parent.appendChild(n);
    return n;
  }
  function texto(parent, x, y, contenido, clase, extra = {}) {
    const t = el('text', { x, y, class: clase, ...extra }, parent);
    t.textContent = contenido;
    return t;
  }
  function limpiar(contenedor) { while (contenedor.firstChild) contenedor.removeChild(contenedor.firstChild); }
  function cssVar(nombre) { return getComputedStyle(document.documentElement).getPropertyValue(nombre).trim(); }
  function color(c) { return c && c.startsWith('--') ? `var(${c})` : c; }
  const anchoTexto = (s, tam = 11) => String(s).length * tam * 0.58;

  // ----------------------------------------------------------------- escala "bonita"
  function escala(max, pasos = 4, entero = false) {
    if (!(max > 0)) max = entero ? 2 : 1;
    const bruto = max / pasos;
    const mag = Math.pow(10, Math.floor(Math.log10(bruto)));
    const norm = bruto / mag;
    let paso = (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 2.5 ? 2.5 : norm <= 5 ? 5 : 10) * mag;
    if (entero) paso = Math.max(1, Math.ceil(paso));
    const tope = Math.ceil(max / paso) * paso;
    const ticks = [];
    for (let v = 0; v <= tope + 1e-9; v += paso) ticks.push(+v.toFixed(10));
    return { max: tope, ticks };
  }

  // ----------------------------------------------------------------- tooltip compartido
  let tip;
  function tooltip() {
    if (!tip) {
      tip = document.createElement('div');
      tip.className = 'tooltip';
      tip.setAttribute('role', 'status');
      document.body.appendChild(tip);
    }
    return tip;
  }
  function mostrarTooltip(x, y, titulo, filas) {
    const t = tooltip();
    limpiar(t);
    if (titulo) { const h = document.createElement('div'); h.className = 'titulo'; h.textContent = titulo; t.appendChild(h); }
    for (const f of filas) {
      const fila = document.createElement('div'); fila.className = 'fila';
      const k = document.createElement('span'); k.className = 'clave'; k.style.background = f.color ? color(f.color) : 'transparent'; fila.appendChild(k);
      const v = document.createElement('span'); v.className = 'valor'; v.textContent = f.valor; fila.appendChild(v);
      const n = document.createElement('span'); n.className = 'nombre'; n.textContent = f.nombre; fila.appendChild(n);
      t.appendChild(fila);
    }
    t.classList.add('visible');
    const r = t.getBoundingClientRect();
    let px = x + 14, py = y + 14;
    if (px + r.width > window.innerWidth - 8) px = x - r.width - 14;
    if (py + r.height > window.innerHeight - 8) py = y - r.height - 14;
    t.style.left = Math.max(8, px) + 'px';
    t.style.top = Math.max(8, py) + 'px';
  }
  function ocultarTooltip() { if (tip) tip.classList.remove('visible'); }

  function etiquetasX(n, etiquetas, plotW, x0) {
    // ~6 etiquetas repartidas, sin solaparse
    const cuantas = Math.max(2, Math.min(n, Math.floor(plotW / 90)));
    const salto = Math.max(1, Math.ceil((n - 1) / (cuantas - 1)));
    const indices = [];
    for (let i = 0; i < n; i += salto) indices.push(i);
    if (indices[indices.length - 1] !== n - 1 && n > 1) indices.push(n - 1);
    return indices;
  }

  // ----------------------------------------------------------------- líneas (con área opcional)
  /**
   * opciones: { series:[{nombre,color,valores:number|null[]}], etiquetasX:[corta], etiquetasLargas:[larga], formato(v), yMax?,
   *             meta?:{valor, etiqueta}, area?:bool, alto? }
   */
  function lineas(contenedor, o) {
    limpiar(contenedor);
    const W = Math.max(280, contenedor.clientWidth || 600), H = o.alto || 250;
    const m = { t: 18, r: o.meta ? 14 + anchoTexto(o.meta.etiqueta, 11.5) : 58, b: 28, l: 46 };
    const pw = W - m.l - m.r, ph = H - m.t - m.b;
    const n = o.etiquetasX.length;
    const valores = o.series.flatMap(s => s.valores.filter(v => v != null));
    const maxDatos = Math.max(0, ...valores, o.meta ? o.meta.valor : 0);
    const esc = o.yMax != null ? { max: o.yMax, ticks: [0, o.yMax / 4, o.yMax / 2, (3 * o.yMax) / 4, o.yMax] } : escala(maxDatos * 1.05, 4, o.entero);
    const svg = el('svg', { viewBox: `0 0 ${W} ${H}`, width: W, height: H, role: 'img', 'aria-label': o.aria || '' }, contenedor);
    const X = i => m.l + (n <= 1 ? pw / 2 : (i / (n - 1)) * pw);
    const Y = v => m.t + ph - (v / esc.max) * ph;

    // rejilla + eje Y
    for (const t of esc.ticks) {
      el('line', { x1: m.l, x2: m.l + pw, y1: Y(t), y2: Y(t), class: t === 0 ? 'g-eje' : 'g-rejilla' }, svg);
      texto(svg, m.l - 8, Y(t) + 4, o.formato(t, true), 'g-texto', { 'text-anchor': 'end' });
    }
    // eje X
    for (const i of etiquetasX(n, o.etiquetasX, pw)) {
      texto(svg, X(i), H - 8, o.etiquetasX[i], 'g-texto', { 'text-anchor': i === 0 ? 'start' : i === n - 1 ? 'end' : 'middle' });
    }
    // meta (umbral): sí es un umbral, por eso va discontinua
    if (o.meta) {
      el('line', { x1: m.l, x2: m.l + pw, y1: Y(o.meta.valor), y2: Y(o.meta.valor), class: 'g-meta' }, svg);
      texto(svg, m.l + pw + 6, Y(o.meta.valor) + 4, o.meta.etiqueta, 'g-texto-2');
    }
    // series
    o.series.forEach(s => {
      const c = color(s.color);
      let d = '', segmento = false;
      s.valores.forEach((v, i) => {
        if (v == null) { segmento = false; return; }
        d += (segmento ? ' L' : ' M') + X(i).toFixed(1) + ' ' + Y(v).toFixed(1);
        segmento = true;
      });
      if (o.area && n > 1) {
        // lavado al 10 %: solo tramos continuos
        let a = '', inicio = null;
        s.valores.forEach((v, i) => {
          if (v == null) { if (inicio != null) { a += ` L${X(i - 1).toFixed(1)} ${Y(0)} Z`; inicio = null; } return; }
          if (inicio == null) { inicio = i; a += ` M${X(i).toFixed(1)} ${Y(0)} L${X(i).toFixed(1)} ${Y(v).toFixed(1)}`; }
          else a += ` L${X(i).toFixed(1)} ${Y(v).toFixed(1)}`;
          if (i === n - 1) a += ` L${X(i).toFixed(1)} ${Y(0)} Z`;
        });
        el('path', { d: a, fill: c, opacity: 0.1 }, svg);
      }
      el('path', { d, fill: 'none', stroke: c, 'stroke-width': 2, 'stroke-linejoin': 'round', 'stroke-linecap': 'round' }, svg);
      // etiqueta directa al final (valor, en tinta) + marcador con anillo
      let ult = -1;
      for (let i = n - 1; i >= 0; i--) if (s.valores[i] != null) { ult = i; break; }
      if (ult >= 0) {
        el('circle', { cx: X(ult), cy: Y(s.valores[ult]), r: 6, fill: 'var(--superficie)' }, svg);
        el('circle', { cx: X(ult), cy: Y(s.valores[ult]), r: 4, fill: c }, svg);
        if (o.etiquetaFinal !== false && !o.meta) texto(svg, X(ult) + 9, Y(s.valores[ult]) + 4, o.formato(s.valores[ult]), 'g-texto-2');
      }
      if (o.pico && s === o.series[0]) {
        let mi = -1, mv = -Infinity;
        s.valores.forEach((v, i) => { if (v != null && v > mv) { mv = v; mi = i; } });
        if (mi >= 0 && mv > 0) {
          el('circle', { cx: X(mi), cy: Y(mv), r: 6, fill: 'var(--superficie)' }, svg);
          el('circle', { cx: X(mi), cy: Y(mv), r: 4, fill: c }, svg);
          texto(svg, Math.min(X(mi), m.l + pw - 60), Y(mv) - 9, 'pico ' + o.formato(mv), 'g-texto-2', { 'text-anchor': X(mi) > m.l + pw - 60 ? 'end' : 'start' });
        }
      }
    });

    // capa de hover: crosshair + tooltip con todas las series; teclado equivalente
    const cruz = el('line', { x1: 0, x2: 0, y1: m.t, y2: m.t + ph, class: 'g-cruz', visibility: 'hidden' }, svg);
    const marcas = o.series.map(s => {
      const g = el('g', { visibility: 'hidden' }, svg);
      el('circle', { r: 6, fill: 'var(--superficie)' }, g);
      el('circle', { r: 4, fill: color(s.color) }, g);
      return g;
    });
    const golpe = el('rect', { x: m.l, y: m.t, width: pw, height: ph, class: 'g-golpe', tabindex: 0,
      'aria-label': 'Usa las flechas izquierda y derecha para recorrer los valores' }, svg);
    let indice = -1;
    function mostrar(i, cx, cy) {
      indice = i;
      cruz.setAttribute('x1', X(i)); cruz.setAttribute('x2', X(i)); cruz.setAttribute('visibility', 'visible');
      o.series.forEach((s, k) => {
        const v = s.valores[i];
        marcas[k].setAttribute('visibility', v == null ? 'hidden' : 'visible');
        if (v != null) marcas[k].setAttribute('transform', `translate(${X(i)} ${Y(v)})`);
      });
      const filas = o.series.map(s => ({ color: s.color, nombre: s.nombre, valor: s.valores[i] == null ? '—' : o.formato(s.valores[i]) }));
      if (o.extra) filas.push(...o.extra(i));
      const r = svg.getBoundingClientRect();
      mostrarTooltip(cx ?? r.left + X(i) * (r.width / W), cy ?? r.top + m.t * (r.height / H), o.etiquetasLargas ? o.etiquetasLargas[i] : o.etiquetasX[i], filas);
    }
    function ocultar() { cruz.setAttribute('visibility', 'hidden'); marcas.forEach(g => g.setAttribute('visibility', 'hidden')); ocultarTooltip(); indice = -1; }
    golpe.addEventListener('pointermove', e => {
      const r = svg.getBoundingClientRect();
      const x = (e.clientX - r.left) * (W / r.width);
      const i = n <= 1 ? 0 : Math.round(((x - m.l) / pw) * (n - 1));
      mostrar(Math.max(0, Math.min(n - 1, i)), e.clientX, e.clientY);
    });
    golpe.addEventListener('pointerleave', ocultar);
    golpe.addEventListener('focus', () => mostrar(indice < 0 ? n - 1 : indice));
    golpe.addEventListener('blur', ocultar);
    golpe.addEventListener('keydown', e => {
      if (e.key === 'ArrowLeft') { e.preventDefault(); mostrar(Math.max(0, (indice < 0 ? n - 1 : indice) - 1)); }
      if (e.key === 'ArrowRight') { e.preventDefault(); mostrar(Math.min(n - 1, (indice < 0 ? n - 1 : indice) + 1)); }
      if (e.key === 'Escape') ocultar();
    });
    return svg;
  }

  // ----------------------------------------------------------------- columnas apiladas
  /** opciones: { categorias:[corta], etiquetasLargas, series:[{nombre,color,valores}], formato(v), alto? } */
  function columnas(contenedor, o) {
    limpiar(contenedor);
    const W = Math.max(280, contenedor.clientWidth || 600), H = o.alto || 250;
    const m = { t: 18, r: 16, b: 28, l: 46 };
    const pw = W - m.l - m.r, ph = H - m.t - m.b;
    const n = o.categorias.length;
    const totales = o.categorias.map((_, i) => o.series.reduce((a, s) => a + (s.valores[i] || 0), 0));
    const esc = escala(Math.max(0, ...totales) * 1.05, 4, o.entero);
    const svg = el('svg', { viewBox: `0 0 ${W} ${H}`, width: W, height: H, role: 'img', 'aria-label': o.aria || '' }, contenedor);
    const banda = pw / n, ancho = Math.min(24, Math.max(3, banda * 0.72));
    const Y = v => m.t + ph - (v / esc.max) * ph;
    for (const t of esc.ticks) {
      el('line', { x1: m.l, x2: m.l + pw, y1: Y(t), y2: Y(t), class: t === 0 ? 'g-eje' : 'g-rejilla' }, svg);
      texto(svg, m.l - 8, Y(t) + 4, o.formato(t, true), 'g-texto', { 'text-anchor': 'end' });
    }
    for (const i of etiquetasX(n, o.categorias, pw)) {
      texto(svg, m.l + banda * i + banda / 2, H - 8, o.categorias[i], 'g-texto', { 'text-anchor': 'middle' });
    }
    const grupos = [];
    o.categorias.forEach((_, i) => {
      const g = el('g', { class: 'g-columna' }, svg);
      grupos.push(g);
      const x = m.l + banda * i + (banda - ancho) / 2;
      let acumulado = 0;
      const visibles = o.series.filter(s => (s.valores[i] || 0) > 0);
      visibles.forEach((s, k) => {
        const v = s.valores[i];
        const y1 = Y(acumulado + v), y0 = Y(acumulado);
        const esTope = k === visibles.length - 1;
        const hueco = k === 0 ? 0 : 2; // 2 px de superficie entre segmentos
        const alto = Math.max(0, y0 - y1 - hueco);
        const yTop = y1;
        const r = esTope ? Math.min(4, alto / 2) : 0;
        const d = `M${x} ${yTop + alto} V${yTop + r} Q${x} ${yTop} ${x + r} ${yTop} H${x + ancho - r} Q${x + ancho} ${yTop} ${x + ancho} ${yTop + r} V${yTop + alto} Z`;
        el('path', { d, fill: color(s.color) }, g);
        acumulado += v;
      });
      // etiqueta directa selectiva: solo el máximo
    });
    const maxI = totales.indexOf(Math.max(...totales));
    if (maxI >= 0 && totales[maxI] > 0) texto(svg, m.l + banda * maxI + banda / 2, Y(totales[maxI]) - 6, o.formato(totales[maxI]), 'g-texto-2', { 'text-anchor': 'middle' });

    // hover por banda (el objetivo es más grande que la marca)
    o.categorias.forEach((cat, i) => {
      const golpe = el('rect', { x: m.l + banda * i, y: m.t, width: banda, height: ph, class: 'g-golpe', tabindex: 0 }, svg);
      const mostrar = (cx, cy) => {
        grupos.forEach((g, k) => g.classList.toggle('atenuada', k !== i));
        const r = svg.getBoundingClientRect();
        mostrarTooltip(cx ?? r.left + (m.l + banda * i + banda / 2) * (r.width / W), cy ?? r.top + m.t * (r.height / H),
          o.etiquetasLargas ? o.etiquetasLargas[i] : cat,
          [...o.series.map(s => ({ color: s.color, nombre: s.nombre, valor: o.formato(s.valores[i] || 0) })), { nombre: 'total', valor: o.formato(totales[i]) }]);
      };
      const ocultar = () => { grupos.forEach(g => g.classList.remove('atenuada')); ocultarTooltip(); };
      golpe.addEventListener('pointermove', e => mostrar(e.clientX, e.clientY));
      golpe.addEventListener('pointerleave', ocultar);
      golpe.addEventListener('focus', () => mostrar());
      golpe.addEventListener('blur', ocultar);
    });
    return svg;
  }

  // ----------------------------------------------------------------- barras horizontales ordenadas (un solo tono)
  /** opciones: { items:[{nombre, valor, detalle:[{nombre,valor}]}], color, formato(v) } */
  function barras(contenedor, o) {
    limpiar(contenedor);
    const items = o.items;
    if (!items.length) { const p = document.createElement('p'); p.className = 'vacio'; p.textContent = o.vacio || 'Sin datos en la ventana'; contenedor.appendChild(p); return; }
    const W = Math.max(260, contenedor.clientWidth || 500), fila = 30, H = items.length * fila + 6;
    const ancho = Math.min(Math.round(W * 0.42), Math.max(90, Math.max(...items.map(i => anchoTexto(i.nombre, 12))) + 10));
    const maxCaracteres = Math.max(8, Math.floor((ancho - 10) / (12 * 0.58)));
    const recortar = s => (s.length > maxCaracteres ? s.slice(0, maxCaracteres - 1) + '…' : s);
    const esc = escala(Math.max(...items.map(i => i.valor)) * 1.05, 4, o.entero);
    const pw = W - ancho - 70;
    const svg = el('svg', { viewBox: `0 0 ${W} ${H}`, width: W, height: H, role: 'img' }, contenedor);
    el('line', { x1: ancho, x2: ancho, y1: 0, y2: H - 6, class: 'g-eje' }, svg);
    items.forEach((it, i) => {
      const y = i * fila + 4, h = 16;
      const w = Math.max(2, (it.valor / esc.max) * pw);
      const etiqueta = texto(svg, ancho - 8, y + h / 2 + 4, recortar(it.nombre), 'g-texto-2', { 'text-anchor': 'end' });
      if (etiqueta.textContent !== it.nombre) { const ti = el('title', {}, etiqueta); ti.textContent = it.nombre; }
      const r = Math.min(4, w / 2);
      const golpe = el('rect', { x: 0, y: y - 4, width: W, height: fila, class: 'g-barra-golpe', tabindex: 0 }, svg);
      el('path', { d: `M${ancho} ${y} H${ancho + w - r} Q${ancho + w} ${y} ${ancho + w} ${y + r} V${y + h - r} Q${ancho + w} ${y + h} ${ancho + w - r} ${y + h} H${ancho} Z`,
        fill: color(o.color), class: 'g-barra' }, svg);
      texto(svg, ancho + w + 8, y + h / 2 + 4, o.formato(it.valor), 'g-texto-2');
      const mostrar = (cx, cy) => {
        const rct = svg.getBoundingClientRect();
        mostrarTooltip(cx ?? rct.left + ancho * (rct.width / W), cy ?? rct.top + y * (rct.height / H), it.nombre,
          [{ color: o.color, nombre: o.serie || '', valor: o.formato(it.valor) }, ...(it.detalle || [])]);
      };
      golpe.addEventListener('pointermove', e => mostrar(e.clientX, e.clientY));
      golpe.addEventListener('pointerleave', ocultarTooltip);
      golpe.addEventListener('focus', () => mostrar());
      golpe.addEventListener('blur', ocultarTooltip);
    });
    return svg;
  }

  // ----------------------------------------------------------------- sparkline (12 puntos, tono de énfasis en el último)
  function sparkline(contenedor, valores, acento, ancho = 132, alto = 36) {
    limpiar(contenedor);
    const v = valores.slice(-12);
    const svg = el('svg', { viewBox: `0 0 ${ancho} ${alto}`, width: ancho, height: alto, 'aria-hidden': 'true' }, contenedor);
    const datos = v.filter(x => x != null);
    if (datos.length < 2) return svg;
    const min = Math.min(...datos), max = Math.max(...datos);
    const X = i => 4 + (i / (v.length - 1)) * (ancho - 8);
    const Y = x => max === min ? alto / 2 : alto - 5 - ((x - min) / (max - min)) * (alto - 10);
    let d = '', seg = false;
    v.forEach((x, i) => { if (x == null) { seg = false; return; } d += (seg ? ' L' : ' M') + X(i).toFixed(1) + ' ' + Y(x).toFixed(1); seg = true; });
    el('path', { d, fill: 'none', stroke: 'var(--tinta-3)', 'stroke-width': 2, 'stroke-linejoin': 'round', 'stroke-linecap': 'round', opacity: 0.8 }, svg);
    let ult = -1; for (let i = v.length - 1; i >= 0; i--) if (v[i] != null) { ult = i; break; }
    if (ult >= 0) {
      el('circle', { cx: X(ult), cy: Y(v[ult]), r: 5.5, fill: 'var(--superficie)' }, svg);
      el('circle', { cx: X(ult), cy: Y(v[ult]), r: 4, fill: color(acento) }, svg);
    }
    return svg;
  }

  // ----------------------------------------------------------------- medidor (una razón contra un límite)
  function medidor(contenedor, { valor, max, color: c, etiqueta }) {
    limpiar(contenedor);
    const W = Math.max(200, contenedor.clientWidth || 260), H = 10;
    const svg = el('svg', { viewBox: `0 0 ${W} ${H}`, width: W, height: H, role: 'img', 'aria-label': etiqueta || '' }, contenedor);
    el('rect', { x: 0, y: 0, width: W, height: H, rx: 5, fill: 'var(--pista)' }, svg);
    const w = Math.max(0, Math.min(1, max > 0 ? valor / max : 0)) * W;
    if (w > 0) el('rect', { x: 0, y: 0, width: Math.max(w, 8), height: H, rx: 5, fill: color(c) }, svg);
    return svg;
  }

  // ----------------------------------------------------------------- barra segmentada alta (un todo en ≤ 6 partes)
  function segmentada(contenedor, partes, formato) {
    limpiar(contenedor);
    const total = partes.reduce((a, p) => a + p.valor, 0);
    const W = Math.max(260, contenedor.clientWidth || 600), H = 28;
    const svg = el('svg', { viewBox: `0 0 ${W} ${H}`, width: W, height: H, role: 'img', 'aria-label': partes.map(p => `${p.nombre}: ${p.valor}`).join(', ') }, contenedor);
    if (total === 0) { el('rect', { x: 0, y: 0, width: W, height: H, rx: 4, fill: 'var(--superficie-2)' }, svg); return svg; }
    const visibles = partes.filter(p => p.valor > 0);
    const huecos = (visibles.length - 1) * 2;
    let x = 0;
    visibles.forEach(p => {
      const w = (p.valor / total) * (W - huecos);
      const g = el('g', { tabindex: 0, class: 'g-columna' }, svg);
      el('rect', { x, y: 0, width: w, height: H, rx: 4, fill: color(p.color) }, g);
      const etiqueta = Math.round((p.valor / total) * 100) + ' %';
      if (anchoTexto(etiqueta, 12) + 14 < w) {
        texto(g, x + 8, H / 2 + 4, etiqueta, '', { fill: p.textoOscuro ? '#0b0b0b' : '#ffffff', 'font-size': 12, 'font-weight': 650, 'font-family': 'var(--fuente)' });
      }
      const mostrar = (cx, cy) => {
        const r = svg.getBoundingClientRect();
        mostrarTooltip(cx ?? r.left + (x + w / 2) * (r.width / W), cy ?? r.top, p.nombre,
          [{ color: p.color, nombre: 'pagos', valor: formato ? formato(p.valor) : String(p.valor) }, { nombre: 'del total', valor: ((p.valor / total) * 100).toFixed(1).replace('.', ',') + ' %' }]);
      };
      g.addEventListener('pointermove', e => mostrar(e.clientX, e.clientY));
      g.addEventListener('pointerleave', ocultarTooltip);
      g.addEventListener('focus', () => mostrar());
      g.addEventListener('blur', ocultarTooltip);
      x += w + 2;
    });
    return svg;
  }

  // ----------------------------------------------------------------- tabla gemela (HTML)
  /** columnas: [{titulo, num?}], filas: [[celda,...]] — siempre con textContent */
  function tabla(contenedor, columnas, filas) {
    limpiar(contenedor);
    const env = document.createElement('div'); env.className = 'tabla-envoltura';
    const t = document.createElement('table'); t.className = 'tabla';
    const thead = t.createTHead(); const tr = thead.insertRow();
    columnas.forEach(c => { const th = document.createElement('th'); th.textContent = c.titulo; if (c.num) th.className = 'num'; tr.appendChild(th); });
    const tb = t.createTBody();
    if (!filas.length) { const r = tb.insertRow(); const td = r.insertCell(); td.colSpan = columnas.length; td.className = 'vacio'; td.textContent = 'Sin datos en la ventana'; }
    filas.forEach(f => { const r = tb.insertRow(); f.forEach((v, i) => { const td = r.insertCell(); if (columnas[i].num) td.className = 'num'; if (v instanceof Node) td.appendChild(v); else td.textContent = v; }); });
    env.appendChild(t); contenedor.appendChild(env);
  }

  return { lineas, columnas, barras, sparkline, medidor, segmentada, tabla, escala, cssVar, ocultarTooltip };
})();
