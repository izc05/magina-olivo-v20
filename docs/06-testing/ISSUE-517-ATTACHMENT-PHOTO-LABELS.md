# #517 — nombres visibles de fotos adjuntas

Slice de relevo autorizado a Codex, 2026-10-08. Rama `fix/517-attachment-photo-labels`, base main `f4b09148`. No modifica las PR #679/#683/#684/#689/#680.

## Comportamiento

Solo fotos cuyo nombre coincide completamente con `captura-UUID.jpg` reciben etiqueta derivada. Pesada: «Foto del vale»; Gasto: «Foto de factura/ticket»; otros propietarios: «Foto». Fecha de creación en la zona de presentación del dispositivo, coherente con el detalle actual de adjuntos. Varias capturas del mismo propietario y día se numeran por createdAt/ID; el ordinal describe la lista visible y puede cambiar tras eliminar una foto.

Lista, descripción accesible de miniatura y diálogo de eliminación usan la misma etiqueta. Abrir/eliminar conservan ID/URI; displayName almacenado no cambia. Archivos elegidos, nombres parecidos y PDF mantienen su nombre. No hay migración, cambios de datos, outbox o Sync. Históricos con nombres internos se presentan mediante la misma regla.

## Evidencia

- Red: con presentación original, 4 tests nuevos ejecutados, 3 fallos esperados y 1 correcto.
- Green: 14 tests unitarios de adjuntos, 0 fallos/errores (4 presentación, 5 tipo, 5 ViewModel).
- APK dev compilado con Gradle 9.4.1/JBR local; no se cambió configuración del proyecto.
- Prueba instrumentada nueva verifica etiqueta en lista y diálogo, ausencia del nombre interno y eliminación mediante ID original.

Resultados finales de lint/emulador se registran en la PR. Sin evidencia física del propietario; no se publica APK candidata ni se fusiona automáticamente. Sin alcance CUE, función productiva o fases posteriores.
