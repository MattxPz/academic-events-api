package ec.edu.ups.academicevents.shared.ratelimit;

import lombok.Getter;

@Getter
public enum RateLimitPolicy {

    LOGIN_IP("rl:login:ip:", 5, 60),
    LOGIN_EMAIL("rl:login:email:", 5, 60),
    REGISTER_IP("rl:register:ip:", 3, 3600),
    PUBLIC_IP("rl:public:ip:", 60, 60),
    AUTHENTICATED_USER("rl:auth:user:", 120, 60),
    REPORTS_USER("rl:reports:user:", 5, 60);

    private final String keyPrefix;
    private final int limit;
    private final int windowSeconds;

    RateLimitPolicy(String keyPrefix, int limit, int windowSeconds) {
        this.keyPrefix = keyPrefix;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }
}
