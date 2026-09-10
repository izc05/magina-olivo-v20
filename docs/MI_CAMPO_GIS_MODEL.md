# Mi Campo — modelo GIS / Catastro / SIGPAC V20

Estado: **estructura definida**.

## 1. Principio principal

La geometría de una Finca es un dato propio de Mágina.

Catastro y SIGPAC son fuentes de referencia. Pueden ayudar a construir o verificar la geometría, pero no definen por sí solos qué entiende el agricultor por su finca.

```text
FINCA
├── geometría canónica propia de Mágina
├── referencias Catastro
├── referencias SIGPAC
└── subdivisiones manuales opcionales
```

## 2. Geometría canónica

Cada Finca puede tener una geometría canónica que será la usada para:

- mapa principal;
- superficie operativa;
- meteorología espacial;
- radar;
- alertas;
- selección de trabajos;
- cálculos geográficos futuros.

La geometría canónica puede proceder inicialmente de:

- una parcela de Catastro;
- un recinto SIGPAC;
- unión de varias geometrías;
- dibujo manual;
- importación GeoJSON/KML/SHP futura;
- corrección manual de una geometría oficial.

Una vez aceptada, se almacena como geometría propia de la Finca. La fuente original continúa vinculada como referencia.

## 3. Catastro

Una Finca puede tener cero, una o muchas referencias catastrales.

Cada vínculo debe conservar:

- referencia catastral;
- geometría recuperada;
- superficie oficial si está disponible;
- fuente/end-point;
- fecha de consulta;
- estado de vínculo;
- si fue utilizada para construir la geometría canónica.

No se asume que una referencia catastral equivale a una Finca.

## 4. SIGPAC

Una Finca puede vincular cero, uno o muchos recintos SIGPAC.

Cada recinto debe conservar identificadores oficiales y geometría.

El recinto SIGPAC puede representar mejor la realidad agrícola que Catastro en algunos casos, pero sigue siendo una referencia técnica.

## 5. Relación Finca ↔ Parcela/recinto

La capa `ParcelRecord` sirve para modelar subdivisiones territoriales dentro de la Finca.

Puede representar:

- referencia catastral;
- recinto SIGPAC;
- área manual interna;
- geometría operativa propia.

Una misma Finca puede tener varias de estas piezas simultáneamente.

## 6. Cuando Catastro y SIGPAC no coinciden

No se fuerza una única verdad administrativa.

El sistema debe poder mostrar:

```text
Geometría operativa Mágina       2,84 ha
Catastro                         2,91 ha
SIGPAC                           2,76 ha
```

Y marcar diferencias sin bloquear el uso de la finca.

La geometría que manda para funciones internas es la canónica de Mágina, elegida o confirmada por el usuario.

## 7. Formas de añadir/localizar una Finca

Flujos admitidos:

```text
AÑADIR FINCA
├── crear solo con nombre
├── pulsar sobre mapa
├── buscar por referencia catastral
├── buscar por polígono/parcela/recinto
├── buscar visualmente en ortofoto
└── dibujar contorno
```

Localizar es recomendable, no obligatorio.

## 8. Flujo mapa → parcela

```text
usuario pulsa mapa
      ↓
lat/lon
      ↓
servicio GIS Mágina
      ├── consulta Catastro
      └── consulta SIGPAC si procede
      ↓
candidatos
      ↓
usuario confirma
      ↓
se vincula referencia
      ↓
opcionalmente se usa como geometría canónica
```

La selección debe ser confirmable antes de guardar.

## 9. Unión de varias parcelas

Si una finca real comprende varias parcelas:

```text
Las Cenillas
├── Catastro A
├── Catastro B
└── SIGPAC recinto C
```

Mágina puede mantener cada referencia individual y además generar una geometría operativa unificada.

No se pierden los límites originales.

## 10. Subdivisiones internas

El agricultor puede querer zonas que no existen administrativamente:

- parte alta;
- parte baja;
- olivas nuevas;
- zona de riego 1;
- zona de secano;
- parcela norte.

Estas zonas se modelan como subdivisiones manuales y pueden usarse para asignar trabajos sin alterar Catastro/SIGPAC.

## 11. Superficie

Se distinguen al menos:

- superficie geométrica canónica;
- superficie Catastro;
- superficie SIGPAC;
- superficie declarada/manual.

La UI puede mostrar una superficie principal y dejar las demás en `Datos`, pero el modelo debe conservar sus fuentes.

## 12. Versionado de geometría

Una corrección de límites no debería borrar silenciosamente la geometría anterior.

Se recomienda conservar:

- geometría anterior;
- nueva geometría;
- fecha;
- origen del cambio;
- usuario;
- motivo opcional.

Esto será especialmente importante si existen trabajos, alertas o cálculos históricos asociados a una geometría previa.

## 13. Privacidad

La geometría de las fincas privadas forma parte de Mi Campo y no se publica por defecto.

Los mapas públicos de territorio usan capas públicas separadas. Nunca se debe reutilizar una geometría privada como contenido público sin consentimiento explícito.

## 14. Reglas fijadas

1. Finca y parcela administrativa no son sinónimos.
2. Mágina mantiene una geometría canónica propia por Finca.
3. Catastro y SIGPAC son referencias técnicas vinculables múltiples.
4. El usuario puede elegir una referencia como origen de la geometría canónica.
5. Las discrepancias entre fuentes se conservan y se explican; no bloquean.
6. Una Finca puede unir varias referencias.
7. Las subdivisiones manuales no alteran fuentes oficiales.
8. Localizar la finca es opcional al crearla.
9. Las geometrías privadas no pasan al mapa público automáticamente.
10. Las futuras correcciones de geometría deben ser auditables.
