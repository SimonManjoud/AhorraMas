package ve.impulsatech.ahorramas.identidad.aplicacion;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida.RepositorioIdentidad;
import ve.impulsatech.ahorramas.identidad.dominio.Usuario;

/** Implementación de la API pública de Identidad para los demás módulos. */
@Service
class ServicioUsuariosApi implements UsuariosApi {

    private final RepositorioIdentidad repo;
    private final Reloj reloj;
    private final Auditoria auditoria;

    ServicioUsuariosApi(RepositorioIdentidad repo, Reloj reloj, Auditoria auditoria) {
        this.repo = repo;
        this.reloj = reloj;
        this.auditoria = auditoria;
    }

    @Override
    @Transactional(readOnly = true)
    public void exigirKycAprobado(long idUsuario) {
        if (!repo.tieneKycAprobado(idUsuario)) {
            throw new ErrorNegocio(ErrorNegocio.Tipo.KYC_PENDIENTE, "Primero verifica tu identidad (cédula y selfie)");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ContactoUsuario contacto(long idUsuario) {
        Usuario u = usuario(idUsuario);
        return new ContactoUsuario(idUsuario, u.nombreCorto(), u.correo(), u.telefonoMovil());
    }

    @Override
    @Transactional(readOnly = true)
    public String documento(long idUsuario) {
        return usuario(idUsuario).documento();
    }

    @Override
    @Transactional(readOnly = true)
    public long seudonimo(long idUsuario) {
        return usuario(idUsuario).idSeudonimo();
    }

    @Override
    @Transactional(readOnly = true)
    public LimiteVigente limiteVigente(long idUsuario) {
        return repo.limiteVigente(idUsuario)
                .orElse(new LimiteVigente(ServicioAcceso.LIMITE_DIARIO_INICIAL_CTS, ServicioAcceso.OPERACIONES_DIARIAS_INICIALES));
    }

    @Override
    @Transactional(readOnly = true)
    public void validarHuellaDispositivo(long idUsuario, long idDispositivo, String huellaCliente) {
        boolean ok = huellaCliente != null && repo.dispositivo(idDispositivo)
                .filter(d -> d.idUsuario() == idUsuario && d.activo() && d.saldoRapidoActivo())
                .map(d -> d.huella().equals(ServicioAcceso.huella(huellaCliente)))
                .orElse(false);
        if (!ok) {
            throw ErrorNegocio.noAutenticado("El saldo rápido no está disponible en este dispositivo");
        }
    }

    @Override
    @Transactional
    public LimiteVigente cambiarLimite(long idUsuario, long montoMaxDiarioCts, int maxOperacionesDiarias, String motivo, long idOperador) {
        usuario(idUsuario);
        if (montoMaxDiarioCts <= 0 || maxOperacionesDiarias <= 0) {
            throw ErrorNegocio.validacion("El límite debe ser positivo");
        }
        var ahora = reloj.ahora();
        LimiteVigente anterior = limiteVigente(idUsuario);
        repo.cerrarLimiteVigente(idUsuario, ahora);
        repo.crearLimite(idUsuario, montoMaxDiarioCts, maxOperacionesDiarias, ahora.plusNanos(1000), motivo);
        auditoria.registrar(Actor.operador, idOperador, "LIMITE_CAMBIADO", "LIMITE_TRANSACCIONAL", idUsuario,
                Map.of("anteriorCts", anterior.montoMaxDiarioCts(), "nuevoCts", montoMaxDiarioCts,
                        "anteriorOps", anterior.maxOperacionesDiarias(), "nuevoOps", maxOperacionesDiarias));
        return new LimiteVigente(montoMaxDiarioCts, maxOperacionesDiarias);
    }

    private Usuario usuario(long id) {
        return repo.usuarioPorId(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Usuario"));
    }
}
