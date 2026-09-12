# Mágina Olivo V20 — Gestión administrativa de datos

Rama: `feat/v20-admin-operations-hub`

## Rutas

- `/admin/operaciones`: métricas, telemetría, explorador de datos y App Gateway.
- `/admin/gestion`: espacios de trabajo, miembros y fincas.
- `/admin/campanas-planes`: campañas agrícolas, planes internos e intereses comerciales.

El principio común es permitir cambios controlados sobre entidades de negocio mediante APIs tipadas y auditadas, sin introducir un editor SQL genérico.

## Fase 1 implementada — Gestión

### Espacios de trabajo

- listar hasta 100 espacios recientes;
- buscar por nombre;
- ver tipo, plan, miembros activos, propietarios activos y fincas;
- editar nombre y tipo;
- auditoría `workspace.updated`.

### Miembros

- ver miembros de un workspace con usuario, correo, rol y estado;
- añadir un usuario existente al workspace;
- cambiar rol y estado;
- auditoría `workspace_member.added` / `workspace_member.updated`;
- regla de integridad: no se puede suspender, revocar o degradar al último `owner` activo.

### Fincas

- listar y filtrar fincas por workspace;
- editar nombre, descripción, cultivo, variedad, número de olivos, régimen hídrico, año de plantación, tenencia y estado activo/archivado;
- auditoría `field.updated`;
- la geometría, `geometry_source`, `geometry_status`, Catastro/SIGPAC y la localidad canónica quedan deliberadamente fuera de este editor y siguen bajo los flujos GIS/territorio.

## Fase 2 implementada — Campañas y planes

### Campañas

- listado global con workspace asociado;
- filtros por texto, workspace y estado;
- edición de nombre, fecha inicial, fecha final y estado;
- validación de que la fecha final no preceda a la inicial;
- auditoría `campaign.updated`.

### Planes internos

- listado global de workspaces con plan, estado, origen y fin de período;
- posibilidad de fijar manualmente `Campo`, `Pro` o `Profesional`;
- estados `active`, `trialing`, `paused` y `cancelled`;
- cualquier cambio desde Admin usa `source = manual`;
- solo `super_admin` puede modificar el plan efectivo;
- auditoría `workspace_plan.updated`;
- **billing y checkout permanecen desactivados**: esta pantalla no cobra, no crea pagos y no activa una pasarela.

### Intereses comerciales

- listado global de solicitudes de plan;
- seguimiento `pending → contacted → converted/cancelled`;
- modificación por `admin` o `super_admin`;
- auditoría `plan_interest.updated`.

## Roles

- `support` y `editor`: consulta en las nuevas superficies de gestión;
- `admin`: workspaces, miembros, fincas, campañas e intereses comerciales;
- `super_admin`: lo anterior más modificación del plan efectivo;
- la gestión de permisos administrativos de plataforma continúa bajo las reglas existentes de `/admin`.

## API

### Gestión

- `GET /api/v1/admin/workspaces`
- `PATCH /api/v1/admin/workspaces/:workspaceId`
- `GET /api/v1/admin/workspaces/:workspaceId/members`
- `PUT /api/v1/admin/workspaces/:workspaceId/members/:userId`
- `GET /api/v1/admin/fields`
- `PATCH /api/v1/admin/fields/:fieldId`

### Campañas y planes

- `GET /api/v1/admin/campaigns`
- `PATCH /api/v1/admin/campaigns/:campaignId`
- `GET /api/v1/admin/plans`
- `PUT /api/v1/admin/workspaces/:workspaceId/plan`
- `PATCH /api/v1/admin/plan-interests/:interestId`

Todas las rutas requieren autenticación de plataforma. Las escrituras aplican además el rol mínimo correspondiente.

## Validación

`admin-management-smoke.ts` crea un workspace y finca reales en PostgreSQL/PostGIS, verifica listados, edición, alta de segundo propietario, bloqueo del último propietario, modificación de finca, preservación del estado GIS y presencia de auditoría.

`admin-campaign-plans-smoke.ts` crea una campaña e interés comercial, verifica validación de fechas, edición de campaña, plan manual sin billing, transición del interés y auditoría.

El workflow `V20 platform admin check` ejecuta ambos smokes después de compilar API/web y aplicar todas las migraciones reales.

## Próximas fases

La misma arquitectura se ampliará sin un editor SQL genérico:

1. agenda y tareas planificadas;
2. trabajos y actividad agrícola;
3. documentos/OCR con acciones de soporte;
4. profesional: clientes, presupuestos, facturas y cobros;
5. herramientas de soporte como reintentos, reasignaciones y refresco de fuentes, siempre auditadas.
