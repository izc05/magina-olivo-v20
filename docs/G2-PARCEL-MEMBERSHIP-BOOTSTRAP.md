# G2 — Parcela y pertenencias recuperables

Propuesta sin despliegue. Cargar `g1c-reservations.sql`, `g2-sync-own-data.sql` y `g2-parcel-bootstrap.sql`, en ese orden, antes de ejecutar RPC. El arnés local y el nativo desechable usan esa misma secuencia.

## Contenido y atomicidad

`create_own_parcel(p_operation_id uuid,p_parcel jsonb)` escribe la parcela, las pertenencias incluidas y un recibo PARCEL en una sola transacción. Conserva los 22 campos de `ParcelEntity`: identidad/workspace, nombre, referencia/polígono/parcela catastrales, municipio/provincia, fuente, geometría, proveedor/fecha de importación, superficies, notas, estado, olivos, variedad y sistema/red/sector/días de riego. Los opcionales admiten null explícito; un sistema no indicado no se convierte en secano.

El objeto exige esos 22 campos más `memberships`. Cada pertenencia incluye exactamente `id`, `farmId`, `validFrom`, `validUntil`, `deletedAt`; el workspace y la parcela se derivan del padre. Se conservan pertenencias terminadas y borradas lógicamente, sin convertirlas en vigentes. Se admiten hasta 1.000 pertenencias y 4 MiB de JSONB; nombre y textos cortos hasta 200 caracteres, notas hasta 10.000. Superficies Double no negativas, olivos enteros no negativos, estados ACTIVE/ARCHIVED, fuentes Room MANUAL/CATASTRO y sistemas de riego conocidos o null. CATASTRO exige referencia y geometría textual no vacías. No se transforma ni trunca el contenido recibido.

Las pertenencias no borradas no pueden solaparse, ni puede haber IDs duplicados o repetir finca/inicio. Los intervalos vacíos pueden conservarse, pero no sirven para autorizar una pesada. Todas las fincas referenciadas deben ser propias, vivas y del mismo workspace. Una cuenta autenticada no anónima y vigente se verifica en cada llamada. La futura consumidora debe comprobar además consentimiento local explícito para **todas** las fincas incluidas: ser titular en el servidor no autoriza a subir historial de otras fincas no consentidas.

Bloqueos: operación → workspace → fincas por UUID ordenado. Un reintento idéntico devuelve la versión original del recibo; no sobrescribe ediciones posteriores. Cambiar contenido o reutilizar un UUID ocupado produce conflicto. Una colisión de pertenencia revierte también la nueva parcela y el recibo. No se deduplica por Catastro, nombre o geometría.

## Fechas exactas y lectura

Android Instant admite nanosegundos y PostgreSQL timestamptz almacena microsegundos. Se guardan las cadenas UTC originales en `source_imported_at_exact`, `valid_from_exact`, `valid_until_exact` y `deleted_at_exact`; la columna temporal se obtiene truncando a microsegundos, sin redondear hacia otro día. El futuro adaptador de restauración debe usar el valor exacto cuando exista y la columna temporal en los demás casos. No hay aún adaptador Android conectado.

La detección de solapes, el orden de lectura y la elegibilidad temporal de pesadas usan claves NUMERIC de nanosegundos, con fallback para filas anteriores. Un intervalo de 1 ns se conserva positivo aunque sus dos valores timestamptz coincidan; uno de 0 ns no autoriza una pesada. Las fechas deben ser UTC ISO reales, con hasta nueve cifras decimales. Un trigger borra el valor exacto antiguo si otro editor cambia solo la fecha temporal y rechaza una pareja explícitamente incoherente; no devuelve silenciosamente una fecha exacta obsoleta.

`get_own_parcel` lee parcela y pertenencias en una sola consulta, con identidad, workspace y fincas autorizados. Exige formato completo y recibo inmutable de creación: poner una marca a una fila antigua no prueba que se haya importado completa. La lectura de campaña exige ahora también ese recibo. No se convierten filas legadas en datos completos por deducción.

## Pruebas y límites

Contratos aislados con claims SQL sintéticos: campos de riego/Catastro, nulos, fechas exactas, invalidación de fechas obsoletas, solapes submicrosegundo y rollback. Suite nativa: reintentos concurrentes, lectura de todos los campos, ediciones posteriores conservadas, recibos, aislamiento, asociaciones ajenas, colisiones de hijos sin filas parciales, historial de dos fincas con borrado lógico y diferencia entre 0 ns y 1 ns para una pesada.

El recorrido cliente usa RPC recuperables para workspace → reserva → finca → parcela/pertenencias → campaña/snapshots → pesada. Los claims de reserva HTTPS siguen siendo el fixture del gateway desechable documentado en `G2-CLIENT-BOOTSTRAP-CONTRACT.md`; no es una prueba Android ni HTTPS real de producción.

Los permisos REST preexistentes continúan disponibles. Las garantías de creación/reintento corresponden a estas RPC. Falta el diario/mapeo y el envío Android, consentimiento justo antes de enviar, codecs completos, modificación/borrado remoto, adjuntos y restauración entre instalaciones. No hay activación agrícola HTTP, despliegue persistente, fusión ni APK candidata.
