# QA final municipal 16/16

Esta fase valida la estructura completa del bloque Ayuntamientos antes del handoff de integración. No afirma que todos los municipios tengan todos los contenidos cargados: esa situación se controla con Cobertura y Huecos.

## Municipios incluidos

1. Albanchez de Mágina — `albanchez-de-magina`
2. Bedmar y Garcíez — `bedmar-y-garciez`
3. Bélmez de la Moraleda — `belmez-de-la-moraleda`
4. Cabra del Santo Cristo — `cabra-del-santo-cristo`
5. Cambil — `cambil`
6. Campillo de Arenas — `campillo-de-arenas`
7. Cárcheles — `carcheles`
8. La Guardia de Jaén — `la-guardia-de-jaen`
9. Huelma — `huelma`
10. Jimena — `jimena`
11. Jódar — `jodar`
12. Larva — `larva`
13. Mancha Real — `mancha-real`
14. Noalejo — `noalejo`
15. Pegalajar — `pegalajar`
16. Torres — `torres`

## Gates estructurales

El contrato final comprueba:
- exactamente 16 slugs únicos en el catálogo SEO compartido;
- `generateStaticParams()` y `generateMetadata()` sobre ese catálogo;
- ficha pública con secciones territoriales esenciales y estados vacíos;
- fuente pública canónica y enlace explícito por `municipality_id`;
- workspace Admin con Ficha, Contenido, Portada, Preview, Patrimonio, Actualidad, Cobertura, Huecos e Historial;
- contexto `?municipio=` en las herramientas municipales;
- Preview, Historial y Huecos siguen siendo de solo lectura;
- SEO/readiness, Preview, Historial y Huecos siguen protegidos por sus contratos específicos;
- ausencia de aleatoriedad en metadata y diagnósticos;
- documentación del roadmap y del handoff preparada.

## Gates de ejecución

El mismo HEAD debe superar:
- V20 municipalities directory;
- V20 environment contract;
- V20 foundation check;
- V20 platform admin check;
- V20 full candidate check;
- V20 staging readiness;
- V20 beta browser E2E.

## Qué no valida esta fase

No convierte huecos de contenido en datos ficticios y no exige que los 16 municipios tengan nueve señales completas. El estado editorial real queda visible en Cobertura/Huecos y debe resolverse con fuentes reales.
