# Mi Campo — arquitectura de navegación V20

Estado: **estructura definida; diseño visual pendiente**.

## 1. Objetivo

Mi Campo debe sentirse como una libreta agrícola moderna, no como un ERP.

La arquitectura interna puede ser compleja, pero el usuario debe navegar por muy pocas superficies.

## 2. Navegación principal de Mi Campo

```text
Mi Campo
├── Resumen
├── Mis fincas
├── Mapa
└── Registrar
```

No habrá pestañas globales separadas para riegos, tratamientos, podas, jornales, maquinaria, gastos, etc. Esas entidades se consultan mediante filtros, actividad, finca y búsqueda.

## 3. Pantalla Mi Campo / Resumen

Debe responder rápidamente:

- qué fincas tengo;
- qué necesita atención;
- qué tengo previsto hoy/próximos días;
- cómo va la campaña;
- qué alertas afectan a mis fincas;
- qué registrar ahora.

Bloques funcionales:

```text
MI CAMPO
├── resumen campaña
├── fincas
├── pendientes / próximos trabajos
├── alertas por finca
├── actividad reciente
└── acceso Registrar
```

Los accesos rápidos actuales son provisionales. No deben convertirse automáticamente en navegación permanente.

## 4. Mis fincas

Listado único de Fincas visibles para el usuario.

Cada tarjeta puede resumir:
- nombre;
- localidad;
- superficie / olivos;
- estado;
- campaña;
- alertas importantes.

Filtros futuros:
- propias;
- arrendadas;
- gestionadas;
- de terceros;
- activas/archivadas.

## 5. Ficha de Finca

La ficha final mantiene solo cinco superficies principales:

```text
FINCA
├── Resumen
├── Actividad
├── Cosecha
├── Datos
└── Documentos

+ Registrar
```

### Resumen
- identidad mínima;
- campaña;
- producción;
- costes esenciales;
- tareas pendientes;
- clima/radar relevante;
- alertas;
- últimas actividades.

### Actividad
Una única línea temporal filtrable que puede contener:
- riego;
- tratamiento;
- abonado;
- poda;
- trituración;
- desbroce;
- laboreo;
- cosecha/trabajo;
- jornales;
- maquinaria;
- gasto;
- observación;
- documentos relevantes.

### Cosecha
- campaña;
- entregas;
- reparto;
- tickets;
- resultados;
- rendimiento;
- histórico.

### Datos
- nombre/localidad;
- olivos;
- variedades;
- régimen hídrico;
- superficie;
- propiedad/gestión;
- mapa;
- parcelas/recintos;
- Catastro;
- SIGPAC;
- geometría propia.

### Documentos
- todos los documentos de finca;
- filtros por tipo;
- relaciones con trabajos, cosecha y economía;
- estado OCR/revisión cuando proceda.

## 6. Registrar como acción transversal

`+ Registrar` debe estar disponible desde:
- Mi Campo;
- una Finca;
- mapa;
- contexto de una tarea;
- futuro acceso rápido móvil.

La pantalla no obliga a elegir primero una tabla técnica.

```text
+ Registrar
→ Tipo
→ Dónde
→ Cuándo
→ Datos específicos
→ Recursos opcionales
→ Economía opcional
→ Documento opcional
→ Seguimiento opcional
→ Guardar
```

Cuando se abre desde una Finca, `Dónde` viene preseleccionado.
Cuando se abre desde una parcela concreta, se preseleccionan Finca + parcela.

## 7. Mapa

El mapa es una vista transversal, no un silo.

Debe permitir:
- ver todas las fincas;
- seleccionar una finca;
- ver parcelas/recintos;
- abrir ficha;
- registrar un trabajo en la selección;
- añadir/vincular geometría;
- activar capas técnicas Catastro/SIGPAC;
- superponer contexto futuro como radar o alertas.

Catastro y SIGPAC se muestran como capas, no como destinos principales de navegación.

## 8. Calendario

Calendario no necesita ser una pestaña primaria permanente.

Puede aparecer desde:
- Mi Campo → pendientes/próximos;
- filtros de Actividad;
- una vista secundaria global;
- recordatorios/notificaciones.

El dato canónico sigue siendo `ScheduledEvent` / seguimiento asociado a actividades o trabajos.

## 9. Personas y recursos

Trabajadores, clientes, cuadrillas, maquinaria y materiales no requieren botones en la navegación principal.

Se gestionan desde:
- Registrar;
- Perfil/gestión profesional cuando sea necesario;
- ficha contextual de trabajo;
- una futura zona secundaria «Personas y recursos» accesible desde Más/Configuración.

## 10. Trabajos para terceros

No crear una aplicación paralela.

La misma entidad Trabajo permite distinguir:
- trabajo propio;
- trabajo para tercero.

La navegación puede ofrecer más adelante un filtro:

```text
Mi Campo
→ Actividad / Trabajos
→ Propios | Para clientes
```

Para usuarios profesionales se podrá elevar este acceso según uso, sin cambiar el dominio.

## 11. Estrategia móvil

La navegación inferior global no debe llenarse con módulos internos de Mi Campo.

Dentro de Mi Campo se recomienda navegación contextual ligera:
- selector/segmentos de las cinco superficies en Finca;
- botón flotante o visible `+ Registrar`;
- volver a Mi Campo siempre accesible.

## 12. Búsqueda futura

La arquitectura debe permitir búsqueda transversal por:
- finca;
- cliente/persona;
- trabajo;
- fecha;
- campaña;
- ticket/albarán;
- documento;
- producto/material.

La búsqueda reduce la necesidad de crear menús profundos.

## 13. Rutas conceptuales

La estructura de URLs final puede converger hacia:

```text
/mi-campo
/mi-campo/fincas
/mi-campo/fincas/:farmId
/mi-campo/fincas/:farmId/actividad
/mi-campo/fincas/:farmId/cosecha
/mi-campo/fincas/:farmId/datos
/mi-campo/fincas/:farmId/documentos
/mi-campo/mapa
/mi-campo/registrar
```

Rutas específicas de riego/tratamiento/etc. pueden mantenerse técnicamente para formularios o deep links, pero no son la arquitectura visible primaria.

## 14. Estados esenciales

Cada superficie debe contemplar desde estructura:
- vacío / primer uso;
- cargando;
- sin conexión;
- datos locales pendientes de sincronizar;
- error recuperable;
- contenido archivado;
- permisos insuficientes;
- datos externos no disponibles.

## 15. Regla de simplicidad

Antes de añadir una pantalla o pestaña nueva, responder:

> ¿Puede esta información vivir correctamente en Resumen, Actividad, Cosecha, Datos, Documentos, Mapa o Registrar?

Si la respuesta es sí, no crear una nueva superficie primaria.
