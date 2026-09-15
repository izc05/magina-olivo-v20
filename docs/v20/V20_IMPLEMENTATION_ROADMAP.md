# Mágina Olivo V20 — Implementation Roadmap

> Rama documental: `feat/v20-ui-ux-premium`
>
> Este documento define el orden real de implementación del producto. No sustituye la auditoría técnica del repositorio: antes de crear nuevas ramas hay que comprobar qué ramas/PR existentes ya contienen trabajo válido.

## Principio rector

V20 se construirá como tres pilares conectados:

1. **Mágina Aventura** — senderismo real gamificado y protagonista principal.
2. **Mi Olivo** — progresión visual, colección, fidelización y recompensas.
3. **Mi Campo** — utilidad profesional para agricultor.

Pueblos, Almazaras, Comunidad, Noticias, Empresas y Experiencias serán capas secundarias que alimentan estos tres pilares.

El primer vertical completo se cerrará en **Bedmar** antes de extender el producto masivamente a toda Sierra Mágina.

## Fase 0 — Auditoría y congelación del punto de partida

Objetivo: saber qué existe realmente antes de programar.

- Revisar candidate actual.
- Revisar ramas de rutas, aventura, Mi Olivo, Mi Campo y Admin.
- Revisar PR abiertos/draft y CI.
- Identificar código ya integrado, duplicado, obsoleto o no mergeable.
- No crear una nueva rama si una rama activa ya es la sucesora correcta.
- No tocar `main`.

Salida: mapa de integración y HEAD de referencia.

## Fase 1 — Contrato de datos y servicios compartidos

Antes de construir pantallas nuevas, cerrar las entidades mínimas:

- users / profiles
- roles
- routes
- route_tracks / GPX
- route_points / checkpoints
- adventures
- adventure_checkpoints
- activity_sessions
- discoveries
- collections
- badges
- XP ledger
- olive_points ledger
- rewards
- reward_stock
- reward_reservations
- reward_redemptions
- farms
- plots
- campaigns
- field_records
- weather cache / alerts

Regla: XP, aceitunas y canjes se calculan/validan en servidor, no solo en frontend.

## Fase 2 — Mágina Aventura: núcleo senderismo

Objetivo: poder seleccionar una ruta real y empezar una actividad.

Implementar y cerrar:

- catálogo de rutas
- ficha de ruta
- distancia, duración, desnivel y dificultad
- mapa y track
- GPX
- perfil de elevación
- puntos de interés
- seguridad y avisos
- meteorología
- botón `Preparar aventura`

QA mínimo:
- TypeScript/build
- API tests
- ruta con datos reales de Bedmar
- responsive móvil
- estados loading/error/empty

## Fase 3 — Aventura en curso: GPS y grabación

Esta es la pantalla prioritaria de V20.

Implementar:

- permiso de ubicación contextual
- GPS actual
- track recorrido
- track pendiente
- distancia recorrida/restante
- tiempo
- pausa/reanudar/finalizar
- desvío de ruta
- GPS débil
- checkpoints por proximidad
- persistencia local de sesión
- recuperación tras cierre accidental
- sincronización posterior

Offline V20.0:
- descargar datos esenciales de la ruta
- conservar actividad local sin cobertura
- sincronizar cuando vuelva la conexión

No cerrar esta fase sin prueba física real en una ruta de Bedmar.

## Fase 4 — Checkpoints, descubrimientos y retos

Sobre una ruta real:

- geofence/proximidad de checkpoint
- desbloqueo una sola vez por actividad/usuario según regla
- categorías: Flora, Fauna, Patrimonio, Olivar, Tradiciones, Paisaje
- ficha de descubrimiento
- reto/pregunta opcional
- feedback de XP/aceitunas
- colección asociada
- protección contra duplicados

La seguridad y navegación siempre tienen prioridad visual sobre la gamificación.

## Fase 5 — Final de aventura + Mi Aventura

Al terminar una ruta:

- validar actividad
- calcular km, tiempo y desnivel
- validar checkpoints
- otorgar XP/aceitunas en servidor
- desbloquear insignias/colecciones
- resumen visual de final
- guardar actividad en historial

Mi Aventura debe mostrar:

- nivel de explorador
- XP
- km acumulados
- rutas completadas
- pueblos explorados
- porcentaje territorial cuando exista base GIS fiable
- insignias
- colecciones
- historial

## Fase 6 — Mi Olivo Premium

Objetivo: convertir la actividad real en progreso visual y emocional.

Implementar:

- OliveHero 2.5D
- etapas visuales del árbol
- nivel / XP
- saldo de aceitunas
- evolución
- subida de nivel
- memoria de actividad
- colecciones e insignias
- conexión con Mi Campo sin inventar datos

Primera versión técnica:
- capas WebP/AVIF transparentes
- CSS/GSAP
- `prefers-reduced-motion`
- fallback estático para dispositivos lentos

Three.js queda para una fase posterior si el beneficio compensa el coste.

## Fase 7 — Recompensas AOVE y QR

Construir primero con una almazara/premio piloto.

Flujo:

1. usuario abre recompensa
2. backend comprueba saldo, elegibilidad y stock
3. transacción/reserva atómica
4. se genera token/QR opaco
5. usuario presenta QR
6. almazara escanea
7. backend valida activo/no usado/no caducado
8. confirma entrega
9. registra canje
10. impide reutilización

Estados:
- disponible
- reservada
- agotada
- activa
- canjeada
- caducada
- inválida

## Fase 8 — Mi Campo núcleo

Después de cerrar el bucle Aventura -> Mi Olivo -> recompensa.

Implementar:

- dashboard
- fincas
- parcelas/mapa
- campañas
- registro rápido
- riego
- tratamiento
- abonado
- poda
- recolección
- observaciones
- producción/entregas
- rendimiento
- meteorología
- alertas
- histórico

Mi Campo es fuente técnica; Mi Olivo solo representa hechos reales ya registrados.

## Fase 9 — Inicio V20 orientado a los tres pilares

Inicio debe priorizar:

- continuar aventura / ruta recomendada
- progreso Mi Olivo
- Mi Campo para agricultor
- tiempo y avisos

Pueblos, Almazaras, Comunidad y Noticias pasan a bloques secundarios y contexto.

## Fase 10 — Módulos secundarios

Extender y pulir:

- Pueblos
- Almazaras
- Comunidad
- Noticias/Agenda
- Empresas
- Experiencias
- Admin completo

Estos módulos no deben bloquear el cierre del bucle principal.

## Fase 11 — QA de producto

Cada bloque P0 necesita:

- unit tests donde aplique
- API/integration tests
- TypeScript/build
- E2E
- responsive 390/768/1440
- accesibilidad básica
- loading/empty/error/offline
- seguridad/permisos
- CI verde

Para GPS: prueba física sobre el terreno.
Para QR: prueba física con dos dispositivos y reintento de doble canje.
Para offline: prueba en modo avión.

## Fase 12 — Staging y mini PC

Flujo de release:

`feature branch -> PR Draft -> CI -> candidate -> staging -> prueba real -> release/tag -> mini PC/producción`

Nunca desplegar directamente desde una rama de experimento.

Datos/secrets:
- código/migraciones/config de ejemplo en GitHub
- `.env`, base de datos, uploads, logs y backups fuera del repositorio

## Vertical piloto obligatorio: Bedmar

Antes de declarar V20 lista para escalar, una persona debe poder hacer de principio a fin:

`registrarse -> ver Mi Olivo en Brote -> elegir una ruta real de Bedmar -> preparar ruta -> iniciar GPS -> caminar -> desbloquear checkpoints -> completar aventura -> recibir XP/aceitunas -> ver crecer Mi Olivo -> reservar recompensa piloto -> generar QR -> validar QR -> ver recompensa canjeada`

Y un agricultor piloto debe poder:

`crear finca -> campaña -> registrar actividad -> consultar histórico -> ver el hecho reflejado en la memoria de Mi Olivo`

## Definition of Done del núcleo V20

No se considera terminado porque las pantallas existan. Se considera terminado cuando el vertical de Bedmar funciona con datos reales, móvil real, GPS real, offline razonable, economía validada, QR validado, CI verde y despliegue reproducible.
