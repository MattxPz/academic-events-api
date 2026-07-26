package ec.edu.ups.academicevents.shared.ratelimit;

public record RateLimitResult(boolean allowed, long ttlSeconds) {
}
