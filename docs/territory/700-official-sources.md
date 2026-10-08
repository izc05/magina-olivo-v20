# Fuentes oficiales del catálogo territorial — #700

Consulta inicial: 2026-10-08. Verificar fecha, licencia y esquema de **cada descarga** antes de publicar un snapshot.

## Municipios (primera prioridad)
- Ficha oficial: https://www.juntadeandalucia.es/datosabiertos/portal/dataset/datos-abiertos-municipios
- Recurso CSV: https://www.juntadeandalucia.es/datosabiertos/portal/dataset/datos-abiertos-municipios/resource/b9d572c7-b41d-4c2e-86cc-0e3a1d7482de
- Documentación API: https://www.juntadeandalucia.es/datosabiertos/portal/aplicaciones/buscador-apis/detalle/2059.html
- OpenAPI anunciada oficialmente: https://datos.juntadeandalucia.es/api/v0/municipalities/openapi.json
- Licencia publicada: CC BY 4.0; periodicidad indicada: diaria.
- **Pendiente:** descargar CSV real, inspeccionar cabeceras y códigos (no asumir que código de registro equivale al código INE), comprobar recuentos oficiales y ejecutar importador.

## Enlaces de entidades locales
- Registro Andaluz de Entidades Locales (RAEL): https://www.juntadeandalucia.es/datosabiertos/portal/dataset/registro-andaluz-de-entidades-locales
- Puede aportar webs y contactos, pero debe cruzarse por identificador comprobado; no hacer joins por nombre libre ni atribuir enlaces no verificados.

## Núcleos de población
- IECA: https://www.juntadeandalucia.es/datosabiertos/portal/dataset/nomenclator-de-entidades-y-nucleos-de-poblacion-de-andalucia
- Fuente distinta a municipios. Nunca inventar código municipal para pedanías/núcleos.

## Ejecución del importador
```bash
python3 tools/territory/build_municipalities.py \
  --input /ruta/descarga-oficial.csv \
  --output /ruta/jaen.json \
  --code-column 'CABECERA_REAL_CODIGO' \
  --name-column 'CABECERA_REAL_NOMBRE' \
  --province 23 \
  --expected-count NUMERO_OFICIAL_VERIFICADO \
  --source-url 'URL_REAL_DESCARGA' \
  --source-date 'AAAA-MM-DD'
python3 -m unittest discover -s tools/territory -p 'test_*.py' -v
```
Las cabeceras son marcadores deliberados: reemplazarlas solo después de inspeccionar el CSV. No publicar un JSON inventado ni declarar que Jaén está cargada sin verificar la fuente.
