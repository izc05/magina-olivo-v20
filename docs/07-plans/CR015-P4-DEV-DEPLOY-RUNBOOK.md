# CR-015 P4 — Despliegue en Supabase DEV (preparado, **sin ejecutar**)

Proyecto DEV: `zubpicdibqbmewxeurhm`. Producción y `main` quedan fuera.

## Ensayo sobre copia restaurada (07/11/2026)

- Respaldo usado: `dev-full-pre-g26.dump.age` del 29/10. Hashes cifrado `ee711553…aab9aae` y claro `9f0765ab…31780d45`
  iguales al manifiesto. Se descifró solo en local y se borró el archivo claro tras copiarlo al contenedor.
- Restaurado con `pg_restore --exit-on-error` en un contenedor aislado `supabase/postgres:17.11.0.006`, sin puertos
  publicados. Ajustes propios del contenedor, que no aplican en DEV: rol de sistema `supabase_realtime_admin` y base
  propiedad de `postgres`.
- Historial restaurado: las 10 versiones de DEV, de `20261011120000` a `20261027155737`. G1c y G2.1 ya están presentes
  (`20261025203210/13/15`, `sync_operations.payload_version`, `campaign_parcels`, `create_own_campaign`).
- Aplicados como `postgres`, una transacción por archivo y `ON_ERROR_STOP`: los 14 archivos G2.6 de
  `docs/sync/MANUAL_SYNC_1_0_HANDOFF.md` (SHA-256 verificados) y `cr015-p4-cycle-sync-v1.sql`
  (`facc200b5e3b1db914fd3749bf3aba3c997c4112bc4fa040f1d7ce8fcb733626`). Resultado: 15/15 correctos.
- `supabase/tests/cr015-p4-dev-rehearsal/p4-smoke.sql` (dentro de `ROLLBACK`): grants y RLS esperados, creación
  (revisión 1), repetición idempotente, cierre (revisión 2), lectura, `STALE_REVISION`, otra cuenta denegada y `anon`
  denegado.
- `fingerprint.sql` antes y después: las 56 tablas existentes tienen el mismo número de filas y el mismo hash.
  `public.parcels` solo añade la columna nula `deleted_at_exact`; sin ella, el hash es idéntico. Hay cinco tablas
  nuevas y vacías.
- El contenedor se eliminó al terminar.

**Límite:** el respaldo es del 29/10. Si DEV cambió desde entonces, este ensayo no lo cubre.

## Requisitos antes de tocar DEV (propietario)

1. Respaldo **nuevo** de DEV, con la contraseña interactiva y acceso JIT, cifrado con `age`, y repetir este ensayo sobre él.
2. Custodia externa de la clave `age` y de los `.age` (pendiente desde el 29/10).
3. Autorización expresa para aplicar los **15** archivos, no solo P4: sin G2.6 la APK no puede enviar recolecciones.
4. Versiones del historial para las 13 propuestas. Propuesta: `20261107120003` … `20261107120015`, por orden, con el
   nombre del archivo.
5. Para la prueba A↔B: APK DEV con `MAGINA_AUTH_DEV_URL`/`PUBLIC_KEY` reales (fuera de Git) y una cuenta de prueba en DEV.

## Ejecución (solo con autorización)

Por cada archivo, en orden: `psql -X -v ON_ERROR_STOP=1 -1` con
1. una precondición que falle si el historial no es exactamente el esperado hasta ese paso;
2. `\i` del archivo, con su SHA-256 comprobado antes;
3. `INSERT INTO supabase_migrations.schema_migrations(version,name)`;
4. la poscondición de sus objetos y grants.

Ante cualquier error, `ROLLBACK` y detenerse. Después, `p4-smoke.sql` y `fingerprint.sql` contra DEV y comparación con
el respaldo nuevo. No usar `supabase db push`.

**Recuperación:** un fallo antes del `COMMIT` lo deshace la propia transacción. Si hay una anomalía después del commit,
no hacer borrados automáticos: conservar DEV, restaurar el respaldo en un entorno aislado y pedir aprobación del plan.

## Prueba A↔B prevista

En dos AVD desechables `MaginaOlivo_AB_A2/B2`, con la misma cuenta:
1. A crea finca, parcela, recolección y ciclo, y sincroniza.
2. B sincroniza: recibe el ciclo con los mismos UUID.
3. A cierra el ciclo; B lo recibe cerrado.
4. Cierre concurrente: conflicto visible en el segundo, sin sobrescritura.
5. Repetir la recepción: sin cambios.

## Preparación del 07/11 (tarde)

- Orden, SHA-256 y versión de cada archivo: `supabase/tests/cr015-p4-dev-rehearsal/deploy-order.txt`. Son 15 versiones
  únicas y crecientes, sin colisión con las 10 de DEV (13 nuevas, de `20261107120003` a `20261107120015`).
- Scripts locales, fuera de Git, en `%USERPROFILE%\.codex\tmp\magina-dev-restore\`:
  - `backup-dev-pre-p4.ps1`: solo lectura, contraseña interactiva sin guardarla, cifrado `age` con verificación de ida y vuelta.
  - `rehearse-restore.ps1`: restaura, aplica los 15 archivos registrando cada versión, ejecuta la prueba funcional y
    compara huellas. Ensayado con la copia del 29/10: `REHEARSAL_OK`, 25 versiones.
  - `copy-external.ps1`: copia solo los `.age` y el manifiesto a `D:\MaginaOlivo-Backups\supabase-dev\` y verifica los hashes.
  - `verify-key-copy.ps1`: comprueba la copia de la clave guardada en el gestor.
  - `build-dev-auth-apk.ps1`: APK DEV con Auth; pide la clave pública en pantalla y no la guarda.
- AVD `MaginaOlivo_AB_A2/B2_Disposable_API35`: arrancan con datos borrados en API 35.
