package ec.edu.ups.academicevents.shared.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private DefaultRedisScript<List> rateLimitScript;

    @InjectMocks
    private RedisRateLimiter redisRateLimiter;

    @Test
    @DisplayName("permite la petición cuando el contador está bajo el límite")
    void allowsRequestUnderTheLimit() {
        doReturn(List.of(1L, 60L)).when(stringRedisTemplate)
                .execute(any(RedisScript.class), anyList(), any(), any());

        RateLimitResult result = redisRateLimiter.tryConsume(RateLimitPolicy.REPORTS_USER, "9");

        assertThat(result.allowed()).isTrue();
        assertThat(result.ttlSeconds()).isEqualTo(60L);
    }

    @Test
    @DisplayName("rechaza la petición al superar el límite y devuelve el TTL restante")
    void rejectsRequestOverTheLimit() {
        doReturn(List.of(0L, 45L)).when(stringRedisTemplate)
                .execute(any(RedisScript.class), anyList(), any(), any());

        RateLimitResult result = redisRateLimiter.tryConsume(RateLimitPolicy.REPORTS_USER, "9");

        assertThat(result.allowed()).isFalse();
        assertThat(result.ttlSeconds()).isEqualTo(45L);
    }

    @Test
    @DisplayName("la clave de Redis combina el prefijo de la política con el identificador")
    void buildsKeyFromPolicyPrefixAndIdentifier() {
        doReturn(List.of(1L, 60L)).when(stringRedisTemplate)
                .execute(any(RedisScript.class), anyList(), any(), any());

        redisRateLimiter.tryConsume(RateLimitPolicy.LOGIN_EMAIL, "ana.torres@academic.test");

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.captor();
        verify(stringRedisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), any(), any());

        assertThat(keysCaptor.getValue())
                .containsExactly(RateLimitPolicy.LOGIN_EMAIL.getKeyPrefix() + "ana.torres@academic.test");
    }
}
