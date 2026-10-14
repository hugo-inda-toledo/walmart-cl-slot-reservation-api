-- GENERADO por scripts/validate-national-coverage.mjs desde el snapshot local validado.
-- Cobertura por CUT; no infiere cobertura desde tiendas, radios ni localidades ambiguas.
-- Se conservan los identificadores, asignaciones y áreas demo existentes.

CREATE TABLE geo_coverage_provenance (
    source_name text NOT NULL,
    comuna_code char(5) PRIMARY KEY REFERENCES geo_comuna (code),
    evidence varchar(20) NOT NULL,
    source_url text,
    captured_on date NOT NULL,
    note text NOT NULL,
    CONSTRAINT geo_coverage_evidence_allowed CHECK (evidence IN ('OFFICIAL_PUBLIC', 'PRODUCT_CONFIRMED')),
    CONSTRAINT geo_coverage_official_url_required CHECK (
        evidence <> 'OFFICIAL_PUBLIC' OR (source_url IS NOT NULL AND length(trim(source_url)) > 0))
);

-- La FK falla antes de crear nodos o zonas si falta cualquier CUT de la DPA.
INSERT INTO geo_coverage_provenance (source_name, comuna_code, evidence, source_url, captured_on, note) VALUES
    ('Iquique', '01101', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Alto Hospicio', '01107', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Pica', '01405', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Mejillones', '02102', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('San Pedro de Atacama', '02203', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Tocopilla', '02301', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Copiapó', '03101', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Caldera', '03102', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Vallenar', '03301', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Freirina', '03303', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Huasco', '03304', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('La Serena', '04101', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Coquimbo', '04102', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Andacollo', '04103', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Vicuña', '04106', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Canela', '04202', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Ovalle', '04301', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Combarbalá', '04302', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Monte Patria', '04303', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Punitaqui', '04304', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Valparaíso', '05101', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Casa blanca', '05102', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Alias inequívoco Casa blanca → Casablanca; referencia pública comunal'),
    ('Concón', '05103', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Puchuncaví', '05105', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Quintero', '05107', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Viña del Mar', '05109', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('La Ligua', '05401', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Cabildo', '05402', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Papudo', '05403', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Zapallar', '05405', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('La Calera', '05502', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Alias inequívoco La Calera → Calera; referencia pública comunal'),
    ('Hijuelas', '05503', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Nogales', '05506', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('San Antonio', '05601', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Algarrobo', '05602', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Cartagena', '05603', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('El Quisco', '05604', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('El Tabo', '05605', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Santo Domingo', '05606', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Catemu', '05702', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Llay Llay', '05703', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Alias inequívoco Llay Llay → Llaillay; referencia pública comunal'),
    ('Quilpué', '05801', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Limache', '05802', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Olmué - Quebrada Alvarado', '05803', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Alias inequívoco Olmué - Quebrada Alvarado → Olmué; referencia pública comunal'),
    ('Villa Alemana', '05804', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Rancagua', '06101', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Codegua', '06102', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Coltauco', '06104', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Las Cabras', '06107', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Malloa', '06109', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('San Francisco Mostazal', '06110', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Alias inequívoco San Francisco Mostazal → Mostazal; referencia pública comunal'),
    ('Peumo', '06112', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Pichidegua', '06113', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Rengo', '06115', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Requínoa', '06116', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('San Vicente', '06117', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Pichilemu', '06201', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('La Estrella', '06202', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Litueche', '06203', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Marchigüe', '06204', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Navidad', '06205', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Chépica', '06302', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Chimbarongo', '06303', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Nancagua', '06305', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Palmilla', '06306', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Peralillo', '06307', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Santa Cruz', '06310', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Constitución', '07102', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Maule', '07105', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('San Clemente', '07109', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('San Rafael', '07110', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Molina', '07304', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Romeral', '07306', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Sagrada Familia', '07307', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Teno', '07308', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Vichuquén', '07309', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Colbún', '07402', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Villa Alegre', '07407', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Yerbas Buenas', '07408', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Coronel', '08102', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Chiguayante', '08103', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Hualqui', '08105', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Lota', '08106', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Penco', '08107', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Santa Juana', '08109', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Tomé', '08111', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Hualpén', '08112', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Arauco', '08202', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Mulchén', '08305', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Nacimiento', '08306', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Negrete', '08307', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Temuco', '09101', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Carahue', '09102', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Cunco', '09103', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Gorbea', '09107', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Lautaro', '09108', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Loncoche', '09109', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Nueva Imperial', '09111', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Perquenco', '09113', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Pucón', '09115', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Puerto Saavedra', '09116', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Alias inequívoco Puerto Saavedra → Saavedra; referencia pública comunal'),
    ('Villarrica', '09120', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Curacautín', '09203', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Lonquimay', '09205', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Victoria', '09211', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Puerto Montt', '10101', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Calbuco', '10102', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Fresia', '10104', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Frutillar', '10105', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Los Muermos', '10106', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Llanquihue', '10107', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Maullín', '10108', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Puerto Varas', '10109', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Castro', '10201', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Ancud', '10202', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Chonchi', '10203', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Dalcahue', '10205', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Osorno', '10301', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Puerto Octay', '10302', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Purranque', '10303', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Puyehue', '10304', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Río Negro', '10305', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('San Pablo', '10307', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Punta Arenas', '12101', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Puerto Natales', '12401', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Alias inequívoco Puerto Natales → Natales; referencia pública comunal'),
    ('Macul', '13118', 'PRODUCT_CONFIRMED', NULL, '2026-10-13', 'Cobertura confirmada por el dueño de producto el 2026-10-13'),
    ('Buin', '13402', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Calera de Tango', '13403', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Melipilla', '13501', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Curacaví', '13503', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('San Pedro', '13505', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Talagante', '13601', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('El Monte', '13602', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Isla de Maipo', '13603', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Peñaflor', '13605', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Valdivia', '14101', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Corral', '14102', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Lanco', '14103', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Los Lagos', '14104', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('San José de La Mariquina', '14106', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Alias inequívoco San José de La Mariquina → Mariquina; referencia pública comunal'),
    ('Paillaco', '14107', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Panguipulli', '14108', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('La Unión', '14201', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Futrono', '14202', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Lago Ranco', '14203', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Río Bueno', '14204', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Arica', '15101', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Putre', '15201', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Chillán', '16101', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Bulnes', '16102', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('El Carmen', '16104', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('Quillón', '16107', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider'),
    ('San Carlos', '16301', 'OFFICIAL_PUBLIC', 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html', '2026-10-13', 'Nombre de comuna publicado en cobertura Lider');

INSERT INTO geo_dispatch_node (code, name) VALUES ('CD-NATIONAL', 'Operación nacional Lider');

-- Una zona operativa por región con comunas nuevas, sin inventar polígonos ni un CD físico.
INSERT INTO geo_delivery_zone (id, code, name, status, dispatch_node_code)
SELECT ('5f1c0e2a-0000-4000-8002-0000000000' || r.code)::uuid,
       'LIDER_REGION_' || r.code, 'Lider - ' || r.name, 'ACTIVE', 'CD-NATIONAL'
FROM geo_region r
WHERE EXISTS (SELECT 1 FROM geo_coverage_provenance p
              LEFT JOIN geo_delivery_zone_comuna existing ON existing.comuna_code = p.comuna_code
              WHERE left(p.comuna_code, 2) = r.code AND existing.comuna_code IS NULL)
ORDER BY r.code;

INSERT INTO geo_delivery_zone_comuna (comuna_code, zone_id)
SELECT p.comuna_code, z.id FROM geo_coverage_provenance p
JOIN geo_delivery_zone z ON z.code = 'LIDER_REGION_' || left(p.comuna_code, 2)
WHERE NOT EXISTS (SELECT 1 FROM geo_delivery_zone_comuna existing WHERE existing.comuna_code = p.comuna_code)
ORDER BY p.comuna_code;

-- Sólo se activan asignaciones con evidencia del snapshot; Maipú demo conserva su estado.
UPDATE geo_delivery_zone z SET status = 'ACTIVE'
WHERE EXISTS (SELECT 1 FROM geo_delivery_zone_comuna c
              JOIN geo_coverage_provenance p ON p.comuna_code = c.comuna_code WHERE c.zone_id = z.id);

-- La dirección demo mantiene su identificador estable y refleja la cobertura nueva por CUT.
UPDATE geo_demo_address
SET label = 'Av. Libertad 100, Viña del Mar (cobertura por comuna)',
    expected_covered = true,
    expected_zone_code = 'LIDER_REGION_05',
    expected_method = 'COMUNA_CODE',
    expected_reason = NULL
WHERE id = 'vina-sin-cobertura';

-- Excepción de rango: cobertura geo y oferta catalog se publican juntas en V4_2 para que Flyway
-- las aplique también en instalaciones que ya ejecutaron V4_1, sin habilitar out-of-order.
-- La sección geo anterior se genera byte a byte desde el snapshot validado; sólo las plantillas
-- siguientes se mantienen manualmente. No se modifica V4_1 ni reserved/held.
-- Identificador determinista de catalog V3_3: sufijo de zona (4), día ISO (2), hora inicio/fin (4), 00.

INSERT INTO catalog_slot_template
    (id, zone_id, day_of_week, start_time, end_time, capacity, price_clp, service_type, status)
SELECT ('5f1c0e2a-0000-4000-8003-' || right(z.id::text, 4) || lpad(day_of_week::text, 2, '0')
        || '161800')::uuid,
       z.id, day_of_week, time '16:00', time '18:00', 20, 2990, 'HOME_DELIVERY', 'ACTIVE'
FROM geo_delivery_zone z
CROSS JOIN generate_series(1, 7) AS days(day_of_week)
WHERE z.code ~ '^LIDER_REGION_[0-9]{2}$'
  AND z.status = 'ACTIVE'
ORDER BY z.code, day_of_week;
