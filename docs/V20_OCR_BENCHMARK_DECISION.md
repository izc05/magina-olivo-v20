# V20 · Benchmark OCR y decisión provisional

Estado: decisión provisional basada en corpus sintético. **No es todavía una aprobación de producción.**

Fecha de benchmark: 2026-09-09.

## Qué se comparó

Los tres motores procesaron exactamente el mismo corpus sintético generado por Mágina Olivo:

- 4 albaranes de entrega: limpio, inclinado, bajo contraste y ruido;
- 3 resultados de molturación: limpio, inclinado y bajo contraste;
- mismas imágenes PNG;
- mismo extractor de campos;
- mismo scoring;
- ejecución CPU en GitHub Actions.

Campos críticos:

- cooperativa / almazara;
- fecha;
- nº de albarán;
- kilos;
- rendimiento;
- humedad.

Antes de comparar se reconstruyó el orden de lectura por geometría para PaddleOCR y docTR. Esto evita penalizar a un motor porque devuelva primero la columna derecha de un formulario.

## Resultados

| Motor | Documentos completos | Campos críticos | CER medio | Tiempo medio CPU |
|---|---:|---:|---:|---:|
| Tesseract CLI 5.3.4 | 7/7 · 100 % | 100 % | 4,8014 % | 315 ms |
| PaddleOCR 3.7 + PP-OCRv6 | 7/7 · 100 % | 100 % | 0,0752 % | 3.635 ms |
| docTR 1.1 | 6/7 · 85,7 % | fallo puntual en rendimiento | 2,5553 % | 2.225 ms |

### Tesseract

Fortalezas observadas:
- todos los campos de negocio correctos;
- mucha menor latencia CPU;
- despliegue sencillo en un worker pequeño;
- buen candidato para Raspberry/mini PC/VPS CPU.

Debilidad observada:
- el texto bruto tiene más pequeñas diferencias que PaddleOCR;
- no debe confundirse CER con exactitud de campos de negocio.

### PaddleOCR PP-OCRv6

Fortalezas observadas:
- todos los campos correctos;
- CER prácticamente nulo en este corpus;
- confianza de reconocimiento muy alta.

Coste observado:
- aproximadamente 11,5 veces más lento que Tesseract en este benchmark CPU;
- runtime/modelos sensiblemente más pesados.

Importante: la primera ejecución parecía mala porque `rec_texts` no estaba en orden semántico. Al reconstruir filas con `rec_boxes`, PaddleOCR pasó a 7/7. El benchmark conserva esa corrección para no repetir una comparación injusta.

### docTR

Tras reconstruir también orden espacial:
- albaranes 4/4 completos;
- fechas, tickets, kilos y humedad correctos;
- un resultado limpio leyó `21,4 I %`, lo que impidió extraer el rendimiento;
- 6/7 documentos completos.

No se selecciona como motor inicial con la evidencia disponible.

## Decisión D3.2

### Motor primario provisional: Tesseract

V20 utilizará **Tesseract como primera pasada OCR CPU** si el corpus real confirma resultados próximos a los actuales.

Razón:

```text
objetivo V20 = extraer bien datos agrícolas
              + funcionar en hardware modesto
              + responder rápido
```

En el corpus actual Tesseract cumple el 100 % de los campos críticos con mucha menor latencia.

### PaddleOCR como segunda pasada candidata

No se elimina PaddleOCR.

La arquitectura conservará la posibilidad de:

```text
Tesseract
   |
   +--> extracción completa y válida -> revisión humana
   |
   +--> falta campo / baja calidad -> PaddleOCR
                                      |
                                      v
                                  revisión humana
```

No activar este fallback automáticamente hasta medirlo con documentos reales, porque el coste CPU/RAM puede no compensar si Tesseract ya resuelve la mayoría.

### docTR

Se conserva en benchmark como referencia, pero no entra en el runtime V20 inicial.

## Regla de seguridad funcional

OCR **no escribe silenciosamente** kilos, rendimiento u otros datos en Mi Campo.

Flujo inicial:

```text
foto/PDF
   ↓
OCR
   ↓
extracción estructurada
   ↓
REVISAR
   ├── dato detectado
   ├── confianza
   └── original visible
   ↓
CONFIRMAR
   ↓
registro de cosecha / rendimiento
```

La corrección humana se conserva como evidencia y futura señal para mejorar extracción.

## Puerta obligatoria antes de producción

Repetir el benchmark con un corpus real anonimizado.

Mínimo recomendado para la primera decisión seria:
- 50 documentos;
- al menos 3 cooperativas/almázaras o formatos distintos;
- fotos de móvil reales;
- PDF/escáner;
- inclinación;
- sombras;
- desenfoque moderado;
- recibos doblados o arrugados;
- varios años/campañas;
- números con ceros iniciales;
- separadores `.` y `,`;
- albaranes y resultados de rendimiento.

Criterios iniciales de aceptación:
- kilos: >= 99 % exactitud;
- nº albarán: >= 99 %;
- fecha: >= 99 %;
- rendimiento: >= 99 %;
- documento con todos los campos críticos: >= 98 %;
- 0 escrituras automáticas sin revisión en fase inicial.

Los umbrales podrán endurecerse cuando exista suficiente corpus.

## Evidencia reproducible

Código:

```text
services/ocr-benchmark/
├── generate_synthetic.py
├── extract_fields.py
├── benchmark.py
└── providers/
    ├── tesseract_cli.py
    ├── paddleocr_v6.py
    └── doctr_1_1.py
```

Workflows:
- `V20 OCR benchmark`
- `V20 OCR benchmark PaddleOCR`
- `V20 OCR benchmark docTR`

La selección de proveedor permanece detrás de `OcrProcessorPort`, por lo que cambiar de Tesseract a PaddleOCR o ejecutar ambos no exige modificar API, documentos, `pg-boss` ni el modelo de Mi Campo.
