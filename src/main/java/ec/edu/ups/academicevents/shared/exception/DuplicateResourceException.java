package ec.edu.ups.academicevents.shared.exception;

import lombok.Getter;

@Getter
public class DuplicateResourceException extends RuntimeException {

    private final ErrorCode code;

    public DuplicateResourceException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
