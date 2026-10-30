# ADR-012: Persistencia Local de Configuraciones de Riego del Agricultor y Estrategia de Sincronización Compatible con G2.6

## Estado
Aprobado e Implementado en B.5 (Fase Local Estricta) / Propuesta de Convergencia con G2.6.

## Contexto y Motivación
En el módulo de Riego 1.0 (sub-fase B.5), los agricultores configuran parámetros operativos y económicos específicos de sus parcelas:
- Características agronómicas e hidráulicas: número de goteros por olivo, caudal nominal unitario (L/h), aforo real de campo verificado y tipo de sistema de riego.
- Planificación operativa semanal: turnos habituales programados (días y franjas horarias con soporte nocturno).
- Estructura económica de costes: tarifa volumétrica variable (€/m³ de agua consumida) y cuota anual fija de comunidad de regantes (€/año por sector/toma).

Durante el desarrollo de Mágina Olivo 1.0, el subsistema de sincronización offline-first (`sync_outbox`, Room migrations, resolución de conflictos bidireccional) está bajo desarrollo activo por parte de Codex en la iniciativa G2.6 (`OfflineFirstSyncManager`).

Para cumplir estrictamente las **Reglas 1, 7, 11 y 14** de `AGENTS.md` y las directrices de coordinación de equipo:
1. **No colisión de esquema:** B.5 no debe mutar las entidades Room, ni añadir migraciones `MIGRATION_X_Y`, ni generar mutaciones en la tabla `sync_outbox` que puedan colisionar con las migraciones concurrentes de Codex.
2. **Aislamiento multi-cuenta y multi-workspace:** La configuración privada de una explotación nunca debe ser visible, compartida ni sobreescrita al cambiar de cuenta o de workspace.
3. **Persistencia fiable e inmediata en el dispositivo:** Los datos introducidos por el agricultor deben sobrevivir al reinicio del dispositivo, recreación de procesos y cierres inesperados.
4. **Límite explícito de fase:** La configuración de B.5 es **estrictamente local a este dispositivo móvil**. No existe sincronización remota ni replicación entre distintos terminales en esta entrega.
5. **Evolución sin fricción (Forward-Compatibility):** La arquitectura debe prever una migración idempotente y sin pérdida de datos hacia las tablas Room de G2.6 cuando el motor de sincronización de Codex se declare estable.

---

## Decisiones de Diseño

### 1. Persistencia Local Particionada por Ámbito (`FarmerIrrigationStore`)

En B.5, la persistencia se gestiona mediante `AndroidFarmerIrrigationStore` sobre Android `SharedPreferences` privado (`farmer_irrigation_configs`).

#### Partición de Claves por Ámbito (Account / Workspace)
Cada registro se almacena con un prefijo jerárquico determinista:
```text
profile_scope_${scopeKey}_${parcelId}
```
Donde:
- `scopeKey = "${userId ?: "local"}:::${workspaceId ?: "default"}"`
- `parcelId` es el UUID canónico de la parcela.

El separador canónico es `:::` para evitar ambigüedades con formatos de identificadores o UUIDs.

#### Compatibilidad No Destructiva y Recuperación de Datos Previos
Para garantizar que instalaciones previas o datos de pruebas no se pierdan ni se atribuyan indebidamente:
- En lecturas de ámbito local (`local:::` o `default`), el almacén consulta de forma transparente el formato legado `profile_${parcelId}` si la clave particionada aún no existe.
- Las cuentas de usuario remotas autenticadas NO se apropian automáticamente de claves legadas sin una acción explícita.
- Se implementa `migrateLegacyProfilesToScope(targetScope)` que genera copias de respaldo inmutables `profile_backup_legacy_${parcelId}` antes de copiar a la partición de destino, preservando siempre las claves originales.
- Las operaciones de borrado (`removeProfile`) se restringen estrictamente a la partición activa (`profile_scope_${scope}_*`), sin eliminar jamás claves legadas globales ni de otros ámbitos.

#### Ciclo de Vida y Limpieza Segura (`clear()`)
El método `clear()` no destruye el archivo global de preferencias ni borra los datos de otros usuarios o explotaciones. Únicamente purga las claves correspondientes al `currentScope` activo, garantizando que un usuario A no borre las configuraciones del usuario B al cerrar sesión o cambiar de finca (garantía A → B → A).

---

### 2. Aislamiento Finca / Parcela y Deduplicación Determinista de Cuotas

Para evitar fugas de información entre parcelas ajenas o multiplicaciones indebidas de cuotas comunitarias:

1. **Filtro de Pertenencia Estricto:** `IrrigationSummarizer.computeSummary` y `IrrigationViewModel` aplican una intersección estricta con los identificadores de parcelas activas y autorizadas de la finca (`relevantProfiles = profiles.filterKeys { it in activeParcelIds }`). Cualquier configuración huérfana o ajena queda descartada de los cálculos de explotación.
2. **Deduplicación de Cuota por Identidad Inequívoca:** En la comarca de Sierra Mágina, múltiples parcelas de un mismo agricultor integradas en el mismo sector de riego comparten una única toma comunitaria y una única cuota de mantenimiento anual. La agregación de cuotas opera sobre la clave canónica:
   ```text
   canonicalKey = feeIdentifier ?: "${communityName.trim().lowercase()}:::${sector?.trim()?.lowercase().orEmpty()}:::${annualFixedFeeMinor}"
   ```
   - **Misma comunidad, mismo sector y mismo importe:** La cuota anual se imputa **una sola vez** (cuota compartida de sector).
   - **Misma comunidad y mismo sector con importes diferentes o identificador explícito:** Se tratan como **obligaciones independientes legítimas** y se suman ambas (ej. canon de pozo adicional + cuota general de sector).
   - **Comunidades o sectores diferentes:** Se preservan y suman de forma independiente.
3. **Claridad en UI:** Se visualiza explícitamente en la interfaz que la cuota de comunidad es una estimación anual fija, independiente del coste volumétrico variable de campaña.

---

### 3. Cero Mutaciones en Outbox durante la Fase Local

Para garantizar la estabilidad del motor offline-first:
- La navegación por la pantalla de Riego, la consulta de aforos, el ajuste de goteros y el cambio de parámetros en la calculadora interactiva son operaciones de memoria y almacenamiento local particionado.
- **No se generan registros en `sync_outbox`** para configuraciones locales en B.5.
- La única operación que genera sincronización formal hacia el backend es el **registro definitivo de un riego en el Cuaderno** (`ActivityType.IRRIGATION`), utilizando la infraestructura probada y validada de `NotebookRepository`.

---

## Propuesta de Convergencia hacia G2.6 (Codex Sync Engine)

Una vez que la arquitectura G2.6 de sincronización de Codex esté finalizada y fusionada en `main`, la transición de las configuraciones de riego a persistencia distribuida se ejecutará siguiendo estas directrices:

### 1. Entidad Room de Esquema Aditivo (Compatible con Room y Supabase)
Se definirá la entidad Room correspondiente a la tabla remota `parcel_irrigation_profiles`:

```kotlin
@Entity(
    tableName = "parcel_irrigation_profiles",
    primaryKeys = ["parcel_id"],
    foreignKeys = [
        ForeignKey(
            entity = ParcelEntity::class,
            parentColumns = ["id"],
            childColumns = ["parcel_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workspace_id")]
)
data class ParcelIrrigationProfileEntity(
    @ColumnInfo(name = "parcel_id") val parcelId: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "drippers_per_tree") val drippersPerTree: Int?,
    @ColumnInfo(name = "dripper_flow_rate_lph") val dripperFlowRateLph: Double?,
    @ColumnInfo(name = "system_type") val systemType: String?,
    @ColumnInfo(name = "water_tariff_eur_per_m3") val waterTariffEurPerM3: Double?,
    @ColumnInfo(name = "community_annual_fee_eur") val communityAnnualFeeEur: Double?,
    @ColumnInfo(name = "scheduled_turns_json") val scheduledTurnsJson: String?,
    @ColumnInfo(name = "aforo_liters_per_tree") val aforoLitersPerTree: Double?,
    @ColumnInfo(name = "version") val version: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.SYNCED
)
```

### 2. Proceso de Migración en Caliente Idempotente (`WarmMigration`)
Al inicializar la aplicación o al autorizar por primera vez un workspace bajo G2.6:
1. Un componente `FarmerIrrigationStoreMigrator` inspecciona las claves locales de `AndroidFarmerIrrigationStore`.
2. Para cada perfil presente en el ámbito del workspace:
   - Si no existe registro en Room para esa `parcel_id`, se inserta la entidad con `sync_status = PENDING`.
   - Se programa una tarea de outbox `UPSERT_IRRIGATION_PROFILE`.
3. **Retención de Respaldo:** Las entradas de `SharedPreferences` NO se eliminan inmediatamente; se conservan como respaldo local hasta que la sincronización confirme un recibo exitoso del servidor.

### 3. Reconciliación Conservadora (No Last-Write-Wins Automático Ciego)
- **Riesgo evitado:** El uso de Last-Write-Wins (LWW) ciego basado en relojes locales de clientes móviles puede causar la sobreescritura accidental de horarios de turnos nocturnos o tarifas pactadas entre distintos miembros de la misma explotación.
- **Estrategia propuesta:**
  - Control de concurrencia basado en **versiones monótonas (`version: Long`)** y marcas de tiempo de servidor.
  - Si se detecta un conflicto concurrente entre el estado local y el estado remoto de una parcela, el sistema **no sobreescribe automáticamente**: marca la entidad para resolución o solicita confirmación explícita del agricultor.

---

## Consecuencias y Garantías

### Positivas
- **Cero bloqueos:** Desarrollo y validación inmediata de B.5 sin dependencias bloqueantes de PRs de sincronización concurrentes.
- **Seguridad y Confidencialidad:** Total aislamiento de perfiles de riego entre distintos workspaces y cuentas de usuario en el mismo dispositivo físico.
- **Rendimiento Óptimo:** Lecturas y escrituras sincrónicas en memoria/SharedPreferences con latencia < 2ms, sin contención de transacciones en la base de datos principal de Room.
- **Transparencia:** Límites de la entrega local claramente explicitados sin falsas promesas de sincronización entre terminales.

### Riesgos Mitigados
- **Riesgo:** Pérdida de configuraciones al alternar sesiones.  
  *Mitigación:* Partición indexada en `scopedProfiles` y `profile_scope_${scope}_*`, preservando configuraciones intactas en transiciones A → B → A.
- **Riesgo:** Distorsión del balance económico por duplicación o descarte de cuotas comunitarias.  
  *Mitigación:* Deduplicación canónica basada en `(comunidad, sector, importe/id)`.
