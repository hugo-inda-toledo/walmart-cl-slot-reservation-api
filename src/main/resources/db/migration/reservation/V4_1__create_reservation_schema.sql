-- slot-reservation: retenciones (holds), reservas y claves de idempotencia.
-- Solo referencia objetos de V3_1 (catalog_delivery_slot). Los contadores reserved/held de la ventana los
-- actualiza el código de reservas con UPDATE condicional; aquí no se escribe la ventana.
-- Cada retención y reserva copia un snapshot de la ventana (zona, fecha, instantes y precio CLP).

CREATE TABLE reservation_slot_hold (
    id            uuid        PRIMARY KEY,
    customer_id   varchar(64) NOT NULL,
    order_id      varchar(64) NOT NULL,
    slot_id       uuid        NOT NULL REFERENCES catalog_delivery_slot (id),
    zone_id       uuid        NOT NULL,
    delivery_date date        NOT NULL,
    start_at      timestamptz NOT NULL,
    end_at        timestamptz NOT NULL,
    cutoff_at     timestamptz NOT NULL,
    price_clp     bigint      NOT NULL,
    status        varchar(10) NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    expires_at    timestamptz NOT NULL,
    closed_at     timestamptz,
    CONSTRAINT reservation_slot_hold_status_allowed
        CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT reservation_slot_hold_expires_after_creation CHECK (expires_at > created_at),
    -- Una retención está abierta (closed_at NULL) si y solo si está activa.
    CONSTRAINT reservation_slot_hold_closed_iff_not_active CHECK ((status = 'ACTIVE') = (closed_at IS NULL)),
    CONSTRAINT reservation_slot_hold_price_non_negative CHECK (price_clp >= 0)
);

-- Una sola retención activa por cliente y orden: la segunda inserción concurrente choca aquí.
CREATE UNIQUE INDEX reservation_slot_hold_active_order_uq
    ON reservation_slot_hold (customer_id, order_id) WHERE status = 'ACTIVE';

-- Lotes del sweeper: solo las activas, ordenadas por vencimiento.
CREATE INDEX reservation_slot_hold_due_idx ON reservation_slot_hold (expires_at) WHERE status = 'ACTIVE';

CREATE INDEX reservation_slot_hold_slot_status_idx ON reservation_slot_hold (slot_id, status);

CREATE TABLE reservation_booking (
    id            uuid        PRIMARY KEY,
    hold_id       uuid        NOT NULL REFERENCES reservation_slot_hold (id),
    customer_id   varchar(64) NOT NULL,
    order_id      varchar(64) NOT NULL,
    slot_id       uuid        NOT NULL REFERENCES catalog_delivery_slot (id),
    zone_id       uuid        NOT NULL,
    delivery_date date        NOT NULL,
    start_at      timestamptz NOT NULL,
    end_at        timestamptz NOT NULL,
    cutoff_at     timestamptz NOT NULL,
    price_clp     bigint      NOT NULL,
    status        varchar(10) NOT NULL,
    confirmed_at  timestamptz NOT NULL,
    cancelled_at  timestamptz,
    CONSTRAINT reservation_booking_hold_unique UNIQUE (hold_id),
    CONSTRAINT reservation_booking_status_allowed CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT reservation_booking_cancelled_iff_cancelled_at
        CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT reservation_booking_price_non_negative CHECK (price_clp >= 0)
);

-- Una sola reserva confirmada por cliente y orden.
CREATE UNIQUE INDEX reservation_booking_confirmed_order_uq
    ON reservation_booking (customer_id, order_id) WHERE status = 'CONFIRMED';

CREATE INDEX reservation_booking_slot_status_idx ON reservation_booking (slot_id, status);

-- Clave de idempotencia por cliente: se reclama en la misma transacción que la operación que protege.
-- outcome y response_payload quedan NULL mientras la operación está en curso.
CREATE TABLE reservation_idempotency_key (
    customer_id         varchar(64)  NOT NULL,
    idempotency_key     varchar(128) NOT NULL,
    operation           varchar(20)  NOT NULL,
    request_fingerprint char(64)     NOT NULL,
    outcome             varchar(10),
    response_payload    jsonb,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    expires_at          timestamptz  NOT NULL,
    CONSTRAINT reservation_idempotency_key_pk PRIMARY KEY (customer_id, idempotency_key),
    CONSTRAINT reservation_idempotency_key_outcome_allowed CHECK (outcome IN ('SUCCEEDED', 'REJECTED')),
    CONSTRAINT reservation_idempotency_key_operation_allowed CHECK (operation IN ('HOLD_SLOT', 'CONFIRM_HOLD'))
);

CREATE INDEX reservation_idempotency_key_expires_idx ON reservation_idempotency_key (expires_at);
