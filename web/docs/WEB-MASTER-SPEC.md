# Mágina Olivo Web — Master Spec 0.1

Estado: **ACTIVE DRAFT**  
Rama inicial: `feat/web-magína-olivo`

## 1. Propósito

Crear la superficie web pública de Mágina Olivo de principio a fin sin alterar la baseline Android RC1.2.

La web debe:

1. presentar la marca y el producto;
2. explicar de forma clara el valor de la aplicación;
3. convertirse progresivamente en una superficie pública útil para el olivar;
4. atraer tráfico orgánico y facilitar el descubrimiento del proyecto;
5. servir como puente hacia la app Android;
6. mantener una identidad visual idéntica al diseño canónico de Mágina Olivo.

## 2. Relación con Android

La app Android es el producto privado de gestión agrícola.

La web es una superficie separada. En sus primeras fases **no replica** el núcleo privado de gestión ni altera sus Gates, arquitectura offline-first, dominio o decisiones RC1.2.

## 3. Arquitectura de información objetivo

- Inicio
- Mi Campo
- Tiempo y alertas
- Aceite y mercado
- Cooperativas y almazaras
- Noticias y territorio
- Guías / consejos
- Sobre Mágina Olivo
- Acceso / descarga de la app

La activación de cada módulo será progresiva.

## 4. Dirección visual canónica

Principios:

- crema cálido como fondo;
- verde olivo como color funcional y de marca;
- verde profundo para contraste;
- dorado apagado como acento;
- fotografía de olivar y Sierra Mágina;
- titulares editoriales serif;
- UI operativa sans-serif;
- tarjetas suaves, bordes discretos y mucho aire;
- animación contenida, nunca ornamental en exceso;
- experiencia mobile-first.

Tokens iniciales:

- Cream: `#F3EFE3`
- Paper: `#FAF8F2`
- Ink: `#153022`
- Olive: `#53673E`
- Olive Deep: `#324634`
- Olive Light: `#A8B18B`
- Gold: `#B99A60`

## 5. Stack

- Next.js 16.3.3
- React 19.3
- TypeScript 6
- Tailwind CSS 4.3
- App Router

La elección prioriza SEO, rendimiento, accesibilidad, rendering híbrido y escalabilidad.

## 6. Roadmap

### Fase Web 0 — Foundation
- workspace aislado;
- sistema visual;
- portada inicial;
- documentación fuente de verdad.

### Fase Web 1 — Estructura pública
- [x] header/footer compartidos;
- [x] rutas principales;
- [x] componentes base;
- [x] navegación móvil;
- [x] páginas Producto, Beneficios, Nuestra tierra y Contacto;
- [x] sitemap y robots;
- [x] Home con narrativa cinematográfica;
- [x] secuencia sticky campo → persona → móvil;
- [ ] validación visual final en navegador y Android;
- [ ] sustitución de assets provisionales por fotogramas definitivos.

### Fase Web 2 — Contenido real
- [~] textos editoriales iniciales;
- [ ] fotografía y fotogramas definitivos;
- [ ] noticias;
- [ ] cooperativas/territorio;
- [ ] guías y contenidos.

### Fase Web 3 — Datos
- meteorología;
- radar/alertas;
- mercado del aceite;
- fuentes externas;
- capa de caché y manejo de errores.

### Fase Web 4 — Calidad
- SEO técnico;
- schema.org;
- sitemap;
- Open Graph;
- accesibilidad;
- Core Web Vitals;
- analítica y consentimiento.

### Fase Web 5 — Producción
- dominio;
- Cloudflare;
- CI/CD;
- observabilidad;
- backups/configuración;
- revisión final responsive.

## 7. Regla de cambio

No se modifica el diseño canónico ni la relación web/app sin decisión explícita. Las nuevas funciones deben añadirse por módulos y sin acoplar la web al núcleo Android.

## 8. Definición de “terminado”

La web se considerará terminada para v1 cuando:

- sea responsive y accesible;
- tenga todas las rutas públicas previstas para v1;
- use contenido real;
- cargue de forma rápida;
- tenga SEO completo;
- disponga de errores/estados vacíos cuidados;
- esté desplegada en producción;
- exista documentación de mantenimiento;
- no rompa ni condicione la baseline Android.
