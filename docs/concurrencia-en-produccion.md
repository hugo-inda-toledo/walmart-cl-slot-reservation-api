# Concurrencia en producción

Este documento separa el mecanismo que protege la capacidad **implementado hoy** de los controles operacionales que se recomiendan al operar el servicio a escala. PostgreSQL es la fuente de verdad: Valkey acelera lecturas de disponibilidad, pero no participa en una decisión de capacidad.

## 1. Invariante y estrategia actual

Para cada ventana se conserva la invariante `reserved >= 0`, `held >= 0` y `reserved + held <= capacity`. Un hold activo cuenta en `held`; una reserva confirmada cuenta en `reserved`.

La creación de un hold ejecuta en PostgreSQL un `UPDATE` condicional que incrementa `held` solo cuando aún hay cupo y el corte no ha pasado. La cantidad de filas afectadas decide si se obtuvo el cupo. El `CHECK catalog_delivery_slot_capacity_check` es la defensa de integridad adicional para todos los escritores. Confirmar mueve un cupo de `held` a `reserved`; liberar, vencer o cancelar lo devuelve, siempre con guardas atómicas.

El hold y su vencimiento se persisten en la misma transacción; `created_at` y `expires_at` se calculan con `now()` de PostgreSQL, no con el reloj de una instancia. El sweeper toma lotes vencidos con `FOR UPDATE SKIP LOCKED`, marca cada hold `EXPIRED` y libera el cupo en la misma transacción. Así varias instancias procesan lotes disjuntos. La carrera entre confirmar y vencer tiene un único ganador porque la transición de estado está guardada.

La idempotencia también se persiste en PostgreSQL: la fila única por cliente y clave, el efecto de negocio y el snapshot de respuesta se confirman o revierten juntos. Un reintento equivalente recibe el resultado almacenado; una clave reutilizada con otro fingerprint no se ejecuta. La espera de una misma clave usa un `lock_timeout` local; al agotarse devuelve `IDEMPOTENCY_REQUEST_IN_PROGRESS` con `Retry-After`.

Las decisiones están detalladas en [ADR-0003: estrategia anti sobre-reserva](adr/reservation-anti-sobre-reserva.md) y [ADR-0004: idempotencia en PostgreSQL](adr/reservation-idempotencia-en-postgresql.md). La evidencia de integración contra PostgreSQL real incluye:

- `CapacityRaceIT`: 200 clientes simultáneos contra capacidad 5; solo cinco reciben `201` y el resto `SLOT_FULL`.
- `ConfirmVersusExpiryRaceIT`: en 50 carreras, exactamente una de confirmación o vencimiento mueve el cupo.
- `IdempotentRetryRaceIT`: reintentos concurrentes con la misma clave preservan un solo resultado.
- `ConcurrentSweeperIT`: varios sweepers no vencen ni devuelven dos veces el mismo hold.
- `ConcurrentReleaseAndCancelIT`: liberar y cancelar concurrentemente mantienen los contadores.

## 2. Por qué no locks distribuidos ni holds solo en caché

No se usa lock distribuido por ventana. Agregaría otro sistema que debe mantener exclusión, expiración y recuperación ante caídas, pero no refuerza la invariante si un escritor de PostgreSQL no coopera. El `UPDATE` condicional y el `CHECK` protegen la fila compartida desde cualquier instancia sin afinidad ni lock externo.

Un hold solo con TTL en Valkey separaría el cupo, el hold y su transición a reserva entre almacenes. Una caída entre escrituras deja un *dual-write gap*: se puede perder capacidad o admitir sobre-reserva; además, un TTL no puede mover atómicamente `held` a `reserved`. Por eso el hold, los contadores y la idempotencia viven en la misma transacción de PostgreSQL. Valkey puede caer sin cambiar esa corrección.

## 3. Escalado horizontal y hot slots

La estrategia actual funciona con varias instancias porque todas compiten por la misma actualización condicionada en el primario. El costo se concentra en una ventana muy demandada: esa fila puede convertirse en un *hot slot* y aumentar esperas y latencia, aunque no rompe la invariante.

Para producción se mide primero la contención y, si domina la latencia, se consideran estas mitigaciones según el patrón de demanda:

- Partir una capacidad grande en cubetas asignables y sumar los resultados de manera controlada; aumenta complejidad de asignación y no debe permitir que el total de cubetas supere la capacidad.
- Encolar por ventana o usar *admission control* para limitar solicitudes concurrentes por `slotId`; el consumidor conserva la actualización condicionada como autoridad final.
- Devolver rápidamente la alternativa o `SLOT_FULL` cuando no hay cupo, en vez de amplificar una oleada con reintentos inmediatos.

Kubernetes u otro orquestador puede añadir instancias, pero no elimina la contención de una fila caliente; la decisión de capacidad sigue en PostgreSQL.

## 4. Réplicas de lectura y caché como apoyo

Las réplicas y la caché sirven para catálogo y disponibilidad con consistencia eventual. La API ya usa Valkey para la caché de disponibilidad y la invalida después del commit; su indisponibilidad no revierte la operación ni degrada `readiness`, que solo requiere `db`.

Una lectura desde caché o réplica puede estar atrasada. Por eso jamás autoriza un hold, confirmación, liberación ni cancelación: cada decisión de capacidad se evalúa en el primario con la actualización condicionada. En producción se debe comunicar esa eventualidad al cliente como disponibilidad informativa, no como garantía de cupo.

## 5. Controles recomendados para operar

Estos controles son recomendaciones; no implican que estén implementados en la entrega:

- **Outbox:** guardar un evento de dominio en la misma transacción y publicarlo de forma asíncrona. Evita un dual write si después se notifican reservas o se actualizan sistemas externos.
- **Reintentos:** aplicar backoff exponencial con jitter, un presupuesto acotado y solo a errores transitorios seguros. Respetar `Retry-After` de `IDEMPOTENCY_REQUEST_IN_PROGRESS`; no reintentar indefinidamente `SLOT_FULL`.
- **Timeouts y pool:** dimensionar Hikari según conexiones disponibles, observar `hikaricp.*`, y establecer `statement_timeout` y `lock_timeout` adecuados para cada operación. Hoy existe el `lock_timeout` local de idempotencia; `statement_timeout` y el ajuste operacional del pool deben definirse para producción.
- **Rate limiting y tormentas:** limitar por cliente y por ventana, devolver `429`/`Retry-After` cuando corresponda, usar circuit breaker hacia dependencias opcionales y evitar reintentos sincronizados. No hay rate limiting implementado en esta entrega.

## 6. Observabilidad y alertas

Las siguientes son métricas que el código expone hoy mediante Micrometer; sus nombres y etiquetas no son propuestas:

| Métrica real | Qué muestra | Alerta sugerida |
| --- | --- | --- |
| `reservation.holds.rejected{code="SLOT_FULL"}` | Rechazos al intentar crear un hold por ventana llena. | Aumento anómalo por ventana o región, correlacionado con latencia. |
| `reservation.holds{event="expired"}` | Holds vencidos liberados por el sweeper. | Crecimiento sostenido o ausencia inesperada junto con holds activos vencidos. |
| `reservation.idempotency{result}` | Resultados `replayed`, `key_reused` e `in_progress`. | Pico de `in_progress` o `key_reused`, señal de clientes/reintentos defectuosos. |
| `catalog.availability.cache.requests{result}` | Operaciones de caché con `hit`, `miss`, `error` o `skipped`. | `error`/`skipped` sostenidos; no se usa para decidir capacidad. |
| `hikaricp.*` | Métricas que Micrometer publica para el pool Hikari. | Pool activo/pending cerca del límite o agotamiento de conexiones. |

Además de alertar, los tableros deben cruzar estas señales con `http.server.requests`, errores de PostgreSQL, saturación de conexiones y la latencia de hold/confirmación. Las métricas de negocio ayudan a distinguir capacidad realmente agotada de una degradación de infraestructura.

## 7. Pruebas de carga

El repositorio incluye una venta relámpago en k6: `./load/run-flash-sale.sh`. Selecciona una ventana con cupo, ejecuta por defecto 300 usuarios virtuales, cada uno con cliente, orden y clave de idempotencia diferentes, crea y confirma el hold, y termina verificando en PostgreSQL que no existan ventanas sobre capacidad. El escenario acepta solo `201` o `409 SLOT_FULL` para el hold y falla ante respuestas inesperadas.

Para escalar la prueba se incrementa `VUS` y se repite con ventanas/capacidades controladas, observando p95/p99, rechazos, `hikaricp.*`, CPU y conexiones de PostgreSQL. Una prueba de carga no sustituye las carreras deterministas: se ejecutan juntas contra el entorno objetivo antes de ampliar capacidad.

## 8. Modos de falla

| Falla | Comportamiento actual | Operación recomendada |
| --- | --- | --- |
| Valkey caído | La caché de disponibilidad falla de forma degradada; PostgreSQL sigue decidiendo capacidad y `readiness` no depende de Valkey. | Alertar `error`/`skipped`, proteger el primario de la mayor carga de lectura y recuperar la caché sin reconstruir capacidad. |
| Failover de PostgreSQL | Durante el failover las transacciones en curso pueden fallar o revertirse; no se debe confirmar un resultado no confirmado. | Reintentar solo operaciones idempotentes con la misma clave y backoff+jitter tras recuperar el primario; validar salud, réplicas y límites de conexión. |
| Desfase de relojes | No cambia la vigencia de holds ni el vencimiento de idempotencia porque esos cálculos usan `now()` de PostgreSQL. | Mantener NTP en nodos y monitorear el desfase para logs, clientes, certificados y tareas no autoritativas. |

## 9. Implementado hoy / Recomendado en producción

| Área | Implementado hoy | Recomendado en producción |
| --- | --- | --- |
| Capacidad | `UPDATE` condicional, `CHECK`, transiciones guardadas y PostgreSQL como fuente de verdad. | Mantener estas garantías y medir contención por ventana. |
| Holds y vencimiento | Hold persistido con `now()` de PostgreSQL; sweeper por lotes con `SKIP LOCKED`. | Ajustar lote/frecuencia con métricas y capacidad real. |
| Idempotencia | `UNIQUE`/reclamo en PostgreSQL, snapshot de respuesta, `lock_timeout` local y `Retry-After`. | Presupuesto de reintentos con backoff+jitter en clientes y borde. |
| Caché y lecturas | Caché Valkey opcional, invalidación después del commit y métricas de caché. | Réplicas/caché solo para lecturas eventualmente consistentes; nunca para autorizar cupo. |
| Escalado | Varias instancias pueden compartir el mismo primario correctamente. | Admission control, cola por ventana o cubetas solo si la contención de hot slots lo justifica. |
| Eventos externos | No hay outbox. | Outbox transaccional y publicador asíncrono antes de integrar efectos externos. |
| Protección de tráfico | No hay rate limiting. | Límites por cliente/ventana, `429` con `Retry-After`, circuit breaker y protección contra tormentas. |
| Pool y timeouts | Métricas `hikaricp.*`; `lock_timeout` local para idempotencia. | Dimensionar Hikari y configurar/medir `statement_timeout` y límites de conexión. |
| Verificación | Cinco pruebas de carrera con PostgreSQL real y k6 con 300 VUs por defecto. | Ejecutarlas contra capacidad objetivo, con pruebas de failover y objetivos de latencia. |
