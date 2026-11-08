# Ficha de parcela 1.0 — Propuesta de migraciones y permisos

Estado: **propuesta sin implementar. Requiere autorización.** No añade migraciones, tablas, SQL ni colaboración real. Room sigue en 36.

## 1. Versiones de Room comprobadas (08/11/2026)

| Rama | Versión | Estado |
|---|---|---|
| Candidato 1.0 (#869) y ficha de parcela | 36 | Candidato |
| Asistente Mistral (`feat/assistant-mistral-foundation`) | 36 | No integrado |
| P5 economía (#864) | 37 | En espera, no integrado |
| Riego B.5 (`feat/antigravity-riego-b5`) | 26 | Rama antigua, no integrada |

Antes de autorizar nada hay que acordar con Codex y P5 quién usa cada versión. Esta propuesta **no reserva** ninguna.

## 2. Datos propuestos

Todos opcionales, aditivos y sin rellenar valores existentes.

| Grupo | Dónde | Campos |
|---|---|---|
| Localización | `parcels` | Paraje o zona |
| Árboles | `parcels` | En producción, jóvenes, improductivos, otras especies |
| Cultivo | `parcels` | Cultivo principal, sistema de cultivo, marco, año de plantación |
| Riego | `parcels` | Fuente de agua |
| Variedades | Tabla hija nueva | Variedad y árboles aproximados, por parcela |
| Recintos SIGPAC | Tabla nueva | Parcela, código completo, recinto, atributos tal como los publica FEGA, fuente, fecha de consulta, campaña si existe |
| Superficie trabajada por recintos | Tabla de relación | Parcela y recintos elegidos por el agricultor |
| Documentos | `attachments` | Tipo y fecha del documento |

Reglas:

- Los recintos nunca se guardan dentro de la parcela catastral, ni modifican superficies o geometrías.
- Se mantienen los UUID y los históricos.
- Recintos y variedades son **solo locales** en esta fase: `validate_parcel_fields` admite una lista cerrada, y ampliar el contrato remoto exige SQL propio.

## 3. Plan de prueba de la migración

1. Esquema exportado y prueba de migración desde Room 36 con datos reales de prueba.
2. Comprobar que no cambia ninguna fila existente.
3. Actualizar desde la APK anterior en un emulador, sin perder datos.
4. Comprobar que la sincronización P4 y G2.6 no envía los campos nuevos.

## 4. Permisos

Situación actual: las mutaciones de parcelas exigen propietario (`lock_owned_farm`, `owner_user_id=actor`). La ficha no ofrece ninguna acción de compartir.

Contrato a acordar con Codex:

| Elemento | Propuesta |
|---|---|
| Unidad de permiso | Capacidad por operación, no por nombre de rol |
| Alcance | Finca, parcelas concretas, operaciones concretas |
| Vigencia | Inicio, caducidad y estado activo |
| Revocación | Efecto inmediato en servidor y sin nuevas consultas remotas |
| Comprobación | RLS y RPC en servidor; la interfaz solo oculta opciones |
| Sin conexión | Política explícita para concesiones caducadas |
| Invitaciones | Correo o QR de un solo uso, con caducidad y aceptación expresa; sin credenciales en el QR |
| Auditoría | Registro de concesión, aceptación y revocación |
| Roles | Titular, gestor, recolectora, jornalero y solo lectura, como plantillas de capacidades |

Pruebas previas a habilitarla: aislamiento A/B/C, colaborador limitado a sus parcelas, revocación, escalada de privilegios y acceso directo por API.

## 5. Decisiones necesarias

1. Orden de versiones de Room entre ficha, P5 y Mistral.
2. Qué campos entran en la 1.0.
3. Si los recintos consultados se guardan para consultarlos sin conexión.
4. Contrato de permisos con Codex.
