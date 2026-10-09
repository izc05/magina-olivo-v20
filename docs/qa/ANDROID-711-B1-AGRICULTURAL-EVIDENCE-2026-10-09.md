# #711 B0/B1 — contexto agrícola y conservación de datos

Android únicamente. Web V3 pausada. Gate21 físico pendiente. Esta evidencia distingue las ejecuciones de emulador de la aceptación del propietario.

## B0 integrado

PR [#712](https://github.com/izc05/magina-olivo-v20/pull/712), rama `codex/696-campaign-expense-context`, HEAD final `92fff335241376ecca13dd4bcab6bcfa1a3034d6`. Merge en main `45e21fe05624ba68a6020c43258ef75e1351c700`, 9 octubre 2026 04:42:29 UTC. Revisión independiente final conforme.

Tres checks SUCCESS sobre ese HEAD: foundation 9m1s y gate3-emulator 18m27s en [Android CI 37883277052](https://github.com/izc05/magina-olivo-v20/actions/runs/37883277052); gate3-evidence 23m22s en [Evidence 37883276035](https://github.com/izc05/magina-olivo-v20/actions/runs/37883276035). Ambos logs leídos: 690 completas, 196 offline y cuatro recorridos de 14 pruebas, todos correctos. Local: lint, 524 JVM sin fallos, tres variantes y test APK correctos; 42 instrumentadas dirigidas PASS / 167.581s. Ver [corrección del contexto](ANDROID-411-CAMPAIGN-EXPENSE-CONTEXT-2026-10-09.md).

## B1: pruebas actuales y UI conservada

La APK y test APK locales del HEAD anterior ejecutaron 284 instrumentadas agrícolas PASS / 160.303s en API35 desechable `emulator-5580`, con modo avión confirmado en 1. Se restauró la red al terminar. Incluye contratos Room finca/campaña, trabajos tipados, portadas/adjuntos, ledger, jornadas, pesadas, maquinaria, recordatorios, pagos, históricos, dos fincas, contexto/búsqueda de pesadas, totales desconocidos y pagos UI. No sumar las 42 dirigidas como cobertura distinta ni atribuir estas fixtures al AVD conservado.

AVD conservado `emulator-5566`, APK DEV 1606 antes de actualizar:

| Comprobación | Resultado verificado |
| --- | --- |
| Mi Campo | 2 fincas, 3 parcelas, 3,5 ha; olivos desconocidos `—` |
| Resumen global | 5.200 kg; recogida 225 €, general 12 €, total 237 €; rendimiento 20 % con cobertura del 22 % de los kilos |
| Búsqueda | `QA_FUERA` filtra a una finca; detalle 2 ha / 1 parcela / sin campaña / olivos `—` |
| Contexto F2 | Cuaderno muestra su poda y gasto general de 12 €, sin registros F1 |
| Cambio a F1 | Cambiar finca desde la pestaña Cuaderno muestra `QA_684`, campaña activa C2 y su propio diario |
| Histórico C1 | Cerrada, 4.000 kg / 2 pesadas / rendimiento 20 % sobre el 28 % de los kilos; jornales 80 €, maquinaria 50 €, otros 25 €, total 155 €; pagos 50 €, pendiente 30 € |
| Campaña C2 | 1.200 kg, media jornada 40 € + 3 horas 30 €, pagados 15 €, pendiente 55 €; rendimiento desconocido, pendiente de análisis |

Copia Room: `integrity_check=ok`, `foreign_key_check` sin incidencias. C1 conserva un snapshot de parcela; C2 tiene dos. Los vales `QA-V1`, `QA-V2`, `QA-C2-V1`, cooperativas y orígenes TREE/GROUND/TREE son correctos. Cada pesada actual tiene una única parcela de origen: su atribución EXACT coincide con el peso total y es válida. Las cargas mixtas con reparto NULL permanecen UNALLOCATED en `DeliveryContractTest`, incluido en las 284 pruebas; no atribuir desconocimiento a una pesada de origen único.

Cuatro Expense POSTED suman 225 € de recogida; el gasto general de F2 suma 12 €. C2 agrega sus dos jornales en un Expense de 70 €: los pagos no generan otro coste. Snapshots FULL_DAY/HALF_DAY de 80 €/día y HOURS de 180 minutos a 10 €/hora conservados. Auditorías sobre copias, sin escribir en Room viva.

## Actualización real sin borrar datos

Tras integrar #712, actualización con `install -r` de DEV 1606 a 1609, sin desinstalar ni downgrade. Se usó la APK del HEAD revisado de la PR, no se presenta como artefacto compilado desde main ni como candidata final.

Origen: run 37883277052, artifact `magina-olivo-dev-debug`, ID `11594988748`, caduca 23 octubre 2026 04:27:43 UTC. APK original SHA256 `9002b9946072abfd119851c1000b4b02ab121e1497efc5e55dd0ea3824030e6f`. Copia firmada con certificado DEV local compatible con el AVD: SHA256 `d529fb9ee3377b1fec096332a767181c7c698456338937ec476937c3b7cbd262`; las 549 entradas fuera de META-INF son idénticas al original. La compatibilidad con el certificado del teléfono sigue sin comprobar.

Respaldos antes/después de instalar: SHA256 idéntico `349da2a30f06cf1810d4e04701525d3c33674d09958735095e2d589f3568d7da`. Foto `733ebe61-da29-41dd-be5c-38e91d70d3f8.jpg`: SHA256 `85fa29fa5e62f61117f32b9a3f38493884b9782b6e6091931a1e2f70c3f6112d`. Auditorías posteriores mantienen todos los totales, snapshots y valores anteriores.

UI actualizada: Cuaderno → C2 → Otros gastos / Ver gastos → Añadir gasto muestra `Recogida · Campaña QA_C2_M1`, sin selector «Fuera de campaña» ni elección de finca. Formulario cancelado sin introducir ni guardar datos; el guardado se cubre en las regresiones instrumentadas de #712.

## Evidencia y pendientes

Logs y copias locales en `artifacts/696-e2e/`: `711-b1-offline-instrumentation.txt`, `711-b1-classes.txt`, `audit-half-hours.py`, `audit-711-origin.py`, `711-b1-origin-audit-final.txt`, `retained-711-{before,after}-pr712-upgrade.tar`, `712-final-checks.json`. XML `711-b1-*` y `711-pr712-*`, captura `711-pr712-fixed-campaign-context.png`. [Registro público #711](https://github.com/izc05/magina-olivo-v20/issues/711#issuecomment-6074417557).

Recorrido previo y upgrade 1577→1606: [media jornada y horas](ANDROID-696-M1-HALF-HOURS-2026-10-09.md). La antigua foto perdida antes del aislamiento de fixtures de #698 sigue documentada como no disponible; no afirmar su restauración.

Pendientes: UI de contraste/navegación/tema en B2, verificación de servicio Catastro y GPS en B3, clima B4 y upgrade a APK final desde main verde en B5. Cámara, GPS/permisos, aviso real a las 08:00, TalkBack, luz exterior y aceptación física Gate21 permanecen pendientes. No iniciar Gate22+.
