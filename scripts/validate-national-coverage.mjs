// Valida un snapshot local contra la DPA versionada y emite la sección geo de V4_2 por stdout.
// Uso: node scripts/validate-national-coverage.mjs ARCHIVO.csv
// No consulta fuentes remotas ni se ejecuta durante el runtime de la API.
import { readFileSync } from 'node:fs';

const columns = ['source_name', 'comuna_code', 'evidence', 'source_url', 'captured_on', 'note'];
// Alias publicados revisados contra la DPA; cada uno pertenece a un CUT específico.
const reviewedAliases = new Map([
  ['05102', 'Casa blanca'],
  ['05502', 'La Calera'],
  ['05703', 'Llay Llay'],
  ['05803', 'Olmué - Quebrada Alvarado'],
  ['06110', 'San Francisco Mostazal'],
  ['09116', 'Puerto Saavedra'],
  ['12401', 'Puerto Natales'],
  ['14106', 'San José de La Mariquina'],
]);
const fail = (message) => { throw new Error(message); };

function parseCsv(text) {
  const rows = [];
  let fields = [], field = '', quoted = false, closed = false, line = 1, start = 1;
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (quoted) {
      if (char === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (char === '"') { quoted = false; closed = true; }
      else { field += char; if (char === '\n') line++; }
    } else if (char === '"') {
      if (field || closed) fail(`línea ${line}: comillas CSV inesperadas`);
      quoted = true;
    } else if (char === ',') {
      fields.push(field); field = ''; closed = false;
    } else if (char === '\n' || char === '\r') {
      if (char === '\r' && text[i + 1] === '\n') i++;
      fields.push(field);
      if (fields.some((value) => value.length)) rows.push({ line: start, fields });
      fields = []; field = ''; closed = false; start = ++line;
    } else {
      if (closed) fail(`línea ${line}: texto después de comillas CSV`);
      field += char;
    }
  }
  if (quoted) fail(`línea ${start}: comillas CSV sin cerrar`);
  if (fields.length || field || closed) { fields.push(field); rows.push({ line: start, fields }); }
  return rows;
}

function validate(input) {
  const csv = parseCsv(readFileSync(input, 'utf8').replace(/^\uFEFF/, ''));
  if (csv[0]?.fields.join(',') !== columns.join(',')) {
    fail(`columnas esperadas (en este orden): ${columns.join(',')}`);
  }
  const dpaSql = readFileSync(new URL('../src/main/resources/db/migration/geo/V2_2__seed_dpa.sql', import.meta.url), 'utf8');
  const comunaRows = dpaSql.split('INSERT INTO geo_comuna (code, province_code, name) VALUES')[1];
  const dpa = new Map([...comunaRows.matchAll(/\('([0-9]{5})', '[0-9]{3}', '((?:''|[^'])*)'\)/g)]
    .map((match) => [match[1], match[2].replaceAll("''", "'")]));
  if (dpa.size !== 346) fail('DPA versionada inválida: se esperaban 346 comunas');
  const codes = new Set();
  const rows = csv.slice(1).map(({ line, fields }) => {
    if (fields.length !== columns.length) fail(`línea ${line}: se esperaban ${columns.length} columnas`);
    const row = Object.fromEntries(columns.map((column, index) => [column, fields[index].trim()]));
    if (!row.source_name) fail(`línea ${line}: source_name es obligatorio`);
    if (!/^[0-9]{5}$/.test(row.comuna_code)) fail(`línea ${line}: comuna_code debe tener cinco dígitos`);
    if (!dpa.has(row.comuna_code)) fail(`línea ${line}: CUT ${row.comuna_code} inexistente en DPA`);
    const officialName = dpa.get(row.comuna_code);
    if (row.source_name !== officialName && row.source_name !== reviewedAliases.get(row.comuna_code)) {
      fail(`línea ${line}: source_name ${JSON.stringify(row.source_name)} no corresponde a CUT ${row.comuna_code} (DPA: ${officialName})`);
    }
    if (codes.has(row.comuna_code)) fail(`línea ${line}: CUT ${row.comuna_code} duplicado`);
    codes.add(row.comuna_code);
    if (!['OFFICIAL_PUBLIC', 'PRODUCT_CONFIRMED'].includes(row.evidence)) {
      fail(`línea ${line}: evidence debe ser OFFICIAL_PUBLIC o PRODUCT_CONFIRMED`);
    }
    if (row.evidence === 'OFFICIAL_PUBLIC' && !row.source_url) {
      fail(`línea ${line}: source_url obligatorio para OFFICIAL_PUBLIC`);
    }
    if (row.source_url) {
      let url;
      try { url = new URL(row.source_url); } catch { fail(`línea ${line}: source_url inválido`); }
      if (!['http:', 'https:'].includes(url.protocol)) fail(`línea ${line}: source_url debe ser HTTP(S)`);
    }
    const date = new Date(`${row.captured_on}T00:00:00Z`);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(row.captured_on) || Number.isNaN(date.getTime())
        || date.toISOString().slice(0, 10) !== row.captured_on) {
      fail(`línea ${line}: captured_on debe ser una fecha válida YYYY-MM-DD`);
    }
    if (!row.note) fail(`línea ${line}: note es obligatorio para conservar la procedencia`);
    return row;
  });
  if (!rows.length) fail('snapshot vacío: se requiere al menos una comuna');
  return rows.sort((a, b) => a.comuna_code.localeCompare(b.comuna_code));
}

const literal = (value) => value ? `'${value.replaceAll("'", "''")}'` : 'NULL';

function generateSql(rows) {
  const values = rows.map((row) => `    (${columns.map((column) => literal(row[column])).join(', ')})`).join(',\n');
  return `-- GENERADO por scripts/validate-national-coverage.mjs desde el snapshot local validado.
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
INSERT INTO geo_coverage_provenance (${columns.join(', ')}) VALUES
${values};

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
`;
}

try {
  const [input, ...extra] = process.argv.slice(2);
  if (!input || extra.length) fail('Uso: node scripts/validate-national-coverage.mjs ARCHIVO.csv');
  const rows = validate(input);
  const sql = generateSql(rows);
  for (const evidence of ['OFFICIAL_PUBLIC', 'PRODUCT_CONFIRMED']) {
    console.error(`${evidence}: ${rows.filter((row) => row.evidence === evidence).length}`);
  }
  console.error(`TOTAL: ${rows.length}`);
  process.stdout.write(sql);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
