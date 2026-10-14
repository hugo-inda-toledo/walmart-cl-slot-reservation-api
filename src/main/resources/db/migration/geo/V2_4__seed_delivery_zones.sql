-- geo-coverage: zonas de despacho demo.
-- Los UUID y códigos de zona son un contrato publicado (README, "Datos geográficos"): otros contextos los
-- referencian en sus propios datos semilla y no deben cambiar entre instalaciones.

INSERT INTO geo_dispatch_node (code, name) VALUES
    ('CD-LSR', 'CD La Serena'),
    ('CD-STGO', 'CD Santiago');

INSERT INTO geo_delivery_zone (id, code, name, status, dispatch_node_code) VALUES
    ('5f1c0e2a-0000-4000-8000-000000000401', 'LA_SERENA_COQUIMBO', 'La Serena-Coquimbo', 'ACTIVE', 'CD-LSR'),
    ('5f1c0e2a-0000-4000-8000-000000001301', 'SANTIAGO_CENTRO', 'Santiago Centro', 'ACTIVE', 'CD-STGO'),
    ('5f1c0e2a-0000-4000-8000-000000001302', 'SANTIAGO_ORIENTE', 'Santiago Oriente', 'ACTIVE', 'CD-STGO'),
    ('5f1c0e2a-0000-4000-8000-000000001303', 'CHICUREO', 'Chicureo', 'ACTIVE', 'CD-STGO'),
    ('5f1c0e2a-0000-4000-8000-000000001304', 'MAIPU', 'Maipú', 'INACTIVE', 'CD-STGO');

INSERT INTO geo_delivery_zone_comuna (comuna_code, zone_id) VALUES
    ('04101', '5f1c0e2a-0000-4000-8000-000000000401'),
    ('04102', '5f1c0e2a-0000-4000-8000-000000000401'),
    ('13101', '5f1c0e2a-0000-4000-8000-000000001301'),
    ('13106', '5f1c0e2a-0000-4000-8000-000000001301'),
    ('13108', '5f1c0e2a-0000-4000-8000-000000001301'),
    ('13127', '5f1c0e2a-0000-4000-8000-000000001301'),
    ('13114', '5f1c0e2a-0000-4000-8000-000000001302'),
    ('13120', '5f1c0e2a-0000-4000-8000-000000001302'),
    ('13123', '5f1c0e2a-0000-4000-8000-000000001302'),
    ('13132', '5f1c0e2a-0000-4000-8000-000000001302'),
    ('13119', '5f1c0e2a-0000-4000-8000-000000001304');

-- Áreas propias: un rectángulo dibujado a mano recortado contra el límite comunal de V2_3, de modo que cada
-- área queda contenida en su comuna aunque el rectángulo toque una comuna vecina o el mar. El recorte usa el
-- límite retranqueado ~1 m (buffer de -0.00001°) para que el ruido de punto flotante de la intersección no
-- deje vértices fuera de la comuna.
INSERT INTO geo_delivery_zone_area (id, zone_id, kind, name, geom)
SELECT '5f1c0e2a-0000-4000-8001-000000001303'::uuid, '5f1c0e2a-0000-4000-8000-000000001303'::uuid, 'INCLUSION',
       'Chicureo',
       ST_Multi(ST_CollectionExtract(ST_Intersection(
               ST_MakeEnvelope(-70.70, -33.32, -70.60, -33.25, 4326),
               ST_Buffer(b.geom, -0.00001, 'join=mitre')), 3))
FROM geo_comuna_boundary b
WHERE b.comuna_code = '13301';

INSERT INTO geo_delivery_zone_area (id, zone_id, kind, name, geom)
SELECT '5f1c0e2a-0000-4000-8001-000000000401'::uuid, '5f1c0e2a-0000-4000-8000-000000000401'::uuid, 'EXCLUSION',
       'Tongoy-Guanaqueros rural',
       ST_Multi(ST_CollectionExtract(ST_Intersection(
               ST_MakeEnvelope(-71.56, -30.30, -71.38, -30.16, 4326),
               ST_Buffer(b.geom, -0.00001, 'join=mitre')), 3))
FROM geo_comuna_boundary b
WHERE b.comuna_code = '04102';
