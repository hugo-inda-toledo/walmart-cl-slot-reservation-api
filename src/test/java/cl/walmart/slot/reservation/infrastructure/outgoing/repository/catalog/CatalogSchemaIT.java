package cl.walmart.slot.reservation.infrastructure.outgoing.repository.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CatalogSchemaIT {

    private static final String ZONE = "00000000-0000-4000-8000-0000000000c1";
    private static final String TEMPLATE = "00000000-0000-4000-8003-0000000000c1";
    private static final String SLOT = "00000000-0000-4000-8004-0000000000c1";
    private static final String INSERT_TEMPLATE = "insert into catalog_slot_template "
            + "(id, zone_id, day_of_week, start_time, end_time, capacity, price_clp, service_type, status) "
            + "values (?::uuid, ?::uuid, ?, ?::time, ?::time, ?, ?, ?, ?)";
    private static final String INSERT_SLOT = "insert into catalog_delivery_slot "
            + "(id, template_id, zone_id, delivery_date, service_type, start_at, end_at, cutoff_at, price_clp, "
            + "capacity) values (gen_random_uuid(), ?::uuid, ?::uuid, ?::date, 'HOME_DELIVERY', "
            + "?::timestamptz, ?::timestamptz, ?::timestamptz, 2990, 5)";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void insertFixtureZoneTemplateAndSlot() {
        jdbc.update("insert into geo_dispatch_node (code, name) values ('CD-CAT', 'CD catálogo')");
        jdbc.update("insert into geo_delivery_zone (id, code, name, status, dispatch_node_code) "
                + "values (?::uuid, 'CATALOG_TEST', 'Zona catálogo', 'ACTIVE', 'CD-CAT')", ZONE);
        jdbc.update(INSERT_TEMPLATE, TEMPLATE, ZONE, 1, "16:00", "18:00", 5, 2990, "HOME_DELIVERY", "ACTIVE");
        jdbc.update("insert into catalog_delivery_slot (id, template_id, zone_id, delivery_date, service_type, "
                + "start_at, end_at, cutoff_at, price_clp, capacity) values (?::uuid, ?::uuid, ?::uuid, "
                + "'2031-03-03', 'HOME_DELIVERY', '2031-03-03T16:00:00-03:00', '2031-03-03T18:00:00-03:00', "
                + "'2031-03-03T14:00:00-03:00', 2990, 5)", SLOT, TEMPLATE, ZONE);
    }

    @Test
    void createsEveryCatalogTable() {
        assertThat(jdbc.queryForList("select table_name from information_schema.tables "
                + "where table_name like 'catalog\\_%' order by table_name", String.class))
                .containsExactly("catalog_delivery_slot", "catalog_holiday", "catalog_slot_template");
    }

    @Test
    void declaresEveryColumnNotNullExceptTheZoneOfAHoliday() {
        assertThat(jdbc.queryForList("select table_name || '.' || column_name from information_schema.columns "
                + "where table_name like 'catalog\\_%' and is_nullable = 'YES'", String.class))
                .containsExactly("catalog_holiday.zone_id");
    }

    @Test
    void startsANewSlotWithZeroCountersAndVersion() {
        assertThat(jdbc.queryForMap("select reserved, held, version from catalog_delivery_slot where id = ?::uuid",
                SLOT)).containsEntry("reserved", 0).containsEntry("held", 0).containsEntry("version", 0L);
    }

    @Test
    void rejectsCountersAboveCapacityWithTheNamedConstraint() {
        jdbc.update("update catalog_delivery_slot set reserved = 3, held = 2 where id = ?::uuid", SLOT);

        assertRejected("catalog_delivery_slot_capacity_check",
                "update catalog_delivery_slot set held = held + 1 where id = ?::uuid", SLOT);
        assertRejected("catalog_delivery_slot_capacity_check",
                "update catalog_delivery_slot set capacity = 4 where id = ?::uuid", SLOT);
    }

    @Test
    void rejectsNegativeCountersAndCapacityWithTheNamedConstraint() {
        assertRejected("catalog_delivery_slot_capacity_check",
                "update catalog_delivery_slot set reserved = -1 where id = ?::uuid", SLOT);
        assertRejected("catalog_delivery_slot_capacity_check",
                "update catalog_delivery_slot set held = -1, reserved = 1 where id = ?::uuid", SLOT);
        assertRejected("catalog_delivery_slot_capacity_check",
                "update catalog_delivery_slot set capacity = -1 where id = ?::uuid", SLOT);
    }

    @Test
    void bumpsTheVersionOnlyWhenCapacityOrCountersChange() {
        jdbc.update("update catalog_delivery_slot set held = held + 1 where id = ?::uuid", SLOT);
        jdbc.update("update catalog_delivery_slot set reserved = reserved + 1, held = held - 1 where id = ?::uuid",
                SLOT);
        jdbc.update("update catalog_delivery_slot set capacity = 6 where id = ?::uuid", SLOT);
        assertThat(version()).isEqualTo(3L);

        jdbc.update("update catalog_delivery_slot set price_clp = 3990 where id = ?::uuid", SLOT);
        jdbc.update("update catalog_delivery_slot set capacity = capacity, reserved = reserved where id = ?::uuid",
                SLOT);
        assertThat(version()).isEqualTo(3L);
    }

    @Test
    void allowsAZeroCapacitySlotAndTemplate() {
        jdbc.update(INSERT_TEMPLATE, "00000000-0000-4000-8003-0000000000c2", ZONE, 2, "16:00", "18:00", 0, 0,
                "HOME_DELIVERY", "ACTIVE");
        jdbc.update("update catalog_delivery_slot set capacity = 0 where id = ?::uuid", SLOT);

        assertThat(jdbc.queryForObject("select capacity from catalog_delivery_slot where id = ?::uuid",
                Integer.class, SLOT)).isZero();
    }

    @Test
    void rejectsInvalidTemplates() {
        String other = "00000000-0000-4000-8003-0000000000c9";
        assertRejected("catalog_slot_template_day_of_week_range",
                INSERT_TEMPLATE, other, ZONE, 8, "16:00", "18:00", 5, 2990, "HOME_DELIVERY", "ACTIVE");
        assertRejected("catalog_slot_template_time_range",
                INSERT_TEMPLATE, other, ZONE, 1, "18:00", "18:00", 5, 2990, "HOME_DELIVERY", "ACTIVE");
        assertRejected("catalog_slot_template_capacity_non_negative",
                INSERT_TEMPLATE, other, ZONE, 1, "10:00", "12:00", -1, 2990, "HOME_DELIVERY", "ACTIVE");
        assertRejected("catalog_slot_template_price_non_negative",
                INSERT_TEMPLATE, other, ZONE, 1, "10:00", "12:00", 5, -1, "HOME_DELIVERY", "ACTIVE");
        assertRejected("catalog_slot_template_service_type_allowed",
                INSERT_TEMPLATE, other, ZONE, 1, "10:00", "12:00", 5, 2990, "STORE_PICKUP", "ACTIVE");
        assertRejected("catalog_slot_template_status_allowed",
                INSERT_TEMPLATE, other, ZONE, 1, "10:00", "12:00", 5, 2990, "HOME_DELIVERY", "PAUSED");
    }

    @Test
    void rejectsTemplatesOfAnUnknownZoneAndDuplicatesButAcceptsOverlaps() {
        assertRejected("catalog_slot_template_zone_id_fkey",
                INSERT_TEMPLATE, "00000000-0000-4000-8003-0000000000c3", "00000000-0000-4000-8000-0000000000ff", 1,
                "10:00", "12:00", 5, 2990, "HOME_DELIVERY", "ACTIVE");
        assertRejected("catalog_slot_template_unique",
                INSERT_TEMPLATE, "00000000-0000-4000-8003-0000000000c4", ZONE, 1, "16:00", "18:00", 9, 1990,
                "HOME_DELIVERY", "INACTIVE");

        jdbc.update(INSERT_TEMPLATE, "00000000-0000-4000-8003-0000000000c5", ZONE, 1, "17:00", "19:00", 5, 2990,
                "HOME_DELIVERY", "ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from catalog_slot_template where zone_id = ?::uuid",
                Integer.class, ZONE)).isEqualTo(2);
    }

    @Test
    void allowsASingleSlotPerTemplateAndDateWithConsistentInstants() {
        assertRejected("catalog_delivery_slot_template_date_unique", INSERT_SLOT, TEMPLATE, ZONE, "2031-03-03",
                "2031-03-03T16:00:00-03:00", "2031-03-03T18:00:00-03:00", "2031-03-03T14:00:00-03:00");
        assertRejected("catalog_delivery_slot_time_range", INSERT_SLOT, TEMPLATE, ZONE, "2031-03-10",
                "2031-03-10T18:00:00-03:00", "2031-03-10T16:00:00-03:00", "2031-03-10T14:00:00-03:00");
        assertRejected("catalog_delivery_slot_cutoff_before_start", INSERT_SLOT, TEMPLATE, ZONE, "2031-03-10",
                "2031-03-10T16:00:00-03:00", "2031-03-10T18:00:00-03:00", "2031-03-10T16:00:01-03:00");
        assertRejected("catalog_delivery_slot_template_id_fkey", INSERT_SLOT,
                "00000000-0000-4000-8003-0000000000ff", ZONE, "2031-03-10",
                "2031-03-10T16:00:00-03:00", "2031-03-10T18:00:00-03:00", "2031-03-10T14:00:00-03:00");
        assertRejected("catalog_delivery_slot_zone_id_fkey", INSERT_SLOT, TEMPLATE,
                "00000000-0000-4000-8000-0000000000ff", "2031-03-10",
                "2031-03-10T16:00:00-03:00", "2031-03-10T18:00:00-03:00", "2031-03-10T14:00:00-03:00");
    }

    @Test
    void allowsOneNationalHolidayPerDateAndOneBlockPerZoneAndDate() {
        String insert = "insert into catalog_holiday (holiday_date, name, zone_id) values (?::date, ?, ?::uuid)";
        jdbc.update(insert, "2031-01-02", "Feriado de prueba", null);
        jdbc.update(insert, "2031-01-02", "Bloqueo de prueba", ZONE);

        assertRejected("catalog_holiday_date_zone_unique", insert, "2031-01-02", "Duplicado nacional", null);
        assertRejected("catalog_holiday_date_zone_unique", insert, "2031-01-02", "Duplicado de zona", ZONE);
        assertRejected("catalog_holiday_zone_id_fkey", insert, "2031-01-03", "Zona inexistente",
                "00000000-0000-4000-8000-0000000000ff");
    }

    @Test
    void indexesSlotsByZoneDateAndStart() {
        List<String> indexes = jdbc.queryForList(
                "select indexdef from pg_indexes where tablename = 'catalog_delivery_slot'", String.class);

        assertThat(indexes).anySatisfy(definition -> assertThat(definition)
                .contains("catalog_delivery_slot_zone_date_start_idx").contains("(zone_id, delivery_date, start_at)"));
    }

    private long version() {
        return jdbc.queryForObject("select version from catalog_delivery_slot where id = ?::uuid", Long.class, SLOT);
    }

    /** Runs a statement expected to violate {@code constraint} inside a savepoint, so the test transaction survives. */
    private void assertRejected(String constraint, String sql, Object... args) {
        jdbc.execute("savepoint rejected_statement");
        try {
            assertThatThrownBy(() -> jdbc.update(sql, args))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(constraint);
        } finally {
            jdbc.execute("rollback to savepoint rejected_statement");
        }
    }
}
