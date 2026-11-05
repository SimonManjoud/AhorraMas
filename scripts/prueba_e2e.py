#!/usr/bin/env python3
"""
Ahorra+ · Prueba de punta a punta contra los servicios reales (núcleo + simuladores).

Requisitos: núcleo en http://localhost:8080 (perfil local/docker, modo demo) y simuladores en
http://localhost:8090. Solo usa la biblioteca estándar de Python.

    python scripts/prueba_e2e.py [--nucleo URL] [--simuladores URL]

Recorre: registro + KYC → vinculación multibanco por consentimiento (emula el navegador en la banca
simulada) → consolidado → Cofre Protector (meta, Ruta del día, aporte, regla automática disparada por
una nómina, racha, sello, Espejo) → pago de TDC (feliz, idempotente, rechazo, timeout, captura
rechazada, diferencia) → IA → avance de día demo (conciliación) → backoffice.
"""
import argparse
import base64
import json
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

FALLAS = []


def llamar(metodo, url, cuerpo=None, token=None, cabeceras=None, form=False, seguir=True):
    datos = None
    h = {"Accept": "application/json"}
    if cuerpo is not None:
        if form:
            datos = urllib.parse.urlencode(cuerpo).encode()
            h["Content-Type"] = "application/x-www-form-urlencoded"
        else:
            datos = json.dumps(cuerpo).encode()
            h["Content-Type"] = "application/json"
    if token:
        h["Authorization"] = "Bearer " + token
    h.update(cabeceras or {})
    req = urllib.request.Request(url, data=datos, headers=h, method=metodo)
    opener = urllib.request.build_opener() if seguir else urllib.request.build_opener(SinRedireccion())
    try:
        with opener.open(req, timeout=60) as r:
            txt = r.read().decode("utf-8")
            return r.status, (json.loads(txt) if txt.strip().startswith(("{", "[")) else txt), r.headers
    except urllib.error.HTTPError as e:
        txt = e.read().decode("utf-8")
        cuerpo = json.loads(txt) if txt.strip().startswith(("{", "[")) else txt
        return e.code, cuerpo, e.headers


class SinRedireccion(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


def paso(nombre, condicion, detalle=""):
    marca = "OK " if condicion else "FALLA"
    print(f"  [{marca}] {nombre}" + (f"  → {detalle}" if detalle else ""))
    if not condicion:
        FALLAS.append(nombre)


def bs(cts):
    return f"Bs {cts / 100:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")


def usd(cts):
    return f"${cts / 100:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")   # consolas de Windows (cp1252)
    ap = argparse.ArgumentParser()
    ap.add_argument("--nucleo", default="http://localhost:8080")
    ap.add_argument("--simuladores", default="http://localhost:8090")
    a = ap.parse_args()
    API = a.nucleo + "/api/v1"
    SIM = a.simuladores

    print("\n== 0. Servicios")
    paso("núcleo arriba", llamar("GET", a.nucleo + "/actuator/health")[1].get("status") == "UP")
    paso("simuladores arriba", llamar("GET", SIM + "/bcv/tasas/hoy?moneda=USD")[0] == 200)

    print("\n== 1. Registro, KYC y login (M1)")
    cedula = random.randint(10_000_000, 29_999_999)
    huella = f"e2e-{cedula}"
    s, reg, _ = llamar("POST", API + "/auth/registro", {
        "nacionalidadDoc": "V", "cedula": cedula, "primerNombre": "María", "primerApellido": "Pérez",
        "fechaNacimiento": "1995-04-12", "correo": f"maria{cedula}@correo.com",
        "telefonoMovil": "0414" + str(random.randint(1_000_000, 9_999_999)), "pin": "1234",
        "dispositivo": {"huella": huella, "plataforma": "Android", "modelo": "Pixel 8"}})
    paso("registro", s == 201, reg if s != 201 else f"usuario {reg['usuario']['idUsuario']}")
    tk = reg["accessToken"]
    img = base64.b64encode(b"foto-cedula").decode()
    s, kyc, _ = llamar("POST", API + "/kyc/verificaciones", {"documentos": [
        {"tipo": "cedula", "contenidoBase64": img}, {"tipo": "selfie", "contenidoBase64": base64.b64encode(b"selfie").decode()}]}, tk)
    paso("KYC aprobado (proveedor simulado)", s == 200 and kyc["resultado"] == "aprobado", kyc)
    s, log, _ = llamar("POST", API + "/auth/login", {"nacionalidadDoc": "V", "cedula": cedula, "pin": "0000", "huellaDispositivo": huella})
    paso("PIN incorrecto rechazado con código propio", s == 401 and log["codigo"] == "PIN_INVALIDO", log.get("codigo"))

    def step_up(con_otp):
        cuerpo = {"pin": "1234"}
        if con_otp:
            _, otp, _ = llamar("POST", API + "/auth/otp", {"proposito": "pago"}, tk)
            cuerpo.update({"idDesafio": otp["idDesafio"], "otp": otp["otpDemo"]})
        return llamar("POST", API + "/auth/step-up", cuerpo, tk)[1]["stepUpToken"]

    print("\n== 2. Vinculación multibanco por consentimiento (F1, sin claves bancarias)")
    _, inst, _ = llamar("GET", API + "/instituciones", token=tk)
    ids = [i["idInstitucion"] for i in inst if i["codigo"] in ("0901", "0902", "C001")]
    s, sols, _ = llamar("POST", API + "/consentimientos", {"instituciones": ids, "permiteDebitoTdc": True}, tk)
    paso("consentimientos creados (selección múltiple)", s == 201 and len(sols) == 3)
    for sol in sols:
        url = urllib.parse.urlparse(sol["urlAutorizacion"])
        q = dict(urllib.parse.parse_qsl(url.query))
        pagina = llamar("GET", sol["urlAutorizacion"])
        paso(f"página del banco {sol['institucion']} (Ahorra+ no ve la clave)", pagina[0] == 200 and "SIMULADOR" in str(pagina[1]).upper())
        s, _, h = llamar("POST", f"{SIM}{url.path}", {"redirect_uri": q["redirect_uri"], "state": q["state"], "scope": q["scope"],
                                                      "cedula": q["login_hint"], "accion": "autorizar"}, form=True, seguir=False)
        destino = h.get("Location")
        s, html, _ = llamar("GET", destino)
        paso(f"callback {sol['institucion']}", s == 200 and "Listo" in html)
    _, cons, _ = llamar("GET", API + "/consentimientos", token=tk)
    paso("3 consentimientos activos", sum(1 for c in cons if c["estatus"] == "activo") == 3)

    print("\n== 3. Posición global (M2)")
    _, c, _ = llamar("GET", API + "/consolidado", token=tk)
    tasa = c["tasa"]["valorBsX10000"] / 10_000
    paso("consolidado en Bs y USD a la tasa BCV", c["totalUsdCts"] > 0 and len(c["instituciones"]) == 3,
         f"{bs(c['totalBsCts'])} / {usd(c['totalUsdCts'])} @ {tasa:.4f} Bs/USD")

    print("\n== 4. Cofre Protector (M5)")
    s, meta, _ = llamar("POST", API + "/metas", {"nombre": "Viaje en marzo", "categoria": "viaje", "montoObjetivoUsdCts": 30000,
                                                "fechaObjetivo": "2027-03-15", "frecuenciaCuota": "diaria"}, tk)
    paso("meta creada", s == 201, meta.get("cuotaHoy", {}).get("mensaje"))
    idm = meta["idMeta"]
    cuota = meta["cuotaHoy"]["montoSugeridoUsdCts"]
    s, ap, _ = llamar("POST", f"{API}/metas/{idm}/aportes", {"montoUsdCts": cuota, "origen": "cuota_del_dia"}, tk)
    paso("aporte de la cuota del día", s == 201 and ap["meta"]["cuotaHoy"]["estatus"] == "cumplida",
         f"racha {ap['racha']['diasConsecutivos']} día(s), +{ap['puntosGanados']} pts")
    su = step_up(False)
    s, regla, _ = llamar("POST", f"{API}/metas/{idm}/reglas", {"tipo": "pct_ingreso", "porcentajePb": 1000}, tk, {"X-Step-Up": su})
    paso("regla 10 % de ingreso aprobada con PIN", s == 201)
    s, _, _ = llamar("POST", f"{SIM}/simulador/ob/0901/movimientos", {"cedula": f"V{cedula}", "naturaleza": "credito", "montoCts": 20_000_000,
                                                                      "descripcion": "PAGO NOMINA", "clasificacion": "ingreso"})
    llamar("POST", API + "/consolidado/sincronizar", None, tk)
    aporte_regla = False
    for _ in range(30):
        _, aps, _ = llamar("GET", f"{API}/metas/{idm}/aportes", token=tk)
        if any(x["origen"] == "regla" for x in aps):
            aporte_regla = True
            break
        time.sleep(0.5)
    paso("la nómina disparó el aporte automático (evento → regla)", aporte_regla)
    s, sello, _ = llamar("POST", f"{API}/metas/{idm}/sello", {"montoSelladoUsdCts": cuota}, tk, {"X-Step-Up": su})
    paso("sello de compromiso", s == 201, f"hasta {sello.get('fechaLiberacion')}")
    _, racha, _ = llamar("GET", API + "/racha", token=tk)
    paso("racha activa", racha["diasConsecutivos"] >= 1, f"x{racha['multiplicadorX100'] / 100} · {racha['colchonesDisponibles']} colchones")
    _, esp, _ = llamar("GET", API + "/espejo-impacto", token=tk)
    paso("Espejo de Impacto", "disclaimer" in esp, esp.get("frase"))

    print("\n== 5. Pago de TDC interbancario (M3 · F2)")
    _, tarjetas, _ = llamar("GET", API + "/tarjetas", token=tk)
    paso("TDC descubierta por consentimiento (sin PAN)", len(tarjetas) >= 1, f"***{tarjetas[0]['ultimos4']} · {tarjetas[0]['banco']}" if tarjetas else "")
    t = tarjetas[0]
    _, benef, _ = llamar("POST", f"{API}/tarjetas/{t['idTarjeta']}/beneficiario", None, tk)
    paso("beneficiario confirmado por el suiche", bool(benef.get("titular")), benef.get("titular"))
    _, origenes, _ = llamar("GET", API + "/pagos-tdc/cuentas-origen", token=tk)
    origen = next(o for o in origenes if o["banco"] != t["banco"]) if len(origenes) > 1 else origenes[0]
    sup = step_up(True)

    def pagar(monto, clave=None):
        clave = clave or str(uuid.uuid4())
        s, p, _ = llamar("POST", API + "/pagos-tdc", {"idTarjeta": t["idTarjeta"], "idCuentaOrigen": origen["idCuenta"], "montoCts": monto,
                                                      "beneficiarioConfirmado": True}, tk, {"X-Step-Up": sup, "Idempotency-Key": clave})
        if "estado" not in p:
            print(f"        respuesta inesperada ({s}): {p}")
            p = dict(p, estado=None, historial=[])
        return s, p, clave

    s, p, clave = pagar(1_500_000)
    paso("pago feliz → CAPTURADO (banco a banco)", s == 201 and p["estado"] == "CAPTURADO", f"{p['referenciaSuiche']} · comisión {bs(p['comisionCts'])}")
    s2, p2, _ = pagar(1_500_000, clave)
    paso("reintento con la misma Idempotency-Key no duplica", s2 == 200 and p2["idPago"] == p["idPago"])
    s, p, _ = pagar(1_000_051)
    paso("rechazo (fondos) → RECHAZADO sin comisión", p["estado"] == "RECHAZADO" and p["comisionCts"] == 0, p.get("motivoRechazo"))
    s, p, _ = pagar(1_000_013)
    hist = [h["estadoNuevo"] for h in p.get("historial", [])]
    paso("timeout → EN_VERIFICACION → consulta → CAPTURADO", p["estado"] == "CAPTURADO" and "EN_VERIFICACION" in hist, " → ".join(hist))
    s, p, _ = pagar(1_000_077)
    paso("captura rechazada → reverso → RECHAZADO", p["estado"] == "RECHAZADO", p.get("motivoRechazo"))

    print("\n== 6. Asistente IA (M4 · F4)")
    s, ev, _ = llamar("POST", API + "/perfil/evaluaciones", {"objetivoPrincipal": "proteger", "horizonteMeses": 6, "toleranciaRiesgo": "baja"}, tk)
    paso("recomendación explicada por el LLM con datos seudonimizados", s == 200, f"perfil {ev.get('perfilResultante')}")
    if s == 200:
        print("        «" + ev["recomendacion"]["explicacion"][:140] + "…»")

    print("\n== 7. Cierre de día demo + conciliación + backoffice (M7)")
    s, dem, _ = llamar("POST", API + "/demo/reloj/avanzar", {"dias": 1})
    paso("avance de día (racha/colchón + conciliación)", s == 200, json.dumps(dem.get("cierres"), ensure_ascii=False))
    s, op, _ = llamar("POST", API + "/backoffice/auth/login", {"correoCorporativo": "conciliacion@ahorramas.test", "clave": "demo"})
    paso("login de operador (SSO simulado)", s == 200)
    fecha = dem["cierres"][0]["dia"]
    _, conc, _ = llamar("GET", f"{API}/backoffice/conciliaciones?fecha={fecha}", token=op["accessToken"])
    paso("archivo del suiche con hash SHA-256 verificado", conc.get("hashValido") is True and conc.get("totalRegistros", 0) > 0,
         f"{conc.get('conciliados')} conciliados · {conc.get('diferencias')} diferencias · {conc.get('estatus')}")
    _, aud, _ = llamar("GET", API + "/backoffice/auditoria?limite=5", token=op["accessToken"])
    paso("cadena de auditoría íntegra", aud.get("cadenaIntegra") is True)
    _, pts, _ = llamar("GET", API + "/puntos", token=tk)
    paso("puntos acumulados", pts["saldo"] > 0, f"{pts['saldo']} pts · nivel {pts['nivel']['nombre']}")

    print("\n" + ("TODO OK ✅" if not FALLAS else f"{len(FALLAS)} FALLA(S): {FALLAS}"))
    return 0 if not FALLAS else 1


if __name__ == "__main__":
    sys.exit(main())
