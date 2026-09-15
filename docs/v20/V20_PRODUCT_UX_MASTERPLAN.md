# Mágina Olivo V20 — Product & UX Masterplan

> Documento maestro de producto, UX y dirección visual de V20.
>
> Rama de trabajo: `feat/v20-ui-ux-premium`
>
> Objetivo: convertir las decisiones de producto y diseño en una referencia estable para Figma, Codex, GitHub y despliegue, evitando que cada frente improvise su propia arquitectura.

## 1. Principios de producto

- **Mágina Olivo es la plataforma general.**
- Mágina Aventura, Mi Olivo, Pueblos, Almazaras, Comunidad, Mi Campo y Noticias son mundos/módulos dentro de la plataforma.
- Fondo global **blanco**. El crema/marfil queda reservado para tarjetas, bloques suaves y estados secundarios.
- Verde bosque como acción primaria y navegación activa.
- Oliva/dorado para progreso, XP, niveles, recompensas y detalles premium.
- Fotografía real de Sierra Mágina como parte central de la identidad.
- Gamificación elegante, territorial y adulta; nunca infantil.
- Mobile-first, pero escritorio con composición propia, no “móvil estirado”.
- Seguridad, claridad y datos reales tienen prioridad sobre decoración o gamificación.

## 2. Lenguaje visual base

### Paleta inicial
- Forest: `#263A20`
- Olive: `#65724A`
- Gold: `#A78A45`
- Cream: `#F5F1E8`
- Paper: `#FBFAF6`
- Ink: `#20231C`
- Background global: `#FFFFFF`

### Tipografía
- Serif elegante para titulares territoriales/emocionales.
- Sans limpia para UI, datos, formularios, tablas y navegación.

### Componentes
- Radios de 16–24 px.
- Botones primarios en verde bosque.
- Sombras suaves.
- Targets táctiles >= 44 px.
- Iconografía orgánica, simple y consistente.

## 3. Navegación global

### Escritorio
`Inicio · Mágina Aventura · Mi Olivo · Pueblos · Almazaras · Comunidad · Mi Campo · Noticias`

A la derecha:
- búsqueda
- notificaciones
- perfil

### Móvil
`Inicio · Explorar · Aventura · Mi Olivo · Perfil`

`Explorar` agrupa Pueblos, Rutas, Almazaras, Experiencias, Noticias y otros recursos territoriales.

## 4. Inicio V20

Objetivo: responder rápido a tres preguntas:
1. ¿Qué está pasando ahora en Sierra Mágina?
2. ¿Qué puedo hacer hoy?
3. ¿A qué parte de la plataforma quiero ir?

### Bloques
- Cabecera global.
- Hero territorial vivo con fotografía real.
- Tiempo / alertas / estado de rutas / agenda.
- Grandes mundos V20.
- “Continúa donde lo dejaste”.
- Aventura destacada.
- Resumen de Mi Olivo.
- Descubre Sierra Mágina.
- Pueblos con historia.
- AOVE y Almazaras.
- Comunidad / actividad territorial.
- Noticias y agenda.

### Personalización
- Visitante: territorio, aventura, pueblos, almazaras, noticias y experiencias.
- Usuario registrado: añade Mi Olivo, progreso, favoritos y continuidad.
- Agricultor: añade resumen Mi Campo.

## 5. Mágina Aventura

Mágina Aventura no es solo senderismo: es exploración territorial con rutas, descubrimientos, retos, colecciones, progreso y seguridad.

### Flujo completo
1. Portada Mágina Aventura.
2. Explorar rutas y aventuras.
3. Ficha de ruta.
4. Preparación antes de salir.
5. Aventura en curso.
6. Checkpoint / descubrimiento.
7. Reto.
8. Final de aventura.
9. Mi Aventura.
10. Colecciones.

### Ruta vs Aventura
- **Ruta**: recorrido real (GPX, track, distancia, desnivel, dificultad, seguridad).
- **Aventura**: experiencia que usa una ruta y añade historia, checkpoints, retos, XP, aceitunas y colecciones.

Una ruta puede soportar varias aventuras.

### Aventura en curso
- Mapa como protagonista.
- GPS / posición actual.
- Track recorrido y pendiente.
- Siguiente checkpoint.
- Distancia restante.
- Tiempo y progreso.
- XP / descubrimientos.
- Controles grandes.
- Modo offline.
- Avisos de seguridad por encima de gamificación.

### Final de aventura
- kilómetros
- tiempo
- desnivel
- descubrimientos
- XP
- aceitunas
- insignias
- colecciones desbloqueadas

## 6. Mi Olivo

Mi Olivo es la representación visual, emocional y de progreso de la actividad del usuario.

### Pantalla principal
- Olivo protagonista y casi real.
- Nivel.
- XP actual / siguiente nivel.
- Saldo de aceitunas.
- Estado visual.
- Evolución.
- Cuidados y memoria.
- Colección.
- Insignias.
- Recompensas.
- QR de canje.

### Experiencia visual
El olivo debe sentirse como un activo digital vivo, pero sin blockchain ni estética cripto llamativa.

Primera versión técnica recomendada:
- 2.5D cinematográfico.
- capas del olivo separadas.
- movimiento suave de hojas y ramas.
- parallax.
- luz dinámica.
- transiciones por nivel.
- CSS/GSAP en producción.

Three.js queda como evolución posterior si aporta valor real.

### Evolución
Ejemplo de etapas:
`Semilla → Brote → Plantón → Joven → Adulto → Maduro → Centenario`

Cada etapa cambia el aspecto del olivo.

### Subida de nivel
Debe sentirse como un momento especial:
- cambio de iluminación
- crecimiento de copa/ramas
- desbloqueo de insignia o recompensa
- feedback visual premium

### Cuidados y memoria
Mi Campo es la fuente técnica; Mi Olivo interpreta la historia.

Ejemplo:
- Mi Campo: `Poda · Parcela Norte · 12 febrero`
- Mi Olivo: `12 febrero · Un nuevo ciclo`

No se inventan datos.

## 7. Recompensas y QR

### Catálogo
Recompensas iniciales centradas en AOVE de almazaras/cooperativas reales.

Cada recompensa puede incluir:
- fotografía real
- productor
- variedad
- formato
- coste en aceitunas
- stock
- límites de usuario
- caducidad

### Flujo
1. Usuario selecciona recompensa.
2. Backend comprueba saldo y stock.
3. Se bloquea/reserva una unidad.
4. Se genera reserva.
5. Se genera QR/token único.
6. Usuario presenta QR en almazara.
7. Almazara valida.
8. Backend impide doble canje.
9. Se registra historial.
10. Recompensa pasa a `Canjeada`.

Estados QR:
- pendiente
- activo
- usado
- caducado

## 8. Pueblos

Cada municipio debe sentirse como un mundo territorial, no como una ficha plana.

### Portada Pueblos
- mapa Sierra Mágina
- fotografía
- búsqueda
- filtros
- lista/mapa móvil

### Ficha de pueblo
- hero fotográfico
- información principal
- progreso del usuario
- qué ver
- rutas
- patrimonio
- naturaleza
- almazaras
- experiencias
- agenda
- noticias
- comunidad

### Progreso territorial
Ejemplo:
- porcentaje explorado
- lugares visitados
- rutas completadas
- descubrimientos
- insignias

### Mis Pueblos
El usuario puede seguir municipios para personalizar avisos, eventos, noticias, rutas y novedades.

## 9. Almazaras

No debe parecer un directorio comercial.

### Ficha de almazara
- identidad y fotografía
- municipio
- historia
- variedades
- productos AOVE
- experiencias
- recompensas
- ubicación
- horarios
- contacto

### AOVE
Separar claramente:
- producto informativo/comercial
- recompensa canjeable con aceitunas

### Panel de almazara
Espacio propio, separado del Admin global:
- perfil
- productos
- experiencias
- recompensas
- stock
- reservas
- QR pendientes
- canjes
- estadísticas básicas

## 10. Comunidad

No será una red social genérica.

Objetivo: mostrar vida territorial útil y moderada.

### Contenido
- rutas completadas
- fotografías aprobadas
- descubrimientos
- reseñas
- visitas
- logros
- actividad local

### Perfil público
Ligero y respetuoso con privacidad:
- avatar
- nivel
- km recorridos
- pueblos explorados
- insignias
- aportaciones públicas aprobadas

Nunca exponer automáticamente fincas, ubicaciones privadas o datos de Mi Campo.

### Moderación
`Pendiente → Aprobado → Rechazado`

Capacidades:
- ocultar
- reportar
- bloquear si fuera necesario

## 11. Noticias y Agenda

Noticias se integra con el territorio; no es un blog aislado.

### Categorías
- actualidad
- agenda
- avisos municipales
- campo y olivar
- AOVE
- naturaleza / rutas

### Contexto
Cada contenido conoce su municipio/categoría y puede aparecer automáticamente en:
- Noticias
- ficha del pueblo
- Mis Pueblos
- Inicio

### Automatización IA
Flujo inicial recomendado:
`Fuente oficial → IA prepara borrador → Admin revisa → Publicar`

No publicación automática masiva al principio.

### Eventos
Datos estructurados:
- fecha
- hora
- lugar
- municipio
- categoría
- enlace
- inscripción cuando aplique

## 12. Mi Campo

Personalidad visual más técnica y operativa, manteniendo la identidad V20.

### Principios
- menos fotografía
- más mapas
- más datos
- gráficos
- formularios
- tablas
- alertas

### Inicio Mi Campo
- fincas
- campaña activa
- producción
- rendimiento
- próximo trabajo
- alertas
- clima agrícola

### Mis Fincas
- nombre
- superficie
- municipio
- parcela/referencia
- mapa
- cultivo
- campaña
- actividad reciente

### Campañas
Agrupan:
- producción
- entregas
- rendimiento
- tratamientos
- abonado
- riegos
- observaciones

### Cuaderno
Registro rápido:
- riego
- tratamiento
- abonado
- poda
- recolección
- observación

### Tiempo agrícola
Separar siempre:
- dato oficial
- interpretación o recomendación propia

## 13. Roles de usuario

### Visitante
Inicio, Pueblos, Noticias, Rutas, Almazaras, Empresas, Experiencias.

### Registrado
Añade Mi Olivo, Comunidad, Mi Aventura, Mis Pueblos, favoritos y recompensas.

### Agricultor
Añade Mi Campo.

### Almazara
Gestiona perfil, productos, experiencias, stock, recompensas y QR.

### Empresa
Gestiona perfil, experiencias y ofertas.

### Admin
Control global.

## 14. Perfil, búsqueda y notificaciones

### Perfil
Debe concentrar:
- cuenta
- privacidad
- preferencias
- Mis Pueblos
- favoritos
- historial
- recompensas
- actividad pública
- accesos a roles especiales

### Búsqueda global
Debe encontrar:
- pueblos
- rutas
- aventuras
- lugares
- almazaras
- experiencias
- noticias

No debe mezclar resultados sin contexto: cada resultado muestra tipo + municipio + acción.

### Notificaciones
Agrupar por relevancia:
- seguridad/avisos
- recompensas
- rutas/aventuras
- pueblos seguidos
- Mi Campo
- comunidad

Evitar ruido y notificaciones puramente promocionales por defecto.

## 15. Admin global

Admin es herramienta de operación, no una versión “grande” de la app pública.

### Módulos mínimos
- usuarios y roles
- pueblos
- lugares/patrimonio
- rutas
- aventuras
- almazaras
- empresas
- experiencias
- recompensas
- stock/reservas/canjes
- noticias
- eventos
- avisos municipales
- comunidad/moderación
- contenidos destacados
- auditoría/actividad

## 16. Estados comunes obligatorios

Toda pantalla importante debe definir al menos:
- cargando
- vacío
- error
- sin conexión
- sin permisos
- bloqueado
- disponible
- completado

Estados específicos:
- GPS sin permiso
- GPS débil
- ruta descargada/no descargada
- QR activo/usado/caducado
- recompensa disponible/reservada/agotada/canjeada
- contenido pendiente/aprobado/rechazado
- usuario sin finca/campaña

## 17. Movimiento y animación

Principio: el movimiento explica cambios; no existe por decorar.

### Mi Olivo
- viento suave
- parallax
- luz
- crecimiento por nivel
- pequeñas hojas/partículas

### Aventura
- movimiento mapa
- checkpoints con pulso
- cards desde abajo
- feedback XP
- transiciones mapa/descubrimiento

### Inicio
- hero/parallax muy suave
- cards hover
- árbol mini animado
- datos con aparición sutil

## 18. Flujo diseño → código → despliegue

`Idea → concepto visual → Figma → aprobación → componentes → Codex/React → datos reales → pruebas → GitHub → candidate/staging → mini PC → producción`

### Figma
Define:
- aspecto
- jerarquía
- componentes
- variantes
- prototipos
- responsive

### GitHub
Conserva:
- código
- componentes
- migraciones
- assets
- Docker
- documentación
- configuración de ejemplo

### Mini PC
Ejecuta:
- contenedores
- app
- base de datos
- uploads
- secretos `.env`
- logs
- backups

## 19. Prioridad de diseño en Figma

1. Foundations.
2. Navegación global.
3. Inicio V20.
4. Mágina Aventura Home.
5. Ficha de ruta.
6. Preparación.
7. Aventura en curso.
8. Checkpoint/Reto.
9. Final de aventura.
10. Mi Aventura.
11. Mi Olivo Premium.
12. Evolución Mi Olivo.
13. Recompensas.
14. QR.
15. Pueblos.
16. Ficha pueblo.
17. Almazaras.
18. Ficha almazara.
19. Comunidad.
20. Noticias/Agenda.
21. Mi Campo.
22. Perfil.
23. Admin.
24. Estados comunes.

## 20. Regla de cierre visual

Una pantalla no se considera cerrada hasta validar:
- móvil 390
- tablet 768
- escritorio 1440
- datos reales o estados explícitos
- navegación
- loading/error/empty cuando aplique
- accesibilidad básica
- comparación con objetivo Figma

---

Este documento es la referencia maestra de producto/UX para V20 y debe actualizarse cuando se apruebe una decisión transversal importante.
