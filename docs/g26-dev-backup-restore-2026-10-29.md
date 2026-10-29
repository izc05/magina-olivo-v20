# G2.6 DEV: respaldo, restauración y ensayo aislado (29/10/2026)

## Identidad y límites

- Proyecto de origen: `zubpicdibqbmewxeurhm` (`magina-olivo-dev`, PostgreSQL 17.11).
- SQL ensayado: `supabase/migrations/20261029044901_g26_workspace_authorization.sql` del commit `b994ed6338b8a7e3857807d996f44cb31fa5ec9c`, SHA-256 `19131ffdbd864e6f3416742d31a4b14451eb0c700d6835af4986fd8672ab1fd3`.
- **No se ejecutó G2.6 en DEV.** La PR #780 siguió abierta en Draft. No se tocaron producción, emuladores ni sincronización.

## Acceso temporal

Se habilitó `Enforce SSL` en DEV con autorización separada; Supabase reinició brevemente PostgreSQL y el proyecto volvió a `ACTIVE_HEALTHY`. Se habilitó JIT y se restringió una única regla al rol `postgres`, a la IPv4 de salida `/32` y a 60 minutos. Se comprobó acceso mediante Session Pooler con `sslmode=verify-full` y PostgreSQL 17.11. Al concluir, JIT se deshabilitó y SSL obligatorio permaneció activado. No se rotó la contraseña de la base.

## Respaldo y recuperación

Los archivos permanecen **fuera de Git** en `C:\Users\ISICIO\.codex\tmp\magina-dev-restore\`:

| Archivo | SHA-256 del contenido original | SHA-256 del cifrado portátil |
| --- | --- | --- |
| `dev-full-pre-g26.dump.age` | `9f0765ab992fe3090758c6f23aef7672ada1b1ec69ad3aeda56ab2f831780d45` | `ee711553d05b1fa9ac511c447102a10bf23102c3b4c3b1372812c7a5178b9aae` |
| `dev-globals-pre-g26.sql.age` | consultar `globals-manifest-pre-g26.json` local | `17bc21af831964381ef1c19e93e029e3a9c3e57a2309a5e2041a9fac5589b221` |

`pg_dump -Fc` capturó 930 entradas de esquema y datos, incluidos `auth`, `storage` y `supabase_migrations`; `pg_dumpall --globals-only` capturó roles. Ambos se cifraron primero con DPAPI CurrentUser y después con `age` X25519 sin guardar la versión portátil en claro. Se verificaron hashes y descifrado. La clave privada `age` está en `%LOCALAPPDATA%\MaginaOlivo\g26-backup\g26-age-identity.txt`, con ACL solo para el usuario y SYSTEM. **Debe copiarse a un gestor seguro o soporte separado del equipo; perder esa clave impide recuperar los archivos `.age`.** Nunca subir clave ni respaldos a GitHub.

No había objetos binarios de Storage (`storage.objects = 0`); se conservaron las dos filas de `storage.buckets`. Si aparecen objetos en un respaldo futuro, exportar también sus bytes por Storage API: `pg_dump` conserva la metadata, no los archivos.

## Restauración y comparación

En una pila Supabase CLI 2.120.0/PostgreSQL 17.11 local y aislada se restauró el archivo completo con `pg_restore --exit-on-error`, conservando propietarios y ACL. Una segunda restauración real partió del `.age` y también terminó con código 0. El historial restaurado contiene **las diez** migraciones presentes en DEV, desde `20261011120000` hasta `20261027155737`.

Antes de aplicar G2.6, se compararon DEV y la copia: 22 tablas de datos con conteos y hashes de fila idénticos (Auth, workspaces, fincas, campañas, pesadas, reservas G1c, operaciones de sincronización, Storage e historial incluidos), 20 tablas de aplicación, 294 columnas, 36 funciones y 35 políticas. Dos ACL explícitas de propietario se representaron como ACL por defecto en la copia; las matrices de permisos efectivos para `anon`, `authenticated`, `service_role` y `postgres` coincidieron. Las políticas `auth.uid()`/`auth.jwt()` se representaron de forma distinta por el `search_path`; su expresión normalizada coincidió.

El SQL exacto de G2.6 se aplicó **solo** a la copia restaurada en una transacción, código 0. `supabase/tests/g26-restored-copy-security.sql` prueba A→C→A, lectura y escritura aisladas, permisos de solo lectura, revocación, vigencia, recuperación, issuer ajeno, JWT caducado, identidad ausente y denegación de `anon`. Se ejecutó con `ON_ERROR_STOP=1`, terminó en `G26_RESTORED_COPY_SECURITY_OK` y revierte sus fixtures con `ROLLBACK`. Los 22 conteos y hashes de datos continuaron idénticos a DEV después del ensayo.

## Estado final y decisión

DEV terminó `ACTIVE_HEALTHY`, con JIT deshabilitado, SSL obligatorio activo, diez migraciones y sin `workspace_memberships` ni G2.6 aplicado. Conservó un workspace, una finca, una pesada, cinco operaciones remotas de sincronización, cuatro usuarios Auth y cero objetos Storage. No se generó APK ni se fusionó la PR.

**GO técnico para el respaldo, su restauración local y la compatibilidad SQL/RLS de G2.6 con el estado real de DEV. NO-GO para desplegar G2.6 en DEV en esta fase:** la instrucción vigente lo prohíbe. Antes de considerar el respaldo recuperable ante pérdida del equipo, custodiar la clave privada `age` fuera de él y verificar esa copia de la clave.
