# #696 — aislamiento de archivos de fixtures Android

Rama `fix/696-isolate-attachment-test-files`, base main `c2556e4e`, 2026-10-08. Durante E2E, la suite offline conservó DBs manuales pero ExpenseLedgerContractTest eliminó la foto QA porque borraba la carpeta global files/attachments. La comprobación inicial de foto en upgrade queda invalidada; no atribuir el borrado a install-r ni aceptar una foto perdida.

Causa confirmada también en AttachmentContractTest, DeliveryContractTest y OfflineFirstFarmCoverRepositoryTest: setup/teardown borraban las raíces globales de adjuntos/cámara. Regression AttachmentFixtureIsolationTest: cuatro fallos API35 antes del arreglo, archivos ajenos eliminados por setup. Evidencia artifacts/696-e2e/isolation-red.txt.

Arreglo exclusivo androidTest: ContextWrapper por fixture, archivos bajo attachments/test-fixtures y camera/test-fixtures; también RecollectionFlow conserva sus archivos separados. Room mantiene bases diferenciadas. FileProvider recibe applicationContext para resolver/cachear las raíces originales, con las fotos dentro de esas raíces. No cambia código productivo, esquema ni persistencia/Sync.

Validación inicial: 524 unitarios y AndroidTest/lint BUILD SUCCESSFUL; recompilación final y regresiones pendientes al escribir este snapshot. Revisión independiente pidió conservar contexto base FileProvider: incorporado. No integrar hasta revisión final y tres checks verdes. Foto QA nueva 733ebe61-da29-41dd-be5c-38e91d70d3f8, SHA256 85fa29fa5e62f61117f32b9a3f38493884b9782b6e6091931a1e2f70c3f6112d: comprobar antes/después de suite y actualización APK. La anterior se conserva como registro QA no disponible, para no ocultar el incidente.

NEXT: completar validación, integrar según gates y repetir conservación del adjunto. Web V3 pausada; Gate21 físico pendiente.
