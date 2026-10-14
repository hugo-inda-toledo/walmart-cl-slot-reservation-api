package architecturefixtures.application.service.reservation;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Contraejemplo: una clase de reservation que usa el cliente clave-valor directamente.
 */
public class ReservationUsingKeyValueClient {

    private final StringRedisTemplate redisTemplate;

    public ReservationUsingKeyValueClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public StringRedisTemplate redisTemplate() {
        return redisTemplate;
    }
}
