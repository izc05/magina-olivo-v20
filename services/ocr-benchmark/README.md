# Mágina Olivo V20 · OCR benchmark

Este directorio existe para **elegir el OCR con datos**, no por intuición.

No forma parte del runtime de producción. Un proveedor solo pasa a `apps/worker` después de superar este benchmark y una ADR.

## Qué medimos

### 1. Texto bruto
- CER (Character Error Rate)
- presencia de tokens críticos

### 2. Campos de negocio
Para albaranes/entregas:
- cooperativa/ almazara
- fecha
- nº de albarán
- kilos

Para resultados:
- fecha
- nº de documento/albarán si existe
- rendimiento %
- humedad/acidez cuando aparezcan

Métrica principal: exactitud normalizada por campo.

### 3. Operación
Cada runner debe informar:
- duración total ms
- proveedor/versión
- dispositivo (cpu/gpu cuando aplique)
- error si no pudo procesar

Más adelante añadiremos memoria pico/CPU si compensa.

## Candidatos iniciales

Baseline de investigación 2026-09:
- Tesseract 5.5.x
- PaddleOCR 3.7.x / PP-OCRv6
- docTR 1.1.x

Las versiones definitivas se fijarán al construir cada runner.

## Corpus

El benchmark comienza con dos fuentes:

1. **Sintético propio**
   - generado por scripts de este repositorio;
   - valores conocidos;
   - distintas inclinaciones, ruido, contraste y formatos;
   - no contiene datos personales.

2. **Real anonimizado** (fase posterior)
   - albaranes/tickets reales autorizados;
   - eliminar/ocultar nombre, DNI, matrícula u otros datos no necesarios;
   - truth manual revisado;
   - nunca subir documentos privados al repositorio público.

## Contrato de resultados

Cada proveedor produce un JSON por caso:

```json
{
  "case_id": "delivery-clean-001",
  "provider": "example",
  "provider_version": "1.2.3",
  "duration_ms": 420,
  "raw_text": "...",
  "fields": {
    "cooperative_or_mill": "SCA SAN ISIDRO",
    "date": "2026-12-12",
    "ticket_number": "008421",
    "total_kg": 1842
  },
  "error": null
}
```

El runner común no sabe cómo funciona el OCR. Solo compara ese resultado con `corpus/manifest.json`.

## Reglas de aceptación iniciales

No son definitivas, pero sirven como gate:

- kilos: >= 99 % exactos;
- rendimiento: >= 99 % dentro de tolerancia 0,05 puntos;
- fecha: >= 98 % exacta;
- nº albarán: >= 97 % exacto;
- casos completamente correctos: >= 95 %;
- ningún proveedor se acepta si inventa un valor crítico cuando no es legible.

Si ningún motor cumple, V20 seguirá mostrando OCR como **propuesta a confirmar**, que de todas formas es la política de producto.

## Ejecutar scoring

```bash
python3 services/ocr-benchmark/benchmark.py \
  --manifest services/ocr-benchmark/corpus/manifest.json \
  --results services/ocr-benchmark/results/provider.jsonl
```

Los resultados y documentos reales no deben versionarse accidentalmente.
