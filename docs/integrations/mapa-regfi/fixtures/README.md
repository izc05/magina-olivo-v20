# Fixtures sintéticas MAPA REGFI

Todos los nombres, números SYN-*, fechas, dosis y usos son datos de prueba. NO constituyen productos registrados ni autorizaciones reales. El código de cultivo tiene forma compatible con el export observado; los códigos sintéticos no son crosswalks IUWS/EPPO.

Las claves, estructura y tipos reproducen el JSON oficial comprobado el 2026-10-07. Los ficheros son el contenido interior `Contenido`, no el transporte serializado; el test reproduce el envoltorio doblemente serializado sin red.

- `catalog-v1.synthetic.json`: dos productos vigentes, cobre, alias, uso, concentración con unidad, campos vacíos y plazo NP.
- `catalog-v2.synthetic.json`: SYN-00001 pasa a cancelado; SYN-00002 desaparece. No se infiere cancelación del desaparecido.
- `duplicates.synthetic.json`: dos variantes del mismo registro con distinto límite de venta, más una repetición idéntica. Resultado exigido: un producto, dos variantes, REVIEW_REQUIRED/CONFLICT.

No se guarda el dataset vivo ni se incluyen secretos. Ejecutar desde raíz: `node --test docs/integrations/mapa-regfi/prep.test.mjs`.
