# Mágina Olivo — arquitectura pública V20

Estado: **estructura definida; visual pendiente**.

## 1. Objetivo

La parte pública debe ser útil aunque el visitante no tenga cuenta. Debe funcionar como guía viva de Sierra Mágina y como puerta de entrada natural a Mi Campo.

## 2. Superficies públicas principales

```text
Inicio
├── Tiempo / alertas
├── Campo hoy
├── Aceite y mercado
├── Noticias
├── Eventos
├── Almazaras / cooperativas
├── Cerca de ti
└── entrada a Mi Campo

Explorar
├── Pueblos
├── Almazaras
├── Cooperativas
├── Negocios y servicios
├── Noticias
├── Eventos
└── contenidos útiles

Tiempo
├── ahora / resumen
├── previsión
├── radar
├── avisos oficiales
└── contexto agrícola general

Aceite y mercado
├── precios
├── evolución
├── campaña
├── rendimientos de referencia
└── fuentes
```

## 3. Inicio público

Inicio no debe ser un portal con veinte tarjetas sin jerarquía. Debe responder a:

- ¿qué está pasando hoy en Mágina?;
- ¿hay algo importante para el campo?;
- ¿cómo está el tiempo?;
- ¿qué ocurre con el aceite?;
- ¿qué puedo consultar cerca de mí?;
- ¿quiero entrar a gestionar mis fincas?

Orden conceptual:

```text
Contexto del pueblo
→ tiempo/alerta relevante
→ campo hoy
→ Mi Campo si existe sesión
→ aceite/mercado
→ noticias/eventos
→ cerca de ti
```

## 4. Contexto territorial

El usuario puede tener un pueblo/localidad de referencia. El contexto territorial no debe confundirse con la ubicación exacta de sus fincas.

Cada contenido público puede estar relacionado con:
- municipio oficial;
- localidad/pueblo visible;
- comarca/Sierra Mágina;
- provincia;
- ámbito general.

Esto permite conservar casos como Garcíez dentro del municipio oficial Bedmar y Garcíez sin borrar la identidad local.

## 5. Pueblos

Cada pueblo/localidad puede tener ficha propia:

- imagen principal;
- descripción;
- tiempo;
- noticias;
- eventos;
- almazaras/cooperativas;
- negocios/servicios;
- puntos de interés;
- contenidos agrícolas relacionados.

Las imágenes deben guardar fuente, autor y derechos de uso.

## 6. Almazaras y cooperativas

Entidad pública reutilizable con:
- nombre;
- tipo;
- municipio/localidad;
- ubicación;
- contacto;
- web/redes;
- DOP;
- ecológico;
- venta directa;
- oleoturismo;
- estado de campaña;
- horario de recepción;
- marcas/productos;
- galería;
- fuentes y nivel de verificación.

La ficha pública puede enlazar en el futuro con entregas privadas de cosecha sin exponer datos privados.

## 7. Cerca de ti

Directorio local propio de Mágina.

Categorías iniciales:
- almazaras;
- talleres agrícolas;
- agrotiendas;
- maquinaria/servicios;
- gasolineras;
- restaurantes;
- alojamientos;
- comercios;
- otros servicios.

Niveles comerciales:
- ficha gratuita;
- verificada;
- destacada;
- patrocinada;
- oferta/promoción.

Regla: pagar mejora visibilidad, no altera hechos ni valoraciones editoriales.

## 8. Noticias

Modelo editorial:
- título;
- resumen;
- cuerpo/enlace;
- fuente;
- fecha publicación;
- fecha del hecho si es distinta;
- ámbito territorial;
- categorías;
- estado de revisión;
- imagen con derechos.

Distinguir contenido propio, agregación/enlace y contenido patrocinado.

## 9. Eventos

Cada evento:
- título;
- fechas/horarios;
- ubicación;
- municipio/localidad;
- organizador;
- categoría;
- descripción;
- enlace/contacto;
- imagen;
- fuente/verificación;
- cancelación/cambio.

## 10. Aceite y mercado

Los precios deben conservar siempre:
- fuente;
- tipo/categoría de aceite;
- unidad;
- fecha/hora del dato;
- mercado/ámbito;
- si es dato oficial, orientativo o calculado.

No mezclar precios de fuentes/mercados distintos como si fueran equivalentes.

## 11. Campo hoy

Capa pública editorial/agronómica general, distinta del contexto privado de una finca.

Puede incluir:
- alertas territoriales;
- fenología;
- plagas/enfermedades desde fuentes oficiales;
- recomendaciones generales con fuente;
- avisos de campaña;
- recordatorios estacionales.

Nunca usar el texto público para simular un diagnóstico específico de una finca.

## 12. Público vs privado

### Público
- territorio;
- tiempo general;
- radar público;
- avisos oficiales;
- noticias;
- eventos;
- precios;
- almazaras/cooperativas;
- negocios;
- contenido editorial.

### Privado
- fincas;
- geometrías propias;
- cosechas;
- costes;
- trabajos;
- personas/clientes;
- documentos;
- reglas personales;
- alertas específicas por finca;
- notificaciones privadas.

No filtrar nunca información privada dentro de endpoints/cachés públicos.

## 13. Monetización

La monetización prevista puede apoyarse en:
- negocios destacados;
- patrocinio claramente marcado;
- ofertas locales;
- campañas temporales;
- perfiles comerciales ampliados.

La parte pública gratuita debe seguir siendo útil para crear audiencia y recurrencia.

## 14. Relación con Mi Olivo

Mi Olivo puede usar acciones públicas para misiones ligeras:
- consultar tiempo;
- leer noticia;
- visitar ficha de pueblo;
- descubrir una almazara;
- participar en eventos/retos.

La gamificación no debe falsear rankings comerciales ni condicionar alertas de seguridad/agronómicas.

## 15. Reglas cerradas

1. La parte pública funciona sin cuenta.
2. El contexto territorial público es distinto de la ubicación privada de las fincas.
3. Cada dato cambiante conserva fuente y fecha.
4. Contenido patrocinado se identifica de forma explícita.
5. Pagar no altera hechos, seguridad ni recomendaciones agronómicas.
6. Noticias/eventos/negocios tienen flujo de verificación/moderación.
7. La información privada no entra en cachés/endpoints públicos.
8. Inicio prioriza relevancia, no cantidad de módulos.
9. Explorar agrupa territorio y directorio sin recargar Inicio.
10. La arquitectura pública puede crecer por contenido sin multiplicar la navegación principal.
