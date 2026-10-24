# Mágina Olivo Web V2 — Cinematic Product-First Master Spec

Estado: **CANONICAL / LOCKED**  
Fecha: 2026-09-21  
Sustituye la dirección visual/comercial de Web V1 para la Home y superficies promocionales.

## 1. Decisión de producto

Mágina Olivo es una aplicación para la gestión del olivar.

La web comercial NO se presenta como una web de Sierra Mágina ni como un portal territorial. El producto debe resultar natural para cualquier olivicultor de Jaén y poder extenderse posteriormente fuera de la provincia sin rehacer su posicionamiento.

La marca conserva el nombre **Mágina Olivo**, pero el mensaje comercial es:

> Producto primero. Olivar primero. Agricultor primero.

El territorio puede aparecer en fotografía como identidad visual de origen, pero nunca como límite funcional o comercial.

## 2. Objetivo de la web

La Home debe conseguir tres cosas en menos de dos minutos:

1. hacer entender que Mágina Olivo sirve para gestionar el olivar;
2. mostrar visualmente cómo acompaña al agricultor desde el campo hasta el registro digital;
3. dejar deseo de probar/descargar la aplicación.

La experiencia debe sentirse como una pieza audiovisual controlada por scroll, no como una sucesión de secciones informativas.

## 3. Principio rector

**Si algo puede contarse visualmente, no se explica con una tarjeta de texto.**

Reducir:
- párrafos largos;
- grids de beneficios;
- tarjetas repetitivas;
- explicaciones técnicas;
- bloques territoriales;
- decoración sin función narrativa.

Aumentar:
- fotografía premium;
- secuencia de imágenes;
- primeros planos;
- gestos reales;
- transiciones;
- pantallas reales de la app;
- frases de 2–8 palabras;
- tiempo visual para cada momento.

## 4. Audiencia

Prioridad inicial:
- agricultores y propietarios de olivar;
- personas que gestionan una o varias fincas;
- usuarios de Jaén en primera fase.

La comunicación no debe excluir:
- otras provincias olivareras;
- usuarios con fincas pequeñas;
- usuarios con varias explotaciones.

No usar lenguaje que convierta el producto en exclusivo de Sierra Mágina.

## 5. Dirección artística

### Identidad
- crema natural;
- verde olivo;
- verde bosque profundo;
- tierra/dorado como acento;
- blanco roto;
- contraste premium y sobrio.

### Fotografía
- realista y de alta calidad;
- agricultor creíble, no modelo publicitario;
- olivar contemporáneo;
- manos, ramas, aceitunas, terreno, maquinaria/contexto;
- luz natural;
- profundidad de campo cinematográfica;
- evitar stock genérico;
- evitar textos incrustados en imágenes.

### Tipografía
- serif editorial para mensajes emocionales;
- sans limpia para UI;
- manuscrita solo como detalle puntual;
- titulares grandes;
- muy poco texto sobre imagen.

### Logo
La V2 debe revisar y elevar el logo antes de cerrar producción.

Criterios:
- legible a 24–32 px;
- reconocible sin texto;
- funcionar en claro/oscuro;
- mantener relación con olivo/hoja/fruto sin caer en iconografía agrícola genérica;
- versión horizontal, sello e icono app.

## 6. Experiencia cinematográfica

La experiencia principal se implementará como **scroll-controlled image sequence**.

No es un carrusel.
No es un vídeo que se reproduce solo.
No es únicamente parallax CSS.

El scroll del usuario controla el fotograma.

Concepto:

`scroll progress → frame index → canvas/image render`

### Secuencia principal

#### Escena A — Entrada
Plano general de olivar. Agricultor dentro del paisaje.

Copy máximo:
> Tu olivar. Más claro.

#### Escena B — Observación
El agricultor camina, observa ramas y aceitunas.

Copy:
> Mira. Decide. Registra.

#### Escena C — Móvil
La mano va al bolsillo, aparece el teléfono y la cámara se acerca.

Copy:
> Todo empieza aquí.

#### Escena D — Producto
El teléfono ocupa la pantalla y entra Mágina Olivo.

Copy:
> Todo tu olivar.

#### Escena E — Funciones
La interfaz se transforma por scroll, una función cada vez:

1. Fincas.
2. Parcelas.
3. Catastro / mapa.
4. Campaña y labores.
5. Cosecha.
6. Gastos y documentos.
7. Tiempo y avisos.
8. Historial y comparativas.

Cada escena lleva una única frase breve.

#### Escena F — Regreso al campo
El móvil vuelve a contexto real. El agricultor continúa trabajando.

Copy:
> Menos papeles. Más control.

#### Escena G — Cierre
Producto + marca + CTA.

Copy objetivo:
> Todo tu olivar. En un solo lugar.

CTA:
> Conocer Mágina Olivo / Próximamente en Android.

## 7. Sistema de frames

### Desktop
Objetivo inicial por secuencia principal:
- 120–180 frames finales;
- lienzo visual equivalente a 1440–1920 px de ancho;
- AVIF/WebP según compatibilidad y coste;
- no cargar todos los frames al inicio.

### Mobile
Secuencia independiente:
- 70–110 frames;
- encuadre vertical;
- resolución y peso reducidos;
- misma narrativa, no simple recorte del desktop.

### Precarga
- poster inicial inmediato;
- precargar primeros 6–10 frames;
- ventana dinámica alrededor del frame actual;
- evitar bloquear el main thread;
- render con requestAnimationFrame;
- cache en memoria de frames usados.

### Fallback
- `prefers-reduced-motion`: escenas estáticas;
- dispositivo lento: secuencia reducida;
- error de asset: poster estable;
- contenido clave siempre accesible sin animación.

## 8. Pantallas del producto

Las pantallas mostradas deben derivar de la app real/canónica, no de mockups inventados.

Mostrar únicamente funciones existentes o comprometidas en el roadmap Android.

Orden promocional recomendado:
1. Inicio / resumen.
2. Fincas.
3. Parcela.
4. Catastro / mapa.
5. Campaña / actuaciones.
6. Cosecha.
7. Gastos / documentos.
8. Tiempo / avisos.
9. Histórico.

La web explica el valor mediante transformación de interfaz, no mediante listas de características extensas.

## 9. Arquitectura pública V2

Navegación principal simplificada:
- Inicio
- Producto
- Cómo funciona
- Beneficios
- Contacto

Opcionales posteriores:
- Guías
- Blog
- Ayuda

No incluir en la Home comercial V2:
- bloque promocional de Sierra Mágina;
- pueblos;
- turismo/territorio;
- directorio de cooperativas;
- noticias territoriales;
- secciones que desvíen del producto.

Esos contenidos, si vuelven, deben ser superficies separadas y nunca condicionar la narrativa comercial.

## 10. Regla de densidad

Objetivo:
- 70–80 % visual;
- 20–30 % texto/UI.

Por escena:
- máximo 1 titular;
- máximo 1 frase secundaria;
- máximo 1 CTA cuando sea necesario.

No usar tres tarjetas para explicar algo que puede demostrarse en la pantalla del móvil.

## 11. Movimiento

Debe sentirse:
- preciso;
- lento;
- elegante;
- físicamente creíble;
- controlado por el usuario.

Evitar:
- rebotes;
- zooms agresivos;
- scroll-jacking;
- elementos flotando sin relación;
- animaciones repetidas por decoración;
- velocidades distintas sin intención.

## 12. Rendimiento objetivo

Antes de producción:
- LCP < 2.5 s en conexión móvil razonable;
- CLS < 0.1;
- INP < 200 ms cuando sea alcanzable;
- navegación sin overflow horizontal;
- secuencia objetivo de 50–60 fps en móvil medio;
- memoria controlada mediante descarga/reutilización de frames;
- imágenes responsive;
- poster visible antes de que la secuencia esté preparada.

El frame sequence nunca debe convertir la Home en una descarga masiva.

## 13. Accesibilidad

Obligatorio:
- navegación por teclado;
- contraste AA;
- reduced motion funcional;
- textos fuera del canvas disponibles en DOM;
- canvas decorativo/visual no sustituye contenido;
- botones y enlaces HTML reales;
- no bloquear el scroll nativo.

## 14. Responsive

Desktop y móvil son composiciones específicas.

No se acepta:
- reducir desktop al 50 %;
- recortar al agricultor accidentalmente;
- texto fuera del área segura;
- teléfono ilegible;
- secuencias horizontales que ensanchen el documento.

Breakpoints mínimos de QA:
- 360×800;
- 390×844;
- 412×915;
- 768×1024;
- 1366×768;
- 1440×900;
- 1920×1080.

## 15. Qué conservamos de Web V1

Se conserva:
- Next.js;
- rutas;
- GitHub Pages preview;
- Docker;
- CI;
- Playwright;
- SEO base;
- seguridad;
- accesibilidad base;
- sistema de staging;
- estructura de repositorio.

Se sustituye progresivamente:
- dirección artística visible;
- hero;
- narrativa de Home;
- bloques territoriales;
- exceso de tarjetas;
- assets provisionales;
- animación basada solo en capas/parallax.

## 16. Regla de cambio

Esta V2 queda como fuente de verdad visual/comercial.

No volver a una Home territorial o cargada de tarjetas sin decisión explícita.

La app Android mantiene su roadmap independiente.

## 17. Definición de éxito

La V2 está conseguida cuando una persona puede recorrer la Home sin leer grandes párrafos y entender:

> tengo fincas y parcelas → trabajo en ellas → saco el móvil → Mágina Olivo organiza lo que hago → puedo revisar la campaña completa.

La web debe vender el producto mostrando cómo se usa.
