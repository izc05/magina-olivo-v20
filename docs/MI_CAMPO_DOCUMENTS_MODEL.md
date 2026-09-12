# Mi Campo — modelo documental V20

Estado: **estructura definida**. Se apoya en la infraestructura documental/OCR ya existente.

## 1. Principio

Un documento es evidencia asociada a la actividad agrícola. No sustituye al dato estructurado.

Ejemplo:

```text
Ticket de cooperativa (PDF/foto)
        ↓ OCR
propuesta de extracción
        ↓ revisión
Entrega estructurada
├── fecha
├── kg
├── ticket
└── finca(s)
```

La imagen original se conserva aunque después se corrija el dato extraído.

## 2. Documento lógico y versiones

El sistema debe separar:

```text
DOCUMENTO LÓGICO
“Factura abono septiembre”
        │
        ├── versión 1 · foto original
        ├── versión 2 · PDF escaneado mejor
        └── versión 3 · archivo corregido
```

Reglas:

1. El documento lógico conserva identidad y relaciones.
2. Cada archivo subido es una versión inmutable.
3. Sustituir un archivo no destruye la versión anterior.
4. SHA-256 y tamaño permiten validar integridad y detectar duplicados.

## 3. Tipos iniciales

- ticket/albarán de entrega;
- resultado/rendimiento;
- factura;
- tratamiento/fitosanitario;
- abonado;
- riego;
- parte de trabajo;
- referencia territorial;
- fotografía;
- análisis;
- justificante de pago/cobro;
- presupuesto/factura futura;
- otro.

Los tipos pueden ampliarse sin crear nuevas pantallas.

## 4. Relaciones

Un documento puede relacionarse con uno o varios contextos:

```text
Documento
├── Finca
├── Parcela/recinto
├── Trabajo
├── Entrega de cosecha
├── Resultado de cosecha
├── Evento económico
├── Pago/cobro
├── Campaña
├── Persona/empresa
└── referencia Catastro/SIGPAC
```

No duplicar el archivo para cada relación. Se guarda una sola vez y se crean vínculos.

## 5. Fotografías

Una fotografía puede ser:

- evidencia de trabajo;
- estado de la finca;
- incidencia;
- cultivo/plaga;
- maquinaria;
- documento fotografiado;
- referencia visual libre.

Las fotos de actividad y los documentos escaneados pueden compartir almacenamiento, pero deben conservar distinta semántica.

## 6. OCR

Flujo canónico:

```text
Archivo
  ↓
OCR run
  ↓
texto bruto
  ↓
extracción estructurada propuesta
  ↓
revisión del usuario
  ├── aceptar
  ├── corregir
  └── descartar
  ↓
registro de dominio
```

Nunca se debe considerar válido un dato sensible de cosecha, economía o fitosanitarios únicamente porque lo haya detectado OCR.

## 7. Historial OCR

Cada ejecución debe conservar:

- versión del documento;
- proveedor/modelo;
- fecha;
- texto bruto;
- campos detectados;
- nivel de confianza si está disponible;
- correcciones del usuario;
- resultado final confirmado.

Un nuevo OCR no sobrescribe el anterior.

## 8. Ejemplos

### Ticket de aceituna

```text
Documento: Ticket 004581
├── archivo original
├── OCR detecta
│   ├── 1.425 kg
│   ├── 18/12/2026
│   └── Cooperativa X
├── usuario confirma/corrige
└── crea/relaciona Entrega
```

### Factura de abono

```text
Documento: Factura proveedor
├── total 900 €
├── proveedor
├── fecha
└── usuario reparte
    ├── Las Cenillas 400 €
    ├── El Cerrillo 300 €
    └── La Loma 200 €
```

El documento puede quedar vinculado al evento económico general y a sus repartos sin replicarse.

## 9. Vista dentro de Finca

`Documentos` será una de las cinco superficies principales de la ficha.

Filtros previstos:

- Todos
- Cosecha
- Trabajos
- Facturas/gastos
- Tratamientos
- Fotos
- Territorio

No hacer carpetas técnicas obligatorias. El usuario debe poder encontrar un documento por fecha, tipo, campaña o actividad relacionada.

## 10. Estado del documento

Estados funcionales propuestos:

- subiendo;
- disponible;
- procesando OCR;
- requiere revisión;
- confirmado;
- error de procesamiento;
- archivado.

El estado OCR no modifica la disponibilidad del archivo original.

## 11. Privacidad y acceso

Los documentos de Mi Campo son privados por defecto y heredan el contexto del workspace/finca.

Una futura colaboración con trabajador o asesor debe compartir relaciones concretas, no convertir automáticamente toda la biblioteca documental en pública.

## 12. Reglas fijadas

1. Documento lógico y archivo/versiones son conceptos distintos.
2. Las versiones son inmutables.
3. El archivo original siempre se conserva.
4. OCR propone; el usuario o una regla explícita confirma.
5. Un documento puede tener múltiples relaciones sin duplicar archivos.
6. Los datos estructurados siguen viviendo en su entidad de dominio.
7. Las correcciones OCR quedan auditables.
8. Fotos y documentos pueden compartir almacenamiento, pero no significado.
9. Documentos privados nunca pasan a contenido público por reutilización automática.
10. La UI documental debe ser sencilla aunque internamente existan versiones, OCR y vínculos complejos.
