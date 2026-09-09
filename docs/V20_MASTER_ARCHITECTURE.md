# Mágina Olivo V20 — Arquitectura maestra

**Estado:** diseño previo a implementación  
**Principio:** *sencillo por fuera, estructurado por dentro*.

## 1. Visión

Mágina Olivo V20 es una plataforma móvil-first/PWA que une dos experiencias claramente separadas:

1. **Mágina pública:** guía territorial, tiempo, radar, noticias, eventos, aceite, cooperativas/almazaras, empresas, gastronomía, rutas, comunidad y promoción.
2. **Mi Campo:** gestión privada de fincas, campañas, cosecha, rendimientos, riego, tratamientos, abonado, poda, trabajos, jornales, maquinaria, costes, documentos, calendario, avisos, mapas e históricos.

La interfaz debe poder utilizarla una persona mayor o poco familiarizada con aplicaciones: iconos + texto, objetivos táctiles grandes, formularios cortos, valores por defecto útiles y progresive disclosure.

---

## 2. Navegación principal

### Móvil
- Inicio
- Mi Campo
- Explorar
- Más

### Inicio
La portada no es un panel agrícola complejo. Debe mostrar solo contexto y lo importante:

- hero premium del municipio detectado;
- ubicación actual, con fallback manual;
- tiempo;
- avisos prioritarios;
- resumen mínimo de Mi Campo;
- noticias y eventos;
- aceite/mercado;
- empresa patrocinada;
- contenido territorial/comunidad.

**Regla:** Inicio sigue al usuario; las alertas agrícolas siguen a las fincas.

---

## 3. Mi Campo

La unidad visible principal es la **finca tal como la conoce el agricultor**: `Las Cenillas`, `El Cerrillo`, etc.

Una finca puede corresponder a:
- una parcela catastral;
- varias parcelas catastrales;
- parte de una parcela;
- varios recintos SIGPAC.

Catastro y SIGPAC son referencias territoriales, no la navegación principal.

### Ficha viva de finca
Debe responder rápidamente:
- qué ha producido;
- rendimiento;
- próximo riego;
- últimos trabajos;
- costes;
- avisos;
- documentación.

Bloques conceptuales:
- Cosecha
- Riegos
- Tratamientos
- Abonado
- Poda
- Trabajos
- Jornales
- Maquinaria
- Costes
- Documentos
- Terreno
- Calendario
- Historia

Cada bloque debe tener:
1. resumen útil;
2. histórico;
3. alta rápida;
4. más detalles opcionales;
5. corrección trazable;
6. documentos;
7. cálculos derivados;
8. exportación cuando aplique.

---

## 4. Formularios prioritarios

### Finca
Nombre, municipio, nº olivos, secano/regadío, foto, superficie/variedades opcionales y localización mediante GPS/mapa/Catastro/SIGPAC/dibujo.

### Entrega/peso
Finca, fecha, kg, cooperativa/almazara, nº albarán y foto. El rendimiento se registra después en un registro separado.

### Rendimiento
Entrega asociada, porcentaje, fecha y documento opcional. El promedio de campaña será ponderado por kilos.

### Riego
Debe soportar €/olivo, €/hora, €/m³, €/riego, cuota anual, precio cerrado u otra modalidad. Puede programar próximo riego y recordatorios.

### Tratamiento
Producto, fecha, motivo, cantidad/dosis, aplicador/equipo opcionales, coste y documentos. OCR puede proponer campos; el usuario confirma los críticos.

### Abonado
Producto, fecha, cantidad, composición/dosis opcionales, coste, proveedor y documento.

### Poda
Actuación agrupadora que puede contener poda, recogida de leña, trituración, retirada, jornales y maquinaria.

### Jornal
Persona/nº personas, fecha, unidad (jornal/horas/medio jornal/€/olivo/€/ha/cerrado), cantidad y tarifa opcional.

### Maquinaria
Máquina, fecha, horas/días, operador/proveedor, tarifa y coste.

### Observación
Foto/nota/categoría, GPS y clima opcionales; puede derivar en tarea o tratamiento.

### Parte profesional
Cliente, finca, fecha, trabajo, mano de obra, maquinaria, materiales, importe, fotos y PDF.

---

## 5. Registros por dominio: no `Activity` universal

No se utilizará una tabla `Activity` como almacén genérico para todo.

Fuentes de verdad separadas:
- Delivery / DeliveryResult
- IrrigationRecord
- TreatmentRecord
- FertilizationRecord
- PruningRecord
- Observation
- WorkReport
- LaborEntry
- MachineEntry

Los registros alimentan proyecciones comunes:

```text
Domain record
   ↓
Domain event
   ├── FarmTimeline
   ├── FarmSummary
   ├── CostLedger
   ├── SearchProjection
   └── NotificationIntent
```

**Regla de extensibilidad:** añadir un nuevo módulo no debe exigir modificar cinco módulos anteriores.

---

## 6. Workspaces y permisos

No asociar todo únicamente a `user_id`.

```text
User
 └── Membership
       └── Workspace
             ├── Farms
             ├── Members
             └── Professional work
```

Un usuario puede pertenecer a:
- espacio familiar/explotación;
- espacio profesional;
- organización futura.

`FarmAccessGrant` permitirá compartir una finca de forma limitada con trabajadores externos, por ejemplo:
- ver ubicación;
- crear trabajo;
- ver su propio trabajo;
- sin ver cosecha/costes/documentos privados.

---

## 7. Trabajo profesional

Una persona puede ser propietaria y prestar servicios a terceros.

Flujo:
`profesional -> cliente -> finca -> parte de trabajo`.

El mismo `WorkReport` puede verse desde el profesional y desde el propietario según permisos, evitando duplicar registros.

Estados económicos futuros:
- borrador;
- pendiente;
- aceptado;
- facturable;
- facturado;
- cobrado;
- anulado.

La factura fiscal completa será un módulo separado; primero se construye parte de trabajo.

---

## 8. Documentos y OCR

`Document` será transversal y se vinculará a registros mediante `AttachmentLink`.

Tipos prioritarios:
- albarán;
- resultado de rendimiento;
- factura/ticket fitosanitario;
- factura de abono;
- recibo de riego;
- parte de trabajo;
- factura de maquinaria;
- documentos territoriales.

Pipeline:

```text
foto/PDF
  ↓
almacenamiento original
  ↓
job OCR
  ↓
texto
  ↓
clasificación/extracción
  ↓
campos + confianza
  ↓
confirmación del usuario
```

Candidatos open-source:
- Tesseract.js — OCR local/rápido;
- PaddleOCR — servidor/document parsing;
- docTR — benchmark alternativo.

No confirmar silenciosamente kilos, rendimiento, importe, fecha o dosis procedentes solo de OCR/IA.

---

## 9. GPS, clima, radar y GIS

### Ubicación actual
Sirve para:
- municipio/hero;
- clima del lugar;
- Cerca de ti;
- radar centrado en el usuario.

### Ubicación de finca
Sirve para:
- previsión de finca;
- radar/alerta de lluvia;
- snapshots meteorológicos;
- trabajo de terceros.

### Mapa único con capas
- base/OSM;
- PNOA;
- GPS;
- fincas V20;
- Catastro;
- SIGPAC;
- radar;
- observaciones/trabajos.

Tecnología candidata: MapLibre GL JS.

El código legacy de AEMET y Catastro será auditado y portado selectivamente, no copiado a ciegas.

---

## 10. Alertas de lluvia

Distinguir:
- previsión;
- precipitación observada por radar;
- acumulado;
- nowcast futuro.

Ejemplos:
- `Lluvia prevista mañana en Las Cenillas`;
- `Precipitación detectada a 8 km`.

No presentar radar observado como predicción.

Reglas por finca: radio, severidad, antelación, canales y silencio horario.

---

## 11. Plataforma pública

### Contenido
- Municipios
- Noticias
- Eventos
- Aceite/mercado
- Cooperativas/Almazaras
- Gastronomía
- Recetas
- Rutas
- Empresas/Cerca de ti
- Comunidad
- Mi Olivo

### Cooperativas/Almazaras
Ficha pública con contacto, mapa, historia, campaña, horarios, marcas, productos, DOP, venta directa, oleoturismo, galería, noticias, eventos y contenido relacionado.

### Rutas
Gastronómicas, AOVE/oleoturismo, pueblos, patrimonio, naturaleza o combinadas, con paradas ordenadas y mapa.

### Publicidad
Una empresa puede existir gratis y pagar por promoción. `business` y `advertising_campaign` serán entidades separadas. Todo contenido pagado se identificará como `Patrocinado`.

---

## 12. Comunidad

Feed visual sencillo, no foro clásico.

Tipos: fotos, preguntas, plagas, campo, maquinaria, cosecha, pueblos, gastronomía.

Interacciones:
- likes/reacciones;
- comentarios;
- guardar;
- reportar;
- compartir.

La geometría exacta de fincas nunca se publica por defecto.

Moderación y auditoría serán obligatorias antes de abrir comunidad real.

---

## 13. Perfil y cuenta

Cuenta mínima al registrarse; completar progresivamente.

Perfil público opcional:
- avatar;
- portada;
- nombre visible;
- bio;
- municipio aproximado opcional;
- rol público;
- publicaciones/logros opcionales.

Privado:
- email;
- fincas;
- documentos;
- clientes;
- costes;
- ubicaciones exactas;
- preferencias;
- sesiones.

Perfil profesional separado y activable para servicios a terceros.

Autenticación candidata: Google + email mediante OIDC/Better Auth o equivalente. Autenticación y autorización se diseñan separadamente.

---

## 14. PWA e instalación

V20 será PWA primero y podrá envolverse con Capacitor en el futuro si necesitamos capacidades nativas profundas.

No pedir todos los permisos al abrir.

Permisos contextuales:
- ubicación cuando se pulsa `Usar mi ubicación`;
- cámara al fotografiar albarán/factura/avatar;
- notificaciones cuando se activa una alerta;
- almacenamiento persistente cuando se activa modo offline.

Fallbacks siempre disponibles.

Mejoras progresivas:
- shortcuts: Registrar, Mi Campo, Radar, Hoy;
- Share Target para enviar fotos/PDF a V20.

---

## 15. Stack propuesto

### Lenguaje
TypeScript `strict` como lenguaje principal. Python solo para OCR/ML cuando aporte valor.

### Monorepo
pnpm workspaces.

```text
apps/
  web/       Next.js + React, pública/PWA/Mi Campo
  admin/     Payload/Next.js candidate
  api/       Fastify + TypeScript
  worker/    jobs TypeScript
services/
  ocr/       Python futuro
packages/
  contracts/
  domain/
  ui/
  auth/
  maps/
  config/
  observability/
  testing/
database/
  migrations/
  seeds/
docs/
infra/
```

### Datos
PostgreSQL + PostGIS.

### Acceso DB
Kysely candidato para el core, manteniendo SQL explícito y tipado.

### Storage
S3-compatible (R2/MinIO/equivalente), separado entre documentos privados y media pública.

### Jobs
pg-boss como candidato inicial para evitar Redis prematuramente.

### UI premium
Tailwind/CSS variables + componentes propios sobre shadcn/ui/Base UI. `packages/ui` será el design system Mágina.

---

## 16. IA y automatización

La IA no manejará el Admin haciendo clics si existe una API.

Usará herramientas explícitas con permisos:
- buscar fuentes;
- crear borrador;
- clasificar;
- programar;
- publicar contenido autorizado;
- actualizar/archivar según policy.

Niveles:
- A0 lectura;
- A1 borradores;
- A2 publicación editorial controlada;
- A3 acciones privilegiadas con aprobación.

La IA nunca cambia silenciosamente kilos/rendimientos confirmados, roles, documentos privados o datos agrícolas críticos.

---

## 17. Admin

Desktop-first, responsive para urgencias.

Dominios:
- Dashboard
- Contenido
- Municipios
- Noticias
- Eventos
- Cooperativas/Almazaras
- Gastronomía
- Rutas
- Empresas
- Publicidad
- Comunidad/Moderación
- Usuarios/Roles
- Mi Olivo
- Alertas/Fuentes
- Configuración
- Auditoría

Roles: super_admin, admin, editor, moderator, commercial, support, analyst/viewer.

Un editor de noticias no obtiene acceso automático a Mi Campo.

---

## 18. Resiliencia

La aplicación debe seguir siendo útil si fallan GPS, AEMET, radar, OCR, IA o CMS.

No existe un loading global esperando todos los proveedores.

El shell abre primero y cada bloque carga/degrada por separado.

La caída de OCR no impide entrada manual; la caída de IA no impide Admin manual; la caída del tiempo no rompe Mi Campo.

---

## 19. Requisitos de plataforma antes de producción

- autorización server-side;
- auditoría;
- rate limits;
- jobs idempotentes;
- migraciones versionadas;
- backups + restore drills;
- observabilidad;
- entornos local/dev/preview/staging/prod;
- CI/CD;
- feature flags;
- privacidad/RGPD/retención;
- pruebas de dominio/API/E2E/offline/accesibilidad/migración/restore.

---

## 20. Regla de extensibilidad

Un módulo nuevo debe declarar como mínimo:
- id/nombre;
- permisos;
- rutas/API;
- comandos;
- queries;
- migraciones;
- eventos;
- adaptador timeline;
- coste si aplica;
- documentos si aplica;
- búsqueda/exportación;
- feature flag;
- tests.

Añadir `SoilAnalysis` no debe requerir modificar Cosecha, Riego, Tratamientos, Costes y Timeline. Se conecta por contratos/eventos/adaptadores.

---

## 21. Gate previo al código estructural

Antes del núcleo definitivo debemos cerrar:

### Producto
- MVP exacto;
- fuera de alcance explícito.

### Dominio
- bounded contexts;
- workspaces/membership;
- modelo finca/referencias;
- campañas;
- unidades/dinero;
- documentos;
- correcciones/versionado.

### Arquitectura
- monolito modular;
- API contract;
- domain events/projections;
- jobs;
- notificaciones;
- feature flags;
- audit log.

### Plataforma
- DB/storage/auth;
- migraciones;
- entornos/CI;
- backup/restore;
- observabilidad;
- privacidad.

### UX
- design system;
- Inicio premium;
- navegación;
- lista de fincas;
- ficha viva;
- formularios prioritarios;
- estados offline/error.

### Reutilización
- inventario legacy;
- benchmark OCR;
- decisión CMS;
- decisión Catastro;
- licencias/THIRD_PARTY_NOTICES.

---

## 22. Regla de trabajo V20

1. Documentar el caso real.
2. Buscar soluciones existentes/open-source.
3. Revisar licencia y mantenimiento.
4. Diseñar contrato/dominio/UX.
5. Prototipar visualmente.
6. Implementar módulo aislado.
7. Probar.
8. Integrar mediante contratos, no acoplamiento directo.

**No se empieza una función importante únicamente porque quepa en una pantalla.**