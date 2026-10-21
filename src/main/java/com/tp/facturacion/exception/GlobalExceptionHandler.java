package com.tp.facturacion.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // El recurso pedido no existe → 404
    @ExceptionHandler(RecursoNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return new ErrorResponse(404, ex.getMessage());
    }

    // La operación viola una regla del negocio (ej. borrar una factura con CAE) → 409
    @ExceptionHandler(ReglaNegocioException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse manejarReglaNegocio(ReglaNegocioException ex) {
        return new ErrorResponse(409, ex.getMessage());
    }

    // Validaciones de los DTOs (@Valid) y parámetros con formato inválido → 400
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse manejarValidacion(BindException ex) {
        List<String> detalles = ex.getFieldErrors().stream()
                .map(error -> error.isBindingFailure()
                        ? error.getField() + ": formato inválido ('" + error.getRejectedValue() + "')"
                        : error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return new ErrorResponse(400, "Datos inválidos", detalles);
    }

    // JSON mal formado o con tipos incorrectos → 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse manejarJsonInvalido(HttpMessageNotReadableException ex) {
        return new ErrorResponse(400,
                "El cuerpo de la petición no es un JSON válido o tiene tipos incorrectos");
    }

    // Datos con valores no permitidos (ej. un estado desconocido) → 400
    @ExceptionHandler(IllegalArgumentException.class)
@   ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse manejarArgumentoInvalido(IllegalArgumentException ex) {
        return new ErrorResponse(400, ex.getMessage());
}

    // Cualquier otro error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorGeneral(Exception ex) {
        // Errores propios de Spring MVC (URL inexistente, método no permitido, etc.):
        // respetamos el código HTTP que ya traen
        if (ex instanceof org.springframework.web.ErrorResponse errorDeSpring) {
            int status = errorDeSpring.getStatusCode().value();
            return ResponseEntity.status(status).body(new ErrorResponse(status, ex.getMessage()));
        }

        log.error("Error no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Se produjo un error interno en el servidor"));
    }
}