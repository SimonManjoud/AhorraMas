/* =====================================================================================
   Modo demo: genera pagos sintéticos con los MISMOS escenarios del simulador del suiche
   (mocks/simuladores · monto % 100 → 13 timeout · 51 fondos · 77 captura rechazada · 98 falta en liquidación ·
   99 monto distinto) y calcula los KPIs con una réplica fiel de CalculadoraKpis.java, para que el tablero mida
   exactamente lo mismo en demo y en vivo. Determinista: la misma hora produce siempre los mismos pagos.
   ===================================================================================== */
const CalculoKpis = (() => {
  const APROBADO_SIN_CAPTURA = new Set(['CAPTURADO', 'CONCILIADO', 'CON_DIFERENCIA']);
  const ORDEN_ESTADOS = ['PENDIENTE', 'AUTORIZADO', 'CAPTURADO', 'CONCILIADO', 'RECHAZADO', 'EN_VERIFICACION', 'REVERSADO', 'CON_DIFERENCIA'];
  const aprobado = p => p.captura != null || APROBADO_SIN_CAPTURA.has(p.estado);
  const rechazado = p => p.estado === 'RECHAZADO' || (p.estado === 'REVERSADO' && p.captura == null);
  const tasaPb = (n, d) => (d === 0 ? null : Math.round((n * 10000) / d));
  function percentil(ordenadas, p) {
    if (!ordenadas.length) return 0;
    const i = Math.ceil((p / 100) * ordenadas.length) - 1;
    return ordenadas[Math.max(0, Math.min(ordenadas.length - 1, i))];
  }
  function latencias(muestras) {
    const v = muestras.slice().sort((a, b) => a - b);
    return { muestras: v.length, p50Ms: percentil(v, 50), p95Ms: percentil(v, 95), p99Ms: percentil(v, 99), maxMs: v.length ? v[v.length - 1] : 0 };
  }
  const inicioDiaLocal = t => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); };
  const ordenarConteo = m => [...m.entries()].sort((a, b) => b[1] - a[1] || (a[0] < b[0] ? -1 : 1)).map(([clave, cantidad]) => ({ clave, cantidad }));

  /** desde/hasta en ms; bucketSeg en segundos. pagos: {idPago, estado, montoCts, comisionCts, creacion, captura, motivoRechazo, referenciaSuiche} */
  function calcular(desde, hasta, bucketSeg, pagos, mensajes, transiciones) {
    let aprobados = 0, rechazados = 0, enCurso = 0, reversados = 0, conDiferencia = 0, conciliados = 0, monto = 0, comision = 0;
    const porEstado = new Map(), motivos = new Map();
    for (const p of pagos) {
      if (aprobado(p)) aprobados++; else if (rechazado(p)) rechazados++; else enCurso++;
      if (p.estado === 'REVERSADO') reversados++;
      if (p.estado === 'CON_DIFERENCIA') conDiferencia++;
      if (p.estado === 'CONCILIADO') conciliados++;
      monto += p.montoCts;
      comision += p.estado === 'RECHAZADO' || p.estado === 'REVERSADO' ? 0 : p.comisionCts;
      porEstado.set(p.estado, (porEstado.get(p.estado) || 0) + 1);
      if (p.estado === 'RECHAZADO') motivos.set(p.motivoRechazo || 'Sin motivo', (motivos.get(p.motivoRechazo || 'Sin motivo') || 0) + 1);
    }
    const todas = [], porTipo = new Map(), timeoutsPorTipo = new Map(), primeraAutorizacion = new Map();
    let timeouts = 0;
    for (const t of ['autoriz', 'captura', 'reverso', 'consulta', 'benef']) { porTipo.set(t, []); timeoutsPorTipo.set(t, 0); }
    for (const m of mensajes) {
      if (!porTipo.has(m.tipo)) { porTipo.set(m.tipo, []); timeoutsPorTipo.set(m.tipo, 0); }
      if (m.respuesta == null) { timeouts++; timeoutsPorTipo.set(m.tipo, timeoutsPorTipo.get(m.tipo) + 1); continue; }
      const ms = Math.max(0, m.respuesta - m.envio);
      todas.push(ms); porTipo.get(m.tipo).push(ms);
      if (m.tipo === 'autoriz' && !primeraAutorizacion.has(m.idPago)) primeraAutorizacion.set(m.idPago, ms);
    }
    const latenciasPorTipo = [];
    for (const [tipo, muestras] of porTipo) {
      const to = timeoutsPorTipo.get(tipo);
      if (muestras.length || to > 0) latenciasPorTipo.push({ tipo, mensajes: muestras.length + to, timeouts: to, latencias: latencias(muestras) });
    }
    const creacionPorPago = new Map(pagos.map(p => [p.idPago, p.creacion]));
    const resultadoMs = new Map(), ultimaTransicion = new Map(), origenReversos = new Map();
    for (const t of transiciones) {
      const creacion = creacionPorPago.get(t.idPago);
      if (creacion == null) continue;
      ultimaTransicion.set(t.idPago, Math.max(ultimaTransicion.get(t.idPago) ?? -Infinity, t.fechaHora));
      if ((t.nuevo === 'CAPTURADO' || t.nuevo === 'RECHAZADO' || t.nuevo === 'REVERSADO') && !resultadoMs.has(t.idPago)) resultadoMs.set(t.idPago, Math.max(0, t.fechaHora - creacion));
      if (t.nuevo === 'REVERSADO') origenReversos.set(t.anterior || 'desconocido', (origenReversos.get(t.anterior || 'desconocido') || 0) + 1);
    }
    const pico = (items, clave) => { const c = new Map(); let max = 0; for (const it of items) { const k = clave(it); const n = (c.get(k) || 0) + 1; c.set(k, n); if (n > max) max = n; } return max; };
    const resumen = {
      pagos: pagos.length, aprobados, rechazados, enCurso, reversados, conDiferencia, conciliados, timeouts, mensajesSuiche: mensajes.length,
      tasaAprobacionPb: tasaPb(aprobados, aprobados + rechazados), tasaReversosPb: tasaPb(reversados, aprobados), tasaTimeoutsPb: tasaPb(timeouts, mensajes.length),
      latenciaSuiche: latencias(todas), latenciaExtremoAExtremo: latencias([...resultadoMs.values()]),
      pagosSegundoPico: pico(pagos, p => Math.floor(p.creacion / 1000)), pagosMinutoPico: pico(pagos, p => Math.floor(p.creacion / 60000)),
      mensajesSegundoPico: pico(mensajes, m => Math.floor(m.envio / 1000)), montoTotalCts: monto, comisionTotalCts: comision,
    };
    // serie temporal
    const porDia = bucketSeg >= 86400, bucketMs = bucketSeg * 1000;
    const inicio = porDia ? inicioDiaLocal(desde) : Math.floor(desde / (bucketSeg >= 3600 ? 3600000 : 60000)) * (bucketSeg >= 3600 ? 3600000 : 60000);
    const indice = t => porDia ? Math.round((inicioDiaLocal(t) - inicio) / 86400000) : Math.floor((t - inicio) / bucketMs);
    const n = Math.max(1, Math.min(2000, indice(hasta - 1) + 1));
    const b = Array.from({ length: n }, (_, i) => ({ inicio: porDia ? inicio + i * 86400000 : inicio + i * bucketMs, pagos: 0, aprobados: 0, rechazados: 0, enCurso: 0, reversados: 0, timeouts: 0, lat: [], seg: new Map(), pagosSegundoPico: 0 }));
    for (const p of pagos) {
      const i = indice(p.creacion); if (i < 0 || i >= n) continue;
      const x = b[i]; x.pagos++;
      if (aprobado(p)) x.aprobados++; else if (rechazado(p)) x.rechazados++; else x.enCurso++;
      if (p.estado === 'REVERSADO') x.reversados++;
      const s = Math.floor(p.creacion / 1000); const c = (x.seg.get(s) || 0) + 1; x.seg.set(s, c); x.pagosSegundoPico = Math.max(x.pagosSegundoPico, c);
    }
    for (const m of mensajes) {
      const i = indice(m.envio); if (i < 0 || i >= n) continue;
      if (m.respuesta == null) b[i].timeouts++; else b[i].lat.push(Math.max(0, m.respuesta - m.envio));
    }
    const series = b.map(x => {
      const l = latencias(x.lat);
      return { inicio: new Date(x.inicio).toISOString(), pagos: x.pagos, aprobados: x.aprobados, rechazados: x.rechazados, enCurso: x.enCurso, reversados: x.reversados, timeouts: x.timeouts,
        tasaAprobacionPb: tasaPb(x.aprobados, x.aprobados + x.rechazados), tasaReversosPb: tasaPb(x.reversados, x.aprobados),
        p50Ms: l.muestras ? l.p50Ms : null, p95Ms: l.muestras ? l.p95Ms : null, pagosSegundoPico: x.pagosSegundoPico };
    });
    const estados = ORDEN_ESTADOS.filter(e => porEstado.get(e)).map(e => ({ clave: e, cantidad: porEstado.get(e) }));
    const pagosRecientes = pagos.slice().sort((a, c) => c.creacion - a.creacion || c.idPago - a.idPago).slice(0, 60).map(p => ({
      idPago: p.idPago, estado: p.estado, montoCts: p.montoCts, escenario: p.montoCts % 100, creacion: new Date(p.creacion).toISOString(), referenciaSuiche: p.referenciaSuiche,
      latenciaAutorizacionMs: primeraAutorizacion.get(p.idPago) ?? null,
      latenciaTotalMs: ultimaTransicion.has(p.idPago) ? Math.max(0, ultimaTransicion.get(p.idPago) - p.creacion) : null, motivoRechazo: p.motivoRechazo,
    }));
    return { resumen, latenciasPorTipo, series, estados, motivosRechazo: ordenarConteo(motivos), origenReversos: ordenarConteo(origenReversos), pagosRecientes };
  }
  return { calcular, percentil, tasaPb };
})();

const DatosDemo = (() => {
  // PRNG determinista (mulberry32)
  function prng(semilla) {
    let a = semilla >>> 0;
    return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  }
  const ESCENARIOS = [[0, 0.87], [51, 0.04], [13, 0.04], [77, 0.02], [98, 0.015], [99, 0.015]];
  const HORA = 3600000;
  // perfil horario de llegadas (pagos/hora) y ráfaga de prueba de carga a las 15:00
  const PERFIL = [1, 1, 0.5, 0.5, 0.5, 1, 2, 4, 7, 9, 10, 10, 9, 9, 10, 11, 10, 9, 8, 7, 5, 4, 3, 2];
  const cache = new Map();

  /** Pagos creados en la hora [h, h+1h) (h = múltiplo de 3 600 000 ms). Deterministas por hora. */
  function generarHora(h, ahora) {
    const clave = h + ':' + Math.floor(ahora / 60000);
    if (cache.has(clave)) return cache.get(clave);
    const r = prng(Math.floor(h / HORA) * 2654435761);
    const horaLocal = new Date(h).getHours();
    const esperado = PERFIL[horaLocal] * (new Date(h).getDay() % 6 === 0 ? 0.6 : 1);
    const cuantos = Math.max(0, Math.round(esperado + (r() - 0.5) * esperado * 0.8));
    const tiempos = [];
    for (let i = 0; i < cuantos; i++) tiempos.push(h + Math.floor(r() * HORA));
    if (horaLocal === 15) for (let i = 0; i < 36; i++) tiempos.push(h + 12 * 60000 + Math.floor(r() * 9000)); // ráfaga: 36 pagos en 9 s
    tiempos.sort((a, b) => a - b);
    const pagos = [], mensajes = [], transiciones = [];
    const lento = () => (r() < 0.03 ? 3.2 : 1);                      // cola p99
    const lat = (base, var_) => Math.round((base + r() * var_ + (r() < 0.25 ? r() * var_ : 0)) * lento());
    tiempos.forEach((t, i) => {
      if (t > ahora) return;
      const id = Math.floor(h / HORA) * 1000 + i + 1;
      let esc = 0, acc = 0, u = r();
      for (const [cod, p] of ESCENARIOS) { acc += p; if (u < acc) { esc = cod; break; } }
      let monto = (500 + Math.floor(r() * 29500)) * 100;             // Bs 500 – 30 000
      monto = monto - (monto % 100) + (esc === 0 ? [0, 7, 23, 42, 66, 88][Math.floor(r() * 6)] : esc);
      const pago = { idPago: id, estado: 'PENDIENTE', montoCts: monto, comisionCts: 2000 + Math.round(monto * 0.002), creacion: t, captura: null, motivoRechazo: null, referenciaSuiche: null };
      const tr = (ant, nuevo, cuando, actor) => transiciones.push({ idPago: id, anterior: ant, nuevo, fechaHora: cuando, actor });
      const msj = (tipo, envio, ms, resultado, codigo) => mensajes.push({ idPago: id, tipo, envio, respuesta: ms == null ? null : envio + ms, resultado, codigo });
      tr(null, 'PENDIENTE', t, 'usuario');
      const ref = 'S7B-' + new Date(t).toISOString().slice(0, 10).replace(/-/g, '') + '-' + String(id % 1000000).padStart(6, '0');
      let cursor = t + 40;
      const conciliacion = inicioDiaSiguiente(t) + HORA;              // 01:00 del día siguiente
      const conciliado = conciliacion <= ahora;
      if (esc === 51) {
        const ms = lat(170, 180); msj('autoriz', cursor, ms, 'rechazado', '51'); cursor += ms;
        pago.estado = 'RECHAZADO'; pago.motivoRechazo = 'Fondos insuficientes'; tr('PENDIENTE', 'RECHAZADO', cursor, 'suiche');
      } else if (esc === 13) {
        msj('autoriz', cursor, null, 'timeout', null); cursor += 8000;
        pago.estado = 'EN_VERIFICACION'; tr('PENDIENTE', 'EN_VERIFICACION', cursor, 'sistema');
        if (ahora - t > 45000) {
          const ms = lat(90, 80); msj('consulta', cursor + 100, ms, 'aprobado', 'autorizado'); cursor += 100 + ms;
          pago.estado = 'AUTORIZADO'; pago.referenciaSuiche = ref; tr('EN_VERIFICACION', 'AUTORIZADO', cursor, 'suiche');
          const mc = lat(150, 200); msj('captura', cursor + 30, mc, 'aprobado', '00'); cursor += 30 + mc;
          pago.estado = 'CAPTURADO'; pago.captura = cursor; tr('AUTORIZADO', 'CAPTURADO', cursor, 'suiche');
          if (conciliado) { pago.estado = 'CONCILIADO'; tr('CAPTURADO', 'CONCILIADO', conciliacion, 'sistema'); }
        }
      } else {
        const ms = lat(160, 220); msj('autoriz', cursor, ms, 'aprobado', '00'); cursor += ms;
        pago.estado = 'AUTORIZADO'; pago.referenciaSuiche = ref; tr('PENDIENTE', 'AUTORIZADO', cursor, 'suiche');
        if (esc === 77) {
          const mc = lat(150, 150); msj('captura', cursor + 30, mc, 'rechazado', '05'); cursor += 30 + mc;
          const mr = lat(110, 120); msj('reverso', cursor + 20, mr, 'aprobado', '00'); cursor += 20 + mr;
          pago.estado = 'RECHAZADO'; pago.motivoRechazo = 'Captura rechazada por el emisor: Operación no permitida por el emisor';
          tr('AUTORIZADO', 'RECHAZADO', cursor, 'suiche');
        } else {
          const mc = lat(140, 200); msj('captura', cursor + 30, mc, 'aprobado', '00'); cursor += 30 + mc;
          pago.estado = 'CAPTURADO'; pago.captura = cursor; tr('AUTORIZADO', 'CAPTURADO', cursor, 'suiche');
          if (conciliado) {
            if (esc === 98 || esc === 99) {
              pago.estado = 'CON_DIFERENCIA'; tr('CAPTURADO', 'CON_DIFERENCIA', conciliacion + 1500, 'sistema');
              const mr = lat(120, 140); msj('reverso', conciliacion + 1600, mr, 'aprobado', '00');
              pago.estado = 'REVERSADO'; tr('CON_DIFERENCIA', 'REVERSADO', conciliacion + 1600 + mr, 'suiche');
            } else { pago.estado = 'CONCILIADO'; tr('CAPTURADO', 'CONCILIADO', conciliacion + 1200, 'sistema'); }
          }
        }
      }
      pagos.push(pago);
    });
    const res = { pagos, mensajes, transiciones };
    cache.set(clave, res);
    return res;
  }
  function inicioDiaSiguiente(t) { const d = new Date(t); d.setHours(24, 0, 0, 0); return d.getTime(); }

  function recolectar(desde, hasta, ahora) {
    const pagos = [], mensajes = [], transiciones = [];
    for (let h = Math.floor(desde / HORA) * HORA; h < hasta; h += HORA) {
      const g = generarHora(h, ahora);
      for (const p of g.pagos) if (p.creacion >= desde && p.creacion < hasta) { pagos.push(p); }
      const ids = new Set(pagos.map(p => p.idPago));
      for (const m of g.mensajes) if (ids.has(m.idPago)) mensajes.push(m);
      for (const t of g.transiciones) if (ids.has(t.idPago)) transiciones.push(t);
    }
    return { pagos, mensajes, transiciones };
  }

  function bucketAutomatico(segundos) { return segundos <= 3 * 3600 ? 60 : segundos <= 3 * 86400 ? 3600 : 86400; }

  /** Devuelve exactamente la forma de KpisApi.TableroKpis. */
  function tablero({ rango, bucket, ahora = Date.now() }) {
    const hasta = ahora;
    const inicioHoy = new Date(hasta); inicioHoy.setHours(0, 0, 0, 0);
    const desde = { hoy: inicioHoy.getTime(), '1h': hasta - HORA, '24h': hasta - 24 * HORA, '7d': hasta - 7 * 24 * HORA, '30d': hasta - 30 * 24 * HORA }[rango] ?? hasta - 24 * HORA;
    const segundos = Math.round((hasta - desde) / 1000);
    let b = bucket === 'minuto' ? 60 : bucket === 'hora' ? 3600 : bucket === 'dia' ? 86400 : bucketAutomatico(segundos);
    while (segundos / b > 2000) b = b < 3600 ? 3600 : 86400;
    const actual = recolectar(desde, hasta, ahora);
    const previo = recolectar(desde - (hasta - desde), desde, ahora);
    const c = CalculoKpis.calcular(desde, hasta, b, actual.pagos, actual.mensajes, actual.transiciones);
    const cp = CalculoKpis.calcular(desde - (hasta - desde), desde, b, previo.pagos, previo.mensajes, previo.transiciones);
    return {
      ventana: { desde: new Date(desde).toISOString(), hasta: new Date(hasta).toISOString(), ahora: new Date(ahora).toISOString(), bucket: b >= 86400 ? 'dia' : b >= 3600 ? 'hora' : 'minuto', segundos, segundosBucket: b },
      resumen: c.resumen, resumenPrevio: cp.resumen, latenciasPorTipo: c.latenciasPorTipo, series: c.series, estados: c.estados,
      motivosRechazo: c.motivosRechazo, origenReversos: c.origenReversos, pagosRecientes: c.pagosRecientes,
    };
  }
  return { tablero };
})();
