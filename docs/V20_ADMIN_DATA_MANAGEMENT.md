# Mágina Olivo V20 — Gestión administrativa de datos

Rama: `feat/v20-admin-operations-hub`

## Ruta

`/admin/gestion`

Esta superficie complementa `/admin/operaciones`: Operaciones observa la plataforma de forma transversal y Gestión permite cambios controlados sobre entidades de negocio.

## Fase 1 implementada

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

## Roles

- `support` y `editor`: consulta;
- `admin` y `super_admin`: edición de workspaces, miembros y fincas;
- la gestión de permisos de plataforma continúa bajo las reglas existentes de `/admin`.

## API

- `GET /api/v1/admin/workspaces`
- `PATCH /api/v1/admin/workspaces/:workspaceId`
- `GET /api/v1/admin/workspaces/:workspaceId/members`
- `PUT /api/v1/admin/workspaces/:workspaceId/members/:userId`
- `GET /api/v1/admin/fields`
- `PATCH /api/v1/admin/fields/:fieldId`

Todas las rutas requieren autenticación de plataforma. Las escrituras requieren al menos rol `admin`.

## Validación

`admin-management-smoke.ts` crea un workspace y finca reales en PostgreSQL/PostGIS, verifica listados, edición, alta de segundo propietario, bloqueo del último propietario, modificación de finca, preservación del estado GIS y presencia de auditoría.

El workflow `V20 platform admin check` ejecuta este smoke después de aplicar todas las migraciones reales.

## Próxima fase

La misma arquitectura se ampliará sin un editor SQL genérico:

1. campañas y agenda;
2. planes y suscripciones;
3. trabajos y actividad agrícola;
4. documentos/OCR con acciones de soporte;
5. profesional: clientes, presupuestos, facturas y cobros;
6. herramientas de soporte (reintentos, reasignaciones y refresco de fuentes), siempre auditadas.
