package cl.walmart.slot.reservation.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ApplicationSmokeIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void startsWithPostgisMigrationAndValkeyAvailable() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1.1' and success = true", Integer.class);
        String postgisVersion = jdbcTemplate.queryForObject("select postgis_version()", String.class);
        String valkeyResponse = redisTemplate.getConnectionFactory().getConnection().ping();

        assertThat(appliedMigrations).isEqualTo(1);
        assertThat(postgisVersion).isNotBlank();
        assertThat(valkeyResponse).isEqualTo("PONG");
    }
}
