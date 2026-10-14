import http from 'k6/http';
import {check} from 'k6';
import {Counter} from 'k6/metrics';

const slotId = __ENV.SLOT_ID;
const initialFree = Number(__ENV.INITIAL_FREE);
const vus = Number(__ENV.VUS || 300);
const runId = __ENV.RUN_ID || 'manual';

if (!slotId) throw new Error('SLOT_ID es obligatorio; usa ./load/run-flash-sale.sh para seleccionarlo.');
if (!Number.isInteger(initialFree) || initialFree < 0) throw new Error('INITIAL_FREE debe ser un entero no negativo.');
if (!Number.isInteger(vus) || vus < 1) throw new Error('VUS debe ser un entero positivo.');

const expected = Math.min(initialFree, vus);
const holdsCreated = new Counter('holds_created');
const bookingsConfirmed = new Counter('bookings_confirmed');
const slotFull = new Counter('slot_full');
const unexpectedResponses = new Counter('unexpected_responses');

export const options = {
  scenarios: {flash: {executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: '2m'}},
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    holds_created: [`count==${expected}`],
    bookings_confirmed: [`count==${expected}`],
    unexpected_responses: ['count==0'],
  },
};

function headers(customerId, idempotencyKey) {
  return {'Content-Type': 'application/json', 'X-Customer-Id': customerId, 'Idempotency-Key': idempotencyKey};
}

function unexpected(response, phase) {
  unexpectedResponses.add(1);
  console.error(`${phase}: HTTP ${response.status} ${response.body}`);
}

export default function flashSale() {
  const customerId = `k6-${runId}-${__VU}`;
  const orderId = `k6-${runId}-${__VU}`;
  const holdResponse = http.post(
    `${__ENV.BASE_URL || 'http://api:8080'}/api/v1/slot-holds`,
    JSON.stringify({slotId, orderId}),
    {headers: headers(customerId, `k6-${runId}-${__VU}-hold`)},
  );
  if (holdResponse.status === 201) {
    holdsCreated.add(1);
    const holdId = holdResponse.json('holdId');
    if (!holdId) return unexpected(holdResponse, 'retención sin holdId');
    const confirmResponse = http.post(
      `${__ENV.BASE_URL || 'http://api:8080'}/api/v1/slot-holds/${holdId}/confirm`,
      null,
      {headers: headers(customerId, `k6-${runId}-${__VU}-confirm`)},
    );
    if (check(confirmResponse, {'confirmación creada': response => response.status === 201})) bookingsConfirmed.add(1);
    else unexpected(confirmResponse, 'confirmación');
  } else if (holdResponse.status === 409 && holdResponse.json('code') === 'SLOT_FULL') {
    slotFull.add(1);
  } else {
    unexpected(holdResponse, 'retención');
  }
}

function metricCount(data, name) { return data.metrics[name] ? data.metrics[name].values.count : 0; }

export function handleSummary(data) {
  const p95Ms = data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'] : 0;
  const p99Ms = data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(99)'] : 0;
  const summary = {runId, slotId, vus, initialFree, expected, holdsCreated: metricCount(data, 'holds_created'), bookingsConfirmed: metricCount(data, 'bookings_confirmed'), slotFull: metricCount(data, 'slot_full'), unexpected: metricCount(data, 'unexpected_responses'), p95Ms, p99Ms};
  const text = `Resumen venta relámpago\nRUN_ID=${runId} SLOT_ID=${slotId}\nVUs=${vus} cupo inicial=${initialFree} esperado=${expected}\nretenciones=${summary.holdsCreated} confirmaciones=${summary.bookingsConfirmed} SLOT_FULL=${summary.slotFull}\ninesperadas=${summary.unexpected} p95=${p95Ms}ms p99=${p99Ms}ms\n`;
  return {'/results/flash-sale-summary.json': JSON.stringify(summary, null, 2), stdout: text};
}
