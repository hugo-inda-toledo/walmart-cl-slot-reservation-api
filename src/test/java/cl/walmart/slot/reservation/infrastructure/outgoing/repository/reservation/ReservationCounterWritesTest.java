package cl.walmart.slot.reservation.infrastructure.outgoing.repository.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Propiedad de los contadores (10.4, 3.5): el SQL de {@code src/main} de reservas (código y migración) solo
 * actualiza {@code reserved}/{@code held} de las ventanas y nunca inserta ni borra ventanas ni cambia otras
 * columnas. Los fixtures de test quedan fuera porque crean y borran ventanas propias.
 */
class ReservationCounterWritesTest {

    private static final String SLOT_TABLE = "catalog_delivery_slot";
    private static final Path JAVA_ROOT = Path.of("src/main/java/cl/walmart/slot/reservation");
    private static final Path CONTEXT_SEGMENT = Path.of("reservation");
    private static final Path MIGRATION_ROOT = Path.of("src/main/resources/db/migration/reservation");

    private static final Pattern SLOT_WRITE = Pattern.compile(
            "\\b(insert\\s+into|delete\\s+from|update)\\s+" + SLOT_TABLE + "\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLOT_UPDATE_SET = Pattern.compile(
            "\\bupdate\\s+" + SLOT_TABLE + "(?:\\s+(?:as\\s+)?\\w+)?\\s+set\\s+(.+?)(?=\\s+(?:from|where|returning)\\b|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ASSIGNED_COLUMN = Pattern.compile("^\\s*(?:\\w+\\.)?(\\w+)\\s*=");

    @Test
    void reservationMainSourcesOnlyUpdateReservedAndHeldOfSlots() throws IOException {
        String sql = mainSql();

        List<String> writes = new ArrayList<>();
        Matcher writeMatcher = SLOT_WRITE.matcher(sql);
        while (writeMatcher.find()) {
            writes.add(writeMatcher.group(1).toLowerCase().replaceAll("\\s+", " "));
        }
        assertThat(writes).as("el código de reservas debe escribir ventanas").isNotEmpty();
        assertThat(writes).as("solo UPDATE sobre la tabla de ventanas").containsOnly("update");

        List<String> assigned = assignedColumns(sql);
        assertThat(assigned).as("columnas asignadas por el SQL de reservas")
                .isNotEmpty().containsOnly("reserved", "held");
    }

    @Test
    void detectsInsertsDeletesAndOtherColumnsInTheScannedSql() {
        assertThat(assignedColumns("update catalog_delivery_slot set held = held + 1, capacity = 3 where id = ?"))
                .containsExactly("held", "capacity");
        assertThat(assignedColumns("UPDATE catalog_delivery_slot s SET s.held = s.held - v.n FROM (values (1)) v"))
                .containsExactly("held");
        assertThat(SLOT_WRITE.matcher("insert into catalog_delivery_slot (id) values (1)").find()).isTrue();
        assertThat(SLOT_WRITE.matcher("delete   from catalog_delivery_slot where 1 = 1").find()).isTrue();
        assertThat(SLOT_WRITE.matcher("update reservation_slot_hold set status = 'X'").find()).isFalse();
    }

    private static List<String> assignedColumns(String sql) {
        List<String> columns = new ArrayList<>();
        Matcher updateMatcher = SLOT_UPDATE_SET.matcher(sql);
        while (updateMatcher.find()) {
            for (String assignment : updateMatcher.group(1).split(",")) {
                Matcher column = ASSIGNED_COLUMN.matcher(assignment);
                if (column.find()) {
                    columns.add(column.group(1).toLowerCase());
                }
            }
        }
        return columns;
    }

    private static boolean inReservationContext(Path file) {
        for (Path segment : JAVA_ROOT.relativize(file.getParent())) {
            if (segment.equals(CONTEXT_SEGMENT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fuentes de producción de reservas (las que tienen el segmento {@code reservation} bajo el paquete base, en
     * cualquier capa) con comentarios fuera y literales Java concatenados en SQL plano.
     */
    private static String mainSql() throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(JAVA_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java"))
                    .filter(ReservationCounterWritesTest::inReservationContext).toList()) {
                String source = Files.readString(file)
                        .replaceAll("(?s)/\\*.*?\\*/", " ")
                        .replaceAll("(?m)//.*$", " ")
                        .replaceAll("\"\\s*\\+\\s*\"", "")
                        .replace("\"\"\"", " ")
                        .replace("\"", " ");
                all.append(source).append('\n');
            }
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
        try (Stream<Path> files = Files.walk(MIGRATION_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                all.append(Files.readString(file).replaceAll("(?m)--.*$", " ")).append('\n');
            }
        }
        return all.toString().replaceAll("\\s+", " ");
    }
}
