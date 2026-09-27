# Revisión Android: tiempo, mercado y campañas — 2026-09-28

## Base y método

- Rama de código revisada: `main`, HEAD `82c7c93cc7f33a1851a3f0881a10775bbf373fa4` (merge de la PR #279).
- APK observado: `0.4.0-dev` (versionCode 570), construido desde el mismo árbol que `main` tras la #279; instalado como actualización firmada con la clave de depuración local para conservar los datos de la app.
- Emulador: `emulator-5554`. Se conservó el estado de usuario. No se borraron datos, no se activó ni editó ninguna campaña y no se desplegó ningún servicio.
- CI de `main`: [Android CI, run 36354136309](https://github.com/izc05/magina-olivo-v20/actions/runs/36354136309), completada correctamente (foundation y QA instrumentado del emulador incluidos).
- Alcance de esta pasada: Home (tiempo/mercado), apertura del radar, Mi Campo, detalle de finca, lista y detalle de campaña, y pantalla de Recolección/Jornadas. No es el recorrido completo de la aplicación.

## Hallazgos observados en pantalla

### Tiempo y radar

- En Home aparece: «Indica el municipio en la ficha de tu finca para ver su tiempo». Las tres fincas de prueba visibles («La Umbría», «La Umbría Norte» y «Los Llanos») muestran que falta completar su ubicación; por ello no alteré esos datos y no pude validar el pronóstico con un municipio. Tampoco se debe interpretar este mensaje como “fuente sin configurar”.
- «Ver radar de lluvia» sí abre el radar y carga mapa, leyenda temporal y controles. No se observó bloqueo ni carga infinita.
- La cartografía visible se percibe borrosa/pixelada en el nivel mostrado. Queda como observación visual, no como fallo confirmado del proveedor.

Evidencia: [`13-farm-list-lower.png`](../../artifacts/audit-20260928/13-farm-list-lower.png) y [`12-radar-open.png`](../../artifacts/audit-20260928/12-radar-open.png).

### Precio del aceite

- «Pulso diario» carga las tres categorías completas (AOVE, Virgen y Lampante) sin desplazamiento dentro del widget. La corrección de altura de la PR #279 queda validada visualmente en esta APK.
- La fecha y hora de actualización se ven junto a la referencia orientativa; se mantiene diferenciado de una cotización oficial.
- El bloque semanal «Tendencia oficial semanal» sigue mostrando «Sin fuente configurada».
- La PR #278 (`fix/oil-market-live-junta-header`) está abierta y sus checks aparecen en verde, pero eso no demuestra despliegue. El workflow más reciente consultado, [Deploy oil-market, run 36341847801](https://github.com/izc05/magina-olivo-v20/actions/runs/36341847801), terminó en fallo. No se relanzó.

Evidencia: [`05-home-market-lower.png`](../../artifacts/audit-20260928/05-home-market-lower.png).

### Campañas, Jornadas y pesadas

- Se encontró la campaña `Campana_2026_2027` en estado «Preparación» y con cero parcelas. El detalle explica «Sin parcelas» y ofrece «Editar»; no se pulsó Activar para no alterar los datos.
- El bloque «Jornadas» del detalle de campaña aún dice «Se crean al registrar la primera pesada del día».
- La pantalla «Recolección» muestra «La primera nace con una pesada» y el estado vacío indica que se registre la primera pesada para abrir la jornada. Ambos textos contradicen la decisión de producto ya aprobada: una Jornada puede crearse antes de cualquier Pesada.
- La creación de Pesadas aparece deshabilitada con esta campaña en Preparación; el texto indica que la finca necesita una campaña activa o en recolección. No se pudo probar el alta sin activar la campaña.
- El error concreto al intentar activar una campaña sin parcelas queda pendiente de probar en un estado desechable, porque la acción podría cambiar el estado de la campaña real del emulador.

Evidencia: [`09-campaign-detail.png`](../../artifacts/audit-20260928/09-campaign-detail.png) y [`10-campaign-jornadas.png`](../../artifacts/audit-20260928/10-campaign-jornadas.png).

## Evaluación breve

- **Pasa en esta pasada:** carga completa del widget diario de precios; apertura y carga del radar; navegación de Mi Campo a Campañas y Recolección; persistencia aparente del estado previo tras actualizar la APK (finca/campaña visibles).
- **Pendiente/bloqueado por datos o servicio:** forecast personalizado requiere municipio; tendencia semanal no está configurada/desplegada; Pesadas requiere campaña activa o en recolección.
- **Contradicción que debe corregirse en la PR de implementación:** los mensajes de Jornadas siguen afirmando que la primera Jornada nace de una Pesada.
- **No comprobado aquí:** pronóstico meteorológico con ubicación completa, activación sin parcelas, flujos de registro/edición, offline, cierre forzado y sincronización remota.

## Protección de datos y seguimiento

Esta auditoría no crea, edita, activa ni archiva fincas, parcelas, campañas o movimientos. No incluye cambios de aplicación. Las capturas muestran datos de prueba que ya estaban guardados localmente; se adjuntan solo las imágenes pertinentes para mantener evidencia acotada.
