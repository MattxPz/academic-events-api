package ec.edu.ups.academicevents.reports.exception;

import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import lombok.Getter;

/** Rango de fechas inválido en un reporte; se traduce a 400. */
@Getter
public class InvalidReportRangeException extends RuntimeException {

    private final ErrorCode code;

    public InvalidReportRangeException(String message) {
        super(message);
        this.code = ErrorCode.VALIDATION_ERROR;
    }
}
