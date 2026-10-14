package cl.walmart.slot.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        run(args);
    }

    public static ConfigurableApplicationContext run(String... args) {
        try {
            return SpringApplication.run(Application.class, args);
        } catch (RuntimeException exception) {
            String jdbcUrl = jdbcUrlArgument(args);
            if (jdbcUrl == null) {
                throw exception;
            }
            throw new IllegalStateException("Application startup failed while connecting to JDBC URL " + sanitize(jdbcUrl),
                    exception);
        }
    }

    private static String jdbcUrlArgument(String[] args) {
        for (String argument : args) {
            if (argument.startsWith("--spring.datasource.url=")) {
                return argument.substring("--spring.datasource.url=".length());
            }
        }
        return null;
    }

    private static String sanitize(String jdbcUrl) {
        return jdbcUrl.replaceFirst("//[^/@]*@", "//");
    }
}
