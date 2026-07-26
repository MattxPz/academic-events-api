package ec.edu.ups.academicevents.shared.exception;

import lombok.Getter;

@Getter
public class InvalidTokenException extends RuntimeException {

    private final ErrorCode code;

    public InvalidTokenException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
