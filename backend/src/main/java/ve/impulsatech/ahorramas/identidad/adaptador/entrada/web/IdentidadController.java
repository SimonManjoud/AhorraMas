package ve.impulsatech.ahorramas.identidad.adaptador.entrada.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.seguridad.NivelStepUp;
import ve.impulsatech.ahorramas.compartido.seguridad.UsuarioActual;
import ve.impulsatech.ahorramas.compartido.seguridad.VerificadorStepUp;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.entrada.CasosDeUsoIdentidad.*;

/** Adaptador de entrada REST del módulo de identidad (contrato §1). */
@RestController
@RequestMapping("/api/v1")
class IdentidadController {

    private final Acceso acceso;
    private final Dispositivos dispositivos;
    private final VerificarIdentidad kyc;
    private final VerificadorStepUp stepUp;

    IdentidadController(Acceso acceso, Dispositivos dispositivos, VerificarIdentidad kyc, VerificadorStepUp stepUp) {
        this.acceso = acceso;
        this.dispositivos = dispositivos;
        this.kyc = kyc;
        this.stepUp = stepUp;
    }

    record DispositivoDto(@NotBlank String huella, String plataforma, String modelo) {
    }

    record RegistroDto(@NotBlank String nacionalidadDoc, @NotNull Integer cedula, @NotBlank String primerNombre, String segundoNombre,
                       @NotBlank String primerApellido, String segundoApellido, @NotNull LocalDate fechaNacimiento,
                       @NotBlank String correo, @NotBlank String telefonoMovil, @NotBlank String pin, @NotNull @Valid DispositivoDto dispositivo) {
    }

    record LoginDto(@NotBlank String nacionalidadDoc, @NotNull Integer cedula, @NotBlank String pin, @NotBlank String huellaDispositivo,
                    String plataforma, String modelo) {
    }

    record OtpDto(String proposito) {
    }

    record StepUpDto(@NotBlank String pin, UUID idDesafio, String otp) {
    }

    record DocumentoDto(@NotBlank String tipo, @NotBlank String contenidoBase64) {
    }

    record KycDto(@NotNull List<@Valid DocumentoDto> documentos) {
    }

    record TokenSaldoDto(String tokenSaldo, OffsetDateTime expiraEn) {
    }

    record RevocarDto(String motivo) {
    }

    @PostMapping("/auth/registro")
    @ResponseStatus(HttpStatus.CREATED)
    Sesion registrar(@Valid @RequestBody RegistroDto d) {
        return acceso.registrar(new ComandoRegistro(d.nacionalidadDoc(), d.cedula(), d.primerNombre(), d.segundoNombre(), d.primerApellido(),
                d.segundoApellido(), d.fechaNacimiento(), d.correo(), d.telefonoMovil(), d.pin(),
                new DatosDispositivo(d.dispositivo().huella(), d.dispositivo().plataforma(), d.dispositivo().modelo())));
    }

    @PostMapping("/auth/login")
    Sesion login(@Valid @RequestBody LoginDto d) {
        return acceso.iniciarSesion(new ComandoLogin(d.nacionalidadDoc(), d.cedula(), d.pin(),
                new DatosDispositivo(d.huellaDispositivo(), d.plataforma(), d.modelo())));
    }

    @GetMapping("/me")
    PerfilUsuario me() {
        return acceso.perfil(UsuarioActual.id());
    }

    @PostMapping("/auth/otp")
    DesafioEmitido otp(@RequestBody(required = false) OtpDto d) {
        return acceso.solicitarOtp(UsuarioActual.id(), d == null ? null : d.proposito());
    }

    @PostMapping("/auth/step-up")
    StepUpEmitido stepUp(@Valid @RequestBody StepUpDto d) {
        return acceso.stepUp(UsuarioActual.id(), UsuarioActual.idDispositivo(), d.pin(), d.idDesafio(), d.otp());
    }

    @PostMapping("/kyc/verificaciones")
    ResultadoVerificacion kyc(@Valid @RequestBody KycDto d) {
        List<DocumentoSubido> docs = d.documentos().stream().map(x -> new DocumentoSubido(x.tipo(), decodificar(x.contenidoBase64()))).toList();
        return kyc.verificar(UsuarioActual.id(), docs);
    }

    @PostMapping("/auth/saldo-rapido")
    TokenSaldoDto activarSaldoRapido() {
        stepUp.exigir(NivelStepUp.PIN);
        var t = dispositivos.activarSaldoRapido(UsuarioActual.id(), UsuarioActual.idDispositivo());
        return new TokenSaldoDto(t.token(), t.expiraEn());
    }

    @DeleteMapping("/auth/saldo-rapido")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desactivarSaldoRapido() {
        dispositivos.desactivarSaldoRapido(UsuarioActual.id(), UsuarioActual.idDispositivo());
    }

    @PostMapping("/dispositivos/actual/revocar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revocar(@RequestBody(required = false) RevocarDto d) {
        dispositivos.revocar(UsuarioActual.id(), UsuarioActual.idDispositivo(), d == null ? "usuario" : d.motivo());
    }

    private static byte[] decodificar(String base64) {
        try {
            String limpio = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
            return Base64.getMimeDecoder().decode(limpio);
        } catch (IllegalArgumentException e) {
            throw ErrorNegocio.validacion("Imagen en base64 inválida");
        }
    }
}
