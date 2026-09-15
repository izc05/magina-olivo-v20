# Mágina Olivo V20 — Navegación maestra y contrato de montaje local para Codex

## Estado

**DECISIÓN DE PRODUCTO APROBADA — PENDIENTE DE MONTAJE EN LOCAL POR CODEX**

Este documento es la autoridad para la arquitectura de navegación principal de V20 dentro del workstream visual `feat/v20-ui-ux-premium` / PR #138.

No tocar `main` para implementar este cambio.

Codex debe partir del candidate/branch vigente que preserve el trabajo más reciente y comprobar antes de programar qué snapshots especializados de Mágina Aventura, Mi Olivo y Almazaras/Recompensas ya están verdes.

---

## Objetivo

V20 ya tiene suficientes módulos como para que una navegación genérica tipo “Explorar” deje de ser clara. La interfaz debe presentar desde la parte superior los **grandes mundos del producto**, de modo que el usuario entienda inmediatamente qué puede hacer y en qué sección se encuentra.

La navegación principal no es un listado de todas las rutas técnicas. Es una capa de producto.

### Los 8 mundos principales

Orden canónico de escritorio:

1. **Inicio** → `/`
2. **Mágina Aventura** → `/aventura`
3. **Mi Olivo** → `/mi-olivo`
4. **Pueblos** → `/pueblos`
5. **Almazaras** → `/almazaras`
6. **Comunidad** → `/comunidad` (nuevo hub de presentación; reutilizar fuentes reales existentes)
7. **Mi Campo** → `/mi-campo`
8. **Noticias** → `/noticias`

Este orden debe considerarse estable salvo decisión de producto explícita posterior.

---

## Regla fundamental de jerarquía

No poner al mismo nivel de la navegación principal rutas que son submódulos, herramientas o destinos secundarios.

Ejemplos que **NO** deben convertirse en tabs principales por defecto:

- `/explorar`
- `/rutas`
- `/empresas`
- `/experiencias`
- `/magina-pass`
- `/eventos`
- `/radar`
- `/mercado`
- `/ayuntamientos`
- `/servicios`
- `/herramientas`
- `/perfil`
- `/ayuda`
- `/admin`

Esas rutas siguen existiendo y siguen siendo importantes, pero se accede a ellas desde el mundo al que pertenecen, desde Inicio, desde navegación secundaria/contextual o desde “Más”.

---

## Mapa de información

### 1. Inicio

**Ruta:** `/`

Rol: portada general de Mágina Olivo V20.

Debe poder resumir y enlazar hacia:

- tiempo y alertas;
- actividad territorial;
- Mágina Aventura;
- Mi Olivo;
- Mi Campo;
- pueblos destacados;
- noticias/eventos;
- almazaras/AOVE;
- comunidad;
- accesos contextuales a empresas, experiencias y Mágina Pass.

Inicio no debe intentar contener toda la funcionalidad. Debe orientar.

### 2. Mágina Aventura

**Ruta:** `/aventura`

Mundo de exploración física y gamificada.

Submódulos/destinos relacionados:

- `/rutas`;
- catálogo de aventuras;
- aventura en curso;
- mapa/GPS;
- checkpoints;
- grabación de actividad;
- seguridad y avisos;
- XP, nivel e insignias;
- Mi Aventura;
- colecciones Flora/Fauna/Patrimonio/Olivar/Tradiciones/Paisaje;
- comunidad específica de rutas.

La rama especializada de Aventura conserva la autoridad sobre su experiencia premium mientras esté activa. La navegación maestra solo debe integrarla, no duplicarla.

### 3. Mi Olivo

**Ruta:** `/mi-olivo`

Mundo personal de progresión y fidelización.

Submódulos:

- olivo digital;
- nivel y evolución visual;
- XP histórico;
- aceitunas/puntos canjeables;
- misiones;
- logros;
- memoria/historia;
- recompensas;
- reservas;
- QR de canje;
- historial.

No convertirlo visualmente en criptomoneda, inversión o wallet. Puede inspirarse en un activo digital coleccionable premium, pero el significado del producto debe permanecer claro.

### 4. Pueblos

**Ruta:** `/pueblos`

Mundo territorial.

Submódulos/destinos:

- fichas de pueblos;
- patrimonio;
- lugares;
- agenda local;
- avisos municipales;
- rutas relacionadas;
- empresas/servicios del municipio;
- almazaras del municipio;
- experiencias;
- “Mis pueblos” cuando aplique.

`/ayuntamientos` puede seguir existiendo como destino secundario/contextual, no como tab principal.

### 5. Almazaras

**Ruta:** `/almazaras`

Mundo AOVE y conexión física con el territorio.

Submódulos:

- directorio de almazaras/cooperativas;
- ficha premium;
- AOVE/productos cuando existan datos;
- visitas/experiencias;
- recompensas compatibles con Mi Olivo;
- stock;
- reserva;
- QR;
- validación física;
- historial de canje.

`/cooperativas` puede mantenerse por compatibilidad/rutas históricas si hoy existe, pero el nombre de producto principal visible será **Almazaras**.

### 6. Comunidad

**Ruta objetivo:** `/comunidad`

**Actualmente no debe asumirse que existe un hub top-level completo. Codex debe comprobar el estado real antes de crearlo.**

Regla: crear una capa de presentación/agregación, no un segundo sistema social.

Reutilizar únicamente fuentes reales ya existentes, por ejemplo cuando estén disponibles:

- valoraciones aprobadas;
- fotografías moderadas;
- actividad reciente;
- avisos/participación;
- aportaciones ligadas a rutas, pueblos o experiencias.

No inventar feed, usuarios, likes, comentarios o contenido ficticio para rellenar la pantalla.

La primera versión puede ser un hub que agregue actividad real por bloques y derive hacia las superficies existentes.

### 7. Mi Campo

**Ruta:** `/mi-campo`

Mundo técnico/operativo del usuario.

Submódulos:

- fincas;
- recintos/parcelas;
- campañas;
- diario/cuaderno;
- kg/rendimientos;
- riegos;
- abonado;
- tratamientos;
- poda y observaciones;
- documentos;
- agenda/hoy;
- herramientas técnicas/GIS/Catastro/SIGPAC cuando corresponda.

Mi Campo debe ser más sobrio y operativo que Mi Olivo.

### 8. Noticias

**Ruta:** `/noticias`

Mundo editorial de actualidad.

Puede agrupar o enlazar:

- noticias territoriales;
- eventos;
- avisos municipales relevantes;
- campo/agricultura;
- aceite y mercado;
- novedades de pueblos;
- contenido editorial administrado.

`/eventos`, `/mercado` y contenidos relacionados se mantienen como destinos secundarios, filtros o subapartados según el diseño final.

---

## Navegación de escritorio

### Objetivo visual

Una cabecera premium y legible, no una barra técnica de enlaces.

Debe contener:

- marca/logo Mágina Olivo a la izquierda;
- navegación de los 8 mundos en el centro/área principal;
- utilidades de cuenta a la derecha: notificaciones, sesión/avatar/perfil y las acciones realmente necesarias;
- estado activo inequívoco para la sección actual.

### Comportamiento

- sticky cuando no interfiera con mapas/pantallas especiales;
- estado activo por ruta y descendientes;
- soportar teclado y foco visible;
- no depender de hover;
- no romper entre 1024–1920 px;
- si los ocho destinos no caben de forma digna en un ancho intermedio, reducir densidad o usar overflow/menú controlado antes que apretar texto ilegible;
- evitar dos barras principales compitiendo entre sí.

### Activación por descendencia

Ejemplos:

- `/aventura/*` y `/rutas/*` deben poder mantener activo **Mágina Aventura** cuando el contexto corresponda;
- `/mi-olivo/*` → **Mi Olivo**;
- `/pueblos/*` → **Pueblos**;
- `/almazaras/*` y compatibilidad de `/cooperativas/*` → **Almazaras**;
- `/comunidad/*` → **Comunidad**;
- `/mi-campo/*` y sus superficies operativas claramente dependientes → **Mi Campo**;
- `/noticias/*` y, según contexto, eventos/editorial → **Noticias**.

No hacer esta asociación con reglas frágiles dispersas por cada página. Preferir una configuración canónica central de navegación.

---

## Navegación móvil

No intentar meter los ocho mundos en una barra superior ni en un bottom nav de ocho iconos.

### Bottom navigation canónica

1. **Inicio**
2. **Aventura**
3. **Mi Olivo**
4. **Mi Campo**
5. **Más**

### “Más”

Abrir sheet/drawer/pantalla de navegación con acceso claro a:

- Pueblos;
- Almazaras;
- Comunidad;
- Noticias;
- Empresas;
- Experiencias;
- Mágina Pass;
- Radar/Tiempo;
- Eventos;
- Mercado/Aceite;
- Servicios/Herramientas cuando proceda;
- Perfil;
- Ayuda;
- Admin solo si rol/permisos lo permiten.

El orden interno de “Más” debe agruparse por intención, no por orden alfabético.

### Cabecera móvil

- mostrar marca o título de sección de forma clara;
- mantener acciones de cuenta/notificaciones sin saturar;
- respetar safe areas;
- no tapar contenido;
- no duplicar los mismos cinco destinos arriba y abajo.

---

## Navegación secundaria/contextual

Cada mundo puede tener su propia navegación interna.

Ejemplos:

**Mágina Aventura:** Inicio aventura · Rutas · En curso · Mi Aventura · Colecciones · Comunidad.

**Mi Olivo:** Mi Olivo · Misiones · Logros · Recompensas · Historial.

**Mi Campo:** Resumen · Fincas · Campañas · Actividad · Agenda · Documentos/Herramientas.

**Pueblos:** Descubrir · Mis pueblos · Agenda/avisos según datos reales.

Esta navegación secundaria nunca debe competir visualmente con la navegación principal global.

---

## Arquitectura técnica recomendada para Codex

Antes de cambiar JSX de muchas páginas, auditar los componentes actuales (`Topbar`, `BottomNav`, shell/layout y reglas globales).

Crear o consolidar una configuración central equivalente a:

```ts
export const PRIMARY_NAV = [
  { id: 'home', label: 'Inicio', href: '/' },
  { id: 'adventure', label: 'Mágina Aventura', href: '/aventura' },
  { id: 'olive', label: 'Mi Olivo', href: '/mi-olivo' },
  { id: 'towns', label: 'Pueblos', href: '/pueblos' },
  { id: 'mills', label: 'Almazaras', href: '/almazaras' },
  { id: 'community', label: 'Comunidad', href: '/comunidad' },
  { id: 'field', label: 'Mi Campo', href: '/mi-campo' },
  { id: 'news', label: 'Noticias', href: '/noticias' },
] as const;
```

La forma final puede variar según la arquitectura real, pero la información no debe quedar duplicada en Topbar, BottomNav y múltiples páginas.

Preferir:

`configuración canónica → Topbar desktop → BottomNav mobile → More navigation → active-route mapping`

sobre listas de enlaces separadas.

---

## Qué debe hacer Codex en local

### Fase A — auditoría

- actualizar referencias remotas sin tocar `main`;
- revisar candidate vigente y PR #138;
- revisar snapshots verdes de Aventura, Mi Olivo, Almazaras/Recompensas y Admin;
- localizar `Topbar`, `BottomNav`, layouts y tests que dependan de la navegación;
- comprobar si ya existe cualquier implementación parcial de `/comunidad` en otra rama antes de crearla;
- comprobar rutas legacy y redirects antes de renombrar/eliminar nada.

### Fase B — montaje

- implementar configuración canónica de navegación;
- montar Topbar escritorio con los ocho mundos;
- adaptar BottomNav móvil a Inicio/Aventura/Mi Olivo/Mi Campo/Más;
- construir “Más” con agrupación coherente;
- resolver estado activo por descendencia;
- crear `/comunidad` solo como hub real si no existe ya una implementación mejor;
- integrar navegación global en los grandes mundos sin duplicar shells;
- preservar rutas existentes y compatibilidad.

### Fase C — pulido

- 360/390/430 px;
- 768 px;
- 1024 px;
- 1280 px;
- 1440 px;
- 1920 px;
- teclado/foco;
- safe areas;
- contraste;
- touch targets >= 44 px;
- reduced motion;
- estados sin sesión y permisos cuando afecten a destinos privados.

### Fase D — validación

Ejecutar sobre el mismo HEAD final, cuando apliquen:

- TypeScript/typecheck;
- build producción;
- Browser E2E;
- Responsive E2E;
- Full Candidate;
- Environment Contract;
- Staging Readiness;
- tests específicos de Aventura/Mi Olivo/Mi Campo/Pueblos/Almazaras;
- smoke de navegación de todos los destinos primarios;
- regresión de rutas legacy.

No considerar terminado con tests en rojo.

---

## Casos E2E mínimos que deben quedar marcados

1. Escritorio: recorrer los ocho mundos desde la navegación superior.
2. Escritorio: estado activo correcto al entrar en descendientes relevantes.
3. Móvil: BottomNav muestra exactamente Inicio/Aventura/Mi Olivo/Mi Campo/Más.
4. Móvil: “Más” permite llegar a Pueblos/Almazaras/Comunidad/Noticias.
5. Móvil: ninguna navegación tapa CTA, formularios, mapas ni contenido inferior.
6. Usuario sin sesión: destinos públicos siguen navegables y destinos privados muestran el flujo existente, no un error roto.
7. Usuario con permisos Admin: acceso Admin visible únicamente en el lugar previsto, nunca como mundo público principal.
8. `/comunidad`: solo datos reales; empty state válido cuando no hay actividad.
9. Rutas legacy: no aparecen 404 por reorganizar la navegación.
10. No hay overflow horizontal en los breakpoints obligatorios.

---

## Decisiones explícitas que NO debe reinterpretar Codex

- El top-level de producto son **8 mundos**, no todos los módulos del repositorio.
- **Explorar deja de ser una categoría principal de la cabecera**; puede seguir existiendo como landing/descubrimiento interno.
- **Mágina Aventura** tiene entrada propia principal.
- **Mi Olivo** tiene entrada propia principal.
- **Pueblos** y **Almazaras** tienen entrada propia principal.
- **Comunidad** debe verse como mundo propio aunque inicialmente sea un agregador real.
- **Mi Campo** debe ser acceso principal, pero visualmente operativo.
- **Noticias** debe ser acceso principal editorial.
- Perfil, Admin, Empresas, Experiencias, Mágina Pass, Radar, Mercado, Eventos, Servicios y Herramientas son secundarios/contextuales.
- En móvil, no mostrar ocho tabs: usar 4 destinos + Más.
- No tocar `main`.
- No inventar contenido.
- No duplicar lógica ni backend por reorganizar la interfaz.
- No cerrar el PR visual hasta que la navegación funcione en escritorio y móvil y el QA esté verde.

---

## Criterio de cierre

La reorganización se considera terminada cuando un usuario nuevo puede abrir V20 y, sin conocer la arquitectura interna, distinguir de inmediato:

**Inicio · Mágina Aventura · Mi Olivo · Pueblos · Almazaras · Comunidad · Mi Campo · Noticias**

y puede entrar y volver entre esos mundos con una navegación consistente, responsive y accesible.

El resultado debe sentirse como **una plataforma grande y ordenada**, no como una suma de páginas o ramas independientes.
