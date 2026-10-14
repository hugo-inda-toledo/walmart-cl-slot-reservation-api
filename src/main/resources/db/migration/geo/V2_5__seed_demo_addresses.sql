-- geo-coverage: direcciones demo con su resultado de cobertura esperado.
-- Las coordenadas se eligieron contra los límites de V2_3 y las áreas de V2_4 (las verifica
-- DemoAddressCoordinatesIT). "la-serena-borde" queda en el mar, fuera de todo límite comunal y a ~150 m del
-- límite simplificado de La Serena: se resuelve por tolerancia de borde.

INSERT INTO geo_demo_address (id, label, street, number, comuna_code, latitude, longitude, expected_covered,
                              expected_zone_code, expected_method, expected_reason, sort_order) VALUES
    ('la-serena-monjitas', 'Monjitas 2334, La Serena', 'Monjitas', '2334', '04101', -29.904500, -71.248900,
     true, 'LA_SERENA_COQUIMBO', 'COMUNA_BOUNDARY', NULL, 1),
    ('santiago-monjitas', 'Monjitas 879, Santiago', 'Monjitas', '879', '13101', -33.437200, -70.645000,
     true, 'SANTIAGO_CENTRO', 'COMUNA_BOUNDARY', NULL, 2),
    ('providencia-av', 'Av. Providencia 1234, Providencia', 'Av. Providencia', '1234', '13123', -33.429000, -70.612000,
     true, 'SANTIAGO_ORIENTE', 'COMUNA_BOUNDARY', NULL, 3),
    ('chicureo', 'Camino Chicureo 1500, Colina', 'Camino Chicureo', '1500', '13301', -33.285000, -70.650000,
     true, 'CHICUREO', 'ZONE_AREA', NULL, 4),
    ('la-serena-borde', 'Av. del Mar 2100, La Serena (borde)', 'Av. del Mar', '2100', '04101', -29.910000, -71.276400,
     true, 'LA_SERENA_COQUIMBO', 'NEAREST_COMUNA', NULL, 5),
    ('coquimbo-sin-coordenadas', 'Aldunate 350, Coquimbo (sin coordenadas)', 'Aldunate', '350', '04102', NULL, NULL,
     true, 'LA_SERENA_COQUIMBO', 'COMUNA_CODE', NULL, 6),
    ('vina-sin-cobertura', 'Av. Libertad 100, Viña del Mar', 'Av. Libertad', '100', '05109', NULL, NULL,
     false, NULL, NULL, 'COMUNA_NOT_SERVED', 7),
    ('maipu-inactiva', 'Av. Pajaritos 2000, Maipú', 'Av. Pajaritos', '2000', '13119', -33.510000, -70.757000,
     false, NULL, NULL, 'ZONE_INACTIVE', 8),
    ('tongoy-excluida', 'Av. Costanera 100, Tongoy (Coquimbo)', 'Av. Costanera', '100', '04102', -30.260000, -71.485000,
     false, NULL, NULL, 'EXCLUDED_AREA', 9);
