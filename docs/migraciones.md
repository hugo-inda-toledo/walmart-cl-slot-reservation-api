# 🗄️ Migraciones de base de datos (Flyway)

[⬅️ Volver al README](../README.md)

Cada spec tiene un rango de versiones reservado y una carpeta propia bajo `src/main/resources/db/migration`:

| Spec | Prefijo de versión | Carpeta |
|------|--------------------|---------|
| `backend-foundation` | `V1_<n>__` | `db/migration/foundation` |
| `geo-coverage` | `V2_<n>__` | `db/migration/geo` |
| `delivery-slot-catalog` | `V3_<n>__` | `db/migration/catalog` |
| `slot-reservation` | `V4_<n>__` | `db/migration/reservation` |

Los datos semilla de dominio usan el mismo rango de su spec.

**Regla de dependencias entre rangos:** una migración solo puede referenciar objetos de su propio rango o de
rangos **menores**. Así el orden por versión en una base vacía (CI, evaluador) siempre es válido, y aplicarla
fuera de orden en local también lo es. La revisión de cada spec hace cumplir esta regla.

`out-of-order=true` solo en `local`: con el volumen persistente, una migración nueva de un rango menor
(p. ej. `V3_4` después de que se aplicó `V4_1`) se aplica igualmente; la validación de checksum sigue activa.
Nunca modifiques una migración ya aplicada: crea una nueva. Para recrear la base local desde cero:

```bash
docker compose down -v && docker compose up -d postgres valkey
```
