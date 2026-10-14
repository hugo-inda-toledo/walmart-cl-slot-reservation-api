# 🧑‍💻 Guía de desarrollo

[⬅️ Volver al README](../README.md)

Todo lo necesario para trabajar en el código: modo desarrollo, comandos, infraestructura, perfiles, endpoints de plataforma, convenciones de extensión e integración continua.

## 🧑‍💻 Modo desarrollo

Para iterar sobre el código sin reconstruir la imagen, levanta **solo la infraestructura** y arranca Spring Boot a mano:

```bash
docker compose up -d postgres valkey
./mvnw spring-boot:run   # :8080, perfil local
```

> ⚠️ **Puerto compartido:** el servicio `api` del compose y `spring-boot:run` usan ambos el 8080. Detén `api`
> (`docker compose stop api`) o cambia `API_PORT` en `.env` antes de arrancar Spring Boot local.
> Para recrear la base desde cero: `docker compose down -v && docker compose up -d postgres valkey`.

## 🔨 Comandos de build

| Comando | Efecto |
|---------|--------|
| `./mvnw verify` | build completo: contrato → código generado → unit tests (surefire) → integration tests `*IT` (failsafe, Testcontainers) |
| `./mvnw test` | solo tests unitarios |
| `./mvnw spring-boot:run` | arranque local (perfil `local`) |
| `npx @redocly/cli@2.62.1 lint src/main/resources/openapi/openapi.yml` | lint del contrato (igual que CI) |
| `./scripts/verify-ci.sh` | reproduce localmente los jobs de CI (actionlint, lint del contrato, OSV-Scanner) |

Los tests de integración levantan sus propios contenedores efímeros; no dependen de `docker compose up`.

## 🐳 Infraestructura local

`docker-compose.yml` define:

| Servicio | Imagen | Puerto | Healthcheck |
|----------|--------|--------|-------------|
| `postgres` | `imresamu/postgis:17-3.5-alpine` (arm64 y amd64) | 5432 | `pg_isready` |
| `valkey` | `valkey/valkey:9.0.6-alpine` | 6379 | `valkey-cli ping` |
| `api` | imagen local de este repositorio | 8080 | `/actuator/health/readiness` |

Los datos de PostgreSQL persisten en el volumen nombrado `pgdata`.

> **Solo desarrollo:** las credenciales por defecto (`slot_reservation` / `slot_reservation`, base
> `slot_reservation`) son para uso local y coinciden con `application-local.yml`. No usar fuera de la máquina local.

## ⚙️ Perfiles

| Perfil | Cuándo | Base de datos | Valkey | Flyway `out-of-order` | CORS permitido |
|--------|--------|---------------|--------|-----------------------|----------------|
| `local` (por defecto) | `spring-boot:run` contra `docker compose up -d postgres valkey`, o el servicio `api` por variables de entorno | `jdbc:postgresql://localhost:5432/slot_reservation` (`slot_reservation`/`slot_reservation`) | `localhost:6379` | `true` | `http://localhost:5173` |
| `test` | tests de integración | contenedores efímeros (Testcontainers, propiedades dinámicas) | contenedor efímero | `false` | `http://localhost:5173` |

Valores comunes (`application.yml`): `spring.profiles.default=local`, `spring.jpa.open-in-view=false`,
`spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.validate-on-migrate=true`, logs estructurados ECS,
timeout de Valkey 500 ms, `app.correlation-id.max-length=64`, `app.problem.type-base-uri=urn:slot-reservation:problem:`.

Comportamiento de arranque: si la base de datos no es alcanzable, si una migración falla o fue modificada, o
si PostGIS no está disponible, el arranque aborta identificando la dependencia o versión afectada.
Valkey caído **no** impide el arranque ni deja `readiness` en DOWN (solo `db` participa en readiness).

## 🩺 Endpoints de plataforma

| Ruta | Descripción |
|------|-------------|
| `/swagger-ui.html` | Swagger UI sobre el contrato versionado |
| `/openapi/openapi.yml` | contrato OpenAPI 3.1 (bytes idénticos al archivo versionado) |
| `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | salud |
| `/actuator/metrics` | `http.server.requests`, `hikaricp.*`, `jvm.*` |
| `/actuator/info` | información de la aplicación |

Solo `health`, `info` y `metrics` están expuestos.
Todo error se responde como `application/problem+json` (RFC 7807) con `code`, `correlationId` y `X-Correlation-Id`.

## 🧩 Convenciones de extensión para specs downstream

Las specs de dominio extienden esta plataforma **por adición**, sin cambiar lo existente.

**Contrato (`src/main/resources/openapi/openapi.yml`)**
- Agregar `paths`, `tags` y `components.schemas` propios (con prefijo propio); no editar `Problem`, `FieldError`,
  headers, parámetros ni respuestas comunes.
- Paths con prefijo `/api/v1/`; `operationId` único en camelCase; un tag por operación.
- Respuestas 4xx/5xx vía `components.responses` o `application/problem+json`.
- Instantes `type: string, format: date-time`, fechas `format: date`, montos CLP `type: integer, format: int64`.
- El lint (`redocly.yaml`, también en CI) hace cumplir: prefijo `/api/v1/`, `operationId` único, tag definido y
  respuestas 4xx como `application/problem+json`. El resto de las convenciones de esta sección (camelCase, un tag
  por operación, formatos de fecha/monto, prefijo de schemas) no las verifica una herramienta: las hace cumplir la
  revisión de cada spec.

**Dependencias (`pom.xml`)**
- Agregar dependencias solo dentro del bloque `<!-- domain-dependencies -->`.
- Todo override de versión sobre la gestionada por el BOM debe ir fijado con un comentario con el motivo.

**Configuración (`application*.yml`)**
- Agregar propiedades bajo su propio prefijo `app.<contexto>.*` (p. ej. `app.reservation.*`) con su propio
  `@ConfigurationProperties`; no modificar las claves existentes.

**Código**
- Paquete base `cl.walmart.slot.reservation`, organizado **capa primero** y con el contexto (`geo`, `catalog`,
  `reservation`) como subpaquete final:

  | Paquete | Contenido |
  |---------|-----------|
  | `Application` | clase de arranque |
  | `cmd.config`, `cmd.config.properties` | raíz de composición: `@Configuration` y `@ConfigurationProperties` |
  | `cmd.exception` | mapeo de errores a Problem Details (`GlobalExceptionHandler`, `ProblemErrorController`) |
  | `domain.{entities,enums,exception}.<ctx>` | value objects, agregados, vistas, enums, `ErrorCode` y excepciones |
  | `domain.repository.<ctx>` | interfaces `*Repository` |
  | `domain.service.<ctx>`, `domain.usecase.<ctx>` | interfaces `*Service`/`*UseCase` y políticas de dominio |
  | `domain.port.<ctx>` | puertos publicados entre contextos y puertos de salida |
  | `application.{usecase,service,helper,listener}.<ctx>` | `*UseCaseImpl`, `*ServiceImpl`, helpers y listeners after-commit |
  | `infrastructure.ingoing.{controller,mapper,scheduler}.<ctx>` | adaptadores de entrada (HTTP y tareas programadas) |
  | `infrastructure.ingoing.openapi.{api,model}` | código generado desde `openapi.yml` (no se versiona) |
  | `infrastructure.outgoing.{repository,cache,codec,metrics}.<ctx>` | adaptadores de salida (PostgreSQL, Valkey, JSON, Micrometer) |

  Los tests siguen el paquete de su sujeto; los temáticos viven en `architecture`, `integration.*`, `smoke` y
  `support`. Las reglas ArchUnit (`ArchitectureRulesTest`, `*BoundaryRulesTest`) rompen el build ante violaciones.
- Errores de dominio: implementar `ErrorCode` y lanzar `DomainException` con una `ErrorCategory`
  (`INVALID_INPUT` 400, `NOT_FOUND` 404, `CONFLICT` 409, `BUSINESS_RULE` 422, `UNAVAILABLE` 503).
- Tiempo: inyectar `Clock`; no usar `now()` sin `Clock`.
- Tests de integración: anotar con `@IntegrationTest` (usa `SharedContainers`).

## 🔁 Integración continua

`.github/workflows/ci.yml` ejecuta en push y pull request: build y tests (`./mvnw -B verify`, Corretto 21),
lint del contrato, escaneo de dependencias y un job de stack que ejecuta `./scripts/smoke-local.sh` y la venta relámpago. Los resultados de carga y logs del compose se conservan como artefactos cuando falla.
