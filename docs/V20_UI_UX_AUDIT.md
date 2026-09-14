# V20 UI/UX Premium — auditoría inicial

## Estado revisado

Rama base revisada: `integrate/v20-beta-closure`.
Rama de trabajo: `feat/v20-ui-ux-premium`.

La base visual actual es sólida y ya contiene responsive, accesibilidad e identidad territorial. El principal riesgo no es la falta de estilo, sino la acumulación de capas globales y patrones visuales que han evolucionado en distintas fases.

## Hallazgo 1 — demasiadas capas CSS globales

`apps/web/src/app/layout.tsx` carga, además de MapLibre, 22 hojas CSS propias en cascada:

1. `globals.css`
2. `premium.css`
3. `pwa.css`
4. `assets.css`
5. `record.css`
6. `new-farm.css`
7. `farm-modules.css`
8. `calendar.css`
9. `local-prototype.css`
10. `map-platform.css`
11. `radar-alerts.css`
12. `mobile-hardening.css`
13. `managed-content.css`
14. `visual-final.css`
15. `visual-mi-campo.css`
16. `visual-farm-detail.css`
17. `visual-today.css`
18. `visual-business.css`
19. `visual-business-mobile.css`
20. `visual-map-radar.css`
21. `responsive-beta.css`
22. `visual-premium-pass.css`

Esto no implica que deban fusionarse todas. Sí implica que cualquier nueva capa global aumenta el riesgo de:

- overrides difíciles de rastrear;
- especificidad creciente;
- diferencias inesperadas entre móvil y escritorio;
- duplicación de tokens;
- regresiones al incorporar módulos nuevos.

### Decisión

No crear una nueva hoja global `ui-ux-premium.css` como parche general.

Primero separar claramente:

- tokens globales;
- shell/navegación;
- componentes compartidos;
- estilos específicos de cada módulo.

## Hallazgo 2 — tokens duplicados

`globals.css` define el sistema compartido de variables `--bg`, `--surface`, `--ink`, familia `--olive-*`, `--gold`, líneas, sombras y radios.

`visual-premium-pass.css`, cargado después, vuelve a declarar gran parte de esos mismos tokens con valores distintos.

En la práctica, la segunda declaración es la que gobierna la experiencia final, pero la fuente de verdad no queda clara al leer el código.

`visual-final.css` añade además un segundo namespace `--v20-*` para parte de la misma semántica visual.

### Decisión

Crear una fuente canónica de tokens en una fase de refactor aislada y **visualmente neutra**. No modificar colores o escalas durante ese traslado. Una vez consolidado el sistema, los cambios de dirección visual serán deliberados y revisables.

## Hallazgo 3 — shell definido en varias generaciones

`globals.css`, `visual-final.css` y `visual-premium-pass.css` intervienen en piezas transversales como:

- `body`;
- `.app-shell`;
- `.topbar`;
- `.brand`;
- `.card`;
- `.section`;
- navegación.

La composición funciona actualmente por orden de carga, no por una frontera de responsabilidad totalmente explícita.

### Decisión

El primer refactor de código debe limitarse a shell/tokens y mantener el resultado visual actual. Solo después se hará la pasada creativa de Inicio/Explorar.

## Hallazgo 4 — la base ya es suficientemente premium

No conviene rehacer V20 desde cero. La base actual ya aporta:

- paleta oliva/arena/dorado;
- fotografía territorial;
- serif editorial + sans para interfaz;
- cards y sombras suaves;
- navegación móvil/escritorio;
- responsive 360–1920;
- foco visible;
- reduced motion;
- iconografía premium.

La nueva fase debe mejorar coherencia, jerarquía, ritmo y personalidad por módulo, no sustituir la identidad existente.

## Primer orden de trabajo

### Paso A — refactor neutro

- consolidar tokens;
- aclarar responsabilidad del shell;
- reducir overrides duplicados cuando sea seguro;
- no cambiar comportamiento ni datos;
- comparar screenshots antes/después.

### Paso B — navegación y arquitectura visual

- topbar;
- navegación móvil;
- navegación de escritorio;
- cabeceras de página;
- buscador global/accesos rápidos si ya existen;
- patrones de CTA;
- estados globales.

### Paso C — Inicio + Explorar

Convertir ambos en la referencia visual del resto del producto:

- portada editorial clara;
- menos ruido;
- prioridad territorial;
- accesos a funciones principales;
- mejor lectura en escritorio;
- continuidad visual hacia Pueblos, Rutas, Empresas, Almazaras y Experiencias.

### Paso D — módulos territoriales

Orden recomendado:

1. Pueblos;
2. Rutas/Senderismo;
3. Mágina Aventura;
4. Almazaras;
5. Empresas;
6. Experiencias;
7. Mágina Pass;
8. Noticias/Eventos.

### Paso E — ecosistema personal

- Mi Olivo;
- niveles/logros/recompensas;
- QR/canje;
- Mi Campo;
- Perfil.

### Paso F — Admin

Admin debe adoptar los mismos tokens y componentes, pero mantener una densidad mayor y un patrón de navegación orientado a escritorio.

## Regla de integración

Cada lote visual debe ser pequeño, reversible y verificable. No se integrarán cambios masivos de CSS junto con lógica funcional nueva.

Antes de absorber un lote al candidate:

- TypeScript/build verdes;
- responsive E2E;
- accessibility smoke;
- Browser E2E si cambia navegación/superficie funcional;
- comparación visual móvil/escritorio;
- sin regresiones de estados loading/empty/error/sin sesión.
