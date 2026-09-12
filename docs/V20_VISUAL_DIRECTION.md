# Mágina Olivo V20 — Dirección visual premium

**Estado:** referencia visual previa a implementación.

## Objetivo

La interfaz V20 debe sentirse territorial, humana, premium y sencilla; nunca como un ERP agrícola genérico.

La identidad mezcla:
- olivar;
- Sierra Mágina;
- fotografía cálida;
- fondos marfil/crema;
- verdes oliva profundos;
- detalles dorados suaves;
- tipografía editorial para títulos;
- tipografía sans clara para datos/acciones.

## Principio visual

> Una aplicación de campo con la calidad visual de una publicación editorial premium.

La estética nunca debe perjudicar la lectura ni la rapidez de registro.

---

## 1. Paleta semántica inicial

No fijar valores definitivos hasta crear tokens, pero trabajar con estas familias:

- `olive-950`: verde oliva muy oscuro — acciones principales/contraste.
- `olive-700`: verde principal.
- `sage-100`: verde muy suave — fondos de estado.
- `cream-50`: fondo principal cálido.
- `sand-100`: superficie secundaria.
- `gold-500`: acento premium muy limitado.
- `sky-100`: riego/clima.
- `rose-100`: alertas sanitarias suaves.
- `amber-100`: atención.
- `ink-950`: texto editorial.
- `ink-600`: texto secundario.

Nunca usar rojo intenso como decoración. Solo para estados críticos.

---

## 2. Tipografía

### Display/editorial
Para:
- logo;
- títulos hero;
- nombres de finca;
- títulos grandes.

Debe tener carácter mediterráneo/editorial, alta legibilidad y soporte español.

### Sans UI
Para:
- formularios;
- cifras;
- botones;
- navegación;
- tablas/admin.

Regla: no usar tipografía manuscrita salvo frases decorativas puntuales, nunca para información funcional.

---

## 3. Fotografía y assets

La dirección creativa de V20 se construirá con imágenes propias/generadas para la marca, no copiadas de páginas turísticas.

Familias de assets:
- paisajes de olivar y sierra;
- pueblos serranos estilizados;
- fincas de referencia;
- ramas/aceitunas/macros;
- manos/trabajo de campo;
- gastronomía/AOVE;
- maquinaria/agricultura;
- texturas suaves de hojas;
- banners de comunidad.

### Regla
Las imágenes generadas son **assets de marca**, no fuentes de datos geográficos. Catastro, SIGPAC, radar, mapas y meteorología real proceden de fuentes técnicas.

### Hero dinámico
El municipio detectado por GPS selecciona una imagen hero V20 asociada. Si no existe, usar hero genérico Sierra Mágina.

---

## 4. Componentes visuales clave

### `WeatherHero`
- imagen de fondo;
- overlay/degradado;
- municipio;
- temperatura;
- condición;
- viento/humedad;
- mensaje corto;
- logo/notificaciones.

### `AlertCard`
- icono claro;
- título corto;
- dato principal;
- severidad por fondo/acento;
- navegación.

### `MyFieldSummary`
- nº fincas;
- nº olivos;
- aviso principal;
- CTA `Entrar en Mi Campo`.

### `FarmCard`
- imagen;
- nombre;
- municipio;
- nº olivos/superficie;
- estado o próximo evento.

### `FarmModuleTile`
- icono + palabra;
- resumen vivo;
- ruta al histórico;
- jamás abrir un formulario directamente salvo acción `Registrar`.

### `TimelineItem`
- fecha;
- icono;
- tipo;
- resumen;
- importe/documento cuando aplique.

### `PrimaryAction`
Botón oliva oscuro, grande, claro, con texto; no depender de icono solo.

---

## 5. Pantallas visuales de referencia

### Pantalla A — Inicio premium
Orden:
1. Hero municipio + clima.
2. Avisos importantes.
3. Resumen Mi Campo.
4. Actualidad/eventos.
5. Cooperativas/Almazaras o patrocinado.
6. Gastronomía/ruta/contenido local.
7. Comunidad/CTA.
8. Bottom navigation.

### Pantalla B — Mi Campo
1. Cabecera simple.
2. Resumen total.
3. Carrusel/lista de fincas.
4. Accesos rápidos.
5. Hoy/próximas tareas.

### Pantalla C — Finca viva
1. Hero/foto finca.
2. Nombre + nº olivos + municipio + régimen.
3. Tres KPIs principales.
4. Grid de módulos.
5. Últimos movimientos.
6. CTA `Registrar`.

### Pantalla D — Registrar cosecha/OCR
1. flujo `Foto -> OCR -> Confirmar`;
2. imagen original;
3. datos detectados;
4. confianza/estado;
5. edición de campos;
6. confirmar y guardar.

### Pantalla E — Radar de lluvia
1. finca/ubicación;
2. mapa/radar;
3. estado de precipitación;
4. próximas horas;
5. configuración de alertas.

### Pantalla F — Perfil
1. cover + avatar;
2. nombre/municipio/roles;
3. completitud;
4. cuenta;
5. preferencias;
6. privacidad/permisos;
7. Mi Olivo;
8. perfil profesional;
9. exportar/cerrar sesión.

### Pantalla G — Web escritorio
Hero grande con narrativa de marca + visual del móvil. Debajo: avisos, Mi Campo, noticias/eventos, almazaras, gastronomía y empresas destacadas.

---

## 6. Navegación móvil

Bottom navigation estable:
- Inicio
- Mi Campo
- Explorar
- Más

`Registrar` es contextual dentro de Mi Campo y ficha de finca; puede evolucionar a FAB solo si las pruebas demuestran que mejora el uso.

---

## 7. Motion

Motion discreto:
- fade/crossfade hero;
- desplazamiento suave de tarjetas;
- cambios de estado;
- radar animado;
- feedback de guardado;
- skeletons.

No usar animaciones largas ni parallax pesado en formularios.

Respetar `prefers-reduced-motion`.

---

## 8. Accesibilidad visual

- targets táctiles >= 48 px;
- texto funcional legible a sol abierto;
- contraste AA como mínimo;
- color + texto/icono para estados;
- focus visible;
- formularios con labels reales;
- no esconder datos importantes sobre fotografía sin overlay suficiente.

---

## 9. Regla para convertir concept art en código

Cada pantalla generada debe transformarse a:
1. layout responsive;
2. tokens;
3. componentes reutilizables;
4. estados reales;
5. contenido simulado estructurado;
6. pruebas visuales;
7. screenshot comparativo.

No perseguir pixel-perfect a costa de accesibilidad o capacidad de respuesta, pero sí mantener composición, jerarquía y carácter.

---

## 10. Primera maqueta funcional

Construir primero con datos mock:
- `/` Inicio premium;
- `/mi-campo`;
- `/mi-campo/fincas/las-cenillas`;
- `/mi-campo/registrar/cosecha`;
- `/radar`;
- `/perfil`.

Sin DB definitiva. Esto será nuestro **Visual V20 Candidate** antes del backend real.
