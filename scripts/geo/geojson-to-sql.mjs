// Convierte el GeoJSON simplificado de comunas en una migración Flyway determinista.
// Uso: node geojson-to-sql.mjs ENTRADA.geojson CODIGOS_ESPERADOS.txt SALIDA.sql
import { readFileSync, writeFileSync } from 'node:fs';

const [input, expectedList, output] = process.argv.slice(2);
if (!input || !expectedList || !output) {
  console.error('Uso: node geojson-to-sql.mjs ENTRADA.geojson CODIGOS_ESPERADOS.txt SALIDA.sql');
  process.exit(2);
}

const fail = (message) => {
  console.error(message);
  process.exit(1);
};

const expected = readFileSync(expectedList, 'utf8').split('\n').map((line) => line.trim()).filter(Boolean);
const collection = JSON.parse(readFileSync(input, 'utf8'));

const rows = collection.features.map((feature) => {
  const raw = feature.properties?.cod_comuna;
  const code = String(raw ?? '').trim().padStart(5, '0');
  if (!/^[0-9]{5}$/.test(code)) {
    fail(`Código comunal inválido en el shapefile: ${raw}`);
  }
  const { type, coordinates } = feature.geometry ?? {};
  if (type !== 'Polygon' && type !== 'MultiPolygon') {
    fail(`Geometría ${type} no soportada para la comuna ${code}`);
  }
  const multiPolygon = { type: 'MultiPolygon', coordinates: type === 'Polygon' ? [coordinates] : coordinates };
  return { code, geoJson: JSON.stringify(multiPolygon) };
});

const actualCodes = rows.map((row) => row.code).sort();
const expectedCodes = [...expected].sort();
const duplicates = actualCodes.filter((code, index) => actualCodes[index - 1] === code);
const missing = expectedCodes.filter((code) => !actualCodes.includes(code));
const unexpected = actualCodes.filter((code) => !expectedCodes.includes(code));
if (duplicates.length || missing.length || unexpected.length || actualCodes.length !== expectedCodes.length) {
  fail(`Comunas distintas de las ${expectedCodes.length} esperadas. `
    + `Faltan: [${missing}] Sobran: [${unexpected}] Duplicadas: [${duplicates}]`);
}

rows.sort((a, b) => a.code.localeCompare(b.code));

const header = [
  '-- GENERADO por scripts/geo/build-comuna-boundaries.sh: no editar a mano.',
  '-- Límites comunales simplificados (referenciales) de las regiones de Coquimbo (04) y Metropolitana (13).',
  '-- Fuente: Biblioteca del Congreso Nacional de Chile, Mapoteca SIIT, "División comunal" (uso libre citando la fuente).',
  '-- WGS 84 (EPSG:4326), coordenadas con 6 decimales.',
  '',
];
const inserts = rows.map(({ code, geoJson }) => 'INSERT INTO geo_comuna_boundary (comuna_code, geom) VALUES '
  + `('${code}', ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON('${geoJson}'), 4326)));`);

writeFileSync(output, `${[...header, ...inserts].join('\n')}\n`, 'utf8');
