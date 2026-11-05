package ve.impulsatech.ahorramas.perfilia.aplicacion;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.cofre.api.CofreApi;
import ve.impulsatech.ahorramas.cofre.api.CofreApi.ReglaCreada;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.consolidacion.api.CuentasApi;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi;
import ve.impulsatech.ahorramas.perfilia.aplicacion.puerto.entrada.AsistentePerfil;
import ve.impulsatech.ahorramas.perfilia.aplicacion.puerto.salida.PuertosPerfil.*;
import ve.impulsatech.ahorramas.perfilia.dominio.ClasificadorPerfil;
import ve.impulsatech.ahorramas.perfilia.dominio.ClasificadorPerfil.Clasificacion;
import ve.impulsatech.ahorramas.perfilia.dominio.ClasificadorPerfil.Rasgos;

/** F4 · Recomendación de IA explicable (no ejecuta sin aprobación). */
@Service
class ServicioAsistente implements AsistentePerfil {

    static final int DIAS_VIGENCIA_RECOMENDACION = 7;

    private final Repositorio repo;
    private final RedactorExplicaciones llm;
    private final CuentasApi cuentas;
    private final CofreApi cofre;
    private final UsuariosApi usuarios;
    private final Reloj reloj;
    private final Auditoria auditoria;

    ServicioAsistente(Repositorio repo, RedactorExplicaciones llm, CuentasApi cuentas, CofreApi cofre, UsuariosApi usuarios, Reloj reloj,
                      Auditoria auditoria) {
        this.repo = repo;
        this.llm = llm;
        this.cuentas = cuentas;
        this.cofre = cofre;
        this.usuarios = usuarios;
        this.reloj = reloj;
        this.auditoria = auditoria;
    }

    @Override
    @Transactional
    public ResultadoEvaluacion evaluar(long idUsuario, String objetivo, int horizonte, String tolerancia) {
        // Paso 1: rasgos seudonimizados (rangos y proporciones; sin nombre, cédula ni cuentas)
        var financieros = cuentas.rasgos(idUsuario);
        Rasgos rasgos = new Rasgos(objetivo, horizonte, tolerancia, ClasificadorPerfil.rangoIngreso(financieros.ingresoMensualUsdCts()),
                financieros.volatilidadSaldoPb(), cofre.metasActivas(idUsuario));
        // Paso 2: clasificación con reglas versionadas
        Clasificacion c = ClasificadorPerfil.clasificar(rasgos);
        VersionModelo version = repo.versionActiva();
        // Paso 3: el LLM solo redacta, con el id seudónimo
        String explicacion = llm.redactar(usuarios.seudonimo(idUsuario), c, rasgos)
                .orElseGet(() -> ClasificadorPerfil.explicacionRespaldo(c, rasgos));
        OffsetDateTime ahora = reloj.ahora();
        long idEval = repo.guardarEvaluacion(idUsuario, version.idVersion(), rasgos, c.perfil(), ahora);
        long idRec = repo.guardarRecomendacion(idEval, explicacion, c.distribucionPb(), c.reglaSugerida().tipo(), c.reglaSugerida().valor(), ahora);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("version", version.etiqueta());
        detalle.put("rasgos", Map.of("rangoIngreso", rasgos.rangoIngreso(), "volatilidadPb", rasgos.volatilidadSaldoPb(),
                "objetivo", objetivo, "horizonte", horizonte, "tolerancia", tolerancia));
        detalle.put("perfil", c.perfil());
        detalle.put("distribucion", c.distribucionPb());
        auditoria.registrar(Actor.sistema, null, "IA_RECOMENDACION", "RECOMENDACION_IA", idRec, detalle);
        return new ResultadoEvaluacion(idEval, c.perfil(), vista(repo.recomendacion(idRec).orElseThrow()));
    }

    /** Paso 5: solo con aprobación (huella/PIN, exigida en el adaptador web) se crea la regla en el Cofre. */
    @Override
    @Transactional
    public ResultadoDecision decidir(long idUsuario, long idRecomendacion, boolean aceptar, Boolean entendio, Long idMeta) {
        Recomendacion r = repo.recomendacion(idRecomendacion).filter(x -> x.idUsuario() == idUsuario)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Recomendación"));
        if (!"propuesta".equals(r.estatus())) {
            throw ErrorNegocio.conflicto("La recomendación ya fue " + r.estatus());
        }
        OffsetDateTime ahora = reloj.ahora();
        if (r.fechaHora().plusDays(DIAS_VIGENCIA_RECOMENDACION).isBefore(ahora)) {
            repo.decidir(idRecomendacion, "expirada", null, ahora);
            return new ResultadoDecision("expirada", null);   // el usuario debe pedir una nueva evaluación
        }
        String estatus = aceptar ? "aceptada" : "rechazada";
        ReglaCreada regla = null;
        if (aceptar && idMeta != null && r.reglaTipo() != null) {
            // R-13: la regla nace de una recomendación aceptada (si la regla no se puede crear, no se registra la decisión)
            repo.decidir(idRecomendacion, estatus, entendio, ahora);
            regla = cofre.crearReglaAprobada(idUsuario, idMeta, r.reglaTipo(), r.reglaValor(), idRecomendacion);
        } else {
            repo.decidir(idRecomendacion, estatus, entendio, ahora);
        }
        auditoria.registrar(Actor.usuario, idUsuario, "IA_DECISION", "RECOMENDACION_IA", idRecomendacion,
                Map.of("decision", estatus, "entendio", String.valueOf(entendio), "reglaCreada", regla != null, "version", r.version()));
        return new ResultadoDecision(estatus, regla);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VistaRecomendacion> recomendaciones(long idUsuario) {
        return repo.recomendaciones(idUsuario).stream().map(this::vista).toList();
    }

    private VistaRecomendacion vista(Recomendacion r) {
        String estatus = "propuesta".equals(r.estatus()) && r.fechaHora().plusDays(DIAS_VIGENCIA_RECOMENDACION).isBefore(reloj.ahora())
                ? "expirada" : r.estatus();
        return new VistaRecomendacion(r.idRecomendacion(), estatus, r.explicacion(),
                r.distribucion().stream().map(d -> new DistribucionVista(d.instrumento(), d.porcentajePb(), d.nivelRiesgo())).toList(),
                r.reglaTipo() == null ? null : new ReglaSugeridaVista(r.reglaTipo(), r.reglaValor() == null ? null : r.reglaValor().intValue()),
                r.version(), DISCLAIMER, r.perfil(), r.fechaHora());
    }
}
