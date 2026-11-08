# Titularidad básica y perfil de riego en Room — Propuesta

Estado: **propuesta para autorización. Sin implementar.** No añade campos, tablas, migraciones ni SQL. Room sigue en 36.

## 1. Titularidad básica

### Conceptos, siempre separados

| Concepto | Qué es | Dónde vive |
|---|---|---|
| Dueño de la cuenta | Quién administra el espacio en la app | `workspaces.owner_user_id` (ya existe) |
| Titular agrícola | Quien explota la finca | Nuevo, opcional |
| Propietario legal | Dueño del terreno | Fuera de la versión básica |
| Responsable de gestión | Quien la lleva, si es otra persona | Nuevo, opcional |
| Personas autorizadas | Permisos en la app | Contrato de permisos, aparte |

### Modelo propuesto

```kotlin
@Entity(tableName = "farm_holdings")
data class FarmHoldingEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "farm_id") val farmId: UUID,          // única entre filas activas
    @ColumnInfo(name = "parcel_id") val parcelId: UUID?,     // null: la finca; valor: excepción de esa parcela
    @ColumnInfo(name = "holder_name") val holderName: String?,
    @ColumnInfo(name = "holder_type") val holderType: String?,   // PERSON | COMPANY
    @ColumnInfo(name = "tenure") val tenure: String?,            // OWNED | LEASED | CEDED | OTHER
    @ColumnInfo(name = "manager_name") val managerName: String?,
    @Embedded val metadata: LocalMetadata,
)
```

Reglas:

- Todo opcional. Sin DNI/NIF, teléfono, contratos ni porcentajes.
- Finca como valor general; la parcela solo guarda una excepción, sin copiar datos.
- Escribir un nombre nunca crea una cuenta ni concede permisos.
- Texto propio y validado: no se usan notas libres.

### Privacidad, antes de habilitarlo

1. Incluir estos datos en la política de privacidad y en Data safety.
2. Incluirlos en la exportación y en el borrado de cuenta.
3. Visibles solo para el propietario del espacio.
4. Fuera de la IA y de los registros de diagnóstico.
5. Solo locales hasta tener contrato remoto.

## 2. Perfil de riego en Room

### Revisión de la ADR-012 de B.5

La idea de una tabla por parcela sirve, pero el esquema propuesto en esa ADR debe corregirse:

| ADR-012 | Problema | Propuesta |
|---|---|---|
| `onDelete = CASCADE` | Las parcelas se archivan y conservan historial | `NO_ACTION`, como el resto |
| Turnos en JSON | No se pueden consultar ni validar | Tablas hijas |
| Importes `Double` en euros | Errores de redondeo | Céntimos enteros y moneda |
| `sync_status` por defecto `SYNCED` | Un dato local aparecería sincronizado | `LocalMetadata`, `LOCAL_ONLY` |
| Repite tipo de riego | Duplica `parcels.irrigation_system` | Se reutiliza el campo existente |

### Esquema propuesto

```kotlin
@Entity(tableName = "parcel_irrigation_profiles", /* FK parcels, workspaces: NO_ACTION */)
data class ParcelIrrigationProfileEntity(
    @PrimaryKey @ColumnInfo(name = "parcel_id") val parcelId: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "water_source") val waterSource: String?,
    @ColumnInfo(name = "hydrant") val hydrant: String?,
    @ColumnInfo(name = "irrigated_trees") val irrigatedTrees: Int?,
    @ColumnInfo(name = "emitters_per_tree") val emittersPerTree: Int?,
    @ColumnInfo(name = "emitter_flow_lph") val emitterFlowLph: Double?,
    @ColumnInfo(name = "emitter_type") val emitterType: String?,
    @ColumnInfo(name = "flow_mode") val flowMode: String?,
    @ColumnInfo(name = "direct_flow_m3h") val directFlowM3PerHour: Double?,
    @ColumnInfo(name = "tariff_type") val tariffType: String?,
    @ColumnInfo(name = "tariff_price_minor") val tariffPriceMinor: Long?,
    @ColumnInfo(name = "annual_quota_minor") val annualQuotaMinor: Long?,
    @ColumnInfo(name = "currency") val currency: String?,
    @Embedded val metadata: LocalMetadata,
)
```

Tablas hijas: `parcel_irrigation_slots` (día, inicio, fin) y `parcel_irrigation_turns` (fecha, inicio, fin, motivo, cancelado).

Sin duplicar:

- Sistema, red, sector y días siguen en `parcels`.
- Los riegos realizados siguen en el Cuaderno (`irrigation_details`), siempre como medición aparte del cálculo teórico.

### Migración y pruebas (cuando se autorice)

1. Solo `CREATE TABLE` e índices; ninguna fila existente cambia.
2. Exportar esquema de la nueva versión.
3. Prueba `36 → nueva` con datos, comprobando que `parcels` no cambia.
4. Prueba de actualización desde la APK anterior en emulador.
5. Comprobar que G2.6 y P4 no envían estos datos.

Las pruebas de migración no se han escrito todavía: dependen del número de versión.

### Trabajo previo pendiente de B.2 a B.5

- **B.2 y B.3:** pantalla y navegación chocan con `AppNavigation`, `HomeScreen` y `FarmScreens`; se reintegran sobre esta base.
- **B.4:** sus protecciones de finca archivada y autorización se conservan al portar el ViewModel.
- **B.5:** el almacén SharedPreferences no se incorpora.

## 3. Coordinación de Room

**No confirmada.** No hay canal directo con Codex ni con el desarrollo de P5 desde esta sesión.

Datos comprobados: candidato y Mistral en 36; P5 (#864) en 37, sin integrar. Hay que acordar con ellos el orden antes de asignar número. Hasta entonces, ninguna rama propia reserva una versión.

## 4. Riesgos y bloqueos

| Prioridad | Riesgo |
|---|---|
| P0 | Numeración de Room sin acordar |
| P0 | Revisión visual y CI completas de la cadena #871–#875 |
| P1 | Titularidad sin controles de privacidad, exportación y borrado |
| P1 | Reintegrar la pantalla de Riego sin romper la navegación actual |
| P2 | Sincronización futura de riego y titularidad, sin contrato |
| P2 | Coste de la calculadora: heredado de B.5, trunca fracciones de céntimo |
