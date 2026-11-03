# Línea base de las pruebas de contrato antiguas (Jornadas, Pesadas y Jornadas manuales), 2026-11-03

> **Corrección (auditoría #841):** el recuento de 47 de 113 de este documento era un artefacto de una salida truncada. El recuento crudo por prueba (`am instrument -r`) da **109 de 113** fallos (Jornada 11/11, Delivery 84/87, Harvest 14/15), así que la comparación «idéntica» de abajo tampoco es válida.
>
> **Recuperadas (2026-11-03):** 114/114 (113 históricas + 1 nueva), sin tocar producción. Ver `docs/06-testing/CONTRACT-TESTS-RECOVERY-2026-11-03.md`.

- **Encargo del propietario (#837):** documentar los fallos de `JornadaPesadasContractTest`, `DeliveryContractTest` y `HarvestContractTest` comparándolos con la base anterior, sin deshabilitar ni relajar ninguna aserción.
- **Ejecución:** emulador de QA aislado `MaginaOlivo_LocalQA_Disposable_API35` (API 35, limpio, único dispositivo, con la guarda superada), con `pm clear` antes de cada pasada y las mismas tres clases en las dos versiones.

## Resultado

| Versión | SHA | Pruebas | Fallos |
| --- | --- | --- | --- |
| Base anterior a J (R3-b) | `d8ce433df` | 113 (11 + 87 + 15) | **47** |
| Esta rama (J + corrección del DAO) | HEAD de `codex/g26-808-r3a-dao-fix` | 113 | **47** |
| R3-c (receptor) | HEAD de `codex/g26-808-r3c-receiver` | 113 | **47**, la misma lista exacta (`diff` vacío) |

**Los dos conjuntos de pruebas fallidas son idénticos** (la diferencia está vacía en ambos sentidos), con la misma causa en cada prueba. **Ninguno es una regresión de J ni de la corrección del DAO.**

| Clase | Fallos | Causa |
| --- | --- | --- |
| `HarvestContractTest` | 0 de 15 | — |
| `JornadaPesadasContractTest` | 11 de 11 | `Validation(field=workspaceId, code=selection_required)` al crear la primera fila |
| `DeliveryContractTest` | 36 de 87 | 35 por `selection_required` (32 «Expected success…», más 3 «Expected validation on …» que reciben antes ese error de workspace); 1 `NullPointerException` (`confirmingATicketRecordsTheReviewedValuesOnceAndFreezesTheReading`, `DeliveryContractTest.kt:540`): `deliveries.observe(id).first()` devuelve `null` tras confirmar un ticket por OCR. Encaja con la misma falta de selección de workspace, pero esta causa concreta **no se ha verificado en detalle**. El fallo es idéntico en la base anterior a J |

**Causa común:** estas clases construyen los repositorios sin la **selección de workspace** de G2.6 (`ActiveWorkspaceScope` exige una selección explícita por issuer y cuenta), así que la validación de alcance falla antes de llegar a cualquier regla de Pesadas o Jornadas. **No forman parte del workflow QA de #808**: solo `android-ci.yml`, en PR a `main` o push a `feat/**`, ejecuta `connectedAndroidTest`.

## Pruebas que cambiarán de expectativa cuando se arreglen (enmienda J de CR-010)

- `JornadaPesadasContractTest.aDaysOriginIsTheUnionOfItsPesadasParcelsWithoutASplit`: con la regla J, un `AUTO_DAY` con participaciones exactas tiene un reparto `EXACT` por parcela; ya no es «sin reparto».
- Las que daban por supuesto que los kilos de una Jornada MANUAL siguen a sus Pesadas (Phase 19B): con J-D2 se conservan los kilos tecleados.

**No se ha deshabilitado ni relajado ninguna aserción.**

**Recomendación:** una corrección aparte, con su propio GO, que haga dos cosas:
1. añadir la selección de workspace al montaje de estas pruebas, igual que hacen las pruebas Room de #808;
2. actualizar, de forma explícita y documentada, las expectativas que cambian con la enmienda J.

## Pruebas fallidas (idénticas en las dos versiones)

- `aConfirmedTicketGoesToItsAutomaticDayAndNeverToAHandRecordedJornada (DeliveryContractTest)`
- `aDayKeepsItsParcelRowsAndNamesWhenItsPesadasChange (JornadaPesadasContractTest)`
- `aDayWithWhatTheFarmerTypedOnItStaysAndItsOriginIsNoLongerDetermined (JornadaPesadasContractTest)`
- `aDaysOriginIsTheUnionOfItsPesadasParcelsWithoutASplit (JornadaPesadasContractTest)`
- `aDeliveryIsItsOwnRecordAndNeedNotMatchTheHarvest (DeliveryContractTest)`
- `aHandRecordedJornadaIsNeverReusedAndKeepsItsOwnKilos (JornadaPesadasContractTest)`
- `aJornadaOpenedBeforeAnyPesadaStartsAtZeroAndThenCarriesItsPesadas (JornadaPesadasContractTest)`
- `aLaterYieldNeverChangesTheOriginalDelivery (DeliveryContractTest)`
- `aPesadaIsNeverDatedAfterItsYieldAnalysis (DeliveryContractTest)`
- `aPesadaLinkedBeforeCr010KeepsItsJornadaUntilItsDateChanges (JornadaPesadasContractTest)`
- `aYieldAddedDaysLaterChangesOnlyTheYieldRecordAndTheDerivedMetrics (JornadaPesadasContractTest)`
- `anAutomaticDayKeepsItsDateKilosAndParcelsWhenItsFormIsSaved (JornadaPesadasContractTest)`
- `anAutomaticDayNeverKeepsKilosItNoLongerHas (JornadaPesadasContractTest)`
- `anExactSplitMustReconcileWithTheDeliveredKilos (DeliveryContractTest)`
- `anOlderAutomaticDayWithoutPesadasLosesItsPresumedOriginOnStart (JornadaPesadasContractTest)`
- `confirmingATicketRecordsTheReviewedValuesOnceAndFreezesTheReading (DeliveryContractTest)`
- `editingAPesadaKeepsItsParcelRowsAndTheirRecordedNames (DeliveryContractTest)`
- `g24AConsentedCorrelatedAckIsAppliedExactlyOnce (DeliveryContractTest)`
- `g24AccountBindingUsesConsentAndDoesNotAdoptLegacyOrLoggedOutEdits (DeliveryContractTest)`
- `g24AckForAConsentedOperationIsNotHeld (DeliveryContractTest)`
- `g24AnAckAfterRevocationIsCorrelatedThenHeldAndTheDeliveryIsUntouched (DeliveryContractTest)`
- `g24AnInterruptedPreparedDispatchSettlesAsUnknownOnlyForTheSameLockOwner (DeliveryContractTest)`
- `g24BlockedWithoutReceiptKeepsTheSameOperationWhenTheServerResultIsUnknown (DeliveryContractTest)`
- `g24EditsAfterRevocationStayUnboundAndKeepOldIntentBlocked (DeliveryContractTest)`
- `g24LateAckAfterRevocationIsHeldOnceWithoutTouchingTheDelivery (DeliveryContractTest)`
- `g24LateConfirmationQuarantinesNewerSnapshotWithoutLosingKilos (DeliveryContractTest)`
- `g24LeaseHandoffRecoversSameOperationAndRejectsWrongNamespaceOrRevocation (DeliveryContractTest)`
- `g24NeedsRebaseWithARevocationAndNoReceiptIsItsOwnTerminalOutcome (DeliveryContractTest)`
- `g24ParityAnInterruptionBetweenTheReceiptAndTheOutboxLeavesNoHalfState (DeliveryContractTest)`
- `g24ParityAnUnknownOutcomeStillAcceptsItsLateAckExactlyOnce (DeliveryContractTest)`
- `g24ParityRePrepareRejectsAChangedRemoteWorkspaceOrConsentTextVersion (DeliveryContractTest)`
- `g24ParityTwoConsecutiveCrashesKeepTheLatestLeaseMarkerAndStayRecoverable (DeliveryContractTest)`
- `g24PesadaSavesAndConsentRevokesWhileAuthRefreshIsStuck (DeliveryContractTest)`
- `g24PrepareRecordsIdentityConsentAndImmutableBytesAndClaimsTheOperation (DeliveryContractTest)`
- `g24ReconciliationNeedsTheLeaseAndTheSameAccountThroughout (DeliveryContractTest)`
- `g24RejectsWrongAccountFarmAndPayloadWithoutMutatingLocalData (DeliveryContractTest)`
- `g24RenewalFollowedByAnotherRevocationDoesNotReconcile (DeliveryContractTest)`
- `g24RevokedConsentAndAlteredHashCannotUnlockEdits (DeliveryContractTest)`
- `g24WorkerAHeadWithAnotherHashIsAConflictAndNeverSynced (DeliveryContractTest)`
- `g24WorkerALostResponseLeavesTheOutcomeUnknownAndRecoversByTheSameUuid (DeliveryContractTest)`
- `g24WorkerAnExpiredTokenNeverIssuesTheRequestAndWritesNothing (DeliveryContractTest)`
- `g24WorkerRefusesAPayloadV1OperationBeforeOpeningAnyConnection (DeliveryContractTest)`
- `newPesadasRequireOriginAtRepositoryBoundaryAndLegacyNullSurvivesEdit (DeliveryContractTest)`
- `pesadaAndYieldUseWorkspaceCalendarAtMidnightBoundaries (DeliveryContractTest)`
- `reopenedHistoricalCampaignKeepsPesadasInsideItsOriginalBoundary (DeliveryContractTest)`
- `threePesadasOfOneDayToTwoCooperativesSurviveRestartAsOneTruthfulJornada (JornadaPesadasContractTest)`
- `unallocatedParcelSnapshotKeepsUnknownWeightAsNull (DeliveryContractTest)`
