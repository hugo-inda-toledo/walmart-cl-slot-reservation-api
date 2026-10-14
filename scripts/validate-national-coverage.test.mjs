import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const header = 'source_name,comuna_code,evidence,source_url,captured_on,note';
const url = 'https://www.lider.cl/landing/static/zonas-de-cobertura/index.html';
const macul = 'Macul,13118,PRODUCT_CONFIRMED,,2026-10-13,Confirmado por dueño de producto';
const vina = `Viña del Mar,05109,OFFICIAL_PUBLIC,${url},2026-10-13,Comuna publicada`;

function validate(rows, columns = header) {
  const dir = mkdtempSync(join(tmpdir(), 'national-coverage-'));
  try {
    const input = join(dir, 'coverage.csv');
    writeFileSync(input, `${columns}\n${rows.join('\n')}\n`, 'utf8');
    return spawnSync(process.execPath, ['scripts/validate-national-coverage.mjs', input], { encoding: 'utf8' });
  } finally {
    rmSync(dir, { recursive: true });
  }
}

test('accepts DPA codes and emits deterministic SQL ordered by CUT with evidence totals', () => {
  const result = validate([macul, vina]);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stderr, /OFFICIAL_PUBLIC: 1/);
  assert.match(result.stderr, /PRODUCT_CONFIRMED: 1/);
  assert.ok(result.stdout.indexOf("'05109'") < result.stdout.indexOf("'13118'"));
  assert.equal(result.stdout, validate([vina, macul]).stdout);
});

test('rejects a published commune name paired with another valid DPA CUT', () => {
  const result = validate([vina.replace('05109', '13118')]);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /línea 2.*Viña del Mar.*13118.*Macul/i);
  assert.equal(result.stdout, '');
});

for (const [name, rows, columns, message] of [
  ['duplicate CUT', [macul, macul], header, /línea 3.*13118.*duplicad/i],
  ['invalid evidence', [macul.replace('PRODUCT_CONFIRMED', 'STORE_RADIUS')], header, /línea 2.*evidence.*OFFICIAL_PUBLIC.*PRODUCT_CONFIRMED/],
  ['nonexistent DPA code', [macul.replace('13118', '99999')], header, /línea 2.*99999.*DPA/],
  ['unexpected column', [macul], `${header},radius`, /columnas.*source_name.*comuna_code/],
  ['short CUT', [macul.replace('13118', '1318')], header, /línea 2.*cinco dígitos/],
  ['missing official URL', [vina.replace(url, '')], header, /línea 2.*source_url.*OFFICIAL_PUBLIC/],
  ['invalid official URL', [vina.replace(url, 'not-a-url')], header, /línea 2.*source_url/],
  ['impossible date', [macul.replace('2026-10-13', '2026-02-30')], header, /línea 2.*captured_on/],
  ['empty snapshot', [], header, /vacío/],
  ['empty provenance', [macul.replace('Macul,', ',')], header, /línea 2.*source_name/],
]) {
  test(`rejects ${name} with actionable diagnostics and no SQL`, () => {
    const result = validate(rows, columns);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, message);
    assert.equal(result.stdout, '');
  });
}

test('supports quoted notes, escaped quotes and UTF-8 without corrupting SQL literals', () => {
  const result = validate([`Macul,13118,PRODUCT_CONFIRMED,,2026-10-13,"Dueño: O'Brien, ""sí"""`]);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Dueño: O''Brien, "sí"/);
});

test('regenerates the geo portion of the consolidated migration from the national snapshot', () => {
  const result = spawnSync(process.execPath, ['scripts/validate-national-coverage.mjs',
    'src/main/resources/coverage/lider-coverage-2026-10-13.csv'], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  const migrationPath = 'src/main/resources/db/migration/reservation/V4_2__seed_national_delivery_coverage_and_slot_templates.sql';
  assert.ok(existsSync(migrationPath), 'falta la migración consolidada V4_2');
  const migration = readFileSync(migrationPath, 'utf8');
  assert.ok(migration.startsWith(result.stdout), 'la sección geo debe coincidir con el SQL generado');
  assert.match(migration.slice(result.stdout.length), /INSERT INTO catalog_slot_template/);
});
