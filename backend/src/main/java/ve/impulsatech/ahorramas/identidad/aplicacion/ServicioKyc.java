package ve.impulsatech.ahorramas.identidad.aplicacion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.cifrado.ServicioCifrado;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.identidad.api.EventosIdentidad.KycResuelto;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.entrada.CasosDeUsoIdentidad.DocumentoSubido;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.entrada.CasosDeUsoIdentidad.ResultadoVerificacion;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.entrada.CasosDeUsoIdentidad.VerificarIdentidad;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida.RepositorioIdentidad;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida.ServiciosExternosIdentidad.*;
import ve.impulsatech.ahorramas.identidad.dominio.Usuario;
import ve.impulsatech.ahorramas.identidad.dominio.VerificacionKyc;

/** KYC: fotos cifradas al almacén de objetos, solo hashes al proveedor y a la BD. */
@Service
class ServicioKyc implements VerificarIdentidad {

    private static final int MAX_BYTES = 6 * 1024 * 1024;

    private final RepositorioIdentidad repo;
    private final ProveedorKyc proveedor;
    private final AlmacenObjetos almacen;
    private final Reloj reloj;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;

    ServicioKyc(RepositorioIdentidad repo, ProveedorKyc proveedor, AlmacenObjetos almacen, Reloj reloj, Auditoria auditoria,
                ApplicationEventPublisher eventos) {
        this.repo = repo;
        this.proveedor = proveedor;
        this.almacen = almacen;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    @Override
    @Transactional
    public ResultadoVerificacion verificar(long idUsuario, List<DocumentoSubido> documentos) {
        Usuario u = repo.usuarioPorId(idUsuario).orElseThrow(() -> ErrorNegocio.noEncontrado("Usuario"));
        if (repo.tieneKycAprobado(idUsuario)) {
            throw ErrorNegocio.conflicto("Tu identidad ya fue verificada");
        }
        Set<String> tipos = documentos == null ? Set.of() : documentos.stream().map(DocumentoSubido::tipo).collect(Collectors.toSet());
        if (!tipos.containsAll(VerificacionKyc.TIPOS_REQUERIDOS)) {
            throw ErrorNegocio.validacion("Debes enviar la foto de tu cédula y una selfie");
        }
        OffsetDateTime ahora = reloj.ahora();
        List<VerificacionKyc.Documento> docs = new ArrayList<>();
        List<DocumentoHash> hashes = new ArrayList<>();
        int sec = 1;
        for (DocumentoSubido d : documentos) {
            if (!VerificacionKyc.TIPOS_REQUERIDOS.contains(d.tipo())) {
                continue;
            }
            if (d.contenido() == null || d.contenido().length == 0 || d.contenido().length > MAX_BYTES) {
                throw ErrorNegocio.validacion("Imagen inválida o mayor a 6 MB: " + d.tipo());
            }
            String hash = ServicioCifrado.sha256Hex(d.contenido());
            String ref = almacen.guardarCifrado(d.contenido(), "kyc-" + idUsuario + "-" + d.tipo());
            docs.add(new VerificacionKyc.Documento(sec++, d.tipo(), ref, hash, ahora));
            hashes.add(new DocumentoHash(d.tipo(), hash));
        }
        ResultadoKyc r = proveedor.verificar(new SolicitudKyc(u.nacionalidadDoc(), u.cedula(), u.primerNombre(), u.primerApellido(),
                u.fechaNacimiento(), hashes));
        VerificacionKyc v = repo.guardarVerificacion(new VerificacionKyc(null, idUsuario, ahora, reloj.ahora(), r.resultado(),
                r.pruebaVidaSuperada(), r.coincideListaSanciones(), r.refProveedor(), r.motivo(), docs));
        auditoria.registrar(Actor.usuario, idUsuario, "KYC_" + r.resultado().toUpperCase(), "VERIFICACION_KYC", v.idVerificacion(),
                Map.of("pruebaVida", String.valueOf(r.pruebaVidaSuperada()), "sanciones", String.valueOf(r.coincideListaSanciones())));
        eventos.publishEvent(new KycResuelto(idUsuario, v.aprobada(), r.motivo()));
        return new ResultadoVerificacion(v.idVerificacion(), v.resultado(), v.pruebaVidaSuperada(), v.coincideListaSanciones(), v.motivoRechazo());
    }
}
