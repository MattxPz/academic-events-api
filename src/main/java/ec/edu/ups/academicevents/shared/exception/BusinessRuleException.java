package ec.edu.ups.academicevents.shared.exception;

import lombok.Getter;

@Getter
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode code;

    public BusinessRuleException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
