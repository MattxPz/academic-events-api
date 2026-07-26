package ec.edu.ups.academicevents.shared.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String ATTEMPTS_KEY_PREFIX = "login-attempts:";
    private static final String BLOCKED_KEY_PREFIX = "blocked-user:";
    private static final long MAX_ATTEMPTS = 5;
    private static final Duration BLOCK_WINDOW = Duration.ofSeconds(900);

    private final StringRedisTemplate stringRedisTemplate;

    public void recordFailure(String email) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;

        Long attempts = stringRedisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            stringRedisTemplate.expire(attemptsKey, BLOCK_WINDOW);
        }

        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            stringRedisTemplate.opsForValue().set(BLOCKED_KEY_PREFIX + email, "1", BLOCK_WINDOW);
        }
    }

    public boolean isBlocked(String email) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLOCKED_KEY_PREFIX + email));
    }

    public void reset(String email) {
        stringRedisTemplate.delete(ATTEMPTS_KEY_PREFIX + email);
    }
}
