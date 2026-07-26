package ec.edu.ups.academicevents.shared.exception;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {

    private final ErrorCode code;
    private final long ttlSeconds;

    public RateLimitExceededException(ErrorCode code, String message, long ttlSeconds) {
        super(message);
        this.code = code;
        this.ttlSeconds = ttlSeconds;
    }
}
