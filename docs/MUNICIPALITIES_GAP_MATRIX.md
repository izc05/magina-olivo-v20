# Mapa de huecos municipal

Ruta: `/admin/ayuntamientos/huecos?municipio=<slug>`.

## Objetivo

Convertir la cobertura municipal en una lista de trabajo accionable: mostrar qué señal concreta falta en cada uno de los 16 municipios y enlazar a la herramienta que puede resolverla.

## Señales

La matriz usa nueve comprobaciones binarias derivadas de datos reales publicados y vigentes:

### Editorial
1. perfil principal publicado;
2. resumen editorial;
3. imagen hero real;
4. fuente + fecha de verificación.

### Descubrimiento
5. patrimonio publicado;
6. naturaleza publicada;
7. turismo publicado.

### Actualidad
8. noticia o evento local publicado.

### Economía
9. almazara/cooperativa (`mill`) o directorio/servicio (`directory`) publicado.

## Reglas

- no existe una nota subjetiva;
- no se ordena por mejor/peor municipio;
- los municipios permanecen en orden alfabético;
- el recuento de huecos es solo el número de señales binarias ausentes;
- se respetan `status`, `starts_at` y `ends_at`;
- el vínculo municipal usa exclusivamente `content_json.municipality_id`.

## Acciones

Cada hueco enlaza al editor municipal ya existente que puede resolverlo: Contenido, Portada, Patrimonio, Actualidad o Cobertura. La matriz no escribe datos por sí misma.

## Arquitectura

Reutiliza `adminApi.territoryCatalog()` y `adminApi.content()`. No añade tablas, endpoints, migraciones ni dependencias con los módulos activos de Rutas o Empresas.
