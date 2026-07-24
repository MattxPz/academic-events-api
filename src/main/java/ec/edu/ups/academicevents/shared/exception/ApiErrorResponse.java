package ec.edu.ups.academicevents.shared.exception;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ApiErrorResponse of(int status, String error, ErrorCode code, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, code, message, path, null);
    }

    public static ApiErrorResponse of(
            int status, String error, ErrorCode code, String message, String path, List<FieldError> fieldErrors) {
        return new ApiErrorResponse(Instant.now(), status, error, code, message, path, fieldErrors);
    }
}
