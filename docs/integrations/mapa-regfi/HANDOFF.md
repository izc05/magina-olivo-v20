# Handoff de preparación REGFI-MAPA — #681 / #688

Fecha: 2026-10-08. PREP completado para revisión; aprobación/merge de #688 y gate productivo son decisiones independientes. No crear ni desplegar la función en este slice.

## Contrato y evidencia

La especificación normativa de proyecto es [CONTRACT.md](CONTRACT.md): POST oficial exacto, transporte doblemente serializado, validación íntegra, normalización, variantes por registro, checksum UTF-8 de Contenido y sourceVersion = Fecha + checksum. Los campos proceden de la descarga observada; el PDF descriptivo falló con HTTP 500. No se afirma garantía de estabilidad del proveedor ni corroboración del PDF.

[fixtures/README.md](fixtures/README.md) identifica tres fixtures sintéticas: cobre y segundo producto, cancelación/desaparición y variantes contradictorias. [QA.md](QA.md) define T01–T20; los cinco tests actuales verifican fixtures, no backend.

## Archivos exactos del siguiente slice autorizado

Crear bajo `supabase/functions/phytosanitary-catalog/`:

| Archivo | Responsabilidad |
| --- | --- |
| contract.ts | Tipos de catálogo, variante, uso y respuesta de lectura |
| mapa.ts / mapa.test.ts | Parser acotado, validación, normalización, checksum y búsqueda |
| catalog-store.ts | Interfaz durable; objetos inmutables y puntero CAS |
| refresh.ts / refresh.test.ts | Staging, cuarentena, idempotencia y conservación LKG |
| handler.ts / catalog.test.ts | Lectura paginada fijada a versión y freshness |
| index.ts | Entry Edge de lectura, sin descarga MAPA por petición |
| fixtures/*.synthetic.json | Copias selectivas de fixtures PREP, claramente sintéticas |

Modificar `supabase/functions/README.md` únicamente en esa PR futura. No crear ahora esos archivos. El [plan](../../superpowers/plans/2026-10-07-mapa-regfi.md) contiene interfaces y asignación de T01–T20.

## Decisiones que exigen evidencia antes de implementación/despliegue

- Medir RSS, tiempo y tamaños con los 86 MB exteriores / 50 MB interiores observados. La ingestión irá en worker si Edge no demuestra margen; lectura Edge separada.
- Elegir adaptador durable, ubicación de artefactos y operación transaccional CAS. Toda migración/configuración necesaria se especificará en la PR futura tras elegir recurso; no inventar un nombre de tabla antes de esa decisión.
- Definir autorización de lectura y operación interna privilegiada de refresh. Endpoint upstream fijo del servidor; ninguna URL arbitraria, secreto o refresh ordinario desde Android.
- Verificar recuperación tras caída antes/después de CAS: candidato no publicado no afecta current; lectura sigue sirviendo anterior/nuevo íntegro. Sin LKG, 503; upstream inválido nunca produce 200 vacío.
- Política propuesta: revisión diaria, retry acotado, STALE ante fallo o antigüedad >8 días; retirada no reescribe snapshots. Solo Fecha+checksum idénticos dan UNCHANGED.

El catálogo es referencia externa; Sync interno y aceptación oficial CUE permanecen separados. No se modifica Android ni se afirma conexión con la Junta. No se libera producción por completar este PREP.
