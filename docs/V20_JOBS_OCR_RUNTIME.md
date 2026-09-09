# V20 · Jobs y runtime OCR

Estado: candidato técnico en validación. No fusionar aún.

## Objetivo

Las tareas pesadas no se ejecutan dentro de una petición HTTP. La API crea registros y encola trabajo; uno o más workers lo consumen en segundo plano.

```text
MÓVIL / WEB
    |
    v
FASTIFY API
    |
    +--> PostgreSQL/PostGIS
    |
    +--> pg-boss: magina-ocr-v1
              |
              v
          OCR WORKER
              |
              +--> Storage S3-compatible
              +--> OCR provider
              +--> ocr_runs
              +--> extraction_runs futuro
```

## Decisión D3.1 · PostgreSQL + pg-boss

V20 usa `pg-boss` inicialmente para jobs.

Motivos:
- ya existe PostgreSQL como dependencia central;
- no introduce Redis en la primera arquitectura;
- soporta reintentos, backoff, expiración y dead-letter queue;
- permite separar API y workers sin crear un microservicio complejo.

Colas v1:
- `magina-ocr-v1`
- `magina-ocr-dlq-v1`

Política OCR inicial:
- 3 reintentos;
- backoff;
- delay inicial 30 s;
- delay máximo 10 min;
- expiración de ejecución 30 min;
- heartbeat 60 s;
- dead-letter después de agotar reintentos.

## Contrato versionado

El payload vive en `packages/contracts`, no dentro de API ni worker.

Campos v1:
- versión del contrato;
- OCR run id;
- document version id;
- storage key;
- MIME type;
- SHA-256 esperado;
- proveedor preferido.

Un cambio incompatible requiere una nueva versión del contrato/cola.

## Idempotencia

`ocr_runs` tiene `workspace_id + client_operation_id` único.

Consecuencia:
- doble toque móvil;
- retry HTTP;
- sincronización offline repetida;
- dos peticiones concurrentes;

no deben crear dos OCR runs ni encolar dos trabajos.

El worker también es idempotente: si un `ocr_run` ya está `succeeded`, reejecutar el mismo payload no vuelve a procesar el documento.

## Validaciones antes de procesar

El worker rechaza un job si no coincide con la base:
- `document_version_id`;
- `storage_key`;
- SHA-256;
- estado de subida.

No se procesa un archivo con integridad marcada como fallida.

## Estados

```text
queued
  |
  v
processing
  |\
  | +--> failed -> retry -> ... -> DLQ
  |
  +----> succeeded
```

El error queda en `ocr_runs.error_code/error_message` para auditoría.

## Procesador OCR

El worker depende de `OcrProcessorPort`.

No se ha seleccionado todavía un OCR de producción. Se realizará benchmark con documentos representativos antes de aceptar un adaptador.

Candidatos:
- Tesseract/Tesseract.js según caso;
- PaddleOCR;
- docTR.

El `DeterministicTestOcrProcessor` existe exclusivamente para CI. El worker se niega a arrancar en producción sin un proveedor real.

## Storage

Documentos usan `StoragePort` y un adaptador S3-compatible.

### Producción
Cloudflare R2 es candidato principal, pero el dominio no depende de R2.

### Desarrollo local
**No fijar todavía un servidor S3 local como dependencia del proyecto.** El servidor open-source histórico de MinIO quedó archivado en 2026 y las alternativas actuales deben probarse antes de incorporarlas al stack. RustFS (Apache-2.0) es candidato de evaluación, no una decisión aceptada.

Hasta cerrar esta decisión:
- CI usa `FakeStorage` para probar contrato/integridad sin servicio externo;
- desarrollo puede apuntar a cualquier endpoint S3-compatible de pruebas;
- `S3_FORCE_PATH_STYLE` permite compatibilidad con proveedores locales que lo requieran;
- cambiar proveedor no modifica Documents, API ni worker.

La API no recibe el binario pesado: reserva una URL PUT prefirmada; el cliente sube al storage y luego confirma la subida.

Antes de OCR se comprueba:
- existencia;
- tamaño si está disponible;
- checksum SHA-256 si está disponible.

## Escalado

Primera etapa:
- 1 API;
- 1 PostgreSQL/PostGIS;
- 1 worker OCR.

Si aumenta carga:
- añadir workers OCR adicionales consumiendo la misma cola;
- API no cambia;
- contratos no cambian;
- datos de Mi Campo no cambian.

## Pendiente antes de producción

- benchmark OCR real;
- adaptador OCR elegido;
- descarga segura desde StoragePort para worker;
- límites de concurrencia CPU/RAM;
- métricas de duración/error/confianza;
- política definitiva de DLQ/reprocesado desde Admin;
- decidir/probar storage local S3-compatible;
- validación antivirus/archivo si se considera necesaria;
- secrets reales fuera del repositorio.
