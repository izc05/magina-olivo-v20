# Mágina Aventura V20 — Objetivo visual de cierre

## Objetivo único

Mágina Aventura no se considerará visualmente terminada hasta que la implementación real de la web alcance el nivel de producto representado en la referencia visual aprobada: una experiencia premium de Sierra Mágina, coherente en escritorio, tablet y móvil, basada exclusivamente en datos y funcionalidades reales de V20.

La referencia visual es una guía de jerarquía, densidad, ritmo, composición y sensación de producto. No se aceptará una traducción literal con datos ficticios ni una colección de pantallas aisladas sin continuidad funcional.

## Definition of Done visual

### 1. Home / Hub de Mágina Aventura
- Hero panorámico fotográfico con identidad Mágina Olivo V20.
- CTA principal de inicio de aventura.
- Accesos rápidos a Rutas, Colecciones, Mi Aventura y Pasaporte/Comunidad.
- Ruta destacada y catálogo real de aventuras.
- Métricas reales de rutas, descubrimientos y XP.
- Progreso personal e historial reciente cuando exista sesión.
- Responsive específico para escritorio, tablet y móvil.

### 2. Catálogo de rutas
- Cards fotográficas premium con dificultad, distancia, duración, desnivel/checkpoints y XP cuando proceda.
- Jerarquía visual clara para ruta destacada.
- Filtros/navegación coherentes con Rutas y Explorar.
- Sin rutas ficticias de relleno.

### 3. Aventura en curso
- Escritorio: mapa protagonista + panel lateral operativo de GPS, seguridad y checkpoints.
- Móvil: experiencia vertical de marcha, controles accesibles y mapa optimizado.
- Estado GPS, progreso, checkpoints y seguridad visibles sin mezclar sus contratos de privacidad.
- Acceso permanente a la ficha técnica real de senderismo.

### 4. Mi Aventura / Gamificación
- Niveles 1–10 con rango actual, XP y progreso al siguiente nivel.
- Kilómetros conquistados separados de kilómetros grabados.
- Porcentaje real de Sierra Mágina explorado.
- Municipios descubiertos.
- Insignias y retos en curso.
- Tratamiento visual premium, no cripto ni casino.

### 5. Colecciones
- Álbum visual con Flora, Fauna, Patrimonio, Olivar, Tradiciones y Paisaje.
- Conteo desbloqueado/disponible y rareza cuando exista.
- Estados vacíos reales.
- Descubrimientos conectados a checkpoints editoriales reales.

### 6. Comunidad
- Integración visual con la comunidad existente de rutas/V20.
- Actividad y logros reales cuando existan datos.
- Sin inventar usuarios, números ni publicaciones.
- Acceso coherente desde Mágina Aventura.

### 7. Sistema visual
- Paleta Mágina Olivo: verdes profundos, oliva, crema y oro.
- Fotografía territorial protagonista.
- Bordes suaves, capas, tarjetas premium y profundidad contenida.
- Tipografía y jerarquía coherentes con el workstream UI/UX V20.
- Iconografía simple, consistente y reconocible.
- Estados loading/error/empty diseñados, no genéricos.

### 8. Responsive
- Desktop >= 1100 px: composiciones amplias, varias columnas, mapa + panel lateral.
- Tablet: reorganización a 2 columnas cuando sea útil.
- Mobile: navegación y acciones táctiles, sin scroll horizontal accidental ni elementos diminutos.
- No se considera terminado si una de las tres experiencias parece una adaptación secundaria.

### 9. Calidad técnica
- No tocar `main`.
- Trabajar en `feat/v20-routes-adventure-premium-mobile` y PR #142.
- Mantener APIs, privacidad GPS, seguridad de rutas y lógica de progreso actuales.
- TypeScript, build, workflows de Aventura, Activity, Full Candidate, Browser E2E, Environment y Staging deben quedar verdes sobre el mismo HEAD antes de promover integración.

## Regla de cierre

El frente se marca como terminado solo cuando:

1. las seis áreas de producto anteriores tienen lenguaje visual coherente;
2. desktop, tablet y móvil están resueltos;
3. no quedan placeholders o datos ficticios usados para simular la referencia;
4. los estados vacíos y de error mantienen la misma calidad visual;
5. el PR está sincronizado con el candidate vigente;
6. todos los gates requeridos están verdes sobre el mismo HEAD.
