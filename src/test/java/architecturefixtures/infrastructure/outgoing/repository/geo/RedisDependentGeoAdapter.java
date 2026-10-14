package architecturefixtures.infrastructure.outgoing.repository.geo;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Contraejemplo: un adaptador de geo que usa el cliente clave-valor.
 */
public class RedisDependentGeoAdapter {

    private final StringRedisTemplate redisTemplate;

    public RedisDependentGeoAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public StringRedisTemplate redisTemplate() {
        return redisTemplate;
    }
}
