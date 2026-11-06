/* =====================================================================================
   Ahorra+ · Tablero de KPIs del prototipo — lógica de la interfaz
   Modos: Demo (datos sintéticos con los escenarios del simulador) · En vivo (GET /api/v1/backoffice/kpis del núcleo).
   Los filtros (rango, granularidad) viven en una sola fila y re-renderizan TODO el tablero sobre la misma ventana.
   ===================================================================================== */
(() => {
  'use strict';

  // ---------------------------------------------------------------- metas de referencia (editables desde la UI)
  const METAS_POR_DEFECTO = { aprobacion: 95, p95: 800, tps: 10, reversos: 1 };
  const guardar = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* almacenamiento no disponible */ } };
  const leer = (k, d) => { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } };

  const estado = {
    modo: leer('kpi.modo', 'demo'), rango: leer('kpi.rango', '24h'), bucket: leer('kpi.bucket', 'auto'), auto: leer('kpi.auto', false),
    metas: { ...METAS_POR_DEFECTO, ...leer('kpi.metas', {}) },
    vivo: { url: leer('kpi.url', 'http://localhost:8080'), token: null, nombre: null, rol: null },
    datos: null, temporizador: null, cargando: false,
  };

  // ---------------------------------------------------------------- utilidades de formato (es-VE)
  const nf = (dec = 0) => new Intl.NumberFormat('es-VE', { minimumFractionDigits: dec, maximumFractionDigits: dec });
  const fmtN = v => nf(0).format(v);
  const fmtPct = (pb, dec = 1) => (pb == null ? '—' : nf(dec).format(pb / 100) + ' %');
  const fmtMs = v => (v == null ? '—' : v >= 10000 ? nf(1).format(v / 1000) + ' s' : fmtN(Math.round(v)) + ' ms');
  const fmtBs = c => 'Bs ' + nf(2).format(c / 100);
  const fmtTps = v => (v == null ? '—' : nf(v >= 10 ? 1 : v >= 1 ? 2 : 3).format(v) + ' TPS');
  const fmtFecha = iso => new Date(iso).toLocaleString('es-VE', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
  const fmtHora = iso => new Date(iso).toLocaleTimeString('es-VE', { hour: '2-digit', minute: '2-digit' });
  const fmtDia = iso => new Date(iso).toLocaleDateString('es-VE', { day: '2-digit', month: 'short' });
  const etiquetaBucket = (iso, bucket) => (bucket === 'dia' ? fmtDia(iso) : bucket === 'hora' ? fmtHora(iso) : fmtHora(iso));
  const etiquetaLarga = (iso, bucket) => (bucket === 'dia' ? new Date(iso).toLocaleDateString('es-VE', { weekday: 'short', day: '2-digit', month: 'long' }) : fmtFecha(iso));
  const $ = s => document.querySelector(s);
  const $$ = s => [...document.querySelectorAll(s)];
  function nodo(tag, clase, texto) { const n = document.createElement(tag); if (clase) n.className = clase; if (texto != null) n.textContent = texto; return n; }
  function limpiar(n) { while (n.firstChild) n.removeChild(n.firstChild); }

  const ESCENARIOS = {
    13: ['Timeout del suiche', 'aviso'], 51: ['Fondos insuficientes', 'mal'], 77: ['Captura rechazada por el emisor', 'mal'],
    98: ['Falta en la liquidación', 'aviso'], 99: ['Monto distinto en liquidación', 'aviso'],
  };
  const ESTADOS = {
    CONCILIADO: ['Conciliado', 'ok', '--serie-1'], CAPTURADO: ['Capturado', 'ok', '--serie-3'], CON_DIFERENCIA: ['Con diferencia', 'aviso', '--serie-4'],
    REVERSADO: ['Reversado', 'mal', '--serie-2'], RECHAZADO: ['Rechazado', 'mal', '--serie-5'], EN_VERIFICACION: ['En verificación', 'aviso', '--serie-6'],
    AUTORIZADO: ['Autorizado', 'info', '--serie-6'], PENDIENTE: ['Pendiente', 'info', '--serie-6'],
  };
  const ORIGEN_REVERSO = { CON_DIFERENCIA: 'Diferencia en conciliación', EN_VERIFICACION: 'El suiche informó el reverso', CAPTURADO: 'Reverso directo tras captura', AUTORIZADO: 'Autorización liberada' };
  const TIPOS_MENSAJE = { autoriz: 'Autorización', captura: 'Captura', reverso: 'Reverso', consulta: 'Consulta de estado', benef: 'Beneficiario' };

  // ---------------------------------------------------------------- iconos
  const ICONOS = {
    check: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>',
    alerta: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/></svg>',
    cruz: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>',
    neutro: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M5 12h14"/></svg>',
    arriba: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5m-7 7 7-7 7 7"/></svg>',
    abajo: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14m7-7-7 7-7-7"/></svg>',
  };
  const svgNodo = html => { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstChild; };

  // ---------------------------------------------------------------- semáforo de cada KPI (icono + texto, nunca solo color)
  function semaforo(nivel) {
    return { bien: ['Dentro de la meta', 'check'], alerta: ['Cerca del límite', 'alerta'], critico: ['Fuera de la meta', 'cruz'], neutro: ['Sin datos', 'neutro'] }[nivel];
  }
  const nivelAprobacion = pb => (pb == null ? 'neutro' : pb / 100 >= estado.metas.aprobacion ? 'bien' : pb / 100 >= estado.metas.aprobacion - 5 ? 'alerta' : 'critico');
  const nivelP95 = (ms, muestras) => (!muestras ? 'neutro' : ms <= estado.metas.p95 ? 'bien' : ms <= estado.metas.p95 * 1.8 ? 'alerta' : 'critico');
  const nivelTps = pico => (pico == null ? 'neutro' : pico <= estado.metas.tps * 0.7 ? 'bien' : pico <= estado.metas.tps ? 'alerta' : 'critico');
  const nivelReversos = pb => (pb == null ? 'neutro' : pb / 100 <= estado.metas.reversos ? 'bien' : pb / 100 <= estado.metas.reversos * 3 ? 'alerta' : 'critico');

  // ---------------------------------------------------------------- pintar una tarjeta KPI
  function pintarKpi(id, { valor, unidad, detalle, nivel, delta, meta, serie, acento, medidor }) {
    const t = $('#' + id);
    t.className = 'tarjeta kpi ' + nivel;
    t.querySelector('.valor .cifra').textContent = valor;
    t.querySelector('.valor small').textContent = unidad || '';
    t.querySelector('.detalle').textContent = detalle;
    t.querySelector('.meta').textContent = meta;
    const past = t.querySelector('.estado-pastilla'); limpiar(past);
    const [txt, ico] = semaforo(nivel); past.appendChild(svgNodo(ICONOS[ico])); past.appendChild(document.createTextNode(txt));
    const d = t.querySelector('.delta'); limpiar(d);
    if (delta) {
      d.className = 'delta ' + delta.clase;
      if (delta.clase !== 'igual') d.appendChild(svgNodo(ICONOS[delta.clase.startsWith('sube') ? 'arriba' : 'abajo']));
      d.appendChild(document.createTextNode(delta.texto + ' '));
      d.appendChild(nodo('span', 'vs', 'vs. periodo anterior'));
      d.title = 'Comparado con la ventana anterior de igual longitud';
    } else { d.className = 'delta igual'; d.textContent = 'Sin periodo anterior'; }
    Graficos.sparkline(t.querySelector('.sparkline'), serie, acento);
    const med = t.querySelector('.medidor');
    if (medidor) { med.hidden = false; Graficos.medidor(med, medidor); } else med.hidden = true;
  }
  function delta(actual, previo, { decimales = 1, unidad = '', subirEsBueno = true, pp = false } = {}) {
    if (actual == null || previo == null) return null;
    const dif = actual - previo;
    if (Math.abs(dif) < Math.pow(10, -decimales) / 2) return { clase: 'igual', texto: 'Sin cambio' };
    const sube = dif > 0;
    const bueno = sube === subirEsBueno;
    return { clase: (sube ? 'sube-' : 'baja-') + (bueno ? 'bien' : 'mal'), texto: (sube ? '+' : '−') + nf(decimales).format(Math.abs(dif)) + (pp ? ' pp' : unidad) };
  }

  // ---------------------------------------------------------------- leyenda + alternador gráfico/tabla
  function leyenda(tarjeta, claves) {
    const l = tarjeta.querySelector('.leyenda'); limpiar(l);
    for (const c of claves) {
      const k = nodo('span', 'clave');
      const m = nodo('span', 'muestra' + (c.rect ? ' rect' : '') + (c.meta ? ' meta' : ''));
      if (c.color) m.style.background = `var(${c.color})`;
      k.appendChild(m); k.appendChild(document.createTextNode(c.nombre)); l.appendChild(k);
    }
  }
  function alternadores() {
    $$('.alternar').forEach(a => a.addEventListener('click', e => {
      const b = e.target.closest('button'); if (!b) return;
      const tarjeta = b.closest('.tarjeta');
      a.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b));
      tarjeta.querySelector('.vista-grafico').hidden = b.dataset.vista !== 'grafico';
      tarjeta.querySelector('.vista-tabla').hidden = b.dataset.vista !== 'tabla';
      if (b.dataset.vista === 'grafico' && estado.datos) pintarGraficos(estado.datos);
    }));
  }

  // ---------------------------------------------------------------- render principal
  function pintar(d) {
    const r = d.resumen, p = d.resumenPrevio, v = d.ventana;
    const serie = d.series;
    // --- KPI 1 · tasa de aprobación
    const tasa = r.tasaAprobacionPb;
    pintarKpi('kpi-aprobacion', {
      valor: tasa == null ? '—' : nf(1).format(tasa / 100), unidad: tasa == null ? '' : '%',
      detalle: `${fmtN(r.aprobados)} aprobados · ${fmtN(r.rechazados)} rechazados · ${fmtN(r.enCurso)} en curso`,
      nivel: nivelAprobacion(tasa), delta: delta(tasa == null ? null : tasa / 100, p.tasaAprobacionPb == null ? null : p.tasaAprobacionPb / 100, { pp: true }),
      meta: `Meta ≥ ${nf(0).format(estado.metas.aprobacion)} % · aprobados ÷ (aprobados + rechazados)`, serie: serie.map(b => b.tasaAprobacionPb == null ? null : b.tasaAprobacionPb / 100), acento: '--serie-1',
    });
    // --- KPI 2 · latencia p95
    const ls = r.latenciaSuiche;
    pintarKpi('kpi-latencia', {
      valor: ls.muestras ? fmtN(ls.p95Ms) : '—', unidad: ls.muestras ? 'ms' : '',
      detalle: `p50 ${fmtMs(ls.p50Ms)} · p99 ${fmtMs(ls.p99Ms)} · máx ${fmtMs(ls.maxMs)} · ${fmtN(r.mensajesSuiche)} mensajes · ${fmtN(r.timeouts)} timeouts`,
      nivel: nivelP95(ls.p95Ms, ls.muestras), delta: delta(ls.muestras ? ls.p95Ms : null, p.latenciaSuiche.muestras ? p.latenciaSuiche.p95Ms : null, { decimales: 0, unidad: ' ms', subirEsBueno: false }),
      meta: `Meta ≤ ${fmtN(estado.metas.p95)} ms · ida y vuelta de cada mensaje al suiche (los timeouts no entran en el percentil)`,
      serie: serie.map(b => b.p95Ms), acento: '--ordinal-2',
    });
    // --- KPI 3 · TPS
    const tpsProm = r.pagos / v.segundos;
    const pico = r.pagos ? r.pagosSegundoPico : null;
    pintarKpi('kpi-tps', {
      valor: pico == null ? '—' : nf(0).format(pico), unidad: pico == null ? '' : 'TPS pico',
      detalle: `promedio ${fmtTps(tpsProm)} · ${fmtN(r.pagos)} pagos en la ventana · pico ${fmtN(r.pagosMinutoPico)} pagos/min · ${fmtN(r.mensajesSegundoPico)} msj/s al suiche`,
      nivel: nivelTps(pico), delta: delta(pico, p.pagos ? p.pagosSegundoPico : null, { decimales: 0, unidad: ' TPS', subirEsBueno: true }),
      meta: `Capacidad de referencia ${fmtN(estado.metas.tps)} TPS · pico = máximo de pagos creados en un mismo segundo`,
      serie: serie.map(b => b.pagos / v.segundosBucket), acento: '--serie-3',
      medidor: { valor: pico || 0, max: estado.metas.tps, color: nivelTps(pico) === 'critico' ? '--critico' : nivelTps(pico) === 'alerta' ? '--alerta' : '--serie-1', etiqueta: 'Pico de TPS frente a la capacidad de referencia' },
    });
    // --- KPI 4 · tasa de reversos
    const rev = r.tasaReversosPb;
    const porDif = (d.origenReversos.find(x => x.clave === 'CON_DIFERENCIA') || {}).cantidad || 0;
    pintarKpi('kpi-reversos', {
      valor: rev == null ? '—' : nf(2).format(rev / 100), unidad: rev == null ? '' : '%',
      detalle: `${fmtN(r.reversados)} reversados de ${fmtN(r.aprobados)} capturados · ${fmtN(porDif)} por diferencia en conciliación · ${fmtN(r.conDiferencia)} con diferencia abierta`,
      nivel: nivelReversos(rev), delta: delta(rev == null ? null : rev / 100, p.tasaReversosPb == null ? null : p.tasaReversosPb / 100, { decimales: 2, pp: true, subirEsBueno: false }),
      meta: `Meta ≤ ${nf(1).format(estado.metas.reversos)} % · reversados ÷ capturados`, serie: serie.map(b => b.tasaReversosPb == null ? null : b.tasaReversosPb / 100), acento: '--serie-2',
    });

    // --- contexto de la ventana
    $('#resumen-ventana').textContent = `${fmtFecha(v.desde)} → ${fmtFecha(v.hasta)} · intervalos de 1 ${v.bucket} · ${fmtN(r.pagos)} pagos · ${fmtBs(r.montoTotalCts)} · comisiones ${fmtBs(r.comisionTotalCts)}`;
    pintarGraficos(d);
    pintarEstados(d);
    pintarRecientes(d);
    $('#ultima-actualizacion').textContent = 'Actualizado ' + new Date().toLocaleTimeString('es-VE');
  }

  function pintarGraficos(d) {
    const v = d.ventana, s = d.series;
    const cortas = s.map(b => etiquetaBucket(b.inicio, v.bucket)), largas = s.map(b => etiquetaLarga(b.inicio, v.bucket));
    const fmtPctEje = (x, eje) => (eje ? nf(0).format(x) + ' %' : nf(1).format(x) + ' %');
    const fmtMsEje = (x, eje) => (eje ? fmtN(x) : fmtN(Math.round(x)) + ' ms');

    // 1 · aprobación en el tiempo
    let t = $('#g-aprobacion');
    leyenda(t, [{ nombre: 'Tasa de aprobación', color: '--serie-1' }, { nombre: `Meta ${nf(0).format(estado.metas.aprobacion)} %`, meta: true }]);
    Graficos.lineas(t.querySelector('.vista-grafico'), {
      series: [{ nombre: 'Tasa de aprobación', color: '--serie-1', valores: s.map(b => b.tasaAprobacionPb == null ? null : b.tasaAprobacionPb / 100) }],
      etiquetasX: cortas, etiquetasLargas: largas, formato: fmtPctEje, yMax: 100, meta: { valor: estado.metas.aprobacion, etiqueta: `Meta ${nf(0).format(estado.metas.aprobacion)} %` },
      extra: i => [{ nombre: 'aprobados', valor: fmtN(s[i].aprobados) }, { nombre: 'rechazados', valor: fmtN(s[i].rechazados) }], aria: 'Tasa de aprobación por intervalo',
    });
    Graficos.tabla(t.querySelector('.vista-tabla'), [{ titulo: 'Intervalo' }, { titulo: 'Aprobados', num: true }, { titulo: 'Rechazados', num: true }, { titulo: 'En curso', num: true }, { titulo: 'Tasa', num: true }],
      s.map((b, i) => [largas[i], fmtN(b.aprobados), fmtN(b.rechazados), fmtN(b.enCurso), fmtPct(b.tasaAprobacionPb)]));

    // 2 · resultado por intervalo (columnas apiladas; colores de estado porque SÍ significan bien/mal)
    t = $('#g-resultados');
    leyenda(t, [{ nombre: 'Aprobados', color: '--bien', rect: true }, { nombre: 'Rechazados', color: '--critico', rect: true }, { nombre: 'En curso', color: '--neutro-mark', rect: true }]);
    Graficos.columnas(t.querySelector('.vista-grafico'), {
      categorias: cortas, etiquetasLargas: largas, formato: (x, eje) => fmtN(x), entero: true,
      series: [{ nombre: 'Aprobados', color: '--bien', valores: s.map(b => b.aprobados) }, { nombre: 'Rechazados', color: '--critico', valores: s.map(b => b.rechazados) }, { nombre: 'En curso', color: '--neutro-mark', valores: s.map(b => b.enCurso) }],
      aria: 'Pagos por resultado y por intervalo',
    });
    Graficos.tabla(t.querySelector('.vista-tabla'), [{ titulo: 'Intervalo' }, { titulo: 'Aprobados', num: true }, { titulo: 'Rechazados', num: true }, { titulo: 'En curso', num: true }, { titulo: 'Total', num: true }],
      s.map((b, i) => [largas[i], fmtN(b.aprobados), fmtN(b.rechazados), fmtN(b.enCurso), fmtN(b.pagos)]));

    // 3 · latencia del suiche en el tiempo (rampa ordinal: p50 claro, p95 oscuro)
    t = $('#g-latencia');
    leyenda(t, [{ nombre: 'p95', color: '--ordinal-2' }, { nombre: 'p50', color: '--ordinal-1' }, { nombre: `Meta ${fmtN(estado.metas.p95)} ms`, meta: true }]);
    Graficos.lineas(t.querySelector('.vista-grafico'), {
      series: [{ nombre: 'p95', color: '--ordinal-2', valores: s.map(b => b.p95Ms) }, { nombre: 'p50', color: '--ordinal-1', valores: s.map(b => b.p50Ms) }],
      etiquetasX: cortas, etiquetasLargas: largas, formato: fmtMsEje, meta: { valor: estado.metas.p95, etiqueta: `Meta ${fmtN(estado.metas.p95)} ms` },
      extra: i => [{ nombre: 'timeouts', valor: fmtN(s[i].timeouts) }], aria: 'Latencia p95 y p50 del suiche por intervalo',
    });
    Graficos.tabla(t.querySelector('.vista-tabla'), [{ titulo: 'Intervalo' }, { titulo: 'p50', num: true }, { titulo: 'p95', num: true }, { titulo: 'Timeouts', num: true }],
      s.map((b, i) => [largas[i], fmtMs(b.p50Ms), fmtMs(b.p95Ms), fmtN(b.timeouts)]));

    // 4 · latencia por tipo de mensaje (barras ordenadas, un tono)
    t = $('#g-tipos');
    const tipos = d.latenciasPorTipo.slice().sort((a, b) => b.latencias.p95Ms - a.latencias.p95Ms);
    Graficos.barras(t.querySelector('.vista-grafico'), {
      color: '--ordinal-2', serie: 'p95', formato: fmtMs,
      items: tipos.map(x => ({ nombre: TIPOS_MENSAJE[x.tipo] || x.tipo, valor: x.latencias.p95Ms,
        detalle: [{ nombre: 'p50', valor: fmtMs(x.latencias.p50Ms) }, { nombre: 'p99', valor: fmtMs(x.latencias.p99Ms) }, { nombre: 'máx', valor: fmtMs(x.latencias.maxMs) }, { nombre: 'mensajes', valor: fmtN(x.mensajes) }, { nombre: 'timeouts', valor: fmtN(x.timeouts) }] })),
    });
    Graficos.tabla(t.querySelector('.vista-tabla'), [{ titulo: 'Mensaje' }, { titulo: 'Mensajes', num: true }, { titulo: 'Timeouts', num: true }, { titulo: 'p50', num: true }, { titulo: 'p95', num: true }, { titulo: 'p99', num: true }, { titulo: 'Máx', num: true }],
      tipos.map(x => [TIPOS_MENSAJE[x.tipo] || x.tipo, fmtN(x.mensajes), fmtN(x.timeouts), fmtMs(x.latencias.p50Ms), fmtMs(x.latencias.p95Ms), fmtMs(x.latencias.p99Ms), fmtMs(x.latencias.maxMs)]));

    // 5 · throughput
    t = $('#g-tps');
    leyenda(t, [{ nombre: 'Pagos por intervalo', color: '--serie-3' }]);
    Graficos.lineas(t.querySelector('.vista-grafico'), {
      series: [{ nombre: 'Pagos', color: '--serie-3', valores: s.map(b => b.pagos) }], etiquetasX: cortas, etiquetasLargas: largas, formato: (x, eje) => fmtN(Math.round(x)), area: true, pico: true, etiquetaFinal: false, entero: true,
      extra: i => [{ nombre: 'TPS promedio del intervalo', valor: fmtTps(s[i].pagos / v.segundosBucket) }, { nombre: 'pico en 1 s', valor: fmtN(s[i].pagosSegundoPico) + ' pagos' }], aria: 'Pagos creados por intervalo',
    });
    Graficos.tabla(t.querySelector('.vista-tabla'), [{ titulo: 'Intervalo' }, { titulo: 'Pagos', num: true }, { titulo: 'TPS promedio', num: true }, { titulo: 'Pico en 1 s', num: true }],
      s.map((b, i) => [largas[i], fmtN(b.pagos), fmtTps(b.pagos / v.segundosBucket), fmtN(b.pagosSegundoPico)]));

    // 6 · reversos por intervalo (columnas de un solo color de serie)
    t = $('#g-reversos');
    leyenda(t, [{ nombre: 'Pagos reversados', color: '--serie-2', rect: true }]);
    Graficos.columnas(t.querySelector('.vista-grafico'), {
      categorias: cortas, etiquetasLargas: largas, formato: (x) => fmtN(x), entero: true, series: [{ nombre: 'Reversados', color: '--serie-2', valores: s.map(b => b.reversados) }], aria: 'Pagos reversados por intervalo',
    });
    Graficos.tabla(t.querySelector('.vista-tabla'), [{ titulo: 'Intervalo' }, { titulo: 'Reversados', num: true }, { titulo: 'Capturados', num: true }, { titulo: 'Tasa', num: true }],
      s.map((b, i) => [largas[i], fmtN(b.reversados), fmtN(b.aprobados), fmtPct(b.tasaReversosPb, 2)]));

    // 7 · origen de reversos y motivos de rechazo
    Graficos.barras($('#g-origen'), { color: '--serie-2', serie: 'reversos', formato: fmtN, entero: true, vacio: 'Ningún reverso en la ventana',
      items: d.origenReversos.map(x => ({ nombre: ORIGEN_REVERSO[x.clave] || x.clave, valor: x.cantidad })) });
    Graficos.barras($('#g-motivos'), { color: '--serie-5', serie: 'rechazos', formato: fmtN, entero: true, vacio: 'Ningún rechazo en la ventana',
      items: d.motivosRechazo.map(x => ({ nombre: x.clave, valor: x.cantidad })) });
    const tablaCausas = $('#t-causas .vista-tabla');
    Graficos.tabla(tablaCausas, [{ titulo: 'Tipo' }, { titulo: 'Causa' }, { titulo: 'Pagos', num: true }],
      [...d.origenReversos.map(x => ['Reverso', ORIGEN_REVERSO[x.clave] || x.clave, fmtN(x.cantidad)]), ...d.motivosRechazo.map(x => ['Rechazo', x.clave, fmtN(x.cantidad)])]);
  }

  function pintarEstados(d) {
    const t = $('#g-estados');
    const partes = d.estados.map(e => ({ nombre: ESTADOS[e.clave] ? ESTADOS[e.clave][0] : e.clave, valor: e.cantidad, color: ESTADOS[e.clave] ? ESTADOS[e.clave][2] : '--serie-6', textoOscuro: e.clave === 'CON_DIFERENCIA' }));
    // ≤ 6 partes: en curso (PENDIENTE/AUTORIZADO/EN_VERIFICACION) se pliegan en una sola
    const enCurso = partes.filter(p => ['Pendiente', 'Autorizado', 'En verificación'].includes(p.nombre));
    const resto = partes.filter(p => !enCurso.includes(p));
    if (enCurso.length) resto.push({ nombre: 'En curso', valor: enCurso.reduce((a, p) => a + p.valor, 0), color: '--serie-6' });
    // orden fijo por slot de la paleta (azul, naranja, aqua, amarillo, magenta, verde): los pares adyacentes están validados
    const ORDEN = ['--serie-1', '--serie-2', '--serie-3', '--serie-4', '--serie-5', '--serie-6'];
    resto.sort((a, b) => ORDEN.indexOf(a.color) - ORDEN.indexOf(b.color));
    Graficos.segmentada(t.querySelector('.vista-grafico'), resto, fmtN);
    const l = t.querySelector('.segmentada-leyenda'); limpiar(l);
    const total = resto.reduce((a, p) => a + p.valor, 0);
    resto.forEach(p => {
      const k = nodo('span', 'clave'); const m = nodo('span', 'muestra rect'); m.style.cssText = `width:12px;height:12px;border-radius:3px;background:var(${p.color})`;
      k.appendChild(m); k.appendChild(document.createTextNode(p.nombre)); k.appendChild(nodo('b', '', fmtN(p.valor) + (total ? ' · ' + nf(1).format((p.valor / total) * 100) + ' %' : ''))); l.appendChild(k);
    });
    Graficos.tabla(t.querySelector('.vista-tabla'), [{ titulo: 'Estado' }, { titulo: 'Pagos', num: true }, { titulo: 'Participación', num: true }],
      d.estados.map(e => [ESTADOS[e.clave] ? ESTADOS[e.clave][0] : e.clave, fmtN(e.cantidad), total ? nf(1).format((e.cantidad / total) * 100) + ' %' : '—']));
  }

  function pintarRecientes(d) {
    const chip = (texto, clase) => { const c = nodo('span', 'chip ' + clase, texto); return c; };
    Graficos.tabla($('#t-recientes .vista-tabla'),
      [{ titulo: 'Pago' }, { titulo: 'Creado' }, { titulo: 'Estado' }, { titulo: 'Monto', num: true }, { titulo: 'Escenario del simulador' }, { titulo: 'Lat. autorización', num: true }, { titulo: 'Lat. total', num: true }, { titulo: 'Referencia suiche' }, { titulo: 'Motivo' }],
      d.pagosRecientes.map(p => {
        const e = ESTADOS[p.estado] || [p.estado, 'info'];
        const esc = ESCENARIOS[p.escenario];
        return ['#' + p.idPago, fmtFecha(p.creacion), chip(e[0], e[1]), fmtBs(p.montoCts), esc ? chip(`${p.escenario} · ${esc[0]}`, esc[1]) : chip(`${String(p.escenario).padStart(2, '0')} · Camino feliz`, 'ok'),
          fmtMs(p.latenciaAutorizacionMs), fmtMs(p.latenciaTotalMs), p.referenciaSuiche || '—', p.motivoRechazo || '—'];
      }));
    $('#t-recientes .vista-tabla').hidden = false;
  }

  // ---------------------------------------------------------------- carga de datos (demo / en vivo)
  async function cargar() {
    if (estado.cargando) return;
    estado.cargando = true;
    $('.contenido').classList.add('cargando');   // se mantiene el frame anterior atenuado, sin esqueleto
    try {
      let datos;
      if (estado.modo === 'demo') {
        datos = DatosDemo.tablero({ rango: estado.rango, bucket: estado.bucket });
        fuente('ok', 'Modo demo · datos sintéticos con los escenarios del simulador');
      } else {
        datos = await pedirKpis();
        fuente('ok', `En vivo · ${estado.vivo.url} · ${estado.vivo.nombre || 'operador'} (${estado.vivo.rol || ''})`);
      }
      estado.datos = datos;
      pintar(datos);
      $('#error-vivo').textContent = '';
    } catch (e) {
      fuente('mal', 'Sin conexión con el núcleo');
      $('#error-vivo').textContent = e.message || String(e);
      $('#panel-vivo').classList.add('visible');
    } finally {
      estado.cargando = false;
      $('.contenido').classList.remove('cargando');
    }
  }
  function fuente(clase, texto) { const p = $('#punto-fuente'); p.className = 'punto ' + clase; $('#texto-fuente').textContent = texto; }

  async function pedirKpis() {
    if (!estado.vivo.token) await entrar();
    const url = `${estado.vivo.url.replace(/\/$/, '')}/api/v1/backoffice/kpis?rango=${encodeURIComponent(estado.rango)}&bucket=${encodeURIComponent(estado.bucket)}`;
    const r = await fetch(url, { headers: { Authorization: 'Bearer ' + estado.vivo.token } });
    if (r.status === 401) { estado.vivo.token = null; throw new Error('La sesión de operador expiró: vuelve a conectar.'); }
    const cuerpo = await r.json().catch(() => null);
    if (!r.ok) throw new Error((cuerpo && cuerpo.mensaje) || 'Error ' + r.status + ' al consultar /backoffice/kpis');
    return cuerpo;
  }
  async function entrar() {
    const correo = $('#correo').value.trim(), clave = $('#clave').value;
    const base = $('#url-nucleo').value.trim().replace(/\/$/, '');
    estado.vivo.url = base; guardar('kpi.url', base);
    const r = await fetch(base + '/api/v1/backoffice/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ correoCorporativo: correo, clave }) });
    const s = await r.json().catch(() => null);
    if (!r.ok) throw new Error((s && s.mensaje) || 'No se pudo iniciar sesión de operador (' + r.status + ')');
    estado.vivo.token = s.accessToken; estado.vivo.nombre = s.nombre; estado.vivo.rol = s.rol;
  }

  // ---------------------------------------------------------------- controles
  function marcar(grupo, valor) { $$(`${grupo} button`).forEach(b => b.setAttribute('aria-pressed', b.dataset.valor === valor)); }
  function programarAuto() {
    clearInterval(estado.temporizador);
    if (estado.auto) estado.temporizador = setInterval(cargar, 30000);
  }
  function tema(valor) {
    if (valor) document.documentElement.setAttribute('data-theme', valor); else document.documentElement.removeAttribute('data-theme');
    guardar('kpi.tema', valor || '');
    if (estado.datos) pintar(estado.datos);
  }
  function iniciar() {
    // parámetros de URL para compartir una vista: ?modo=demo|vivo&rango=24h&bucket=hora&tema=light|dark
    const q = new URLSearchParams(location.search);
    if (['demo', 'vivo'].includes(q.get('modo'))) estado.modo = q.get('modo');
    if (['hoy', '1h', '24h', '7d', '30d'].includes(q.get('rango'))) estado.rango = q.get('rango');
    if (['auto', 'minuto', 'hora', 'dia'].includes(q.get('bucket'))) estado.bucket = q.get('bucket');
    if (['light', 'dark'].includes(q.get('tema'))) guardar('kpi.tema', q.get('tema'));
    const t = leer('kpi.tema', ''); if (t) document.documentElement.setAttribute('data-theme', t);
    $('#url-nucleo').value = estado.vivo.url;
    marcar('#modo', estado.modo); marcar('#rango', estado.rango); marcar('#bucket', estado.bucket);
    $('#auto').checked = estado.auto;
    $('#panel-vivo').classList.toggle('visible', estado.modo === 'vivo');
    alternadores();

    $('#modo').addEventListener('click', e => {
      const b = e.target.closest('button'); if (!b) return;
      estado.modo = b.dataset.valor; guardar('kpi.modo', estado.modo); marcar('#modo', estado.modo);
      $('#panel-vivo').classList.toggle('visible', estado.modo === 'vivo');
      cargar();
    });
    $('#rango').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; estado.rango = b.dataset.valor; guardar('kpi.rango', estado.rango); marcar('#rango', estado.rango); cargar(); });
    $('#bucket').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; estado.bucket = b.dataset.valor; guardar('kpi.bucket', estado.bucket); marcar('#bucket', estado.bucket); cargar(); });
    $('#auto').addEventListener('change', e => { estado.auto = e.target.checked; guardar('kpi.auto', estado.auto); programarAuto(); });
    $('#actualizar').addEventListener('click', cargar);
    $('#tema').addEventListener('click', () => {
      const actual = document.documentElement.getAttribute('data-theme');
      const oscuroSO = window.matchMedia('(prefers-color-scheme: dark)').matches;
      const esOscuro = actual ? actual === 'dark' : oscuroSO;
      tema(esOscuro ? 'light' : 'dark');
    });
    $('#form-vivo').addEventListener('submit', async e => {
      e.preventDefault(); estado.vivo.token = null; estado.modo = 'vivo'; guardar('kpi.modo', 'vivo'); marcar('#modo', 'vivo');
      await cargar();
    });
    $$('.acceso-rapido').forEach(b => b.addEventListener('click', () => { $('#correo').value = b.dataset.correo; $('#clave').value = 'demo'; }));

    // metas de referencia
    const dlg = $('#dialogo-metas');
    $('#abrir-metas').addEventListener('click', () => {
      $('#meta-aprobacion').value = estado.metas.aprobacion; $('#meta-p95').value = estado.metas.p95; $('#meta-tps').value = estado.metas.tps; $('#meta-reversos').value = estado.metas.reversos;
      dlg.showModal();
    });
    $('#guardar-metas').addEventListener('click', e => {
      e.preventDefault();
      estado.metas = { aprobacion: +$('#meta-aprobacion').value || 95, p95: +$('#meta-p95').value || 800, tps: +$('#meta-tps').value || 10, reversos: +$('#meta-reversos').value || 1 };
      guardar('kpi.metas', estado.metas); dlg.close(); if (estado.datos) pintar(estado.datos);
    });
    $('#restaurar-metas').addEventListener('click', e => { e.preventDefault(); estado.metas = { ...METAS_POR_DEFECTO }; guardar('kpi.metas', estado.metas); dlg.close(); if (estado.datos) pintar(estado.datos); });

    let r; window.addEventListener('resize', () => { clearTimeout(r); r = setTimeout(() => { if (estado.datos) { pintarGraficos(estado.datos); pintarEstados(estado.datos); } }, 150); });
    programarAuto();
    cargar();
  }
  document.addEventListener('DOMContentLoaded', iniciar);
})();
