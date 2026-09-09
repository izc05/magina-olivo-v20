# Mágina Olivo V20 — Mi Campo Data Contract

Estado: diseño técnico del candidato V20. No implica migración ni backend activo todavía.

## 1. Principio de producto

En la interfaz el agricultor trabaja con **Fincas**. Una finca es la unidad que la familia conoce y gestiona (`Las Cenillas`, `El Cerrillo`, etc.). Catastro y SIGPAC son referencias técnicas asociadas y nunca deben sustituir esa entidad de usuario.

Regla central:

> Registrar una vez; consultar el resultado en todos los lugares relacionados.

Ejemplo:

```text
Registrar riego · 22 € · próximo 14/09
        │
        ├── activity: riego
        ├── cost_entry: 22 €
        ├── scheduled_event: 14/09
        └── reminders: según preferencias

Consultas derivadas:
Historia · Riegos · Gastos · Calendario
```

No existe una segunda entrada manual del mismo coste ni una copia separada del registro para Historia.

---

## 2. Entidades canónicas

### 2.1 `fields`

Representa la **Finca** visible para el agricultor.

Campos recomendados:

```text
id UUID PK
holding_id UUID NOT NULL
name TEXT NOT NULL
description TEXT NULL
municipality TEXT NULL
province TEXT NULL
geometry GEOMETRY(MultiPolygon, 4326) NULL
calculated_area_ha NUMERIC NULL
tree_count INTEGER NULL
crop TEXT DEFAULT 'olivar'
variety TEXT NULL
water_regime TEXT NULL        -- secano | regadio | mixto
planting_year INTEGER NULL
tenure_type TEXT NULL
status TEXT DEFAULT 'active'
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

UX: el alta mínima solo necesita `name` y, para olivar, `tree_count`. El resto puede completarse después.

### 2.2 `field_land_refs`

Vincula una finca con referencias administrativas o geometrías auxiliares.

```text
id UUID PK
field_id UUID FK fields
source TEXT NOT NULL          -- catastro | sigpac | manual
reference TEXT NULL
geometry GEOMETRY(MultiPolygon, 4326) NULL
area_ha NUMERIC NULL
status TEXT NOT NULL          -- pending | linked | verified
metadata_json JSONB NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Una finca puede tener **0..N** referencias. Esto permite:
- una finca familiar que ocupa parte de una parcela oficial;
- una finca que agrupa varias parcelas;
- varios recintos SIGPAC dentro de una misma finca.

Nunca imponer relación 1:1 entre finca y parcela catastral.

### 2.3 `campaigns`

```text
id UUID PK
holding_id UUID NOT NULL
name TEXT NOT NULL            -- 2026/27
start_date DATE NOT NULL
end_date DATE NULL
status TEXT NOT NULL          -- planned | active | closed
created_at TIMESTAMPTZ NOT NULL
```

### 2.4 `activities`

Registro canónico de todo lo realizado o observado.

```text
id UUID PK
holding_id UUID NOT NULL
field_id UUID NOT NULL
campaign_id UUID NULL
type TEXT NOT NULL
occurred_at TIMESTAMPTZ NOT NULL
notes TEXT NULL
payload_json JSONB NOT NULL DEFAULT '{}'
source TEXT NOT NULL          -- manual | ocr | import | api
created_by UUID NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Tipos iniciales:

```text
harvest
irrigation
treatment
fertilization
pruning
labor
machinery
expense
observation
```

`payload_json` contiene los campos específicos sin inflar la tabla base. Debe validarse en API mediante schemas versionados por tipo.

Ejemplos:

```json
{
  "type": "irrigation",
  "payload": {
    "duration_hours": 2,
    "water_m3": 12
  }
}
```

```json
{
  "type": "treatment",
  "payload": {
    "reason": "Mosca del olivo",
    "product": "...",
    "dose": "250 ml / 100 L",
    "quantity": "1.5 L"
  }
}
```

### 2.5 `cost_entries`

Libro canónico de costes.

```text
id UUID PK
holding_id UUID NOT NULL
field_id UUID NOT NULL
campaign_id UUID NULL
activity_id UUID NULL
occurred_on DATE NOT NULL
category TEXT NOT NULL
amount_eur NUMERIC(12,2) NOT NULL
description TEXT NULL
source TEXT NOT NULL          -- activity | manual
created_at TIMESTAMPTZ NOT NULL
```

Restricción recomendada:

```text
UNIQUE(activity_id) WHERE activity_id IS NOT NULL
```

Si un formulario de riego incluye 22 €, la transacción crea o actualiza **un único** `cost_entry` ligado a la actividad. El usuario no vuelve a introducirlo en Gastos.

`expense` crea una actividad + un `cost_entry` manual asociado, manteniendo Historia y Gastos sincronizados.

### 2.6 `scheduled_events`

```text
id UUID PK
holding_id UUID NOT NULL
field_id UUID NULL
activity_id UUID NULL
title TEXT NOT NULL
scheduled_at TIMESTAMPTZ NOT NULL
status TEXT NOT NULL          -- planned | completed | postponed | cancelled
source TEXT NOT NULL          -- manual | activity_followup | smart
metadata_json JSONB NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Si el usuario registra una actividad y marca «próximo riego» o «revisar resultado», se crea un evento `activity_followup`.

### 2.7 `reminders`

```text
id UUID PK
scheduled_event_id UUID NOT NULL
channel TEXT NOT NULL         -- in_app | push
remind_at TIMESTAMPTZ NOT NULL
status TEXT NOT NULL          -- pending | sent | failed | cancelled
sent_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ NOT NULL
```

No pedir permiso push durante onboarding. Solo cuando el usuario active una función que lo necesite.

### 2.8 `attachments`

```text
id UUID PK
field_id UUID NOT NULL
activity_id UUID NULL
kind TEXT NOT NULL            -- photo | ticket | invoice | document
storage_key TEXT NOT NULL
mime_type TEXT NULL
caption TEXT NULL
created_by UUID NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

La foto/ticket no se almacena dentro del JSON de actividad.

### 2.9 `harvest_deliveries`

Necesaria porque un albarán de cooperativa puede mezclar producción de varias fincas.

```text
id UUID PK
holding_id UUID NOT NULL
campaign_id UUID NULL
cooperative_name TEXT NULL
delivery_at TIMESTAMPTZ NOT NULL
ticket_number TEXT NULL
total_kg NUMERIC NOT NULL
yield_percent NUMERIC NULL
attachment_id UUID NULL
created_at TIMESTAMPTZ NOT NULL
```

Tabla puente:

```text
harvest_delivery_fields
delivery_id UUID
field_id UUID
kg NUMERIC NULL
PRIMARY KEY(delivery_id, field_id)
```

---

## 3. Qué NO debe tener tabla propia

### Historia
No duplicar registros en `history`. Historia es una consulta ordenada de `activities` + metadatos relacionados.

### Resumen de costes
No guardar totales manuales por finca. Se calculan de `cost_entries`; más adelante se pueden materializar/cachar si el volumen lo exige.

### Calendario de finca
No copiar actividades a una tabla de calendario. Solo existen eventos futuros o programados en `scheduled_events`.

### «Último tratamiento», «último abono», etc.
Son proyecciones/consultas sobre `activities`.

---

## 4. Contrato de escritura: una operación lógica

La API debe aceptar una operación de alto nivel y resolver todo dentro de una transacción.

Ejemplo conceptual:

```json
POST /api/v1/fields/:fieldId/activities
{
  "type": "irrigation",
  "occurred_on": "2026-09-03",
  "cost_eur": 22,
  "payload": {
    "duration_hours": 2
  },
  "follow_up": {
    "date": "2026-09-14",
    "time": "08:00"
  }
}
```

La API realiza:

```text
BEGIN
  INSERT activity
  UPSERT cost_entry si cost_eur existe
  INSERT scheduled_event si follow_up existe
  INSERT reminders según preferencias
COMMIT
```

Respuesta recomendada:

```json
{
  "activity": { "id": "..." },
  "projections": {
    "cost_entry_id": "...",
    "scheduled_event_id": "...",
    "reminder_ids": ["..."]
  }
}
```

---

## 5. Lecturas que necesita la UI V20

### Ficha viva

```text
GET /api/v1/fields/:id/summary?campaign=2026-27
```

Devuelve:
- datos de finca;
- campaña actual;
- kg y rendimiento;
- coste acumulado;
- próximo evento;
- último tratamiento/abono/poda;
- nº de documentos;
- alertas relevantes.

### Historia

```text
GET /api/v1/fields/:id/activities?type=&campaign=&cursor=
```

### Costes

```text
GET /api/v1/fields/:id/costs?campaign=
```

### Calendario

```text
GET /api/v1/fields/:id/events?from=&to=&status=planned
```

### Terreno

```text
GET /api/v1/fields/:id/land-references
```

---

## 6. Offline / PWA

La arquitectura debe ser local-first:

```text
UI
 ↓
IndexedDB local
 ↓
Outbox de operaciones
 ↓ cuando hay red
Sync API
 ↓
PostgreSQL/PostGIS
```

Cada escritura local debe tener:

```text
client_operation_id UUID
entity_id UUID generado cliente
created_at cliente
sync_status pending | synced | conflict | failed
```

La API debe ser idempotente por `client_operation_id` para evitar duplicados cuando una operación se reintenta.

Para fotos, guardar primero referencia local y subir archivo de forma separada con reintento.

---

## 7. Reglas de integridad que no se deben romper

1. `field_id` siempre pertenece al `holding_id` autenticado.
2. Una actividad solo genera un `cost_entry` vinculado.
3. Borrar una actividad debe definir explícitamente qué ocurre con coste, evento y adjuntos relacionados.
4. Modificar coste de actividad debe actualizar el `cost_entry`, nunca crear otro.
5. Completar un evento puede crear una actividad, pero debe conservar trazabilidad `scheduled_event → activity`.
6. Catastro/SIGPAC nunca son requisito para registrar una actividad.
7. Una finca puede tener múltiples referencias oficiales.
8. El nombre de finca es controlado por el usuario y no se reemplaza por el nombre administrativo.
9. Las proyecciones (Historia, costes, «último…») se derivan de datos canónicos.
10. Todo dato demo debe estar claramente separado de producción.

---

## 8. Orden de implementación recomendado

### D1 — Persistencia base
- holdings
- fields
- campaigns
- activities
- cost_entries

### D2 — Calendario
- scheduled_events
- reminders
- transacción actividad → coste/evento

### D3 — Archivos
- attachments
- subida de ticket/foto
- OCR de albarán

### D4 — Terreno
- field_land_refs
- PostGIS
- Catastro
- SIGPAC

### D5 — Offline
- IndexedDB
- outbox
- idempotencia
- resolución de conflictos

### D6 — Integraciones inteligentes
- clima
- RAIF
- recomendaciones
- alertas automáticas

Este contrato debe considerarse la frontera entre el prototipo visual V20 y el backend definitivo de Mi Campo.
