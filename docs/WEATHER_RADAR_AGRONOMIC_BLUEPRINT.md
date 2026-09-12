# Tiempo, radar y contexto agronómico — plano V20

Estado: **estructura definida**. La interfaz visual seguirá siendo provisional hasta cerrar el plano funcional completo.

## 1. Regla principal

Mágina no debe mostrar una sola bolsa llamada «alertas». Debe distinguir claramente:

```text
Dato meteorológico
→ interpretación de contexto
→ recomendación agronómica
→ regla personal
→ notificación
```

Cada capa conserva su origen, fecha y grado de incertidumbre.

## 2. Cinco familias de información

### A. Previsión
Predicción futura de AEMET u otro proveedor validado.

Ejemplos:
- probabilidad de precipitación;
- temperatura mínima/máxima;
- viento previsto;
- horizonte temporal.

No se presenta como observación real.

### B. Observación
Medición ya ocurrida procedente de estación, sensor o fuente oficial.

No debe mezclarse con una predicción.

### C. Radar
Indica ecos de precipitación observados y su relación espacial con la finca.

Datos útiles:
- cobertura;
- precipitación detectada;
- eco más cercano;
- dirección;
- reflectividad;
- momento de observación;
- calidad del análisis.

Radar no implica por sí solo «lloverá en X minutos».

### D. Aviso oficial
Avisos meteorológicos emitidos por una autoridad competente. Deben conservar nivel, zona, vigencia y fuente oficial.

### E. Contexto agronómico
Capa derivada por Mágina que combina evidencia para responder preguntas de trabajo de campo.

Ejemplos:
- condiciones favorables/desfavorables para tratar;
- riesgo de viento;
- contexto para cosecha;
- posible necesidad de revisar riego;
- riesgo de calor o helada;
- accesibilidad probable tras lluvia.

Nunca se formula como garantía y debe incluir evidencia y confianza.

## 3. Contexto por finca

La unidad operativa es la Finca.

```text
FINCA
├── geometría / punto representativo
├── municipio oficial para previsión
├── radar espacial
├── observaciones disponibles
├── avisos oficiales aplicables
└── trabajos/calendario
        ↓
   Contexto agronómico
```

Home puede resumir varias fincas, pero el cálculo debe conservar la finca de origen.

## 4. Evidencia trazable

Toda recomendación agronómica debe poder explicar:

- qué fuentes utilizó;
- cuándo se observaron o elaboraron;
- si alguna estaba caducada/stale;
- qué regla/version produjo la recomendación.

No guardar solamente el texto «No trates hoy». Guardar también la evidencia y la versión de la regla.

## 5. Aptitud para tareas

Se usarán estados sencillos:

- `good`: condiciones compatibles;
- `caution`: revisar antes de actuar;
- `avoid`: condiciones desaconsejadas por las reglas configuradas;
- `unknown`: evidencia insuficiente.

Y riesgo:

- none;
- low;
- medium;
- high;
- unknown.

La UI podrá traducirlos a lenguaje cotidiano sin ocultar incertidumbre.

## 6. Tratamientos

El futuro indicador «Buen momento para tratar» no debe depender de una única variable.

Como mínimo podrá considerar, según disponibilidad y tipo de tratamiento:

- viento;
- lluvia observada/prevista;
- temperatura;
- humedad;
- ventana temporal posterior al tratamiento;
- instrucciones o restricciones del producto cuando existan datos fiables.

La regla será versionada y configurable. No se debe inventar una recomendación fitosanitaria específica si faltan datos del producto o base técnica suficiente.

## 7. Riego

Mágina puede aportar **contexto de riego**, no ordenar automáticamente regar.

Puede combinar:
- lluvia reciente;
- lluvia prevista;
- histórico de riegos;
- tipo de finca/regadío;
- futuros sensores;
- calendario del usuario.

Sin sensor de suelo o modelo agronómico validado no afirmar «la finca necesita X litros».

## 8. Cosecha

Contexto posible:
- precipitación prevista;
- lluvia observada reciente;
- viento;
- temperaturas extremas;
- avisos oficiales.

Debe ayudar a planificar sin reemplazar la decisión del agricultor.

## 9. Radar y nowcast

La arquitectura actual de radar se conserva:

```text
AEMET radar
→ snapshot validado
→ GeoTIFF / grid
→ análisis espacial por finca
→ FarmRadarObservation
→ regla personal
→ NotificationIntent
→ canal push
```

Reglas fijadas:

1. no convertir dBZ a mm/h de forma automática sin metodología validada;
2. no mostrar ETA de lluvia hasta disponer de un método de movimiento temporal validado;
3. una sola imagen radar permite proximidad, no velocidad fiable;
4. conservar `observed_at`, no solo `fetched_at`;
5. indicar cobertura parcial/no disponible;
6. no emitir avisos si la evidencia está fuera de vigencia o calidad mínima.

## 10. Reglas personales

Una regla de usuario puede ser específica por finca.

Ejemplo radar:
- habilitada;
- radio máximo;
- dBZ mínimo;
- cooldown.

Más adelante se podrán añadir reglas como:
- avisar si mañana hay alta probabilidad de lluvia antes de un tratamiento programado;
- avisar por viento previsto antes de una tarea sensible;
- recordar revisar un riego si cambia sustancialmente la previsión.

La regla personal no altera el dato meteorológico; decide si ese dato merece generar una intención de notificación.

## 11. NotificationIntent

La notificación se mantiene separada del origen.

```text
Evidence / Advisory
→ regla
→ NotificationIntent
→ deduplicación
→ cooldown
→ preferencias del usuario
→ suscripción activa
→ push
```

Esto permite en el futuro añadir email, WhatsApp u otros canales sin reescribir radar o meteorología.

## 12. Home, Tiempo y Finca

Los tres lugares consumen la misma fuente normalizada.

### Home
Resumen territorial + asuntos realmente relevantes para las fincas del usuario.

### Tiempo
Vista meteorológica completa del municipio/territorio, previsión, radar y avisos.

### Finca
Solo el contexto que afecte a esa finca y sus trabajos.

No mantener valores meteorológicos hardcoded diferentes entre pantallas.

## 13. Contrato de dominio

Se añade `packages/contracts/src/agronomy.ts` con:

- `WeatherEvidence`;
- `FarmWeatherContext`;
- `AgronomicAdvisory`;
- niveles de riesgo;
- aptitud;
- confianza;
- clases de recomendación.

Esta capa se sitúa entre proveedores meteorológicos/radar y la interfaz/notificaciones.

## 14. Futuro de máxima calidad

Cuando exista evidencia suficiente, la arquitectura permitirá incorporar sin romper el producto:

- estaciones meteorológicas cercanas;
- sensores propios;
- humedad de suelo;
- evapotranspiración;
- Sentinel/teledetección;
- modelos de plagas/enfermedades validados;
- RAIF;
- reglas por cultivo/producto;
- nowcasting temporal mediante secuencias radar;
- explicación de por qué se genera cada recomendación.

Cada fuente será un adaptador; la UI no dependerá directamente de un proveedor.

## 15. Reglas cerradas

1. Previsión, observación, radar, aviso oficial y recomendación agronómica son entidades semánticamente distintas.
2. Toda recomendación debe ser explicable mediante evidencia.
3. Radar actual no promete ETA.
4. No inferir lluvia acumulada directamente desde reflectividad sin método validado.
5. Las recomendaciones incluyen incertidumbre/confianza.
6. Las reglas personales generan notificaciones; no modifican hechos meteorológicos.
7. Home, Tiempo y Finca comparten la misma capa normalizada.
8. La Finca es la unidad privada a la que se asocia el contexto agronómico.
9. La ausencia de datos produce `unknown`, no una falsa condición favorable.
10. Las futuras fuentes se integrarán por adaptadores sin cambiar el modelo visible.
