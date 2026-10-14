# Arquitectura de la API

Este documento describe la estructura que está implementada en la API. El contrato
HTTP está definido en `src/main/resources/openapi/openapi.yml`; PostgreSQL conserva
la fuente de verdad de la capacidad y Valkey solo acelera lecturas.

<a id="arquitectura-hexagonal"></a>
## Arquitectura hexagonal

Las dependencias avanzan desde los adaptadores hacia el dominio. `cmd` es la raíz de
composición compartida: configura los adaptadores sin convertirse en dependencia de
los contextos.

```mermaid
graph LR
    HTTP[Cliente HTTP] --> IN[Adaptadores de entrada<br/>controllers y scheduler]
    IN --> APP[application<br/>casos de uso y servicios]
    APP --> DOMAIN[domain<br/>entidades, políticas y puertos]

    subgraph shared[shared / cmd]
        CFG[Configuración, errores RFC 9457<br/>y correlación]
        CUSTOMER[Adaptador X-Customer-Id]
    end

    subgraph geo[geo]
        GEO_APP[application.geo]
        GEO_DOMAIN[domain.geo<br/>ZoneResolutionPort]
        GEO_DB[Adaptador PostgreSQL/PostGIS]
        GEO_APP --> GEO_DOMAIN
        GEO_DB --> GEO_DOMAIN
    end

    subgraph catalog[catalog]
        CAT_APP[application.catalog]
        CAT_DOMAIN[domain.catalog<br/>SlotQueryPort<br/>AvailabilityCacheInvalidationPort]
        CAT_DB[Adaptador PostgreSQL]
        CAT_CACHE[Adaptador Valkey]
        CAT_APP --> CAT_DOMAIN
        CAT_DB --> CAT_DOMAIN
        CAT_CACHE --> CAT_DOMAIN
    end

    subgraph reservation[reservation]
        RES_APP[application.reservation]
        RES_DOMAIN[domain.reservation<br/>CurrentCustomerProvider]
        RES_DB[Adaptador PostgreSQL]
        RES_APP --> RES_DOMAIN
        RES_DB --> RES_DOMAIN
    end

    IN --> GEO_APP
    IN --> CAT_APP
    IN --> RES_APP
    CUSTOMER --> RES_DOMAIN
    CAT_APP --> GEO_DOMAIN
    RES_APP --> CAT_DOMAIN
    CFG --> IN
    CFG --> GEO_DB
    CFG --> CAT_DB
    CFG --> CAT_CACHE
    CFG --> RES_DB
```

Los puertos mantienen explícitos los límites entre contextos: catálogo resuelve zonas
mediante `ZoneResolutionPort`; reservas consulta la ventana mediante `SlotQueryPort`
e invalida la disponibilidad por `AvailabilityCacheInvalidationPort`. La identidad
llega por `CurrentCustomerProvider`, no por el cuerpo del request.

<a id="modelo-de-entidades"></a>
## Modelo de entidades

V1 solamente habilita PostGIS. Las tablas mostradas abajo provienen de V2 (geo), V3
(catálogo) y V4 (reservas); los nombres de claves y relaciones corresponden a las
migraciones Flyway reales.

```mermaid
erDiagram
    geo_region {
        char2 code PK
        varchar name
    }
    geo_province {
        char3 code PK
        char2 region_code FK
    }
    geo_comuna {
        char5 code PK
        char3 province_code FK
    }
    geo_comuna_boundary {
        char5 comuna_code PK, FK
        geometry geom
    }
    geo_dispatch_node {
        varchar code PK
    }
    geo_delivery_zone {
        uuid id PK
        varchar dispatch_node_code FK
    }
    geo_delivery_zone_comuna {
        char5 comuna_code PK, FK
        uuid zone_id FK
    }
    geo_delivery_zone_area {
        uuid id PK
        uuid zone_id FK
    }
    geo_demo_address {
        varchar id PK
        char5 comuna_code FK
    }
    catalog_slot_template {
        uuid id PK
        uuid zone_id FK
    }
    catalog_holiday {
        uuid id PK
        uuid zone_id FK
    }
    catalog_delivery_slot {
        uuid id PK
        uuid template_id FK
        uuid zone_id FK
        int capacity
        int reserved
        int held
    }
    reservation_slot_hold {
        uuid id PK
        uuid slot_id FK
    }
    reservation_booking {
        uuid id PK
        uuid hold_id FK
        uuid slot_id FK
    }
    reservation_idempotency_key {
        varchar customer_id PK
        varchar idempotency_key PK
    }

    geo_region ||--o{ geo_province : region_code
    geo_province ||--o{ geo_comuna : province_code
    geo_comuna ||--o| geo_comuna_boundary : comuna_code
    geo_comuna ||--o{ geo_delivery_zone_comuna : comuna_code
    geo_comuna ||--o{ geo_demo_address : comuna_code
    geo_dispatch_node ||--o{ geo_delivery_zone : dispatch_node_code
    geo_delivery_zone ||--o{ geo_delivery_zone_comuna : zone_id
    geo_delivery_zone ||--o{ geo_delivery_zone_area : zone_id
    geo_delivery_zone ||--o{ catalog_slot_template : zone_id
    geo_delivery_zone o|--o{ catalog_holiday : "zone_id nullable"
    geo_delivery_zone ||--o{ catalog_delivery_slot : zone_id
    catalog_slot_template ||--o{ catalog_delivery_slot : template_id
    catalog_delivery_slot ||--o{ reservation_slot_hold : slot_id
    reservation_slot_hold ||--o| reservation_booking : hold_id
    catalog_delivery_slot ||--o{ reservation_booking : slot_id
```

La última defensa de capacidad es el `CHECK`
`catalog_delivery_slot_capacity_check`: `reserved >= 0`, `held >= 0` y
`reserved + held <= capacity`. Las unicidades parciales también son parte del
modelo: `reservation_slot_hold_active_order_uq` impide más de una retención activa
por cliente y orden, y `reservation_booking_confirmed_order_uq` más de una reserva
confirmada por cliente y orden. Además, `reservation_booking_hold_unique` garantiza
una reserva por hold y la PK compuesta de `reservation_idempotency_key` reclama la
clave por cliente. `catalog_holiday.zone_id` puede ser `NULL` para representar un
feriado nacional; si tiene valor, el feriado se limita a esa zona.

<a id="secuencia-retener-confirmar"></a>
## Secuencia retener → confirmar

El reloj autoritativo de las decisiones que cambian capacidad es `now()` de
PostgreSQL. La caché se invalida únicamente después de que la transacción se haya
confirmado, por lo que su indisponibilidad no puede alterar la capacidad.
La operación de retención es el `UPDATE` condicional `SET held = held + 1 WHERE
reserved + held < capacity AND cutoff_at > now()`; una fila afectada significa que
la capacidad se obtuvo y cero filas activa el diagnóstico para `SLOT_FULL`.

```mermaid
sequenceDiagram
    participant C as Cliente
    participant Ctrl as ReservationController
    participant UC as Hold/Confirm UseCase
    participant DB as PostgreSQL
    participant Cache as Valkey
    participant Sweeper as ExpiredHoldSweeper

    C->>Ctrl: POST hold + Idempotency-Key
    Ctrl->>UC: comando + CurrentCustomerProvider
    UC->>DB: BEGIN y reclamar idempotencia UNIQUE
    DB-->>UC: existente o fila reclamada
    alt respuesta idempotente existente
        UC-->>Ctrl: respuesta persistida
        Ctrl-->>C: misma respuesta
    else nueva operación
        UC->>DB: UPDATE slot, held + 1<br/>si capacidad disponible y cutoff vigente según now()
        alt UPDATE afectó una fila
            UC->>DB: INSERT hold(expires_at = now() + ttl)
            UC->>DB: guardar resultado y COMMIT
            UC-->>Cache: after-commit: invalidar disponibilidad
            Ctrl-->>C: 201 hold y expiresAt
        else UPDATE afectó 0 filas
            UC->>DB: diagnosticar cierre/capacidad, guardar rechazo y COMMIT
            UC-->>DB: consultar alternativas
            Ctrl-->>C: 409 SLOT_FULL + alternatives
        end
    end

    C->>Ctrl: POST confirmar hold + Idempotency-Key
    Ctrl->>UC: confirmar
    UC->>DB: BEGIN y reclamar idempotencia UNIQUE
    UC->>DB: confirmar si expires_at sigue vigente según now()<br/>held - 1 y reserved + 1
    UC->>DB: INSERT reservation_booking y COMMIT
    UC-->>Cache: after-commit: invalidar disponibilidad
    Ctrl-->>C: 201 reserva confirmada

    Sweeper->>DB: seleccionar holds vencidos FOR UPDATE SKIP LOCKED
    Sweeper->>DB: marcar EXPIRED, held = held - 1 y COMMIT
    Sweeper-->>Cache: after-commit: invalidar disponibilidad
```

La secuencia resume las guardas de concurrencia. El detalle de columnas, estados y
restricciones está en las migraciones `V2_1__create_geo_schema.sql`,
`V3_1__create_catalog_schema.sql` y `V4_1__create_reservation_schema.sql`.
