# 🔬 Recorrido con curl paso a paso

[⬅️ Volver al README](../README.md)

Con el stack arriba, copia y pega los bloques **en orden, en la misma terminal** (las variables `BASE_URL`, `Q`,
`RUN`, `DATE` y `SLOT_ID` se reutilizan entre pasos). No necesitas `jq`: los valores se extraen con `grep`/`sed`.

**Preparación** (una sola vez):

```bash
BASE_URL=http://localhost:8080
Q='comunaCode=13101&latitude=-33.4372&longitude=-70.6450'   # una dirección de Santiago Centro
RUN=$(date +%s)                                              # hace únicos tus datos en cada corrida
```

### 1. 📍 ¿Qué direcciones de demo existen?

```bash
curl -s "$BASE_URL/api/v1/demo-addresses"
```

Incluye `santiago-monjitas`, `la-serena-monjitas` y `maipu-inactiva`.

### 2. 🗺️ Consultar una dirección cubierta

```bash
curl -s "$BASE_URL/api/v1/delivery-dates?$Q"
```

✅ Esperado: la zona `SANTIAGO_CENTRO` y una lista de fechas con `"status":"AVAILABLE"`.

### 3. 🈵 Ver una fecha agotada

```bash
curl -s "$BASE_URL/api/v1/delivery-dates?comunaCode=04101&latitude=-29.9045&longitude=-71.2489"
```

✅ Esperado: La Serena trae al menos una fecha con `"status":"SOLD_OUT"`.

### 4. 🎯 Elegir la primera fecha y bloque disponibles

```bash
DATE=$(curl -s "$BASE_URL/api/v1/delivery-dates?$Q" | grep -o '"date":"[0-9-]*","label":"[^"]*","status":"AVAILABLE"' | head -1 | sed 's/"date":"\([0-9-]*\)".*/\1/')
SLOT_ID=$(curl -s "$BASE_URL/api/v1/delivery-dates/$DATE/slots?$Q" | grep -o '"slotId":"[^"]*"[^}]*"status":"AVAILABLE"' | head -1 | cut -d'"' -f4)
echo "$DATE $SLOT_ID"
```

✅ Esperado: una línea con una fecha (`2026-10-14`) y un identificador de bloque.

### 5. ⏳ Retener la ventana (hold)

Reservar es un proceso de dos tiempos: primero **retienes** el cupo por unos minutos y luego lo **confirmas**.

```bash
curl -s -X POST "$BASE_URL/api/v1/slot-holds" -H 'Content-Type: application/json' \
  -H "X-Customer-Id: evaluador-$RUN" -H "Idempotency-Key: hold-$RUN" \
  -d "{\"slotId\":\"$SLOT_ID\",\"orderId\":\"orden-$RUN\"}"
```

✅ Esperado: HTTP **201** con `holdId`, `status: ACTIVE` y `expiresAt`. **Copia el `holdId`.**

💡 Si repites exactamente este comando (misma `Idempotency-Key`), recibes el **mismo** `holdId`: no se duplica la reserva.

### 6. ✅ Confirmar la reserva

Reemplaza `<holdId>` por el valor del paso anterior:

```bash
curl -s -X POST "$BASE_URL/api/v1/slot-holds/<holdId>/confirm" \
  -H "X-Customer-Id: evaluador-$RUN" -H "Idempotency-Key: confirm-$RUN"
```

✅ Esperado: HTTP **201** con `reservationId` y `status: CONFIRMED`.

### 7. 🏃‍♂️ Llenar la ventana con 25 clientes a la vez

```bash
for i in $(seq 1 25); do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE_URL/api/v1/slot-holds" \
    -H 'Content-Type: application/json' -H "X-Customer-Id: carga-$RUN-$i" \
    -H "Idempotency-Key: carga-$RUN-$i" -d "{\"slotId\":\"$SLOT_ID\",\"orderId\":\"orden-carga-$RUN-$i\"}"
done | sort | uniq -c
```

✅ Esperado: unas pocas líneas `201` (exactamente el cupo que quedaba) y el resto `409`. **Nunca más de lo disponible.**

### 8. 🚫 Ver el rechazo con alternativas

```bash
curl -s -X POST "$BASE_URL/api/v1/slot-holds" -H 'Content-Type: application/json' \
  -H "X-Customer-Id: ultimo-$RUN" -H "Idempotency-Key: ultimo-$RUN" \
  -d "{\"slotId\":\"$SLOT_ID\",\"orderId\":\"orden-ultimo-$RUN\"}"
```

✅ Esperado: HTTP **409** con `SLOT_FULL` y una lista `alternatives` de otros bloques.

### 9. 📭 Dirección sin cobertura

```bash
curl -s -X POST "$BASE_URL/api/v1/coverage/resolve" -H 'Content-Type: application/json' \
  -d '{"street":"Av. Pajaritos","number":"2000","comunaCode":"13119"}'
```

✅ Esperado: HTTP **422** con `ADDRESS_NOT_COVERED` y `reason: ZONE_INACTIVE` (Maipú está inactiva a propósito).

> ℹ️ `vina-sin-cobertura` hoy **sí** está cubierta por el catálogo nacional (`LIDER_REGION_05`); para probar "sin cobertura"
> usa `maipu-inactiva`.

---
