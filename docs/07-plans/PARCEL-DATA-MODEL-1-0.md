# Datos de finca y parcela 1.0 — Auditoría y modelo propuesto

Estado: **propuesta para autorización.** Lo implementado usa solo campos ya existentes. Room sigue en 36 y no hay SQL. Base: #873 (`9eaf43129`).

## 1. Inventario de lo que ya existe

| Dato | Dónde | Observación |
|---|---|---|
| Nombre, descripción, municipio, provincia, notas, portada de finca | `farms` | |
| Nombre, referencia, polígono, parcela, municipio, provincia, notas de parcela | `parcels` | |
| Geometría, origen, proveedor, fecha de importación | `parcels` | |
| Superficie catastral y trabajada | `parcels` | `cadastral_area_m2`, `managed_area_m2` |
| Número de olivos, variedad | `parcels` | Una sola variedad |
| Sistema de riego, red, sector, días | `parcels` | Nulo significa sin indicar |
| Pertenencia a finca con histórico | `farm_parcel_memberships` | |
| Dueño de la app | `workspaces.owner_user_id` | Es la cuenta, no la titularidad legal |
| Cooperativas, proveedores, comunidades | Organizaciones (`OrganizationRepository`) | Reutilizable para contactos |
| Trabajos y reparto por parcela | `activity_parcel_targets` y detalles por tipo | Incluye `irrigation_details` y tarifa histórica |
| Campaña por parcela | Instantánea de campaña por parcela | Conserva superficie y nombre de inicio |
| Recolección por parcela | `harvest_parcels`, `domain/harvest`, `domain/analytics` | Repartos mixtos identificados |
| Gastos por parcela | `expenses.parcel_id` | Opcional |
| Adjuntos | `AttachmentRepository` | Nombre, tipo de archivo, estado de subida; sin categoría ni fecha de documento |
| SIGPAC | Consulta en vivo | Sin persistencia |

**Totales de finca.** Los olivos se suman por parcela e indican si el total está completo (`olive_tree_count_complete`). Los 123 olivos de la prueba siguen esa regla.

**Superficie total de finca: a corregir.** `FarmDao` usa `COALESCE(managed_area_m2, cadastral_area_m2)`, así que mezcla ambas superficies. No necesita migración, pero cambia una cifra visible en Mi Campo; requiere decidir cómo mostrarla.

## 2. Campos realmente nuevos

| Sección | Campos |
|---|---|
| Titularidad | Titular (persona o empresa), propietarios, gestor, relación, fechas, porcentaje, contacto, identificador de explotación |
| Identificación | Paraje; coordenadas se derivan de la geometría |
| Cultivo | Cultivo principal, árboles productivos, jóvenes e improductivos, marco, año de plantación, sistema, condición declarada, observaciones, reparto de variedades |
| Terreno y suelo | Orientación, textura, profundidad, drenaje, erosión, observaciones; análisis con pH, conductividad y materia orgánica |
| SIGPAC | Recintos consultados y recintos trabajados |
| Riego | Fuente, hidrante, olivos regados, goteros, caudal, aforo, horarios, turnos, costes (ver §5) |
| Documentos | Categoría y fecha del documento, certificaciones opcionales |

La edad se calcula a partir del año de plantación; pendiente y altitud oficiales vienen de SIGPAC.

## 3. Modelo recomendado

```
Cuenta (app) ── Workspace ── Finca ──< Pertenencia >── Parcela ──< Recinto consultado
                                │                         │
                         Acuerdo de explotación ──────────┤ (por finca, con excepción por parcela)
                                │                         ├── Plantación ──< Variedad
                         Persona (titular,                ├── Terreno ──< Análisis de suelo
                         propietario, gestor)             ├── Perfil de riego ──< Turno
                                                          └── Recinto trabajado
```

Reglas:

1. **Persona** es una ficha de datos, no una cuenta. Registrar un nombre nunca concede permisos.
2. Cuatro conceptos separados: propietario del terreno, titular agrícola, usuario creador y personas autorizadas.
3. **Acuerdo de explotación** se define en la finca y puede tener excepciones por parcela, sin copiar datos.
4. Los datos oficiales (Catastro, SIGPAC), los del agricultor y los calculados se guardan o derivan por separado, con fuente y fecha.
5. Producción, gastos y actividades no se copian: se consultan.
6. Los datos fiscales y personales van cifrados o con acceso restringido, y nunca son obligatorios.

## 4. Diseño de Datos

Implementado, plegable: **Resumen agrícola** (abierto), **Cultivo y plantación**, **Catastro y SIGPAC**, **Riego e instalaciones**, **Información adicional**.

Sin mostrar hasta tener datos reales, para no ofrecer apartados vacíos: **Titularidad y gestión**, **Terreno y suelo**, **Producción y economía**, **Personas y permisos**.

SIGPAC solo se consulta al abrir su apartado.

## 5. Riego B.1–B.5 (#783, #784, #785, #787, #790)

Reutilizable: `WateringDoseCalculator`, `WeeklyIrrigationCalendar`, `IrrigationSummary` y los modelos de `FarmerIrrigationConfig` (fuente, aforo, franjas, tarifas, turnos), con sus pruebas.

**No se integra todavía** porque:

- `FarmerIrrigationStore` guarda en SharedPreferences, que esta especificación descarta para datos agrícolas compartidos.
- La rama parte de una base muy anterior (Room 26) y choca en `FarmScreens`, `HomeScreen` y `AppNavigation`.

Propuesta: traer solo dominio y calculadora; sustituir el almacén por Room según la ADR-012 de esa rama; reutilizar `parcels` para sistema, red, sector y días, sin duplicarlos. El cálculo teórico y la medición real (`irrigation_details`) se muestran por separado. Necesita autorización de migración.

## 6. Persistencia y sincronización

| Dato | Propuesta |
|---|---|
| Plantación, terreno, recintos trabajados | Tablas locales aditivas; sin envío en la 1.0 |
| Personas y acuerdos | Tablas locales con acceso solo del propietario; sincronización posterior con contrato propio |
| Recintos consultados | Caché local por parcela, campaña y fuente, si se aprueba |
| Perfil de riego | Tabla local; migración desde SharedPreferences solo si B.5 llegó a instalarse |
| Sincronización | Cada entidad necesita contrato, RPC y pruebas A/B/C con Codex |

La numeración de Room debe acordarse con P5 (37, sin integrar) y con Codex. Cada migración será aditiva, con esquema exportado y prueba de actualización desde 36.

## 7. Riesgos y dependencias

- **Datos personales:** titulares y contactos implican RGPD, política de privacidad, exportación y borrado.
- **Modelo:** decidir mal Persona y Acuerdo obligaría a rehacerlo; conviene validarlo antes de programar.
- **Permisos:** dependen del contrato de capacidades con Codex.
- **SIGPAC:** el servicio no da campaña; pendiente y altitud no tienen unidad documentada.
- **Producción por parcela:** solo es fiable con reparto confirmado.

## 8. Orden recomendado para la 1.0

1. Revisión visual y CI de #871, #872, #873 y esta rama.
2. Decidir la superficie total de finca.
3. Acuerdo de Room con Codex y P5.
4. Migración 1: plantación y paraje.
5. Producción y actividad en la ficha, sin esquema nuevo.
6. Migración 2: recintos y perfil de riego; integrar calculadora.
7. Titularidad, terreno y categorías de documentos.
8. Roles y permisos con Codex, posiblemente después de la 1.0.
9. Pruebas de actualización, aislamiento A/B/C, sin conexión y revisión visual.
