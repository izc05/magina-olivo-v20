# CR-015 P1 — revisión de interfaz

Base validada: `1c2a4574ea73b044d5937578afe37f1cb3745924` (#853).
Alcance autorizado y plan: #855 / #856. PR independiente apilada sobre #853.

El propietario aprobó las 12 capturas de P1 en `d43d46cfafab4eadc692e14022dd071c64d3613b`.
Se conserva ese diseño; el cierre funcional solo admite correcciones mínimas de funcionamiento
o accesibilidad, sin rediseño, cambios de persistencia ni ampliación de funcionalidades.

Orden sugerido para revisar la APK DEV:

1. **Inicio:** campana, avatar e IA en cabecera; la tarjeta de recolección abre Campaña.
2. **Mi Campo:** fincas, superficies, parcelas, olivos y próxima tarea. La ficha conserva
   mapas, parcelas, Cuaderno, documentos y gestión de recolecciones; añade «Ver campaña de esta finca».
3. **+ / Cuaderno:** seis formularios existentes. Diario, Fitosanitario y Gastos mantienen
   los mismos registros; Campaña tiene ahora su propia raíz.
4. **Campaña:** Trabajos del año, Recolección, Economía y balance, Gráficas e históricos.
   Cambiar finca, consultar recolección cerrada, volver atrás y cerrar/reabrir la aplicación.
5. **Todas mis fincas:** resumen de consulta e informe existente por periodo; incluye
   histórico archivado y declara por separado los gastos sin finca.

Límites visibles de P1:

- Agrupación provisional fija de septiembre a agosto. No hay ciclos agrícolas flexibles.
- Trabajos y gastos por finca muestran su historial completo, sin reasignarlo al periodo.
- El informe global conserva su ámbito temporal y sus monedas; «Sin asignar» no pertenece a una finca.
- Ingresos pendientes de registrar; balance incompleto. No hay beneficio estimado.
- No cambia Room, escritores, sincronización, Supabase ni liquidaciones.

La validación y los artefactos de revisión se documentan en la PR. Las pruebas usan
datos ficticios y un emulador desechable; la APK de revisión se actualiza con `install -r`,
comprobando antes la firma y conservando los datos del emulador de integración.
