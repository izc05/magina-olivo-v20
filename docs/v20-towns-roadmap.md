# V20 · Pueblos de Mágina / Mis pueblos

## Objetivo

Convertir `Pueblos de Mágina` en el hub territorial de Mágina Olivo V20 y añadir después una capa personal `Mis pueblos` para usuarios registrados.

La implementación debe reutilizar módulos y datos existentes; no duplicar rutas, empresas, almazaras, ayuntamientos, eventos, noticias ni contenido ya administrado por otros dominios.

## Ramas

### `feat/v20-towns-hub`

Base pública y compartida.

Alcance:
- directorio público de pueblos;
- ficha municipal/pueblo;
- navegación territorial;
- relaciones con Ayuntamiento, Empresas, Almazaras, Eventos, Noticias, Rutas/Explorar y Mágina Aventura cuando estén disponibles;
- contenidos publicados desde Administración;
- SEO/URLs y estados loading/empty/error reales;
- responsive y accesibilidad;
- pruebas de navegación pública.

### `feat/v20-my-towns`

Capa personal, dependiente de `feat/v20-towns-hub`.

Alcance:
- seleccionar uno o varios pueblos favoritos;
- `Mi Mágina > Mis pueblos`;
- persistencia de favoritos por usuario;
- contenido priorizado por pueblos seguidos;
- quitar/añadir pueblos sin afectar datos públicos;
- onboarding opcional para elegir pueblos;
- futura integración con notificaciones y eventos locales.

## Arquitectura funcional

### Pueblos de Mágina

Ruta pública principal:
- `/pueblos`

Cada ficha debe actuar como un hub territorial y no como una página aislada.

Bloques previstos:
1. Hero / identidad del pueblo.
2. Resumen e historia.
3. Qué ver / patrimonio / naturaleza.
4. Rutas y Mágina Aventura.
5. Agenda y eventos.
6. Noticias locales.
7. Empresas, servicios, comer y dormir.
8. Almazaras, cooperativas y AOVE.
9. Ayuntamiento y enlaces oficiales.
10. Tiempo y contexto territorial.
11. Mapa y puntos de interés cuando la capa cartográfica esté disponible.

## Regla de datos

La ficha de pueblo es una vista agregadora:
- rutas se leen del módulo de Rutas;
- empresas del directorio Empresas;
- almazaras/cooperativas de su módulo;
- ayuntamiento del bloque municipal;
- eventos de Agenda/Eventos;
- noticias del sistema editorial;
- tiempo del módulo meteorológico;
- aventura/progreso de Mágina Aventura.

No se deben crear copias independientes de esos registros dentro de la ficha del pueblo.

## Modelo territorial mínimo

La entidad pueblo debe disponer, como mínimo, de:
- `id`
- `slug`
- `name/title`
- `summary`
- `body/history`
- `municipality/town`
- `location`
- `address` o referencia geográfica si procede
- `hero/media`
- `official_url`
- `featured`
- `sort_order`
- `published_at`
- `updated_at`

Los campos adicionales de patrimonio, naturaleza, gastronomía, fiestas y recursos deben poder crecer sin romper las fichas existentes.

## UX pública

El listado `/pueblos` debe permitir:
- búsqueda;
- destacados;
- entrada clara a cada pueblo;
- estados vacíos y de error sin datos inventados;
- diseño móvil y escritorio.

La ficha debe priorizar descubrimiento: `Conoce`, `Explora`, `Haz`, `Visita`.

## Mis pueblos

Objetivo: permitir que una misma cuenta siga varios municipios.

Ejemplo:
- Bedmar
- Jódar
- Jimena

Esto no cambia la localidad legal o de residencia del usuario; son preferencias territoriales.

Estados mínimos:
- sin pueblos seleccionados;
- uno seleccionado;
- varios seleccionados;
- añadir;
- quitar;
- reordenar/priorizar en una fase posterior.

## Integraciones futuras

- Inicio personalizado por pueblos seguidos.
- Avisos y agenda local.
- Mágina Pass / recompensas locales.
- Almazaras y comercios adheridos.
- Mágina Aventura: porcentaje de pueblo descubierto.
- Insignias por completar lugares, rutas o patrimonio.
- Panel municipal verificado para ayuntamientos.

## Orden de ejecución

1. Consolidar `feat/v20-towns-hub` sobre el candidate actual.
2. Aprovechar la base ya existente de `/pueblos` y su CMS público.
3. Convertir la ficha en un hub territorial enlazado con módulos reales.
4. Añadir pruebas y validar responsive.
5. Sincronizar `feat/v20-my-towns` encima del HEAD validado de towns-hub.
6. Implementar favoritos personales y la superficie `Mi Mágina > Mis pueblos`.
7. Integrar cada rama por separado en el candidate; nunca directamente en `main`.

## No hacer en esta fase

- no duplicar bases de datos de otros módulos;
- no inventar información municipal;
- no integrar directamente en `main`;
- no mezclar el trabajo antiguo de `agent/pueblos-publicos` con esta nueva línea sin revisión explícita;
- no bloquear Rutas, Empresas, Almazaras, Ayuntamientos o Mágina Aventura.
