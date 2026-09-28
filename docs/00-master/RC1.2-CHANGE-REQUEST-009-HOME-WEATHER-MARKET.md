# CR-009 — Inicio: tiempo en portada y mercado de un vistazo

**Estado:** IMPLEMENTACIÓN EN CURSO — PR #284 (sin fusionar)
**Base:** `main` en `bc3a1b7b87147c881b1a16b6994d553b2d1e1cf4` (compilación Android 0.5.0-dev, build 580).
**Ámbito:** ajuste de Inicio y detalle semanal del tiempo. No cambia la navegación principal ni añade áreas de producto.

## Problema observado

En Inicio, la tarjeta de primera finca y la cuadrícula de accesos rápidos repiten acciones que ya tienen un destino claro en Mi Campo, Cuaderno y las pantallas de Jornadas, Pesadas y Gastos. La información meteorológica queda lejos de la imagen principal y el bloque de mercado ocupa mucho espacio con el pulso diario, precios y texto antes de llegar a la tendencia. El usuario debe desplazarse y distinguir varias fuentes para obtener dos respuestas rápidas: qué tiempo hará y cómo evoluciona el mercado.

## Decisiones de experiencia propuestas

1. **Portada:** conservar la fotografía, marca, saludo y estilo crema/olivo. Retirar de Inicio la tarjeta «Empieza por tu primera finca» y la cuadrícula «Accesos rápidos». No se elimina ninguna función: crear/consultar fincas sigue disponible desde **Mi Campo**; Jornadas, Pesadas y Gastos siguen accesibles desde sus destinos actuales.
2. **Tiempo en la imagen:** incorporar una tarjeta compacta y pulsable sobre la fotografía, con icono/estado actual, temperatura si existe, municipio utilizado y una indicación breve de máximas/mínimas o lluvia cuando haya datos reales. Pulsarla abre una pantalla de semana con hasta siete días, detalle disponible y acceso al radar existente.
3. **Ubicación:** usar solo la ubicación que ya resuelve la app a partir de las fincas. Mostrar claramente el municipio. No añadir GPS, permisos, selector nuevo ni preferencias de ubicación en este cambio. Si la ubicación no se puede resolver sin ambigüedad, explicar el motivo y no atribuir un pronóstico a otro municipio.
4. **Datos meteorológicos:** ampliar de forma compatible la respuesta ya usada por `weather-forecast` con una lista diaria de hasta siete días: fecha, mínima/máxima, estado, probabilidad/volumen de lluvia y viento, todos opcionales si el proveedor no los ofrece. Una respuesta completa debe provenir de un proveedor; no mezclar días de AEMET y MET Norway. No inventar ni interpolar valores. Indicar fuente, hora de actualización del proveedor y antigüedad de la última consulta de la app. En modo avión, mostrar semana en caché con su antigüedad; sin caché, explicar que hace falta conexión.
5. **Mercado en Inicio:** sustituir el widget incrustado, la repetición de filas de tendencia y el texto largo por una tarjeta más baja con una gráfica de líneas de 12 semanas ya disponible en el detalle de mercado. Mantener series separadas y colores consistentes para AOVE, Virgen y Lampante, leyenda legible, unidad €/kg, semana/fuente visibles y acción **Ver mercado**. Conservar el pulso diario de AOVE.net y sus datos en la pantalla de detalle existente; no confundirlo con la serie oficial semanal de la Junta ni con el precio pagado por aceituna/cooperativa.
6. **Jerarquía:** Inicio responde primero dónde estoy/qué tiempo hace, después destaca actividad importante existente, y deja el mercado como consulta secundaria. No convertirlo en panel de KPI ni duplicar accesos de las pestañas inferiores.

## Recorrido propuesto

- Inicio → leer municipio/estado meteorológico en la fotografía → tocar la tarjeta → revisar los próximos días → abrir el radar existente → volver a Inicio.
- Inicio → comparar la tendencia real de las tres categorías en la tarjeta compacta → **Ver mercado** para pulso AOVE.net, fuente oficial, fechas y detalle completo.
- Sin fincas: Inicio mantiene una portada limpia; **Mi Campo** conserva el estado vacío y la acción para crear la primera finca.

## Datos, arquitectura y compatibilidad

- No se elimina ningún modelo, campo, registro ni pantalla de detalle existente.
- Sin cambios de Room ni migración: la caché actual `weather_cache.payload_json` debe leer tanto respuestas antiguas (sin `daily`) como nuevas.
- Mantener el patrón actual de `FeedState`, caché local, llamadas HTTPS a las Edge Functions y proveedor/fuente atribuida.
- Cambiar el contrato de `weather-forecast` exige actualizar fixtures y pruebas y desplegar la función correspondiente. El despliegue en Supabase queda fuera de esta PR y requiere autorización explícita.
- Reutilizar la serie oficial y la gráfica de 12 semanas de la pantalla Mercado; la tarjeta de Inicio debe mostrar huecos si faltan observaciones, nunca valores interpolados.
- Mantener intacta la navegación raíz: **Inicio · Mi Campo · Cuaderno · Avisos · Perfil**.

## Fuera de alcance

- GPS, seguimiento de ubicación o nuevo selector de municipio (se evaluará con el trabajo posterior de Perfil/ubicación).
- Nuevas fuentes de precios, estimación de ingresos de finca, precios de aceituna, cooperativa o jornadas.
- Rediseño de marca, cambios de onboarding, nuevas funciones de radar o nuevas áreas principales.
- Eliminar el widget diario AOVE.net de su pantalla actual.

## Criterios de aceptación

1. No aparecen en Inicio «Empieza por tu primera finca» ni «Accesos rápidos»; crear finca y accesos funcionales siguen encontrándose en sus destinos establecidos.
2. La tarjeta del tiempo está sobre la imagen, indica el municipio realmente consultado y permite abrir la semana y el radar.
3. La semana presenta hasta siete fechas y solo datos recibidos; valores ausentes se muestran como no disponibles, con fuente y antigüedad comprensibles.
4. El pronóstico semanal funciona con caché previa sin conexión y presenta un estado honesto si no existe caché.
5. La tarjeta de mercado de Inicio muestra las tres series oficiales con colores/leyenda coherentes, fechas y fuente, sin el widget largo embebido; **Ver mercado** conserva el detalle y el pulso de AOVE.net.
6. La tarjeta de Home y el detalle no mezclan precio del aceite en almazara, precio de aceituna pagado al agricultor ni ingresos estimados.
7. Las pestañas raíz, las acciones de crear finca y los bloques existentes de Inicio no afectados siguen funcionando; volver atrás desde semana/radar/mercado regresa al punto esperado.
8. Gate 20 se revalida: añadir B4 (semana: filas, fuente, valores ausentes) y D5 (semana en caché y antigüedad en modo avión), conservando el checklist actual. Antes de dar Gate20 por cerrado debe instalarse y probarse un APK actualizado, además del Gate físico pendiente.

## Pruebas requeridas

- Tests de contrato/serialización de `daily`, incluyendo respuesta antigua y datos parciales.
- Tests de ubicación única, no resuelta/ambigua, caché y estado sin caché.
- Tests de representación de la gráfica: series/colores estables, huecos sin interpolación, etiquetas de fuente/periodo/unidad.
- Build, lint, tests Android disponibles y recorrido real en emulador desde instalación limpia; comprobar Inicio, semana, radar, mercado, rotación/scroll si aplica, modo avión, cerrar/reabrir y rutas a Mi Campo/Cuaderno.
- Checklist físico B4/D5 actualizado para el nuevo APK; el gate no queda validado solo por compilar.

## Alternativas consideradas

- **Mantener Inicio actual y solo reducir textos:** no resuelve la duplicación de accesos ni acerca el tiempo a la portada.
- **Mostrar el widget AOVE.net completo junto a la gráfica:** descartado para Home porque conserva la tarjeta larga y mezcla dos cadencias/fuentes; el pulso diario permanece en el detalle.
- **Elegir ubicación por GPS o añadir un selector:** se difiere; amplía permisos/estado y no es necesario para el objetivo visual acordado.

## Revisión propia / riesgos pendientes

- La resolución actual de tiempo puede quedar vacía si las fincas no comparten municipio. Esta propuesta no elige una finca de forma silenciosa: el estado ambiguo debe explicarse, aunque puede requerir una decisión posterior si el usuario quiere fijar finca meteorológica.
- La respuesta diaria de AEMET y MET Norway debe verificarse con fixtures reales antes de fijar el mapeo; no se considera terminado hasta probar ambos formatos.
- El contrato semanal requiere despliegue externo para que el APK obtenga datos semanales reales. La PR no debe afirmar que la semana remota está operativa hasta tener ese despliegue y validación.
- La tarjeta sobre fotografía necesita contraste accesible sobre imágenes claras y oscuras, área táctil amplia y estado legible en luz exterior.
- El mercado necesita huecos reales cuando falten semanas; la línea no debe insinuar continuidad falsa.

## Seguimiento de implementación (PR #284)

- Alcance confirmado por el propietario: retirar de Inicio la tarjeta inicial de finca y la cuadrícula de accesos; colocar el tiempo en la foto con acceso a la semana; representar el mercado oficial con una gráfica compacta de tres líneas.
- Implementado en código: contrato diario aditivo de `weather-forecast` (AEMET/MET Norway, fuente única por respuesta), compatibilidad de caché Android, pantalla de semana/radar reutilizado, Inicio simplificado y gráfica compacta. Sin migración Room, sin GPS, sin mezcla de proveedores/mercados y sin despliegue Supabase.
- Pruebas Edge: `node --experimental-strip-types --test supabase/functions/weather-forecast/forecast.test.ts` pasó (13 pruebas); CI de la PR valida los cambios Android.
- Pendiente: CI Android completa, verificación visual en emulador/artefacto y dispositivo físico. Hasta verificar un despliegue autorizado de la Edge Function, la disponibilidad remota de siete días no se da por garantizada.
- No fusionar #284 sin autorización expresa del propietario.
