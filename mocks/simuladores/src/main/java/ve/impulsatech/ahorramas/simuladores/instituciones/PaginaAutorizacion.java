package ve.impulsatech.ahorramas.simuladores.instituciones;

import java.util.List;
import java.util.Map;

import org.springframework.web.util.HtmlUtils;

/** HTML de la "banca en línea" simulada donde el usuario autoriza a Ahorra+. */
final class PaginaAutorizacion {

    private static final Map<String, String> COLORES = Map.of(
            "0901", "#1f5f8b", "0902", "#8b4a1f", "0903", "#1f7a4d", "0904", "#6b2d8b",
            "C001", "#b8860b", "C002", "#2d6b6b");

    private static final Map<String, String> DESCRIPCION_ALCANCE = Map.of(
            OAuthService.SALDOS, "Consultar el saldo de tus cuentas",
            OAuthService.MOVIMIENTOS, "Ver los movimientos de tus cuentas (últimos 60 días)",
            OAuthService.DEBITO_TDC, "Debitar de tu cuenta para pagar tu tarjeta de crédito, "
                    + "solo cuando tú lo ordenes desde Ahorra+");

    private PaginaAutorizacion() {
    }

    static String html(Institucion inst, String clientId, String redirectUri, String state, List<String> alcances,
                       String loginHint) {
        String color = COLORES.getOrDefault(inst.codigo(), "#333");
        StringBuilder items = new StringBuilder();
        for (String a : alcances) {
            items.append("<li>").append(htmlEscape(DESCRIPCION_ALCANCE.get(a)))
                    .append(" <code>").append(htmlEscape(a)).append("</code></li>");
        }
        String cedula = loginHint == null ? "" : htmlEscape(loginHint);
        String accion = "/ob/" + inst.codigo() + "/oauth/authorize";
        return """
                <!doctype html>
                <html lang="es">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%1$s · Banca en línea (Simulador)</title>
                <style>
                  body { margin:0; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
                         background:#f2f4f7; color:#1d2433; }
                  .aviso { background:#ffe08a; color:#5a4500; text-align:center; padding:6px 12px;
                           font-size:13px; font-weight:600; letter-spacing:.3px; }
                  header { background:%2$s; color:#fff; padding:18px 20px; }
                  header h1 { margin:0; font-size:22px; }
                  header small { opacity:.85; }
                  main { max-width:440px; margin:24px auto; padding:0 16px; }
                  .tarjeta { background:#fff; border-radius:12px; padding:20px; box-shadow:0 2px 10px rgba(0,0,0,.08); }
                  h2 { font-size:17px; margin:0 0 12px; }
                  ul { padding-left:20px; margin:8px 0 16px; }
                  li { margin:6px 0; line-height:1.4; }
                  code { font-size:11px; color:#667; }
                  label { display:block; font-size:13px; margin:12px 0 4px; color:#445; }
                  input { width:100%%; box-sizing:border-box; padding:10px; border:1px solid #c8cdd6;
                          border-radius:8px; font-size:15px; }
                  .botones { display:flex; gap:10px; margin-top:20px; }
                  button { flex:1; padding:12px; border:0; border-radius:8px; font-size:15px; cursor:pointer; }
                  .autorizar { background:%2$s; color:#fff; }
                  .rechazar { background:#e4e7ec; color:#1d2433; }
                  .nota { font-size:12px; color:#667; margin-top:16px; line-height:1.4; }
                </style>
                </head>
                <body>
                <div class="aviso">SIMULADOR · Esta no es la página de un banco real · Ahorra+ MVP</div>
                <header><h1>%1$s</h1><small>Banca en línea · Código %3$s</small></header>
                <main>
                  <div class="tarjeta">
                    <h2><strong>Ahorra+</strong> solicita permiso para:</h2>
                    <ul>%4$s</ul>
                    <form method="post" action="%5$s">
                      <input type="hidden" name="client_id" value="%6$s">
                      <input type="hidden" name="redirect_uri" value="%7$s">
                      <input type="hidden" name="state" value="%8$s">
                      <input type="hidden" name="scope" value="%9$s">
                      <label for="cedula">Cédula del titular</label>
                      <input id="cedula" name="cedula" value="%10$s" placeholder="V12345678" required>
                      <label for="usuario">Usuario de %1$s</label>
                      <input id="usuario" name="usuario" autocomplete="off" placeholder="usuario">
                      <label for="clave">Clave de %1$s</label>
                      <input id="clave" name="clave" type="password" autocomplete="off" placeholder="••••••••">
                      <div class="botones">
                        <button class="rechazar" type="submit" name="accion" value="rechazar" formnovalidate>Rechazar</button>
                        <button class="autorizar" type="submit" name="accion" value="autorizar">Autorizar</button>
                      </div>
                    </form>
                    <p class="nota">Tus credenciales se ingresan solo aquí, en tu institución; Ahorra+ nunca
                    las ve. En este simulador no se validan ni se guardan: cualquier valor sirve.
                    Puedes revocar el acceso cuando quieras.</p>
                  </div>
                </main>
                </body>
                </html>
                """.formatted(htmlEscape(inst.nombre()), color, htmlEscape(inst.codigo()), items, accion,
                htmlEscape(clientId == null ? "" : clientId), htmlEscape(redirectUri),
                htmlEscape(state == null ? "" : state), htmlEscape(String.join(" ", alcances)), cedula);
    }

    /** Escapa solo los caracteres especiales de HTML (los acentos se envían tal cual en UTF-8). */
    private static String htmlEscape(String texto) {
        return HtmlUtils.htmlEscape(texto, "UTF-8");
    }
}
