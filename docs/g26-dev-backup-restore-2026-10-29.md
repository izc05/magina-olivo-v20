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

## Seguimiento desde 71dd858e: portabilidad y custodia

Se localizaron los dos `.age` y la clave privada en las rutas anteriores. Se recalcularon los SHA-256 cifrados: `ee711553d05b1fa9ac511c447102a10bf23102c3b4c3b1372812c7a5178b9aae` y `17bc21af831964381ef1c19e93e029e3a9c3e57a2309a5e2041a9fac5589b221`, ambos iguales al manifiesto. En un contenedor Alpine Linux 3.21 independiente del perfil Windows, `age -d` produjo los SHA-256 originales `9f0765ab992fe3090758c6f23aef7672ada1b1ec69ad3aeda56ab2f831780d45` y `7c247e5b2d612526a99dc165ac587ce33625b2a5f81170f4f04f98d62022e719`. No intervino DPAPI.

Se restauró de nuevo el `.age` desde Linux, con `pg_restore --exit-on-error`, a una base nueva `magina_dev_g26_portable_linux` en la pila Supabase aislada. Resultado: `PORTABLE_LINUX_RESTORE_OK`. La copia tiene las diez versiones originales, 35 políticas públicas y los roles necesarios `anon`, `authenticated`, `service_role` y `supabase_admin` en la pila. Las **55 tablas comunes** con la restauración de referencia dieron conteos y hashes de filas iguales, sin diferencias. Después se aplicó el mismo SQL G2.6 solo en esta copia y se ejecutó `g26-restored-copy-security.sql` con `ON_ERROR_STOP=1`: `G26_RESTORED_COPY_SECURITY_OK`; los fixtures quedaron revertidos y `workspace_memberships` volvió a cero. El archivo cifrado y la clave temporal se eliminaron del contenedor al terminar.

Incidencia resuelta: un primer intento de restauración por red con el rol local `postgres` falló al crear `auth` por falta de permisos; al cambiar el propietario de la base, falló al reasignar `auth` a `supabase_admin`. Se descartó esa vía y se ejecutó la restauración dentro del contenedor Supabase con `supabase_admin`, conservando propietarios y ACL. Los errores precedieron a cualquier restauración completa; la base parcial se eliminó **solo en la pila aislada** y se creó de nuevo.

**Custodia externa pendiente.** No se detectó una unidad extraíble ni se proporcionó otro destino externo autorizado. Los `.age` y la clave siguen en el mismo equipo, aunque separados por directorios. Para cubrir pérdida del equipo, copiar los dos `.age` a un destino externo controlado y la clave privada a un gestor de contraseñas o soporte distinto; verificar allí hashes y capacidad de leer la clave. No se ha afirmado ni ejecutado esa copia. El archivo de roles se descifró y verificó por hash; la restauración completa probó propietarios y ACL sobre una pila Supabase que ya contiene sus roles de sistema. No se ensayó el bootstrap de roles sobre PostgreSQL vacío, por lo que la recuperación requiere una pila Supabase compatible.

## Procedimiento DEV preparado, **sin ejecutar**

1. Obtener autorización expresa nueva para el despliegue. Confirmar de nuevo ID de proyecto `zubpicdibqbmewxeurhm`, estado saludable, conexión TLS, JIT limitado y diez versiones exactas del historial; detenerse ante cualquier divergencia. No usar `supabase db push` ni un despliegue global de migraciones.
2. Obtener un respaldo **nuevo**, cifrado y restaurado en otra pila aislada si los datos DEV cambiaron desde el 29/10/2026. Comprobar Auth, Storage (incluidos bytes de objetos si los hubiera), roles, ACL, funciones, políticas, claves, conteos y hashes. Repetir el ensayo G2.6 y la prueba de seguridad sobre esa copia. Sin respaldo recuperable y custodia externa verificada: NO-GO.
3. Extraer el SQL de `b994ed6338b8a7e3857807d996f44cb31fa5ec9c:supabase/migrations/20261029044901_g26_workspace_authorization.sql` a un archivo local temporal. Comprobar SHA-256 `19131ffdbd864e6f3416742d31a4b14451eb0c700d6835af4986fd8672ab1fd3`; si no coincide: NO-GO. Confirmar que `workspace_memberships`, las dos RPC y la versión `20261029044901` no existen aún en DEV.
4. Conectar solo a DEV con TLS verificado, identidad de administrador y `ON_ERROR_STOP=1`. Ejecutar **una transacción** con este orden: `BEGIN`; comprobar precondiciones mediante SQL que falle ante una versión inesperada; `\i` del archivo exacto; `INSERT INTO supabase_migrations.schema_migrations(version,name) VALUES ('20261029044901','g26_workspace_authorization')`; comprobar dentro de la transacción que existen tabla, RLS, políticas, RPC y grants esperados; `COMMIT`. El campo opcional `statements` puede quedar nulo, como en la migración G2.5 ya registrada. Guardar evidencia de commit y versión; ante cualquier error antes de commit, `ROLLBACK` y NO-GO. No aplicar archivos pendientes adicionales.
5. Tras commit, comprobar versión única en historial, contrato RPC v1 bajo JWT válido, denegación de `anon`, aislamiento A/C, revocación, expiración, grants y RLS. Comparar datos existentes con el respaldo nuevo. Cualquier diferencia inesperada: NO-GO y suspensión de uso; conservar evidencia y preparar recuperación supervisada.

Plantilla transaccional para el paso 4, a guardar **solo cuando se autorice** en un archivo local fuera de Git y ejecutar desde la raíz del checkout con `psql -X -v ON_ERROR_STOP=1 -f <archivo-local>`. La conexión se configura fuera del archivo, sin contraseña en argumentos ni logs. La lista de versiones debe cotejarse de nuevo antes de usarla:

```sql
BEGIN;
DO $$
BEGIN
  IF (SELECT array_agg(version ORDER BY version) FROM supabase_migrations.schema_migrations)
     IS DISTINCT FROM ARRAY[
       '20261011120000','20261011120001','20261011120002',
       '20261016041222','20261016041601','20261024125046',
       '20261025203210','20261025203213','20261025203215',
       '20261027155737']::text[]
     OR to_regclass('public.workspace_memberships') IS NOT NULL
     OR to_regprocedure('public.list_authorized_workspaces_v1()') IS NOT NULL
     OR to_regprocedure('public.verify_workspace_access_v1(uuid,text)') IS NOT NULL THEN
    RAISE EXCEPTION 'G2.6 preflight divergence';
  END IF;
END $$;
\i supabase/migrations/20261029044901_g26_workspace_authorization.sql
INSERT INTO supabase_migrations.schema_migrations(version,name)
VALUES ('20261029044901','g26_workspace_authorization');
DO $$
BEGIN
  IF to_regclass('public.workspace_memberships') IS NULL
     OR to_regprocedure('public.list_authorized_workspaces_v1()') IS NULL
     OR to_regprocedure('public.verify_workspace_access_v1(uuid,text)') IS NULL
     OR NOT (SELECT relrowsecurity FROM pg_class WHERE oid='public.workspace_memberships'::regclass)
     OR NOT has_function_privilege('authenticated','public.list_authorized_workspaces_v1()','EXECUTE')
     OR has_function_privilege('anon','public.list_authorized_workspaces_v1()','EXECUTE')
     OR (SELECT count(*) FROM supabase_migrations.schema_migrations WHERE version='20261029044901') <> 1 THEN
    RAISE EXCEPTION 'G2.6 postflight divergence';
  END IF;
END $$;
COMMIT;
```

La plantilla se probó **solo en** `magina_dev_g26_runbook`, creada desde el `.age`: restauración `RUNBOOK_RESTORE_OK`, transacción `COMMIT`, exactamente una versión `20261029044901` y prueba de seguridad `G26_RESTORED_COPY_SECURITY_OK`. No se conectó a DEV para este ensayo.

**Recuperación:** un fallo previo a `COMMIT` se revierte con la propia transacción y se verifica que la versión no quedó registrada. Tras un commit con anomalía, no ejecutar borrados ni rollback destructivo automático: preservar DEV y su respaldo, restaurar primero en entorno aislado, comparar integridad y permisos, y solicitar aprobación separada del plan concreto para devolver DEV al estado anterior. El respaldo actual no sustituye a uno nuevo si DEV cambió.

**Decisión actual:** GO para portabilidad criptográfica y restauración en Linux/Supabase compatible; **NO-GO de custodia externa** hasta disponer de copias separadas verificadas. **NO-GO de despliegue DEV**: está prohibido en esta fase y falta renovar preflight/backup al momento de ejecutar.
