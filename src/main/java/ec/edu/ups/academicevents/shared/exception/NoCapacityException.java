package ec.edu.ups.academicevents.shared.exception;

import lombok.Getter;

@Getter
public class NoCapacityException extends RuntimeException {

    private final ErrorCode code;

    public NoCapacityException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
