package ec.edu.ups.academicevents.shared.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<List> rateLimitScript;

    @SuppressWarnings("unchecked")
    public RateLimitResult tryConsume(RateLimitPolicy policy, String identifier) {
        String key = policy.getKeyPrefix() + identifier;

        List<Long> result = stringRedisTemplate.execute(
                rateLimitScript,
                List.of(key),
                String.valueOf(policy.getWindowSeconds()),
                String.valueOf(policy.getLimit()));

        boolean allowed = result.get(0) == 1L;
        long ttlSeconds = result.get(1);
        return new RateLimitResult(allowed, ttlSeconds);
    }
}
