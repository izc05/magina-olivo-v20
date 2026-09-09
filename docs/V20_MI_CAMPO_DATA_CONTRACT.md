# Mágina Olivo V20 — Mi Campo Data Contract

**Estado:** contrato técnico previo al backend.  
**Autoridad:** complementa `V20_MASTER_ARCHITECTURE.md` y respeta su decisión de **no usar una tabla Activity universal como fuente de verdad**.

## 1. Regla de producto

El agricultor trabaja con **Fincas** (`Las Cenillas`, `El Cerrillo`...). Catastro y SIGPAC se vinculan por debajo y nunca sustituyen ese nombre ni obligan a una relación 1:1.

> Registrar una vez; proyectar en todos los lugares relacionados.

Ejemplo:

```text
IrrigationRecord · 22 € · próximo 14/09
        │
        ├── CostLedgerProjection +22 €
        ├── FarmTimelineProjection
        ├── FarmSummaryProjection
        └── ScheduledEvent 14/09

UI: Riegos · Gastos · Historia · Calendario
```

La UI puede usar un modelo local normalizado para trabajar offline, pero PostgreSQL mantiene **fuentes de verdad por dominio**.

---

## 2. Workspace y finca

### `workspaces`

```text
id UUID PK
name TEXT NOT NULL
type TEXT NOT NULL           -- family | professional | organization
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

### `workspace_memberships`

```text
workspace_id UUID FK workspaces
user_id UUID NOT NULL
role TEXT NOT NULL
status TEXT NOT NULL
PRIMARY KEY(workspace_id, user_id)
```

### `fields`

Representa la **Finca** visible para el agricultor.

```text
id UUID PK
workspace_id UUID NOT NULL
name TEXT NOT NULL
description TEXT NULL
municipality TEXT NULL
province TEXT NULL
geometry GEOMETRY(MultiPolygon,4326) NULL
calculated_area_ha NUMERIC NULL
tree_count INTEGER NULL
crop TEXT DEFAULT 'olivar'
variety TEXT NULL
water_regime TEXT NULL       -- secano | regadio | mixto
planting_year INTEGER NULL
tenure_type TEXT NULL
status TEXT DEFAULT 'active'
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Alta mínima V20: `name` + `tree_count`. Catastro/SIGPAC son opcionales.

### `field_land_refs`

```text
id UUID PK
field_id UUID FK fields
source TEXT NOT NULL         -- catastro | sigpac | manual
reference TEXT NULL
geometry GEOMETRY(MultiPolygon,4326) NULL
area_ha NUMERIC NULL
status TEXT NOT NULL         -- pending | linked | verified
metadata_json JSONB NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Una finca admite **0..N referencias** administrativas.

### `campaigns`

```text
id UUID PK
workspace_id UUID NOT NULL
name TEXT NOT NULL           -- 2026/27
start_date DATE NOT NULL
end_date DATE NULL
status TEXT NOT NULL         -- planned | active | closed
created_at TIMESTAMPTZ NOT NULL
```

---

## 3. Fuentes de verdad por dominio

Todos los registros comparten columnas de trazabilidad (`id`, `workspace_id`, `field_id`, `campaign_id`, `created_by`, timestamps, `client_operation_id`), pero cada dominio conserva campos tipados propios.

### `irrigation_records`

```text
id UUID PK
workspace_id UUID NOT NULL
field_id UUID NOT NULL
campaign_id UUID NULL
occurred_at TIMESTAMPTZ NOT NULL
duration_hours NUMERIC NULL
water_m3 NUMERIC NULL
cost_eur NUMERIC(12,2) NULL
notes TEXT NULL
client_operation_id UUID UNIQUE NOT NULL
created_by UUID NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Las modalidades complejas de precio (`€/olivo`, `€/h`, `€/m³`, cuota anual...) se añadirán mediante campos de pricing tipados, no ocultándolas en un JSON genérico.

### `treatment_records`

```text
id UUID PK
workspace_id UUID NOT NULL
field_id UUID NOT NULL
campaign_id UUID NULL
occurred_at TIMESTAMPTZ NOT NULL
reason TEXT NOT NULL
product_name TEXT NOT NULL
dose TEXT NULL
quantity TEXT NULL
applicator TEXT NULL
equipment TEXT NULL
cost_eur NUMERIC(12,2) NULL
notes TEXT NULL
client_operation_id UUID UNIQUE NOT NULL
created_by UUID NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

### `fertilization_records`

```text
id UUID PK
workspace_id UUID NOT NULL
field_id UUID NOT NULL
campaign_id UUID NULL
occurred_at TIMESTAMPTZ NOT NULL
product_name TEXT NOT NULL
quantity_kg NUMERIC NULL
application_method TEXT NULL
composition TEXT NULL
cost_eur NUMERIC(12,2) NULL
supplier TEXT NULL
notes TEXT NULL
client_operation_id UUID UNIQUE NOT NULL
created_by UUID NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

### `pruning_records`

```text
id UUID PK
workspace_id UUID NOT NULL
field_id UUID NOT NULL
campaign_id UUID NULL
occurred_at TIMESTAMPTZ NOT NULL
pruning_type TEXT NOT NULL
workers INTEGER NULL
hours NUMERIC NULL
cost_eur NUMERIC(12,2) NULL
notes TEXT NULL
client_operation_id UUID UNIQUE NOT NULL
created_by UUID NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

### `labor_entries`

```text
id UUID PK
workspace_id UUID NOT NULL
field_id UUID NOT NULL
campaign_id UUID NULL
occurred_at TIMESTAMPTZ NOT NULL
task TEXT NOT NULL
workers INTEGER NULL
hours NUMERIC NULL
pricing_unit TEXT NULL
quantity NUMERIC NULL
rate_eur NUMERIC NULL
cost_eur NUMERIC(12,2) NULL
worker_or_crew TEXT NULL
notes TEXT NULL
client_operation_id UUID UNIQUE NOT NULL
created_by UUID NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

### `machine_entries`

```text
id UUID PK
workspace_id UUID NOT NULL
field_id UUID NOT NULL
campaign_id UUID NULL
occurred_at TIMESTAMPTZ NOT NULL
machine TEXT NOT NULL
hours NUMERIC NULL
fuel_liters NUMERIC NULL
operator_or_supplier TEXT NULL
cost_eur NUMERIC(12,2) NULL
notes TEXT NULL
client_operation_id UUID UNIQUE NOT NULL
created_by UUID NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

### `observations`

```text
id UUID PK
workspace_id UUID NOT NULL
field_id UUID NOT NULL
occurred_at TIMESTAMPTZ NOT NULL
category TEXT NULL
notes TEXT NOT NULL
latitude NUMERIC NULL
longitude NUMERIC NULL
weather_snapshot_id UUID NULL
client_operation_id UUID UNIQUE NOT NULL
created_by UUID NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

### Gastos manuales

`expense_entries` existe solo para gastos que **no nacen de otro registro**.

```text
id UUID PK
workspace_id UUID NOT NULL
field_id UUID NOT NULL
campaign_id UUID NULL
occurred_on DATE NOT NULL
category TEXT NOT NULL
concept TEXT NOT NULL
amount_eur NUMERIC(12,2) NOT NULL
client_operation_id UUID UNIQUE NOT NULL
created_by UUID NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

Un riego de 22 € NO crea además un `expense_entry`.

---

## 4. Cosecha y albaranes

La cosecha necesita modelo propio porque una entrega puede mezclar varias fincas.

### `deliveries`

```text
id UUID PK
workspace_id UUID NOT NULL
campaign_id UUID NULL
cooperative_or_mill TEXT NULL
delivery_at TIMESTAMPTZ NOT NULL
ticket_number TEXT NULL
total_kg NUMERIC NOT NULL
source TEXT NOT NULL         -- manual | ocr | import
client_operation_id UUID UNIQUE NOT NULL
created_by UUID NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

### `delivery_fields`

```text
delivery_id UUID FK deliveries
field_id UUID FK fields
kg NUMERIC NULL
PRIMARY KEY(delivery_id, field_id)
```

### `delivery_results`

Rendimiento separado de la entrega para permitir que llegue después.

```text
id UUID PK
delivery_id UUID NOT NULL
yield_percent NUMERIC NOT NULL
result_at TIMESTAMPTZ NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

El promedio de campaña se calcula **ponderado por kg**.

---

## 5. Proyecciones comunes

Las fuentes de verdad anteriores emiten eventos de dominio. De ellos nacen vistas comunes.

```text
Domain record
   ↓
Domain event
   ├── FarmTimeline
   ├── FarmSummary
   ├── CostLedger
   ├── SearchProjection
   └── NotificationIntent
```

### `farm_timeline_projection`

No es fuente de verdad; permite construir Historia de forma rápida y uniforme.

```text
id UUID PK
field_id UUID NOT NULL
occurred_at TIMESTAMPTZ NOT NULL
domain_type TEXT NOT NULL
domain_record_id UUID NOT NULL
title TEXT NOT NULL
summary TEXT NULL
icon_key TEXT NULL
created_at TIMESTAMPTZ NOT NULL
UNIQUE(domain_type, domain_record_id)
```

### `cost_ledger_projection`

```text
id UUID PK
field_id UUID NOT NULL
campaign_id UUID NULL
occurred_on DATE NOT NULL
domain_type TEXT NOT NULL
domain_record_id UUID NOT NULL
category TEXT NOT NULL
amount_eur NUMERIC(12,2) NOT NULL
UNIQUE(domain_type, domain_record_id)
```

Así un `IrrigationRecord.cost_eur = 22` genera exactamente una fila de proyección. Editar el riego actualiza esa fila; no crea un segundo gasto.

### `farm_summary_projection`

Materialización/caché de valores como:
- kg campaña;
- rendimiento ponderado;
- coste acumulado;
- último tratamiento;
- último abonado;
- última poda;
- próximo evento;
- número de documentos.

Siempre reconstruible desde fuentes de verdad.

---

## 6. Calendario y recordatorios

### `scheduled_events`

```text
id UUID PK
workspace_id UUID NOT NULL
field_id UUID NULL
source_domain_type TEXT NULL
source_domain_record_id UUID NULL
title TEXT NOT NULL
scheduled_at TIMESTAMPTZ NOT NULL
status TEXT NOT NULL         -- planned | completed | postponed | cancelled
source TEXT NOT NULL         -- manual | domain_followup | smart
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

### `reminders`

```text
id UUID PK
scheduled_event_id UUID NOT NULL
channel TEXT NOT NULL        -- in_app | push
remind_at TIMESTAMPTZ NOT NULL
status TEXT NOT NULL         -- pending | sent | failed | cancelled
sent_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ NOT NULL
```

Registrar un riego con `next_irrigation_at` crea/actualiza un `ScheduledEvent` ligado al riego mediante `source_domain_*`.

---

## 7. Documentos

### `documents`

```text
id UUID PK
workspace_id UUID NOT NULL
storage_key TEXT NOT NULL
mime_type TEXT NULL
kind TEXT NOT NULL           -- photo | ticket | invoice | document
ocr_status TEXT NULL
created_by UUID NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

### `attachment_links`

```text
id UUID PK
document_id UUID NOT NULL
domain_type TEXT NOT NULL
domain_record_id UUID NOT NULL
relation TEXT NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

Esto mantiene `Document` transversal sin meter blobs ni arrays dentro de cada registro.

---

## 8. Escrituras API

No habrá un endpoint universal `POST /activities` como fuente de verdad.

Endpoints iniciales:

```text
POST /api/v1/fields/:fieldId/irrigations
POST /api/v1/fields/:fieldId/treatments
POST /api/v1/fields/:fieldId/fertilizations
POST /api/v1/fields/:fieldId/prunings
POST /api/v1/fields/:fieldId/labor
POST /api/v1/fields/:fieldId/machines
POST /api/v1/fields/:fieldId/observations
POST /api/v1/fields/:fieldId/expenses
POST /api/v1/deliveries
```

Ejemplo riego:

```json
{
  "client_operation_id": "uuid",
  "occurred_at": "2026-09-03T10:00:00+02:00",
  "cost_eur": 22,
  "duration_hours": 2,
  "follow_up": {
    "scheduled_at": "2026-09-14T08:00:00+02:00"
  }
}
```

Transacción lógica:

```text
BEGIN
  INSERT irrigation_record
  UPSERT cost_ledger_projection
  UPSERT farm_timeline_projection
  INSERT/UPDATE scheduled_event si follow_up
  UPDATE farm_summary_projection
  enqueue notification intents
COMMIT
```

Los handlers de API llaman servicios de dominio; no escriben proyecciones ad hoc desde el controlador HTTP.

---

## 9. Lecturas para la UI

La UI sí puede consumir vistas agregadas uniformes:

```text
GET /api/v1/fields/:id/summary?campaign=
GET /api/v1/fields/:id/timeline?type=&cursor=
GET /api/v1/fields/:id/costs?campaign=
GET /api/v1/fields/:id/events?from=&to=
GET /api/v1/fields/:id/land-references
```

Y lecturas específicas cuando se abre un módulo:

```text
GET /api/v1/fields/:id/irrigations
GET /api/v1/fields/:id/treatments
GET /api/v1/fields/:id/fertilizations
GET /api/v1/fields/:id/prunings
GET /api/v1/fields/:id/labor
GET /api/v1/fields/:id/machines
GET /api/v1/fields/:id/observations
GET /api/v1/fields/:id/deliveries
```

---

## 10. Offline / PWA

En el cliente podemos normalizar temporalmente distintos registros en una **proyección local común** para pintar Historia y sincronizar UX. Esa proyección NO redefine el modelo de servidor.

```text
UI
 ↓
IndexedDB
 ├── irrigation_records_local
 ├── treatment_records_local
 ├── ...
 ├── timeline_projection_local
 └── outbox
 ↓ cuando vuelve la red
Sync API por dominio
 ↓
PostgreSQL/PostGIS
```

Cada escritura local incluye:

```text
client_operation_id UUID
entity_id UUID generado cliente
sync_status pending | synced | conflict | failed
```

La API es idempotente por `client_operation_id`.

El prototipo actual usa `localStorage` y un `ActivityRecord` normalizado **solo como simulación de proyección local**. Antes de producción se migrará a IndexedDB y stores por dominio.

---

## 11. Integridad

1. `field_id` siempre pertenece al workspace autorizado.
2. Catastro/SIGPAC nunca son requisito para registrar trabajo.
3. Una finca puede tener múltiples referencias oficiales.
4. El nombre de finca es controlado por el usuario.
5. Un coste de dominio genera una sola entrada en `CostLedgerProjection`.
6. Editar/borrar un registro actualiza o elimina sus proyecciones de forma determinista.
7. Historia nunca se edita directamente: refleja eventos de dominio.
8. `FarmSummary` siempre puede reconstruirse.
9. OCR no confirma silenciosamente kg, fecha, importe, rendimiento o dosis.
10. Todo dato demo queda separado de producción.

---

## 12. Orden de implementación

### D1 — Core
- workspaces / memberships
- fields / campaigns
- migraciones PostgreSQL
- contratos compartidos

### D2 — Registros prioritarios
- irrigation_records
- treatment_records
- fertilization_records
- pruning_records
- expense_entries
- proyecciones Timeline/CostLedger/Summary

### D3 — Cosecha
- deliveries
- delivery_fields
- delivery_results
- OCR + documentos

### D4 — Calendario
- scheduled_events
- reminders

### D5 — Terreno
- PostGIS
- field_land_refs
- Catastro
- SIGPAC

### D6 — Offline real
- IndexedDB por dominio
- outbox
- idempotencia
- conflictos

### D7 — Trabajo avanzado
- labor_entries
- machine_entries
- observations
- partes profesionales

Este contrato es la frontera entre la UX V20 ya prototipada y el backend Fastify/Kysely/PostgreSQL definitivo.
