# CR-015 — AUDITORÍA TÉCNICA DEFINITIVA P2: CIERRE Y CONVERGENCIA

**Fecha:** 06/11/2026  
**Autor:** Antigravity (Auditoría Técnica Independiente)  
**Destinatarios:** Propietario del Producto, Codex (Integración y Desarrollo Android)  
**Documento base evaluado:** `docs/superpowers/plans/2026-11-06-cr015-p2-technical-proposal.md` (HEAD `5dd46664f`)  
**Contexto de partida:** PR #857 (P1, commit `cddf94ecf`) cerrada y aprobada en verde; issue #855 y especificación funcional CR-015.  
**Estado:** **AUDITORÍA CERRADA · RECOMENDACIÓN DE GO PARA P3 LOCAL**.

---

## 1. Resumen Ejecutivo

Esta auditoría técnica definitiva consolida y cierra los análisis previos de Antigravity sobre:
1. Modelo de ciclos agrícolas con fechas flexibles independientes por finca.
2. Invariantes de integridad en base de datos local (Room v34) y contratos de migración.
3. Compatibilidad con el motor de sincronización manual G2.6 y desacoplamiento de red.
4. Máquina de estados desacoplada: Cierre Agronómico vs. Regularizaciones Económicas.
5. Modelo de ingresos, anticipos, liquidaciones de cooperativa y deduplicación contable.
6. Tratamiento estricto de conflictos offline y no-reasignación automática.

### Veredicto Técnico
Tras el contraste minucioso de la propuesta técnica elaborada por Codex (`5dd46664f`) frente a los principios de CR-015 y las directrices del propietario, **se certifica que la propuesta técnica de P2 de Codex ha incorporado satisfactoriamente los criterios clave y NO presenta contradicciones arquitectónicas**.

La propuesta P2 queda **homologada y lista para recibir la autorización de implementación local (P3)** bajo las condiciones y salvaguardas que se detallan a continuación.

---

## 2. Validación de las Siete Decisiones Obligatorias de Producto

| # | Decisión Obligatoria de Producto | Estado en la Propuesta P2 de Codex | Evaluación de Antigravity |
|---|---|---|---|
| **1** | **Ciclos independientes por finca y cierre confirmado:** Cada finca tiene su propio ciclo agrícola. El fin de recolección confirmado marca el cierre agronómico de la campaña. | Incorporado en §§2 y 3. El ciclo es por `farm_id`. | **CONFORME.** Se garantiza que una finca en campiña y otra en sierra tengan calendarios autónomos. |
| **2** | **Prohibida la reasignación automática:** Ni en histórico ni en conflictos offline el sistema moverá registros entre campañas silenciosamente. | Incorporado explícitamente en §§4 y 6 tras subsanación. | **CONFORME.** Todo conflicto o ambigüedad retiene el apunte y exige intervención explícita del usuario autorizado. |
| **3** | **Última pesada como sugerencia, nunca imposición:** La fecha del último porte se propone por comodidad visual, pero el agricultor elige y confirma la fecha efectiva. | Incorporado en §3.3 y matriz §7. | **CONFORME.** Se diferencia `effective_end_date` (decisión de usuario) de `closed_at` (auditoría del sistema). |
| **4** | **Asociaciones explícitas por UUID, no por rango septiembre–agosto:** Los registros se vinculan por ID; `OliveSeason` queda relegada a agrupación histórica de lectura. | Incorporado en §§2 y 5 mediante tablas y claves foráneas aditivas. | **CONFORME.** Elimina el riesgo de que al rectificar fechas del ciclo se altere dinámicamente la imputación de gastos o labores. |
| **5** | **Verificación empírica de versión Room (sin prejuzgar v35):** Constatación real antes de migrar. | Constatada v34 sobre P1 en §1; compromiso de revalidar en P3. | **CONFORME.** La versión activa en `MaginaOlivoDatabase.kt` es la 34 (`MIGRATION_33_34`). P3 definirá el incremento contra el HEAD real. |
| **6** | **Modelo económico sin duplicación:** Distinción de devengo (bruto) vs. cobro (líquido), retenciones (IRPF), cuotas de cooperativa y gastos. Sin reparto heurístico multifinca. | Aceptado como frontera de diseño en §5; P5 segregada formalmente. | **CONFORME.** Los anticipos no inflan ingresos reconocidos; las liquidaciones multifinca no se prorratean de forma forzada. |
| **7** | **Preservar G2.6 y autorizaciones independientes (P3 / P4 / P5):** P3 es solo local; P4 requerirá diseño de transporte y contratos Supabase; P5 regulará liquidaciones. | Aceptado íntegramente en §§1, 4 y 8. | **CONFORME.** Cero impacto en los emisores y receptores validados de la PR #852 / #853. |

---

## 3. Puntos Críticos y Análisis de Riesgos Residuales

### 3.1. Tablas Laterales vs. Columnas Nullable en Entidades Existentes (Decisión P3)
- **Propuesta de Codex:** Sugiere tablas de asociación aditivas (e.g., `cycle_activities`, `cycle_expenses`) para no modificar los DTOs y hashes de sincronización actuales de G2.6.
- **Dictamen de Auditoría:** Es una estrategia muy segura para no quebrar G2.6 en local. No obstante, en la fase de implementación (P3) debe vigilarse que las consultas agregadas de lectura (Joins) no penalicen el rendimiento en dispositivos de gama baja (Android Go / 360 dp). Una columna `agricultural_cycle_id UUID?` en la entidad local con índice secundario es igualmente inocua para G2.6 si el serializador de red la ignora, pero la opción de tabla lateral es aceptable si aísla completamente los tests de serialización existentes.

### 3.2. Cierre Agronómico vs. Asentamiento Financiero Posterior
- El sector cooperativo liquida a 6–12 meses vista. Queda ratificado el desacoplamiento:
  $$\text{Estado Agronómico} \in \{\text{OPEN}, \text{CLOSED}\} \quad \perp \quad \text{Estado Financiero} \in \{\text{PENDING\_SETTLEMENT}, \text{SETTLED}\}$$
- Una campaña con cierre agronómico confirmado bloquea la adición de nuevas pesadas y jornales de campo, pero **mantiene habilitado su balance para imputar liquidaciones y cobros complementarios** sin obligar al agricultor a reabrir la recolección.

### 3.3. Prevención de Doble Imputación de Mano de Obra
- La única fuente canónica de costes monetarios en la app es `ExpenseEntity` en estado `POSTED`.
- La regla gobernada por `DayCostLedger` debe preservarse rigurosamente en P3: los peones y tractores calculados por jornada (`DAY_LABOUR`, `DAY_EQUIPMENT`) nunca deben sumarse dos veces si el usuario introduce una factura de sustitución (`DAY_REPLACEMENT`).

---

## 4. Matriz de Pruebas de Aceptación para P3 (Fase Local)

Para que la futura implementación P3 se considere aprobada antes de plantear la sincronización remota (P4), deberán cumplirse estas pruebas en emulador/dispositivo:

1. **`test_flexible_cycle_effective_date_boundary`:**
   - Finca A cierra recolección 2026/27 indicando fecha efectiva 25/01/2027.
   - Se registra una poda con fecha 26/01/2027.
   - *Verificación:* La poda queda asignada inequívocamente al nuevo ciclo 2027/28; el ciclo 2026/27 permanece cerrado agronómicamente con fecha de corte 25/01.
2. **`test_last_delivery_suggestion_not_enforced`:**
   - Última pesada registrada el 15/01/2027.
   - El formulario de cierre sugiere 15/01/2027, pero el agricultor corrige manualmente a 20/01/2027 (ej. labores finales de repaso).
   - *Verificación:* La fecha efectiva registrada es 20/01/2027; ninguna validación bloquea la entrada.
3. **`test_historical_records_immutability_on_migration`:**
   - Base de datos con historial Room v34 (campañas y labores previas sin ciclo explícito).
   - Ejecución de la migración local aditiva.
   - *Verificación:* Todos los registros históricos conservan sus UUIDs, importes y fechas intactos. No se produce ningún *backfill* heurístico que intente vincularlos por meses de calendario.
4. **`test_offline_reconciliation_zero_silent_reassignment`:**
   - Conflicto simulado entre estados de ciclo locales.
   - *Verificación:* El motor de resolución retiene ambos estados, emite alerta de conflicto y no mueve ninguna labor o gasto de ciclo sin confirmación visual del usuario.
5. **`test_settlement_post_closure_intact_deliveries`:**
   - Ciclo 2026/27 cerrado agronómicamente con 45.000 kg entregados.
   - Seis meses después se añade una liquidación cooperativa de 12.000 €.
   - *Verificación:* Los 45.000 kg y sus jornadas permanecen inmutables; el balance económico refleja los ingresos reconocidos y actualiza el margen de la campaña.
6. **`test_standalone_advance_reconciliation`:**
   - Anticipo de 4.000 € cobrado en diciembre (sin liquidación emitida).
   - Liquidación total emitida en mayo por 11.000 € netos que absorbe el anticipo.
   - *Verificación:* Los ingresos reconocidos computan exactamente 11.000 € (no 15.000 €); el pendiente de cobro en banco se establece en 7.000 €.

---

## 5. Delimitación de Fases y Responsabilidades

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             ESTADO DE LAS FASES                             │
├─────────────────────────────────────────────────────────────────────────────┤
│ • P0 (Auditoría de Impacto)       : APROBADA (PR #856)                      │
│ • P1 (Navegación y Campaña UI)   : COMPLETADA Y VERIFICADA (PR #857, cddf9)│
│ • P2 (Diseño Técnico de Ciclos)   : HOMOLOGADA / CERRADA (Este documento)   │
├─────────────────────────────────────────────────────────────────────────────┤
│                     PRÓXIMAS FASES (Requieren GO)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│ • P3 (Implementación Local)       : LISTA PARA AUTORIZAR (Codex)            │
│ • P4 (Sincronización Supabase)    : EN ESPERA (Diseño RPC / Contratos)      │
│ • P5 (Liquidaciones y Economía)   : EN ESPERA (Fase financiera específica)  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Conclusión y Recomendación Final

1. **Convergencia alcanzada:** Las observaciones de Antigravity sobre la autonomía de fechas por finca, la prohibición de reasignación automática, el aislamiento del transporte G2.6 y el desacoplamiento económico han sido plenamente integradas por Codex en la propuesta técnica de P2.
2. **Recomendación:** Se recomienda al Propietario del Producto emitir **AUTORIZACIÓN FORMAL (GO) para P3**, asignando su desarrollo a Codex en una rama independiente basada en el HEAD de P1 (`cddf94ecf`), asegurando el avance incremental y seguro de Mágina Olivo 1.0.
