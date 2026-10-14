# 📚 Referencia por dominio

[⬅️ Volver al README](../README.md)

Detalle funcional de cada contexto: datos geográficos, catálogo de ventanas y reservas.

## Datos geográficos

Sección propia de `geo-coverage`. Los datos geográficos se cargan con Flyway (`db/migration/geo/V2_*`) sobre
una base vacía, sin pasos manuales.

**Catálogo DPA** (`V2_2__seed_dpa.sql`): 16 regiones, 56 provincias y 346 comunas con su Código Único
Territorial (CUT de 5 dígitos, `RRPCC`), según el Decreto Exento 817/2010 y la Ley 21.033 / DTO 1115/2018
(Región de Ñuble).

**Límites comunales** (`V2_3__seed_comuna_boundaries.sql`): polígonos de las 15 comunas de Coquimbo (04) y de
las 52 de la Región Metropolitana (13); las comunas de las otras 14 regiones no tienen límite.

- **Fuente:** Biblioteca del Congreso Nacional de Chile (BCN), Mapoteca SIIT — "División comunal: polígonos de
  las comunas de Chile" (<https://www.bcn.cl/siit/mapas_vectoriales>).
- **Condición de uso:** uso libre citando la fuente: "Fuente: Biblioteca del Congreso Nacional de Chile".
- **Carácter referencial:** límites simplificados (8 % de los vértices, topología de arcos compartidos
  preservada) y reproyectados a WGS 84 (EPSG:4326) con 6 decimales. No tienen precisión geodésica ni validez
  legal; las direcciones en bordes se resuelven con la tolerancia `app.geo.border-tolerance-meters`.

Regeneración (requiere `curl`, `unzip` y Node con `npx`; no forma parte de `./mvnw verify`):

```bash
./scripts/geo/build-comuna-boundaries.sh /tmp/comuna-boundaries.sql
```

El script descarga el shapefile de la BCN desde una URL fijada, verifica su SHA-256 (falla de forma explícita
si la BCN cambió el archivo), simplifica con `mapshaper@0.7.68` y escribe un SQL determinista: la misma entrada
produce el mismo archivo byte a byte, con exactamente las 67 comunas de `scripts/geo/expected-comunas-04-13.txt`.
Si los datos cambian, la salida se versiona como una migración `V2_<n>` nueva; nunca se edita `V2_3`.

**Zonas de despacho demo** (`V2_4__seed_delivery_zones.sql`). Sus UUID y códigos son un contrato publicado:
son idénticos en toda instalación y otros contextos pueden referenciarlos en sus propios datos semilla.

| UUID | Código | Nombre | Estado | Nodo de despacho | Comunas completas | Áreas propias |
|------|--------|--------|--------|------------------|-------------------|---------------|
| `5f1c0e2a-0000-4000-8000-000000000401` | `LA_SERENA_COQUIMBO` | La Serena-Coquimbo | `ACTIVE` | `CD-LSR` CD La Serena | La Serena (04101), Coquimbo (04102) | exclusión "Tongoy-Guanaqueros rural" (dentro de Coquimbo) |
| `5f1c0e2a-0000-4000-8000-000000001301` | `SANTIAGO_CENTRO` | Santiago Centro | `ACTIVE` | `CD-STGO` CD Santiago | Santiago (13101), Estación Central (13106), Independencia (13108), Recoleta (13127) | — |
| `5f1c0e2a-0000-4000-8000-000000001302` | `SANTIAGO_ORIENTE` | Santiago Oriente | `ACTIVE` | `CD-STGO` CD Santiago | Las Condes (13114), Ñuñoa (13120), Providencia (13123), Vitacura (13132) | — |
| `5f1c0e2a-0000-4000-8000-000000001303` | `CHICUREO` | Chicureo | `ACTIVE` | `CD-STGO` CD Santiago | — | inclusión "Chicureo" (dentro de Colina, 13301) |
| `5f1c0e2a-0000-4000-8000-000000001304` | `MAIPU` | Maipú | `INACTIVE` | `CD-STGO` CD Santiago | Maipú (13119) | — |

**Direcciones demo** (`V2_5__seed_demo_addresses.sql`), con el resultado de cobertura esperado de cada una:

| Id | Dirección | Comuna declarada | Coordenadas (lat, lng) | Resultado esperado |
|----|-----------|------------------|------------------------|--------------------|
| `la-serena-monjitas` | Monjitas 2334 | La Serena (04101) | -29.904500, -71.248900 | cubierta, `LA_SERENA_COQUIMBO`, `COMUNA_BOUNDARY` |
| `santiago-monjitas` | Monjitas 879 | Santiago (13101) | -33.437200, -70.645000 | cubierta, `SANTIAGO_CENTRO`, `COMUNA_BOUNDARY` |
| `providencia-av` | Av. Providencia 1234 | Providencia (13123) | -33.429000, -70.612000 | cubierta, `SANTIAGO_ORIENTE`, `COMUNA_BOUNDARY` |
| `chicureo` | Camino Chicureo 1500 | Colina (13301) | -33.285000, -70.650000 | cubierta, `CHICUREO`, `ZONE_AREA` (sub-zona) |
| `la-serena-borde` | Av. del Mar 2100 | La Serena (04101) | -29.910000, -71.276400 | cubierta, `LA_SERENA_COQUIMBO`, `NEAREST_COMUNA` (en el mar, ~150 m del límite) |
| `coquimbo-sin-coordenadas` | Aldunate 350 | Coquimbo (04102) | — | cubierta, `LA_SERENA_COQUIMBO`, `COMUNA_CODE` |
| `vina-sin-cobertura` | Av. Libertad 100 | Viña del Mar (05109) | — | cubierta por la cobertura nacional de `slot-reservation` (`LIDER_REGION_05`, `COMUNA_CODE`) |
| `maipu-inactiva` | Av. Pajaritos 2000 | Maipú (13119) | -33.510000, -70.757000 | no cubierta, `ZONE_INACTIVE` |
| `tongoy-excluida` | Av. Costanera 100 | Coquimbo (04102) | -30.260000, -71.485000 | no cubierta, `EXCLUDED_AREA` |

Las coordenadas son referenciales: se eligieron contra los límites simplificados y las áreas demo, no
provienen de un geocodificador.

## Catálogo de ventanas

Sección propia de `delivery-slot-catalog` (contexto `catalog`, migraciones `V3_*`). El catálogo genera y
expone las ventanas de despacho (zona + fecha + bloque horario con capacidad) y es el **dueño del esquema**;
solo `slot-reservation` modifica los contadores `reserved` y `held`.

**Endpoints** (contrato en `openapi.yml`, tag `catalog`):

| Operación | Ruta | Resumen |
|-----------|------|---------|
| `listDeliveryDates` | `GET /api/v1/delivery-dates?comunaCode=&latitude=&longitude=` | siete fechas (hoy + 6) con etiqueta y estado `AVAILABLE`/`SOLD_OUT`/`NO_SERVICE` |
| `listDeliverySlots` | `GET /api/v1/delivery-dates/{date}/slots?comunaCode=...` | bloques de la fecha con precio, capacidad restante y estado |

**Plantillas demo** (`V3_3__seed_slot_templates.sql`). Sus UUID son un contrato publicado, idénticos en toda
instalación: `5f1c0e2a-0000-4000-8003-ZZZZDDHHhh00` con `ZZZZ` = sufijo del UUID de la zona de geo, `DD` = día ISO
(01 lunes … 07 domingo) y `HH`/`hh` = hora local de inicio/término. Los bloques duran 2 h salvo Chicureo (3 h).

| Zona | Días | Bloques | Capacidad | Precio CLP |
|------|------|---------|-----------|------------|
| La Serena-Coquimbo (`…0401`) | lunes a domingo | 16–18, 17–19, 18–20, 19–21 | 8 | 2.990 |
| Santiago Centro (`…1301`) | lunes a sábado | 9–11, 11–13, 15–17, 18–20 | 10 | 3.490 |
| Santiago Oriente (`…1302`) | lunes a domingo | 10–12, 14–16, 19–21 | 6 | 1.990 |
| Chicureo (`…1303`) | lunes a viernes | 10–13, 15–18 | 4 | 4.990 |
| Maipú (`…1304`, inactiva) | — (sin plantillas) | — | — | — |

**Feriados** (`V3_2__seed_holidays.sql`): feriados legales nacionales de Chile 2026–2027, con su fuente
(calendario de feriados.cl respaldado por las leyes de la Biblioteca del Congreso Nacional, consultado el
2026-10-11) citada en la cabecera de la migración. `catalog_holiday.zone_id` nulo = feriado nacional; con valor =
bloqueo de esa zona. Un día cerrado se reporta `NO_SERVICE` y no se generan ventanas. Los feriados posteriores se
agregan con una migración `V3_<n>` nueva.

**Modo demo** (`app.catalog.demo.enabled`, `false` por defecto y `true` en el perfil `local`): tras cada
generación agota idempotentemente un día futuro de la zona demo (La Serena-Coquimbo, a `sold-out-day-offset`
días), igualando `capacity` a `reserved + held` en sus ventanas aún abiertas (`cutoff_at` futuro) para que la web muestre una fecha `SOLD_OUT` sin reservar.
Nunca toca `reserved`/`held`.

**Configuración `app.catalog.*`** (todas con valor por defecto):

| Propiedad | Default | Efecto |
|-----------|---------|--------|
| `display-days` | `7` | fechas que muestra el selector (≤ `generation.horizon-days`) |
| `generation.horizon-days` | `14` | días hacia adelante que se generan |
| `generation.cron` | `0 15 0 * * *` | generación diaria, zona `America/Santiago` |
| `generation.run-on-startup` | `true` | genera también al quedar lista la aplicación |
| `cutoff-lead-time` | `PT2H` | anticipación del cierre antes del inicio del bloque |
| `availability-cache.enabled` | `true` | `false` usa una caché no-op (mismas respuestas) |
| `availability-cache.ttl` | `PT30S` | vigencia de cada zona-fecha en Valkey |
| `availability-cache.failure-backoff` | `PT5S` | pausa de la caché tras una falla de Valkey |
| `demo.enabled` / `demo.zone-id` / `demo.sold-out-day-offset` | `false` / La Serena / `2` | modo demo |

**Contrato para `slot-reservation`**

- **Tabla `catalog_delivery_slot`**: `id`, `template_id`, `zone_id`, `delivery_date`, `service_type`, `start_at`,
  `end_at`, `cutoff_at`, `price_clp`, `capacity`, `reserved`, `held`, `version`, `created_at`; única por
  `(template_id, delivery_date)`.
- **Invariante de capacidad**: `CHECK catalog_delivery_slot_capacity_check (reserved >= 0 AND held >= 0 AND
  reserved + held <= capacity)`. Es la última línea de defensa ante cualquier escritor.
- **Cierre**: `cutoff_at` (inicio − `cutoff-lead-time`); pasado el cierre la ventana no se puede tomar.
- **Versión**: un trigger incrementa `version` en cada cambio de `capacity`, `reserved` o `held`, sin depender de
  la disciplina del escritor.
- **Toma atómica de referencia** (0 filas ⇒ agotada o cerrada; distinguir con `SlotQueryPort.findSlot`):

  ```sql
  UPDATE catalog_delivery_slot SET held = held + 1
  WHERE id = :id AND reserved + held < capacity AND cutoff_at > now()
  RETURNING zone_id, delivery_date;
  ```

- **Puertos publicados** (`cl.walmart.slot.reservation.domain.port.catalog`, único punto de contacto; lo
  verifica `CatalogBoundaryRulesTest`):
  - `SlotQueryPort`: `findSlot(slotId)` y `findAvailableSlots(zoneId, from, to)`; leen siempre de PostgreSQL.
  - `AvailabilityCacheInvalidationPort.invalidate(zoneId, deliveryDate)`: reservas lo llama **después del commit**
    de cada cambio de contadores, con los valores del `RETURNING`. Nunca propaga una falla de caché.
- **Clave de caché**: `catalog:availability:v1:{zoneId}:{yyyy-MM-dd}` (JSON de la zona-fecha, TTL
  `availability-cache.ttl`). La escribe `catalog` y la invalida `reservation`; si Valkey cae, las respuestas son
  las mismas (PostgreSQL es la única fuente de verdad).

## Reservas de ventanas

El contexto `reservation` toma holds temporales, los confirma como reservas y devuelve capacidad al liberar,
vencer o cancelar. PostgreSQL es la fuente de verdad de los contadores y de la idempotencia; Valkey no participa
en estas decisiones. Las decisiones de concurrencia están documentadas en los ADRs
[Estrategia anti sobre-reserva](adr/reservation-anti-sobre-reserva.md) e
[Idempotencia en PostgreSQL y no en Valkey](adr/reservation-idempotencia-en-postgresql.md).

**Endpoints** (contrato completo en `src/main/resources/openapi/openapi.yml`, tag `reservation`):

| Operación | Ruta | Headers requeridos | Respuesta exitosa |
|-----------|------|--------------------|-------------------|
| Crear retención | `POST /api/v1/slot-holds` | `X-Customer-Id`, `Idempotency-Key` | 201 `ReservationSlotHold` + `Location` |
| Consultar retención | `GET /api/v1/slot-holds/{holdId}` | `X-Customer-Id` | 200 `ReservationSlotHold` |
| Liberar retención | `DELETE /api/v1/slot-holds/{holdId}` | `X-Customer-Id` | 204 |
| Confirmar retención | `POST /api/v1/slot-holds/{holdId}/confirm` | `X-Customer-Id`, `Idempotency-Key` | 201 `ReservationBooking` + `Location` |
| Consultar reserva | `GET /api/v1/reservations/{reservationId}` | `X-Customer-Id` | 200 `ReservationBooking` |
| Cancelar reserva | `POST /api/v1/reservations/{reservationId}/cancel` | `X-Customer-Id` | 200 `ReservationBooking` |

`X-Customer-Id` identifica al cliente y nunca forma parte del body. `Idempotency-Key` es obligatorio solo al
crear y confirmar; reutilizarlo con otra operación o contenido da `IDEMPOTENCY_KEY_REUSED` (422). Si otra
solicitud con la misma clave continúa en curso después de la espera configurada, la API devuelve
`IDEMPOTENCY_REQUEST_IN_PROGRESS` (409) y `Retry-After`; reintenta con la misma clave. Las respuestas de error
son `application/problem+json`, con `code`, `correlationId` y `X-Correlation-Id`.

Los códigos de negocio más relevantes son `SLOT_FULL` (409, con `alternatives`), `ACTIVE_HOLD_EXISTS` (409),
`ORDER_ALREADY_RESERVED` (409), `HOLD_EXPIRED` (409), `HOLD_NOT_ACTIVE` (409), `SLOT_CUTOFF_PASSED` (422) y
`RESERVATION_CANCELLATION_CLOSED` (422). Las entradas inválidas, incluidos headers ausentes o inválidos, usan
`VALIDATION_ERROR` (400). Los recursos inexistentes o ajenos responden 404 (`SLOT_NOT_FOUND`,
`HOLD_NOT_FOUND`, `RESERVATION_NOT_FOUND`) y una ventana sin servicio, `SLOT_NO_SERVICE` (422). Consulta el contrato publicado para la lista completa de errores y sus detalles.

**Configuración `app.reservation.*`** (valores por defecto):

| Propiedad | Default | Propósito |
|-----------|---------|-----------|
| `hold-ttl` | `PT10M` | duración de una retención activa |
| `alternatives.max` | `3` | alternativas cuando la ventana está llena |
| `sweeper.enabled` / `sweeper.fixed-delay` / `sweeper.batch-size` / `sweeper.max-batches-per-run` | `true` / `PT15S` / `500` / `20` | liberación programada de holds vencidos |
| `idempotency.retention` | `PT24H` | tiempo de retención de resultados idempotentes |
| `idempotency.wait-timeout` / `idempotency.retry-after-seconds` | `PT5S` / `1` | espera máxima por clave en curso y sugerencia de reintento |
| `idempotency.purge-enabled` / `idempotency.purge-cron` / `idempotency.purge-batch-size` | `true` / `0 30 * * * *` / `1000` | purga programada de claves vencidas |

En el perfil `test`, sweeper y purga quedan deshabilitados para que cada prueba controle el vencimiento de forma
explícita. Para ejecutar los tests de concurrencia contra PostgreSQL real (Testcontainers), desde la raíz del
repo de la API ejecuta:

```bash
./mvnw -B clean verify
```

La suite incluye `CapacityRaceIT` (200 peticiones contra capacidad 5), `IdempotentRetryRaceIT`,
`ConfirmVersusExpiryRaceIT`, `ConcurrentSweeperIT` y `ConcurrentReleaseAndCancelIT`. No requiere
`docker compose up`: los tests levantan sus propios contenedores. En una instalación de Docker no estándar,
exporta las variables de Testcontainers indicadas en [Antes de empezar](../README.md#-antes-de-empezar) antes de ejecutar el comando.

La configuración CORS expone `X-Correlation-Id`, `Retry-After` y `Location`, por lo que la web puede leer el
intervalo de reintento y las ubicaciones relativas de los recursos creados desde una respuesta cross-origin.
