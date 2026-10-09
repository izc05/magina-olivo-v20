# M1: media jornada y horas conservadas sin conexión

Continuación del recorrido agrícola #696, ejecutada en el AVD API35 conservado `emulator-5566`. App DEV instalada desde main `64308853`, versionCode 1577. No se borró ni reinstaló la app. Los registros son fixtures QA explícitos del 8 de octubre; el recorrido terminó después de medianoche.

## Datos y resultados comprobados

- C1 `QA_2026`, cerrada: 4.000 kg, 155 € de gastos POSTED, 50 € de pagos. La nueva campaña no modifica estos registros históricos.
- C2 `QA_C2_M1`, activa: nueva pesada manual de 1.200 kg, vale `QA-C2-V1`, destino `Coop_QA_C2`, árbol/vuelo. Seleccionar una parcela de origen no inventa un reparto de kilos por parcela. Rendimiento desconocido, mostrado como `—` / pendiente de análisis.
- `QA_JORNAL_M1`, media jornada: snapshot de tarifa 80 €/jornada, coste 40 €, pago parcial 15 €.
- `QA_HORAS_M1`: 180 minutos, snapshot de tarifa 10 €/hora, coste 30 €, sin pago registrado.
- Tras force-stop y reapertura en modo avión, Cuaderno muestra 1.200 kg y jornales 70 €, `2 personas · 1 media · 3 h`, `15 € pagados · 55 € pendientes`. El pago no vuelve a sumar al coste.

Una copia de Room extraída del respaldo pasó `PRAGMA integrity_check` y `foreign_key_check` sin incidencias. Se verificaron separadamente los totales de C1 y C2 y los snapshots FULL_DAY, HALF_DAY y HOURS. No se consultó ni modificó directamente la base viva. Conexión restaurada al terminar.

## Respaldo y trazabilidad

Respaldo local: `artifacts/696-e2e/retained-m1-after-half-hours.tar`, SHA256 `f04050ceced125893a097ea03f2121de802c34a148bcbbb15223bf7b46a20025`.

Foto `733ebe61-da29-41dd-be5c-38e91d70d3f8.jpg`: SHA256 `85fa29fa5e62f61117f32b9a3f38493884b9782b6e6091931a1e2f70c3f6112d`, idéntico al respaldo anterior. La antigua foto perdida antes del aislamiento de fixtures de #698 continúa documentada como no disponible; este resultado se refiere a la nueva foto respaldada.

Evidencias locales: `audit-half-hours.py`, `m1-c2-offline-costs.xml`, `m1-half-hours-reopened-offline.xml`, `m1-c2-reopened-offline-summary.xml`. [Actualización publicada en #696](https://github.com/izc05/magina-olivo-v20/issues/696#issuecomment-6072150865).

## APK preliminar de main y actualización

Después del recorrido se preparó la APK DEV desde main `fe79efd17c67450d0923868be110a52c33e6d4a3`, con #709 integrado. Android CI [37867522148](https://github.com/izc05/magina-olivo-v20/actions/runs/37867522148): SUCCESS (foundation y gate3-emulator). Artefacto `magina-olivo-dev-debug`, ID `11589521417`; versionCode 1606, versionName `0.5.0-dev`.

La copia local `artifacts/696-e2e/magina-olivo-main-fe79efd1-dev-debug.apk` se firmó con el certificado DEV local compatible con el AVD conservado: SHA256 del certificado `a5b06ba689aa790eaebe8a527f883a5468690f6d0ec4509bcb5a9a3f83ed0882`. Las 549 entradas fuera de META-INF son idénticas al APK de CI. La copia firmada tiene SHA256 `6caa63f14496bc649039492b6a62dc22b667e801231821835d53c9e9cae584fe`; no es el mismo binario firmado que el original de CI.

Instalación con `adb -s emulator-5566 install -r`: SUCCESS, actualización 1577 → 1606. Sin desinstalación ni downgrade. Respaldos `retained-before-main-fe79-upgrade.tar` y `retained-after-main-fe79-upgrade.tar`: mismo SHA256 `c76266d98d1c5ecc135f86a7c23c0453ee7eb1c3cd42c4e3c5e129bc6d7f0822`, foto conservada. Auditoría de la copia posterior: los mismos totales de C1/C2 y snapshots completos/media/horas; integridad y claves foráneas correctas. Arranque real muestra C2 con 1.200 kg; Perfil muestra «Activados. Android controla el sonido y la vibración.». XML `main-fe79-upgrade-start.xml` y `main-fe79-upgrade-profile.xml`.

Es una APK preliminar agrícola, anterior a los cambios aprobados de contraste/navegación. Emulador: PASS para los casos descritos. Móvil físico/Gate21: PENDIENTE. Este resultado no declara la app completa ni cambia autorización de fases posteriores. Web V3 pausada.
