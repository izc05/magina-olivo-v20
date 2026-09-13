# Integración visual local — 13 de septiembre de 2026

Rama: `codex/v20-local-visual-integration`.
Base local conservada: `ad48391` (iconos premium, Perfil y Administración).
La referencia coordinada sigue siendo `integrate/v20-beta-closure`; esta integración no cambia las ramas remotas ni publica en producción.

## Apartados reunidos

| PR | Rama incorporada | Apartado |
| --- | --- | --- |
| 79 | codex/v20-profile-admin-polish | Base visual, Perfil y Admin |
| 86 | feat/v20-explore-hub | Explorar, incluyendo Empresas #80 y Rutas #81 |
| 83 | feat/v20-municipalities-directory | Ayuntamientos y sus 16 fichas |
| 84 | feat/v20-business-experiences | Experiencias y gestión de reservas |
| 82 | feat/v20-business-magina-pass | Pasaporte territorial |
| 87 | feat/v20-routes-adventure | Aventura dentro de las fichas de rutas |
| 74 | feat/v20-mi-olivo-seasons-v6 | Memoria de campañas de Mi Olivo |
| 66 | feat/v20-admin-operations-hub | Operaciones y analítica de administración |

Se conservaron ambos registros API y accesos de Experiencias y Pass al resolver conflictos. La ficha de Rutas conserva negocios cercanos y añade Aventura. Admin mantiene la navegación premium en lugar del antiguo bloque flotante.

## Cambios de integración

- Explorar enlaza Experiencias, Mágina Pass, Rutas y Ayuntamientos con iconos del sistema existente.
- Empresas, Rutas, Experiencias y Pass comparten cabecera, navegación territorial y navegación móvil.
- Mágina Pass pasa de migración `0073` a `0074` para evitar duplicar la numeración de Experiencias. Se actualiza su workflow. No se aplicó ninguna migración a una base real. Si un entorno ya aplicó el nombre antiguo, revisar su registro de migraciones antes del despliegue.
- El smoke de gestión de Experiencias elimina sintaxis TypeScript de un archivo `.mjs` y resuelve Kysely desde el paquete API, donde está declarado.
- Se amplía la matriz responsive existente con ocho rutas de los módulos integrados.

## Ver en local

- Compilación exportada: http://127.0.0.1:3000/explorar/
- Desarrollo con actualización al guardar: http://127.0.0.1:3001/explorar/

Para volver a arrancar desarrollo desde la raíz, con Node 22 y pnpm 10.15.1:

```powershell
pnpm --filter @magina/web dev --hostname 127.0.0.1 --port 3001
```

El equipo tiene otras versiones globales. Si no está seleccionado el runtime del proyecto:

```powershell
npx --yes --package=node@22 --package=pnpm@10.15.1 pnpm --filter @magina/web dev --hostname 127.0.0.1 --port 3001
```

Para actualizar la exportación, detener primero el servidor de desarrollo, ejecutar `pnpm build` y servir con `node scripts/serve-static-export.mjs`. No ejecutar build y dev simultáneamente: se observó una colisión de tipos generados en `.next/dev`.

## Alcance de validación

- `pnpm check:fast`: correcto; contratos de entorno, migraciones y tipos de todo el monorepo.
- Build de API, worker y paquetes: correcto.
- Build web: 114 páginas generadas; JavaScript total 3,16 MiB, por debajo de 10 MiB; mayor fragmento 0,98 MiB, por debajo de 2,5 MiB.
- Pruebas GPX: 3/3 correctas.
- Matriz responsive: 179/179 correctas sobre la exportación local, ocho anchos de 360 a 1920 px; navegación, ausencia de errores JavaScript y desbordamientos, foco de teclado y espacio para la barra móvil.
- Contrato de Ayuntamientos y sintaxis del smoke de Experiencias: correctos.

No hay base PostgreSQL local configurada ni API autenticada disponible en esta sesión. Los catálogos muestran su estado de indisponibilidad; no se añadieron datos ficticios. Reservas, recompensas, GPS, administración autenticada y migraciones necesitan pruebas con base de datos de ensayo antes de promover esta rama al candidate. El PR #86 también presentaba una prueba `farmer-browser` fallida en GitHub al iniciar la integración.
