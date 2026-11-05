package ve.impulsatech.ahorramas.simuladores.comun;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Traduce cualquier error al formato común {@code {"codigo","mensaje"}}. */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorRespuesta> api(ApiException e) {
        return responder(e.status(), e.codigo(), e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorRespuesta> parametroFaltante(MissingServletRequestParameterException e) {
        return responder(HttpStatus.BAD_REQUEST, "PARAMETRO_FALTANTE",
                "Falta el parámetro '" + e.getParameterName() + "'");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorRespuesta> headerFaltante(MissingRequestHeaderException e) {
        return responder(HttpStatus.BAD_REQUEST, "PARAMETRO_FALTANTE",
                "Falta el header '" + e.getHeaderName() + "'");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorRespuesta> tipoInvalido(MethodArgumentTypeMismatchException e) {
        return responder(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                "Valor inválido para '" + e.getName() + "'");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> jsonInvalido(HttpMessageNotReadableException e) {
        return responder(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "El cuerpo no es un JSON válido");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorRespuesta> tipoContenido(HttpMediaTypeNotSupportedException e) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_CONTENIDO_NO_SOPORTADO",
                "Tipo de contenido no soportado");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorRespuesta> metodo(HttpRequestMethodNotSupportedException e) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorRespuesta> noExiste(NoResourceFoundException e) {
        return responder(HttpStatus.NOT_FOUND, "RECURSO_NO_EXISTE", "La ruta no existe en el simulador");
    }

    private static ResponseEntity<ErrorRespuesta> responder(HttpStatus status, String codigo, String mensaje) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorRespuesta(codigo, mensaje));
    }
}
