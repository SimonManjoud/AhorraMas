<#
  Ahorra+ - Crea una cuenta demo completa contra el nucleo + simuladores (modo demo):
  registro + KYC, 6 instituciones vinculadas (cuentas, TDC, cripto), 4 metas con aportes,
  racha con dia colchon, reglas automaticas, sello de compromiso, puntos y recomendacion de la IA.

    powershell -File scripts\crear_cuenta_demo.ps1 [-Nucleo http://localhost:8081] [-Simuladores http://localhost:8090]

  Entrar en la app con:  V 20123456  /  PIN 123456
  Es idempotente solo a medias: si la cedula ya existe, reutiliza la cuenta (login) y no duplica metas.
#>
param(
    [string]$Nucleo = "http://localhost:8081",
    [string]$Simuladores = "http://localhost:8090",
    [int]$Cedula = 12345678,
    [string]$Pin = "123456"
)
$ErrorActionPreference = "Stop"
$API = "$Nucleo/api/v1"

function Llamar($metodo, $url, $cuerpo = $null, $token = $null, $cab = @{}) {
    $h = @{ Accept = "application/json" } + $cab
    if ($token) { $h["Authorization"] = "Bearer $token" }
    $p = @{ Method = $metodo; Uri = $url; Headers = $h; UseBasicParsing = $true }
    if ($null -ne $cuerpo) {
        $p.Body = [Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json -Depth 6 -Compress))
        $p.ContentType = "application/json; charset=utf-8"
    }
    try {
        $r = Invoke-WebRequest @p
        if ($r.Content) { return $r.Content | ConvertFrom-Json }
    } catch {
        $resp = $_.Exception.Response
        $detalle = ""
        if ($resp) { $detalle = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd() }
        throw "$metodo $url -> $detalle"
    }
}

function StepUp($token) {
    (Llamar POST "$API/auth/step-up" @{ pin = $Pin } $token).stepUpToken
}

Write-Host "== 1. Registro y KYC"
$huella = "demo-pixel-$Cedula"
$cuerpoReg = @{
    nacionalidadDoc = "V"; cedula = $Cedula; primerNombre = "Maria"; segundoNombre = "Gabriela"
    primerApellido = "Gonzalez"; segundoApellido = "Rivas"; fechaNacimiento = "1994-08-21"
    correo = "maria.gonzalez.$Cedula@correo.com"; telefonoMovil = "0412" + ("{0:D7}" -f ($Cedula % 10000000)); pin = $Pin
    dispositivo = @{ huella = $huella; plataforma = "Android"; modelo = "Pixel 10" }
}
$nueva = $true
try {
    $ses = Llamar POST "$API/auth/registro" $cuerpoReg
} catch {
    if ("$_" -notmatch "esa c.+dula") { throw }
    $nueva = $false
    Write-Host "   La cedula ya estaba registrada: se reutiliza (login)."
    $ses = Llamar POST "$API/auth/login" @{ nacionalidadDoc = "V"; cedula = $Cedula; pin = $Pin; huellaDispositivo = $huella; plataforma = "Android"; modelo = "Pixel 10" }
}
$tk = $ses.accessToken
if ($nueva) {
    $img = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("foto-cedula-demo"))
    $sel = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("selfie-demo"))
    $k = Llamar POST "$API/kyc/verificaciones" @{ documentos = @(@{ tipo = "cedula"; contenidoBase64 = $img }, @{ tipo = "selfie"; contenidoBase64 = $sel }) } $tk
    Write-Host "   KYC: $($k.resultado)"
}

Write-Host "== 2. Vinculacion de bancos y plataformas (consentimiento OAuth simulado)"
$inst = Llamar GET "$API/instituciones" $null $tk
$ids = @($inst | ForEach-Object { $_.idInstitucion })
$sols = Llamar POST "$API/consentimientos" @{ instituciones = $ids; permiteDebitoTdc = $true } $tk
foreach ($sol in $sols) {
    if (-not $sol.urlAutorizacion) { continue }
    $u = [Uri]$sol.urlAutorizacion
    $q = [Web.HttpUtility]::ParseQueryString($u.Query)
    $form = @{ redirect_uri = $q["redirect_uri"]; state = $q["state"]; scope = $q["scope"]; cedula = $q["login_hint"]; accion = "autorizar" }
    $loc = $null
    $cuerpoForm = ($form.GetEnumerator() | ForEach-Object { "$($_.Key)=$([Uri]::EscapeDataString([string]$_.Value))" }) -join "&"
    $req = [Net.HttpWebRequest]::Create("$Simuladores$($u.AbsolutePath)")
    $req.Method = "POST"; $req.AllowAutoRedirect = $false; $req.ContentType = "application/x-www-form-urlencoded"
    $bytes = [Text.Encoding]::UTF8.GetBytes($cuerpoForm)
    $req.ContentLength = $bytes.Length
    $st = $req.GetRequestStream(); $st.Write($bytes, 0, $bytes.Length); $st.Close()
    try { $resp = $req.GetResponse() } catch [Net.WebException] { $resp = $_.Exception.Response }
    if ($resp) { $loc = $resp.Headers["Location"]; $resp.Close() }
    if (-not $loc) { Write-Warning "Banca $($sol.institucion): sin redirect" }
    if (-not $loc) { Write-Warning "Sin redirect para $($sol.institucion)"; continue }
    $loc = $loc -replace "//10\.0\.2\.2:", "//localhost:"   # el redirect usa la IP del emulador
    Invoke-WebRequest -Uri $loc -UseBasicParsing | Out-Null
    Write-Host "   vinculado: $($sol.institucion)"
}
Llamar POST "$API/consolidado/sincronizar" $null $tk | Out-Null
Start-Sleep 2
$c = Llamar GET "$API/consolidado" $null $tk
Write-Host ("   Posicion: Bs {0:N2} / USD {1:N2} en {2} instituciones" -f ($c.totalBsCts / 100), ($c.totalUsdCts / 100), $c.instituciones.Count)

Write-Host "== 3. Cofre Protector: metas"
$existentes = @(Llamar GET "$API/metas" $null $tk | Where-Object { $_ })
if ($existentes.Count -gt 0) {
    Write-Host "   Ya hay $($existentes.Count) metas; no se crean de nuevo."
    $metas = $existentes
} else {
    $defs = @(
        @{ nombre = "Viaje en marzo";        categoria = "viaje";      montoObjetivoUsdCts = 30000; fechaObjetivo = "2027-03-15"; frecuenciaCuota = "diaria" },
        @{ nombre = "Fondo de emergencia";   categoria = "emergencia"; montoObjetivoUsdCts = 50000; fechaObjetivo = "2027-06-30"; frecuenciaCuota = "semanal" },
        @{ nombre = "Diplomado de datos";    categoria = "estudio";    montoObjetivoUsdCts = 18000; fechaObjetivo = "2027-01-31"; frecuenciaCuota = "diaria" },
        @{ nombre = "Remodelar la cocina";   categoria = "hogar";      montoObjetivoUsdCts = 80000; fechaObjetivo = "2027-12-15"; frecuenciaCuota = "semanal" }
    )
    $metas = @($defs | ForEach-Object { Llamar POST "$API/metas" $_ $tk })
    $metas | ForEach-Object { Write-Host "   meta: $($_.nombre) -> $($_.cuotaHoy.mensaje)" }
}
$viaje = $metas[0].idMeta; $emerg = $metas[1].idMeta; $estudio = $metas[2].idMeta; $cocina = $metas[3].idMeta

Write-Host "== 4. Aportes y racha (avanzando el reloj demo; un dia se salta para usar el colchon)"
function Aportar($id, $usdCts, $origen = "manual") {
    Llamar POST "$API/metas/$id/aportes" @{ montoUsdCts = $usdCts; origen = $origen } $tk | Out-Null
}
function AvanzarDia() { Llamar POST "$API/demo/reloj/avanzar" @{ dias = 1 } | Out-Null }

# Dia 1
Aportar $viaje 230 "cuota_del_dia"; Aportar $emerg 2500; Aportar $estudio 1500
AvanzarDia
# Dia 2
Aportar $viaje 230 "cuota_del_dia"; Aportar $estudio 400; Aportar $cocina 5000
AvanzarDia
# Dia 3: sin aporte (usa dia colchon)
AvanzarDia
# Dia 4
Aportar $viaje 460 "cuota_del_dia"; Aportar $emerg 3000
AvanzarDia
# Dia 5
Aportar $viaje 230 "cuota_del_dia"; Aportar $estudio 800; Aportar $cocina 2500
AvanzarDia
# Hoy: aporte de la cuota
Aportar $viaje 230 "cuota_del_dia"
Write-Host "   aportes registrados en 5 dias + hoy"

Write-Host "== 5. Reglas automaticas y sello (requieren PIN)"
$su = StepUp $tk
try {
    Llamar POST "$API/metas/$emerg/reglas" @{ tipo = "pct_ingreso"; porcentajePb = 1000 } $tk @{ "X-Step-Up" = $su } | Out-Null
    Llamar POST "$API/metas/$viaje/reglas" @{ tipo = "redondeo"; multiploRedondeoCts = 100 } $tk @{ "X-Step-Up" = $su } | Out-Null
    Write-Host "   reglas: 10% de ingresos -> Fondo de emergencia; redondeo -> Viaje"
} catch { Write-Warning "Reglas: $_" }
try {
    Llamar POST "$API/metas/$emerg/sello" @{ montoSelladoUsdCts = 4000 } $tk @{ "X-Step-Up" = $su } | Out-Null
    Write-Host "   sello: USD 40,00 sellados en el Fondo de emergencia"
} catch { Write-Warning "Sello: $_" }

Write-Host "== 6. Ingreso de nomina (dispara la regla del 10%)"
try {
    Invoke-RestMethod -Method POST -Uri "$Simuladores/simulador/ob/0901/movimientos" -ContentType "application/json" `
        -Body (@{ cedula = "V$Cedula"; naturaleza = "credito"; montoCts = 12000000; descripcion = "PAGO NOMINA"; clasificacion = "ingreso" } | ConvertTo-Json) | Out-Null
    Llamar POST "$API/consolidado/sincronizar" $null $tk | Out-Null
    Start-Sleep 3
} catch { Write-Warning "Nomina: $_" }

Write-Host "== 7. Resumen"
$r = Llamar GET "$API/racha" $null $tk
Write-Host ("   Racha: {0} dias, multiplicador x{1}, colchones: {2}" -f $r.diasConsecutivos, ($r.multiplicadorX100 / 100), $r.colchonesDisponibles)
$t = @(Llamar GET "$API/tarjetas" $null $tk)
Write-Host "   Tarjetas descubiertas: $($t.Count)"
try { $p = Llamar GET "$API/puntos" $null $tk; Write-Host "   Puntos: $($p | ConvertTo-Json -Compress)" } catch {}
Write-Host ""
Write-Host "Listo. En la app entra con:  V $Cedula  /  PIN $Pin"
