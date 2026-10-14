-- delivery-slot-catalog: plantillas de bloque, feriados/bloqueos y ventanas de despacho.
-- Solo referencia objetos de geo (V2_1: geo_delivery_zone).
-- Contrato con slot-reservation (README, "Catálogo de ventanas"): reservas solo actualiza reserved y held de
-- catalog_delivery_slot; catalog nunca los escribe. La invariante de capacidad la garantiza esta base de datos.

CREATE TABLE catalog_slot_template (
    id           uuid        PRIMARY KEY,
    zone_id      uuid        NOT NULL REFERENCES geo_delivery_zone (id),
    day_of_week  integer     NOT NULL,
    start_time   time        NOT NULL,
    end_time     time        NOT NULL,
    capacity     integer     NOT NULL,
    price_clp    bigint      NOT NULL,
    service_type varchar(20) NOT NULL,
    status       varchar(10) NOT NULL,
    CONSTRAINT catalog_slot_template_day_of_week_range CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT catalog_slot_template_time_range CHECK (start_time < end_time),
    CONSTRAINT catalog_slot_template_capacity_non_negative CHECK (capacity >= 0),
    CONSTRAINT catalog_slot_template_price_non_negative CHECK (price_clp >= 0),
    CONSTRAINT catalog_slot_template_service_type_allowed CHECK (service_type = 'HOME_DELIVERY'),
    CONSTRAINT catalog_slot_template_status_allowed CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT catalog_slot_template_unique UNIQUE (zone_id, day_of_week, start_time, end_time, service_type)
);

-- zone_id NULL = feriado nacional (todas las zonas); con valor = bloqueo de esa zona.
CREATE TABLE catalog_holiday (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_date date        NOT NULL,
    name         varchar(80) NOT NULL,
    zone_id      uuid        REFERENCES geo_delivery_zone (id),
    CONSTRAINT catalog_holiday_date_zone_unique UNIQUE NULLS NOT DISTINCT (holiday_date, zone_id)
);

CREATE INDEX catalog_holiday_date_idx ON catalog_holiday (holiday_date);

CREATE TABLE catalog_delivery_slot (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id   uuid        NOT NULL REFERENCES catalog_slot_template (id),
    zone_id       uuid        NOT NULL REFERENCES geo_delivery_zone (id),
    delivery_date date        NOT NULL,
    service_type  varchar(20) NOT NULL,
    start_at      timestamptz NOT NULL,
    end_at        timestamptz NOT NULL,
    cutoff_at     timestamptz NOT NULL,
    price_clp     bigint      NOT NULL,
    capacity      integer     NOT NULL,
    reserved      integer     NOT NULL DEFAULT 0,
    held          integer     NOT NULL DEFAULT 0,
    version       bigint      NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT catalog_delivery_slot_template_date_unique UNIQUE (template_id, delivery_date),
    -- Invariante de capacidad: última línea de defensa ante cualquier escritor (catalog o reservas).
    -- Implica capacity >= 0.
    CONSTRAINT catalog_delivery_slot_capacity_check
        CHECK (reserved >= 0 AND held >= 0 AND reserved + held <= capacity),
    CONSTRAINT catalog_delivery_slot_time_range CHECK (start_at < end_at),
    CONSTRAINT catalog_delivery_slot_cutoff_before_start CHECK (cutoff_at <= start_at),
    CONSTRAINT catalog_delivery_slot_price_non_negative CHECK (price_clp >= 0),
    CONSTRAINT catalog_delivery_slot_service_type_allowed CHECK (service_type = 'HOME_DELIVERY')
);

CREATE INDEX catalog_delivery_slot_zone_date_start_idx ON catalog_delivery_slot (zone_id, delivery_date, start_at);

-- La versión sube con cada cambio de capacidad o contadores, sin depender de la disciplina de cada escritor.
CREATE FUNCTION catalog_delivery_slot_bump_version() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.capacity IS DISTINCT FROM OLD.capacity
        OR NEW.reserved IS DISTINCT FROM OLD.reserved
        OR NEW.held IS DISTINCT FROM OLD.held THEN
        NEW.version := OLD.version + 1;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER catalog_delivery_slot_bump_version
    BEFORE UPDATE ON catalog_delivery_slot
    FOR EACH ROW
EXECUTE FUNCTION catalog_delivery_slot_bump_version();
