# #692 — consolidación de etiquetas de fotos

Slice P0.3 de #696, 2026-10-08. Rama `fix/517-attachment-photo-labels`; PR https://github.com/izc05/magina-olivo-v20/pull/692. Relevo previamente autorizado en la PR; ningún otro worktree ejecuta esta rama. Main `690e2195` incorporado sin conflictos (merge `0e293fe3`), después de integrar #689/#680/#684 con revisión y tres checks verdes.

Diff productivo heredado: etiquetas humanas de capturas internas por propietario/fecha, numeración estable por createdAt/ID; lista, vista previa accesible y confirmación usan igual etiqueta. Abrir/eliminar siguen usando IDs/URIs originales y displayName persistido no cambia. Nombres elegidos y PDF intactos; sin migración ni cambios de datos/Sync.

Validación actual: 524 unitarios sin fallos/errores; APK DEV y AndroidTest compiladas. API35 modo avión: AttachmentsSectionTest/AttachmentContractTest/OfflineFirstFarmCoverRepositoryTest OK (22 tests). Actualización install-r sin desinstalar conserva finca, parcela1ha, campañaQA_2026 y pesada2850kg en Inicio. testDevDebugUnitTest/assembleDevDebug/assembleDevDebugAndroidTest/lintDevDebug BUILD SUCCESSFUL (6m15s). Revisión independiente de todo el diff sin bloqueos; diff check limpio. Evidencia local artifacts/696-692. El documento original docs/06-testing/ISSUE-517-ATTACHMENT-PHOTO-LABELS.md describe la preparación anterior; #696 autoriza ahora consolidar/integrar tras los gates.

No fusionar hasta foundation/gate3-emulator/gate3-evidence verdes en el HEAD final. #517 conserva aceptación física pendiente; este slice acredita integración técnica, no aceptación en móvil. NEXT recorrido agrícola sobre main con las cuatro PRs integradas y APK candidata. Web V3 pausada; sin firma release/Gate28.


