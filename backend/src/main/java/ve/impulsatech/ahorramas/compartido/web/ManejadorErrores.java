package ve.impulsatech.ahorramas.compartido.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/** Traduce errores a la respuesta estándar {@code {codigo, mensaje, detalles}} del contrato. */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    public record RespuestaError(String codigo, String mensaje, Map<String, Object> detalles) {
    }

    @ExceptionHandler(ErrorNegocio.class)
    ResponseEntity<RespuestaError> negocio(ErrorNegocio e) {
        HttpStatus status = switch (e.tipo()) {
            case VALIDACION, IDEMPOTENCY_KEY_REQUERIDA -> HttpStatus.BAD_REQUEST;
            case NO_AUTENTICADO, STEP_UP_REQUERIDO -> HttpStatus.UNAUTHORIZED;
            case PROHIBIDO, KYC_PENDIENTE -> HttpStatus.FORBIDDEN;
            case NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case CONFLICTO -> HttpStatus.CONFLICT;
            case REGLA_NEGOCIO, LIMITE_EXCEDIDO -> HttpStatus.UNPROCESSABLE_CONTENT;
            case SERVICIO_EXTERNO -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(new RespuestaError(e.codigo(), e.getMessage(), e.detalles()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<RespuestaError> validacion(MethodArgumentNotValidException e) {
        Map<String, Object> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> campos.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new RespuestaError("VALIDACION", "Revisa los datos enviados", campos));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<RespuestaError> cabecera(MissingRequestHeaderException e) {
        if ("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())) {
            return ResponseEntity.badRequest().body(new RespuestaError("IDEMPOTENCY_KEY_REQUERIDA",
                    "Todo pago requiere la cabecera Idempotency-Key", Map.of()));
        }
        return ResponseEntity.badRequest().body(new RespuestaError("VALIDACION", "Falta la cabecera " + e.getHeaderName(), Map.of()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<RespuestaError> malFormado(Exception e) {
        return ResponseEntity.badRequest().body(new RespuestaError("VALIDACION", "Solicitud mal formada", Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<RespuestaError> integridad(DataIntegrityViolationException e) {
        log.warn("Violación de integridad: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new RespuestaError("CONFLICTO", "La operación entra en conflicto con datos existentes", Map.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<RespuestaError> noExiste(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new RespuestaError("NO_ENCONTRADO", "Recurso no encontrado", Map.of()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<RespuestaError> metodo(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new RespuestaError("VALIDACION", "Método no permitido", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<RespuestaError> inesperado(Exception e) {
        log.error("Error inesperado", e);
        return ResponseEntity.internalServerError()
                .body(new RespuestaError("ERROR_INTERNO", "Ocurrió un error inesperado. Intenta de nuevo.", Map.of()));
    }
}
