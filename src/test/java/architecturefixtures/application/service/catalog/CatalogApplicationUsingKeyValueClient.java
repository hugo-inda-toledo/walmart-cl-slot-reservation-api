package architecturefixtures.application.service.catalog;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Contraejemplo: una clase de aplicación de catalog que usa el cliente clave-valor directamente.
 */
public class CatalogApplicationUsingKeyValueClient {

    private final StringRedisTemplate redisTemplate;

    public CatalogApplicationUsingKeyValueClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public StringRedisTemplate redisTemplate() {
        return redisTemplate;
    }
}
