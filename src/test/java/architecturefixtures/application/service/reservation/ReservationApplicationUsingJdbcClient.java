package architecturefixtures.application.service.reservation;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Contraejemplo: una clase de aplicación de reservation que usa el cliente JDBC fuera de la persistencia.
 */
public class ReservationApplicationUsingJdbcClient {

    private final JdbcClient jdbcClient;

    public ReservationApplicationUsingJdbcClient(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public JdbcClient jdbcClient() {
        return jdbcClient;
    }
}
