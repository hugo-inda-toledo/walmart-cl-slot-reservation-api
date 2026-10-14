-- geo-coverage: esquema geográfico (DPA, límites comunales, zonas de despacho y direcciones demo).
-- Solo referencia objetos de foundation (V1_1: extensión PostGIS).

CREATE TABLE geo_region (
    code                 char(2)      PRIMARY KEY,
    name                 varchar(80)  NOT NULL,
    north_to_south_order smallint     NOT NULL,
    CONSTRAINT geo_region_code_format CHECK (code ~ '^[0-9]{2}$'),
    CONSTRAINT geo_region_order_positive CHECK (north_to_south_order > 0),
    CONSTRAINT geo_region_order_unique UNIQUE (north_to_south_order)
);

CREATE TABLE geo_province (
    code        char(3)     PRIMARY KEY,
    region_code char(2)     NOT NULL REFERENCES geo_region (code),
    name        varchar(80) NOT NULL,
    CONSTRAINT geo_province_code_format CHECK (code ~ '^[0-9]{3}$'),
    CONSTRAINT geo_province_code_prefix CHECK (left(code, 2) = region_code)
);

CREATE INDEX geo_province_region_code_idx ON geo_province (region_code);

CREATE TABLE geo_comuna (
    code          char(5)     PRIMARY KEY,
    province_code char(3)     NOT NULL REFERENCES geo_province (code),
    name          varchar(80) NOT NULL,
    CONSTRAINT geo_comuna_code_format CHECK (code ~ '^[0-9]{5}$'),
    CONSTRAINT geo_comuna_code_prefix CHECK (left(code, 3) = province_code)
);

CREATE INDEX geo_comuna_province_code_idx ON geo_comuna (province_code);

CREATE TABLE geo_comuna_boundary (
    comuna_code char(5)                      PRIMARY KEY REFERENCES geo_comuna (code),
    geom        geometry(MultiPolygon, 4326) NOT NULL,
    CONSTRAINT geo_comuna_boundary_geom_valid CHECK (ST_IsValid(geom))
);

CREATE INDEX geo_comuna_boundary_geom_idx ON geo_comuna_boundary USING gist (geom);

CREATE TABLE geo_dispatch_node (
    code varchar(20) PRIMARY KEY,
    name varchar(80) NOT NULL,
    CONSTRAINT geo_dispatch_node_code_format CHECK (code ~ '^[A-Z][A-Z0-9-]{1,19}$')
);

CREATE TABLE geo_delivery_zone (
    id                 uuid        PRIMARY KEY,
    code               varchar(40) NOT NULL,
    name               varchar(80) NOT NULL,
    status             varchar(10) NOT NULL,
    dispatch_node_code varchar(20) NOT NULL REFERENCES geo_dispatch_node (code),
    CONSTRAINT geo_delivery_zone_code_unique UNIQUE (code),
    CONSTRAINT geo_delivery_zone_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT geo_delivery_zone_status_allowed CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX geo_delivery_zone_dispatch_node_code_idx ON geo_delivery_zone (dispatch_node_code);

-- La clave primaria por comuna garantiza que una comuna completa pertenezca a lo sumo a una zona.
CREATE TABLE geo_delivery_zone_comuna (
    comuna_code char(5) PRIMARY KEY REFERENCES geo_comuna (code),
    zone_id     uuid    NOT NULL REFERENCES geo_delivery_zone (id)
);

CREATE INDEX geo_delivery_zone_comuna_zone_id_idx ON geo_delivery_zone_comuna (zone_id);

CREATE TABLE geo_delivery_zone_area (
    id      uuid                         PRIMARY KEY,
    zone_id uuid                         NOT NULL REFERENCES geo_delivery_zone (id),
    kind    varchar(10)                  NOT NULL,
    name    varchar(80)                  NOT NULL,
    geom    geometry(MultiPolygon, 4326) NOT NULL,
    CONSTRAINT geo_delivery_zone_area_kind_allowed CHECK (kind IN ('INCLUSION', 'EXCLUSION')),
    CONSTRAINT geo_delivery_zone_area_geom_valid CHECK (ST_IsValid(geom))
);

CREATE INDEX geo_delivery_zone_area_zone_id_kind_idx ON geo_delivery_zone_area (zone_id, kind);
CREATE INDEX geo_delivery_zone_area_geom_idx ON geo_delivery_zone_area USING gist (geom);

CREATE TABLE geo_demo_address (
    id                 varchar(40)  PRIMARY KEY,
    label              varchar(120) NOT NULL,
    street             varchar(120) NOT NULL,
    number             varchar(20)  NOT NULL,
    comuna_code        char(5)      NOT NULL REFERENCES geo_comuna (code),
    latitude           numeric(9, 6),
    longitude          numeric(9, 6),
    expected_covered   boolean      NOT NULL,
    expected_zone_code varchar(40),
    expected_method    varchar(20),
    expected_reason    varchar(30),
    sort_order         smallint     NOT NULL,
    CONSTRAINT geo_demo_address_id_format CHECK (id ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT geo_demo_address_sort_order_unique UNIQUE (sort_order),
    CONSTRAINT geo_demo_address_coordinates_paired CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT geo_demo_address_latitude_range CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT geo_demo_address_longitude_range CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT geo_demo_address_expected_method_allowed
        CHECK (expected_method IN ('ZONE_AREA', 'COMUNA_BOUNDARY', 'NEAREST_COMUNA', 'COMUNA_CODE')),
    CONSTRAINT geo_demo_address_expected_reason_allowed
        CHECK (expected_reason IN ('EXCLUDED_AREA', 'OUTSIDE_SERVICE_AREA', 'COMUNA_NOT_SERVED', 'ZONE_INACTIVE')),
    CONSTRAINT geo_demo_address_expected_consistent CHECK (
        (expected_covered AND expected_zone_code IS NOT NULL AND expected_method IS NOT NULL
            AND expected_reason IS NULL)
        OR (NOT expected_covered AND expected_zone_code IS NULL AND expected_method IS NULL
            AND expected_reason IS NOT NULL)
    )
);

CREATE INDEX geo_demo_address_comuna_code_idx ON geo_demo_address (comuna_code);
