package ve.impulsatech.ahorramas.simuladores.consola;

import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ve.impulsatech.ahorramas.simuladores.bcv.BcvDtos.Tasa;
import ve.impulsatech.ahorramas.simuladores.bcv.BcvService;
import ve.impulsatech.ahorramas.simuladores.instituciones.BancoService;
import ve.impulsatech.ahorramas.simuladores.instituciones.BancoService.CuentaSim;
import ve.impulsatech.ahorramas.simuladores.instituciones.Institucion;
import ve.impulsatech.ahorramas.simuladores.notificaciones.NotificacionesService;
import ve.impulsatech.ahorramas.simuladores.notificaciones.NotificacionesService.Notificacion;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.Operacion;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheService;

/**
 * Consola web del simulador (contrato §7): OTP y notificaciones, operaciones del suiche, tasa del
 * día y formularios para los controles demo. Los datos se refrescan cada 5 s sin recargar la página
 * (así no se pierde lo escrito en los formularios).
 */
@RestController
public class ConsolaController {

    private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);

    private final BcvService bcv;
    private final NotificacionesService notificaciones;
    private final SuicheService suiche;
    private final BancoService banco;

    public ConsolaController(BcvService bcv, NotificacionesService notificaciones, SuicheService suiche,
                             BancoService banco) {
        this.bcv = bcv;
        this.notificaciones = notificaciones;
        this.suiche = suiche;
        this.banco = banco;
    }

    @GetMapping("/")
    public ResponseEntity<String> consola() {
        return ResponseEntity.ok().contentType(HTML).body(PAGINA
                .replace("{{OPCIONES_INSTITUCION}}", opcionesInstitucion())
                .replace("{{DATOS}}", datos()));
    }

    @GetMapping("/consola/datos")
    public ResponseEntity<String> fragmento() {
        return ResponseEntity.ok().contentType(HTML).body(datos());
    }

    // ---------------------------------------------------------------- fragmento dinámico

    private String datos() {
        StringBuilder sb = new StringBuilder();
        Tasa tasa = bcv.tasaHoy("USD");
        sb.append("<section><h2>Tasa BCV del día</h2><p class=\"tasa\">")
                .append(bs(tasa.valorBsX10000())).append(" Bs/USD</p><p class=\"tenue\">")
                .append(htmlEscape(tasa.fecha())).append(" · valorBsX10000 = ").append(tasa.valorBsX10000())
                .append(bcv.hayTasaForzadaHoy() ? " · <strong>forzada por la consola</strong>" : " · generada")
                .append("</p></section>");

        List<Notificacion> notis = notificaciones.ultimas(null, 25);
        sb.append("<section><h2>Notificaciones enviadas (SMS / push / correo)</h2>");
        if (notis.isEmpty()) {
            sb.append("<p class=\"tenue\">Aún no se ha enviado ninguna notificación.</p>");
        } else {
            sb.append("<table><tr><th>Hora</th><th>Canal</th><th>Destino</th><th>Tipo</th><th>Mensaje</th></tr>");
            for (Notificacion n : notis) {
                boolean otp = "otp".equals(n.tipo());
                sb.append("<tr").append(otp ? " class=\"otp\"" : "").append("><td>").append(hora(n.fechaHora()))
                        .append("</td><td>").append(htmlEscape(n.canal())).append("</td><td>")
                        .append(htmlEscape(n.destino())).append("</td><td>").append(htmlEscape(nulo(n.tipo())))
                        .append("</td><td>");
                if (n.titulo() != null && !n.titulo().isBlank()) {
                    sb.append("<strong>").append(htmlEscape(n.titulo())).append("</strong> ");
                }
                sb.append(htmlEscape(n.mensaje())).append("</td></tr>");
            }
            sb.append("</table>");
        }
        sb.append("</section>");

        List<Operacion> ops = suiche.ultimas(30);
        sb.append("<section><h2>Operaciones del suiche</h2>");
        if (ops.isEmpty()) {
            sb.append("<p class=\"tenue\">Sin operaciones todavía.</p>");
        } else {
            sb.append("<table><tr><th>Actualizada</th><th>Referencia</th><th>Ref. suiche</th><th>Autorización</th>"
                    + "<th class=\"num\">Monto (Bs)</th><th>Estado</th></tr>");
            for (Operacion o : ops) {
                sb.append("<tr><td>").append(hora(o.fechaHora())).append("</td><td><code>")
                        .append(htmlEscape(o.referencia())).append("</code></td><td>")
                        .append(htmlEscape(nulo(o.referenciaSuiche()))).append("</td><td>")
                        .append(htmlEscape(nulo(o.codigoAutorizacion()))).append("</td><td class=\"num\">")
                        .append(o.montoCts() == null ? "" : bsCts(o.montoCts())).append("</td><td><span class=\"estado ")
                        .append(htmlEscape(o.estado())).append("\">").append(htmlEscape(o.estado()))
                        .append("</span></td></tr>");
            }
            sb.append("</table>");
        }
        sb.append("</section>");

        List<CuentaSim> cuentas = banco.todasLasCuentas();
        sb.append("<section><h2>Cuentas generadas en el simulador</h2>");
        sb.append("<table><tr><th>Cédula</th><th>Inst.</th><th>refCuenta</th><th>Tipo</th>"
                + "<th class=\"num\">Saldo</th><th class=\"num\">Disponible</th></tr>");
        for (CuentaSim c : cuentas.stream().limit(60).toList()) {
            sb.append("<tr><td>").append(htmlEscape(c.cedula())).append("</td><td>").append(htmlEscape(c.codigo()))
                    .append("</td><td><code>").append(htmlEscape(c.ref())).append("</code></td><td>")
                    .append(htmlEscape(c.tipo())).append("</td><td class=\"num\">").append(bsCts(c.saldoCts()))
                    .append(' ').append(htmlEscape(c.moneda())).append("</td><td class=\"num\">")
                    .append(bsCts(c.saldoDisponibleCts())).append("</td></tr>");
        }
        sb.append("</table></section>");
        return sb.toString();
    }

    private static String opcionesInstitucion() {
        StringBuilder sb = new StringBuilder();
        for (Institucion i : Institucion.CATALOGO) {
            sb.append("<option value=\"").append(i.codigo()).append("\">").append(i.codigo()).append(" · ")
                    .append(htmlEscape(i.nombre())).append("</option>");
        }
        return sb.toString();
    }

    /** Bs con 2 decimales a partir de un valor ×10⁴ (formato venezolano: 1.234,56). */
    private static String bs(long valorX10000) {
        return bsCts((valorX10000 + 50) / 100);
    }

    /** Monto en céntimos con formato 1.234,56 (solo presentación). */
    private static String bsCts(long cts) {
        String signo = cts < 0 ? "-" : "";
        long abs = Math.abs(cts);
        String enteros = String.format(Locale.ROOT, "%,d", abs / 100).replace(',', '.');
        return signo + enteros + "," + String.format(Locale.ROOT, "%02d", abs % 100);
    }

    private static String hora(String fechaHora) {
        return fechaHora == null || fechaHora.length() < 19 ? nulo(fechaHora) : fechaHora.substring(11, 19);
    }

    private static String nulo(String s) {
        return s == null ? "—" : s;
    }

    private static final String PAGINA = """
            <!doctype html>
            <html lang="es">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Consola de simuladores · Ahorra+</title>
            <style>
              body { margin:0; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
                     background:#f4f6f9; color:#1d2433; }
              header { background:#14324a; color:#fff; padding:14px 20px; }
              header h1 { margin:0; font-size:20px; }
              header p { margin:4px 0 0; font-size:13px; opacity:.8; }
              main { display:grid; grid-template-columns: minmax(0,2fr) minmax(280px,1fr); gap:16px;
                     padding:16px; max-width:1400px; margin:0 auto; }
              @media (max-width: 900px) { main { grid-template-columns: 1fr; } }
              section, form { background:#fff; border-radius:10px; padding:14px 16px; margin-bottom:16px;
                              box-shadow:0 1px 4px rgba(0,0,0,.06); }
              h2 { font-size:15px; margin:0 0 10px; }
              table { width:100%; border-collapse:collapse; font-size:13px; }
              th, td { text-align:left; padding:5px 6px; border-bottom:1px solid #eceff3; vertical-align:top; }
              th { color:#556; font-weight:600; }
              .num { text-align:right; font-variant-numeric: tabular-nums; white-space:nowrap; }
              tr.otp td { background:#fff7d6; font-weight:600; }
              .tasa { font-size:28px; font-weight:700; margin:0; }
              .tenue { color:#667; font-size:13px; margin:4px 0; }
              .estado { padding:2px 8px; border-radius:10px; font-size:12px; background:#e4e7ec; }
              .estado.capturado { background:#d3f2df; color:#14632f; }
              .estado.autorizado { background:#dbe8ff; color:#1d3f8a; }
              .estado.rechazado { background:#ffdede; color:#8a1d1d; }
              .estado.reversado { background:#f1e3ff; color:#5a1d8a; }
              label { display:block; font-size:12px; color:#445; margin:8px 0 3px; }
              input, select { width:100%; box-sizing:border-box; padding:7px; border:1px solid #c8cdd6;
                              border-radius:6px; font-size:14px; }
              button { margin-top:10px; padding:8px 14px; border:0; border-radius:6px; background:#14324a;
                       color:#fff; cursor:pointer; font-size:14px; }
              button.secundario { background:#e4e7ec; color:#1d2433; }
              pre { background:#f4f6f9; padding:8px; border-radius:6px; font-size:12px; white-space:pre-wrap;
                    word-break:break-all; max-height:180px; overflow:auto; }
              code { font-size:12px; }
            </style>
            </head>
            <body>
            <header>
              <h1>Consola de simuladores · Ahorra+ (MVP)</h1>
              <p>BCV · Open Banking · Suiche · KYC · IA · Notificaciones — todo es simulado, nada sale de esta máquina.
                 Se actualiza cada 5 s.</p>
            </header>
            <main>
              <div id="datos">{{DATOS}}</div>
              <div>
                <form id="formTasa">
                  <h2>Fijar la tasa BCV de hoy</h2>
                  <label for="tasaBs">Bs por USD (ej. 510,25)</label>
                  <input id="tasaBs" placeholder="510,25" required>
                  <button type="submit">Fijar tasa</button>
                  <button type="button" class="secundario" id="quitarTasa">Volver a la tasa generada</button>
                </form>
                <form id="formMov">
                  <h2>Inyectar movimiento bancario</h2>
                  <label for="codigo">Institución</label>
                  <select id="codigo">{{OPCIONES_INSTITUCION}}</select>
                  <label for="cedula">Cédula</label>
                  <input id="cedula" value="V12345678" required>
                  <label for="refCuenta">refCuenta (vacío = primera cuenta)</label>
                  <input id="refCuenta" placeholder="0901-AH-7f3a9c">
                  <label for="naturaleza">Naturaleza</label>
                  <select id="naturaleza"><option>credito</option><option>debito</option></select>
                  <label for="montoBs">Monto (ej. 5000,00)</label>
                  <input id="montoBs" value="5000,00" required>
                  <label for="descripcion">Descripción</label>
                  <input id="descripcion" value="PAGO NOMINA">
                  <label for="clasificacion">Clasificación</label>
                  <select id="clasificacion"><option>ingreso</option><option>gasto</option>
                    <option>transf_propia</option><option>pago_tdc</option></select>
                  <button type="submit">Inyectar</button>
                </form>
                <section>
                  <h2>Respuesta del último control</h2>
                  <pre id="resultado">—</pre>
                </section>
                <section>
                  <h2>Escenarios del suiche (montoCts % 100)</h2>
                  <table>
                    <tr><td><code>13</code></td><td>Timeout (queda autorizado; responde tarde)</td></tr>
                    <tr><td><code>51</code></td><td>Rechazo por fondos insuficientes</td></tr>
                    <tr><td><code>77</code></td><td>Autoriza pero rechaza la captura</td></tr>
                    <tr><td><code>98</code></td><td>No aparece en el archivo de liquidación</td></tr>
                    <tr><td><code>99</code></td><td>Liquidación con +100 cts</td></tr>
                  </table>
                  <p class="tenue">KYC: cédula terminada en 666 ⇒ sanciones; en 000 ⇒ falla prueba de vida.</p>
                </section>
              </div>
            </main>
            <script>
              const resultado = document.getElementById('resultado');
              function aCentimos(texto, factor) {
                const limpio = String(texto).trim().replace(/\\./g, '').replace(',', '.');
                const valor = Number(limpio);
                if (!isFinite(valor) || valor <= 0) { throw new Error('Monto inválido: ' + texto); }
                return Math.round(valor * factor);
              }
              async function enviar(url, metodo, cuerpo) {
                try {
                  const r = await fetch(url, { method: metodo, headers: { 'Content-Type': 'application/json' },
                                               body: cuerpo ? JSON.stringify(cuerpo) : undefined });
                  resultado.textContent = r.status + '\\n' + JSON.stringify(await r.json(), null, 2);
                  refrescar();
                } catch (e) { resultado.textContent = String(e); }
              }
              document.getElementById('formTasa').addEventListener('submit', ev => {
                ev.preventDefault();
                try {
                  enviar('/simulador/bcv/tasa-hoy', 'POST',
                         { valorBsX10000: aCentimos(document.getElementById('tasaBs').value, 10000) });
                } catch (e) { resultado.textContent = String(e); }
              });
              document.getElementById('quitarTasa').addEventListener('click', () => enviar('/simulador/bcv/tasa-hoy', 'DELETE'));
              document.getElementById('formMov').addEventListener('submit', ev => {
                ev.preventDefault();
                const v = id => document.getElementById(id).value;
                try {
                  enviar('/simulador/ob/' + v('codigo') + '/movimientos', 'POST', {
                    cedula: v('cedula'), refCuenta: v('refCuenta') || null, naturaleza: v('naturaleza'),
                    montoCts: aCentimos(v('montoBs'), 100), descripcion: v('descripcion'),
                    clasificacion: v('clasificacion') });
                } catch (e) { resultado.textContent = String(e); }
              });
              async function refrescar() {
                try {
                  const r = await fetch('/consola/datos');
                  if (r.ok) { document.getElementById('datos').innerHTML = await r.text(); }
                } catch (e) { /* el simulador se está reiniciando */ }
              }
              setInterval(refrescar, 5000);
            </script>
            </body>
            </html>
            """;

    /** Escapa solo los caracteres especiales de HTML (los acentos se envían tal cual en UTF-8). */
    private static String htmlEscape(String texto) {
        return HtmlUtils.htmlEscape(texto, "UTF-8");
    }
}
