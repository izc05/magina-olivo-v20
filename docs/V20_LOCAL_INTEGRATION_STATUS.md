# Estado de integración local V20

Última revisión: 14 de septiembre de 2026  
Rama de trabajo: `codex/v20-local-visual-integration`  
Commit actual: `9542a788`

Esta rama reúne las funciones estables de las ramas `origin/feat/v20-*` y una selección revisada de los PR abiertos. `main` no se modifica y no se ha enviado ningún cambio al remoto.

## Integrado

- Admin unificado y controles de permisos con respuestas fail-closed.
- Comunidad, actividad y aventura, incluida la reanudación móvil y el grabador compatible con el contrato actual.
- Almazaras: catálogo, recompensas QR, reservas, historial de canjes, administración de stock y progresión de Mi Olivo.
- Pueblos: hub público, pueblos seguidos, feed territorial y avisos municipales.
- Mi Olivo: Discovery V3, brújula y pasaporte de progreso, bienvenida inicial, línea de actividad, comparación de campañas y cuidados del olivo.
- Integración visual conservando los componentes actuales y el degradado cuando la API no está configurada.

## PR revisados

- #122/#132: recompensas de almazara y progresión; se renumeraron las migraciones heredadas a `0088` y `0089`.
- #123/#124/#129: pueblos, seguimiento y avisos municipales.
- #125: Discovery; se conserva el motor V3 actual y se incorporan sus superficies de UI y smoke test.
- #133: aventura móvil; se integró por commits aislados para mantener el contrato vigente del grabador.
- #113: bienvenida, timeline, comparación de campañas y cuidados; se tomaron sus componentes sin reemplazar los módulos más recientes.
- #117: revisado frente al centro Admin unificado actual; no se mezclaron borrados masivos de una rama antigua.
- #128: comunidad ya estaba absorbido por la rama de comunidad estable.

## Comprobaciones

- `pnpm check:migrations`: OK, 67 migraciones, rango `0001..0089`, siguiente prefijo sugerido `0090`.
- `pnpm check:admin-unified`: OK, 22 superficies registradas y rutas protegidas.
- `pnpm --filter @magina/api typecheck`: OK.
- `pnpm --filter @magina/web typecheck`: OK tras limpiar `.next/dev` generado por el servidor local.
- Rutas HTTP locales comprobadas con respuesta 200: `/mi-olivo`, `/mi-olivo/canjes`, `/aventura`, `/aventura/en-curso`, `/pueblos`, `/mis-pueblos`, `/admin/ayuntamientos/avisos` y `/admin/empresas/almazaras`.

## Arranque local

```powershell
pnpm install
.\scripts\check-local-v20.ps1
.\scripts\run-local-v20.ps1 -SeedDemo
```

El servidor local funciona sin API configurada y muestra estados vacíos seguros. Docker Desktop está instalado, pero su motor sigue bloqueado por la virtualización de firmware; habrá que activar VT-x/virtualización en BIOS y reiniciar el PC cuando sea posible. AEMET, S3, Google y VAPID siguen requiriendo credenciales externas para probar sus integraciones reales.

## Referencias auditadas

- `origin/main`: `d6249bb0`.
- `origin/integrate/v20-beta-closure`: `427d783d`.
- Se ejecutó `git fetch origin --prune` antes de revisar las ramas y PR abiertos.
