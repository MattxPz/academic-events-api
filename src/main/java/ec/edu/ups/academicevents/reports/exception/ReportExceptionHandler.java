package ec.edu.ups.academicevents.reports.exception;

import ec.edu.ups.academicevents.shared.exception.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Advice propio del módulo de reportes.
 *
 * <p>Se declara con la máxima precedencia porque {@code GlobalExceptionHandler} incluye un
 * handler para {@code Exception}: sin esta prioridad, el rango inválido acabaría respondiendo 500.
 * Vive aquí para no modificar el manejador compartido.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReportExceptionHandler {

    @ExceptionHandler(InvalidReportRangeException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRange(
            InvalidReportRangeException ex, HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getCode(),
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
