package ve.impulsatech.ahorramas.backoffice.aplicacion;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import ve.impulsatech.ahorramas.backoffice.aplicacion.puerto.salida.RepositorioBackoffice;
import ve.impulsatech.ahorramas.backoffice.aplicacion.puerto.salida.RepositorioBackoffice.*;
import ve.impulsatech.ahorramas.backoffice.dominio.ReglasAml;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.Montos;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.seguridad.ServicioTokens;
import ve.impulsatech.ahorramas.consolidacion.api.TasasApi;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi;
import ve.impulsatech.ahorramas.pagos.api.EventosPagos.DiferenciaConciliacion;
import ve.impulsatech.ahorramas.pagos.api.EventosPagos.PagoSolicitado;
import ve.impulsatech.ahorramas.pagos.api.PagosApi;

/** Casos de soporte, alertas AML, reportes regulatorios, límites y conciliación para operadores. */
@Service
public class ServicioBackoffice {

    private final RepositorioBackoffice repo;
    private final PagosApi pagos;
    private final UsuariosApi usuarios;
    private final TasasApi tasas;
    private final ServicioTokens tokens;
    private final Auditoria auditoria;
    private final Reloj reloj;
    private final JsonMapper json;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);

    ServicioBackoffice(RepositorioBackoffice repo, PagosApi pagos, UsuariosApi usuarios, TasasApi tasas, ServicioTokens tokens,
                       Auditoria auditoria, Reloj reloj, JsonMapper json) {
        this.repo = repo;
        this.pagos = pagos;
        this.usuarios = usuarios;
        this.tasas = tasas;
        this.tokens = tokens;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.json = json;
    }

    public record SesionOperador(String accessToken, String rol, String nombre) {
    }

    // ------------------------------------------------------------ acceso (SSO simulado)

    @Transactional
    public SesionOperador login(String correo, String clave) {
        Operador o = repo.operadorPorCorreo(correo == null ? "" : correo.trim().toLowerCase())
                .filter(x -> "activo".equals(x.estatus()) && x.claveHash() != null && bcrypt.matches(clave == null ? "" : clave, x.claveHash()))
                .orElseThrow(() -> ErrorNegocio.noAutenticado("Credenciales corporativas inválidas"));
        auditoria.registrar(Actor.operador, o.idOperador(), "LOGIN_BACKOFFICE", "OPERADOR", o.idOperador());
        return new SesionOperador(tokens.operador(o.idOperador(), o.rol()).token(), o.rol(), o.nombre());
    }

    @Transactional
    public void sembrarOperador(String nombre, String apellido, String correo, String rol, String clave) {
        repo.asegurarOperador(nombre, apellido, correo, rol, bcrypt.encode(clave));
    }

    public static void exigirRol(String rolActual, String... permitidos) {
        if (!"admin".equals(rolActual) && !Set.of(permitidos).contains(rolActual)) {
            throw ErrorNegocio.prohibido("Tu rol (" + rolActual + ") no permite esta operación");
        }
    }

    // ------------------------------------------------------------ monitoreo (desde eventos)

    @Transactional
    public void abrirCasoPorDiferencia(DiferenciaConciliacion e) {
        String desc = "Diferencia de conciliación del " + e.fechaConciliacion() + ": " + e.resultado() + " ("
                + Montos.formatear(e.diferenciaCts(), Moneda.VES) + ")" + (e.referenciaSuiche() == null ? "" : ", ref. suiche " + e.referenciaSuiche())
                + (e.idPago() == null ? ". Registro del suiche sin pago en Ahorra+." : ". Se solicitó el reverso al suiche.");
        repo.abrirCaso(e.idUsuario(), e.idPago(), "diferencia", "alta", desc,
                "conc:" + e.fechaConciliacion() + ":" + (e.idPago() != null ? e.idPago() : e.referenciaSuiche()), reloj.ahora());
    }

    @Transactional
    public void monitorearPago(PagoSolicitado e) {
        LocalDate dia = e.fechaHora().atZoneSameInstant(reloj.zona()).toLocalDate();
        var tasa = tasas.tasaDe(dia);
        var resumen = pagos.resumenDelDia(e.idUsuario(), dia);
        for (ReglasAml.Alerta a : ReglasAml.evaluarPago(tasa.bsAUsd(e.montoCts()), tasa.bsAUsd(resumen.montoCts()), resumen.operaciones())) {
            String clave = a.regla().startsWith("AML-01") ? "aml:" + a.regla() + ":" + e.idPago() : "aml:" + a.regla() + ":" + e.idUsuario() + ":" + dia;
            repo.crearAlerta(e.idUsuario(), e.idPago(), a.regla(), a.nivelRiesgo(), clave, reloj.ahora());
        }
    }

    @Transactional
    public void alertaPorKyc(long idUsuario, String motivo) {
        if (motivo != null && motivo.toLowerCase().contains("sancion")) {
            repo.crearAlerta(idUsuario, null, "AML-04-LISTAS", "alto", "aml:listas:" + idUsuario, reloj.ahora());
        }
    }

    // ------------------------------------------------------------ casos

    public List<Caso> casos(String estatus) {
        return repo.casos(estatus, 200);
    }

    @Transactional
    public Caso actualizarCaso(long id, String estatus, String resolucion, long idOperador) {
        repo.caso(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Caso"));
        if (!Set.of("abierto", "en_proceso", "resuelto", "cerrado").contains(estatus)) {
            throw ErrorNegocio.validacion("Estatus de caso inválido");
        }
        boolean cierra = "resuelto".equals(estatus) || "cerrado".equals(estatus);
        repo.actualizarCaso(id, estatus, resolucion, idOperador, cierra ? reloj.ahora() : null);
        auditoria.registrar(Actor.operador, idOperador, "CASO_" + estatus.toUpperCase(), "CASO_SOPORTE", id);
        return repo.caso(id).orElseThrow();
    }

    // ------------------------------------------------------------ alertas AML y reportes

    public List<Alerta> alertas(String estatus) {
        return repo.alertas(estatus, 200);
    }

    @Transactional
    public Alerta revisarAlerta(long id, String estatus, String justificacion, long idOperador) {
        repo.alerta(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Alerta"));
        if (!Set.of("en_revision", "descartada", "escalada").contains(estatus)) {
            throw ErrorNegocio.validacion("Estatus de alerta inválido");
        }
        if (!"en_revision".equals(estatus) && (justificacion == null || justificacion.isBlank())) {
            throw ErrorNegocio.validacion("Descartar o escalar una alerta exige una justificación");
        }
        repo.revisarAlerta(id, estatus, justificacion, idOperador, reloj.ahora());
        auditoria.registrar(Actor.operador, idOperador, "ALERTA_AML_" + estatus.toUpperCase(), "ALERTA_AML", id);
        return repo.alerta(id).orElseThrow();
    }

    /** ROS en borrador a partir de una alerta escalada (sin PII en claro en la auditoría). */
    @Transactional
    public Reporte crearRos(long idAlerta, long idOperador) {
        Alerta a = repo.alerta(idAlerta).orElseThrow(() -> ErrorNegocio.noEncontrado("Alerta"));
        if (!"escalada".equals(a.estatus())) {
            throw ErrorNegocio.regla("ROS", "Solo se genera un ROS desde una alerta escalada");
        }
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("tipo", "Reporte de Operación Sospechosa");
        contenido.put("sujetoObligado", "Impulsa Tech, C.A. (Ahorra+)");
        contenido.put("idAlerta", idAlerta);
        contenido.put("reglaDisparada", a.reglaDisparada());
        contenido.put("nivelRiesgo", a.nivelRiesgo());
        contenido.put("cliente", usuarios.documento(a.idUsuario()));
        contenido.put("idPago", a.idPago());
        contenido.put("justificacion", a.justificacion());
        long id = repo.crearReporte(idAlerta, idOperador, "ROS", "UNIF", json.writeValueAsString(contenido), reloj.ahora());
        auditoria.registrar(Actor.operador, idOperador, "ROS_BORRADOR", "REPORTE_REGULATORIO", id, Map.of("alerta", idAlerta));
        return repo.reporte(id).orElseThrow();
    }

    @Transactional
    public Reporte enviarReporte(long id, long idOperador) {
        Reporte r = repo.reporte(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Reporte"));
        if (!"borrador".equals(r.estatus())) {
            throw ErrorNegocio.conflicto("El reporte ya fue enviado");
        }
        String acuse = r.destinatario() + "-ACK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        repo.marcarEnviado(id, acuse, reloj.ahora());
        auditoria.registrar(Actor.operador, idOperador, "REPORTE_ENVIADO", "REPORTE_REGULATORIO", id, Map.of("acuse", acuse));
        return repo.reporte(id).orElseThrow();
    }

    public List<Reporte> reportes() {
        return repo.reportes(200);
    }
}
