package ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import ve.impulsatech.ahorramas.cofre.dominio.Aporte;
import ve.impulsatech.ahorramas.cofre.dominio.MetaAhorro;
import ve.impulsatech.ahorramas.cofre.dominio.Racha;
import ve.impulsatech.ahorramas.cofre.dominio.ReglaAhorro;
import ve.impulsatech.ahorramas.cofre.dominio.SelloCompromiso;

/** Puerto de persistencia del esquema {@code cofre}. */
public interface RepositorioCofre {

    MetaAhorro guardarMeta(MetaAhorro meta);

    Optional<MetaAhorro> meta(long idMeta);

    /** Bloquea la fila de la meta (serializa aportes concurrentes sobre el mismo cofre). */
    Optional<MetaAhorro> metaParaActualizar(long idMeta);

    List<MetaAhorro> metas(long idUsuario);

    List<MetaAhorro> metasActivasDeTodos();

    List<Long> usuariosConMetas();

    Aporte insertarAporte(Aporte aporte);

    boolean existeClaveOrigen(String clave);

    List<Aporte> aportes(long idMeta);

    List<Aporte> aportesDeUsuario(long idUsuario);

    long acumulado(long idMeta);

    long aportadoEntre(long idMeta, OffsetDateTime desde, OffsetDateTime hasta);

    boolean huboAporteDelUsuarioEntre(long idUsuario, OffsetDateTime desde, OffsetDateTime hasta, long excluirIdAporte);

    Optional<CuotaGuardada> cuota(long idMeta, LocalDate fechaPeriodo);

    CuotaGuardada insertarCuota(CuotaGuardada cuota);

    void actualizarEstatusCuota(long idCuota, String estatus);

    List<CuotaGuardada> cuotasPendientesHasta(LocalDate fecha);

    ReglaAhorro guardarRegla(ReglaAhorro regla);

    Optional<ReglaAhorro> regla(long idRegla);

    List<ReglaAhorro> reglas(long idMeta);

    List<ReglaActivaDeUsuario> reglasActivasDeUsuario(long idUsuario);

    List<ReglaActivaDeUsuario> reglasActivasPorTipo(String tipo);

    List<Racha.Tramo> tramosVigentes();

    Optional<Racha> rachaActiva(long idUsuario);

    /** Bloquea la racha activa del usuario. */
    Optional<Racha> rachaActivaParaActualizar(long idUsuario);

    List<Racha> rachasActivas();

    Racha crearRacha(long idUsuario, LocalDate inicio);

    void agregarDiaRacha(long idRacha, LocalDate fecha, String tipo);

    void cerrarRacha(long idRacha, LocalDate fechaFin, String motivo);

    List<Racha.Dia> diasRachaUsuario(long idUsuario, LocalDate desde, LocalDate hasta);

    Optional<SelloCompromiso> selloActivo(long idMeta);

    Optional<SelloCompromiso> ultimoSello(long idMeta);

    SelloCompromiso guardarSello(SelloCompromiso sello);

    Optional<ReporteGuardado> reporteMensual(long idUsuario, int anio, int mes);

    void insertarReporteMensual(ReporteGuardado reporte);

    record CuotaGuardada(Long idCuota, long idMeta, LocalDate fecha, long montoSugeridoUsdCts, long montoSugeridoBsCts,
                         long tasaValorBsX10000, int periodosRestantes, String estatus) {
    }

    record ReglaActivaDeUsuario(ReglaAhorro regla, MetaAhorro meta) {
    }

    record ReporteGuardado(long idUsuario, int anio, int mes, long totalBsCts, long totalUsdCts, long saldoCofresUsdCts,
                           long perdidaEvitadaBsCts, long tasaInicioBsX10000, long tasaFinBsX10000, Integer inflacionMensualPb,
                           OffsetDateTime fechaGeneracion) {
    }
}
