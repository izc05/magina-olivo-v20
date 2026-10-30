# ADR-011: Arquitectura de Notificaciones Oficiales de Turnos de Riego

## Estado
Propuesta técnica aprobada para evolución (B.5 / CR-004).

## Contexto y Motivación
En el olivar de regadío tradicional y moderno de Sierra Mágina (Jaén), los agricultores pertenecen a diversas comunidades de regantes (ej. *Comunidad de Regantes San Isidro*, *Comunidad de Aguas Subterráneas El Peñón*, *Sindicato Central del Guadalquivir*). Las convocatorias de turnos, averías en compuertas, cortes de caudal y riegos de socorro son emitidos periódicamente por las juntas de gobierno de estas entidades.

Hasta la fecha, en Mágina Olivo 1.0 (B.1 a B.5), el sistema de riego gestiona:
1. **Plan personal del agricultor:** turnos habituales y franjas horarias configuradas por el propio agricultor en cada parcela.
2. **Riegos reales ejecutados:** registros formales e inmutables en el Cuaderno de campo (`ActivityType.IRRIGATION`).

Para fases futuras, es necesario prever cómo las cooperativas o comunidades de regantes podrán publicar y notificar turnos oficiales sin romper los principios arquitectónicos del producto:
- **Regla 2 y 4:** No acoplar el núcleo ni asumir la existencia de APIs oficiales estandarizadas en todas las comunidades.
- **Regla 6:** Toda integración externa debe quedar aislada tras un adapter propio (`IrrigationNoticeAdapter`).
- **Regla 7:** Cálculos de volúmenes, costes y fechas deterministas.
- **Regla 11:** Separación estricta entre datos públicos/colectivos de comunidades y datos privados de explotaciones.
- **Offline-First:** No depender de servicios remotos de mensajería *push* (FCM / APNs) en la capa base de la aplicación.

---

## Principios de Diseño

```
+-----------------------------------------------------------+
|          Canales Externos de la Comunidad / Colectivo     |
|   (Feed Atom/JSON, Webhook Cooperativa, Boletín Digital)  |
+-----------------------------------------------------------+
                              |
                              v
+-----------------------------------------------------------+
|             IrrigationNoticeAdapter (SPI)                 |
|  - Normalización de formato a CanonicalNoticeMessage      |
|  - Verificación de integridad / hash de procedencia       |
+-----------------------------------------------------------+
                              |
                              v
+-----------------------------------------------------------+
|               CommunityNoticeRepository                   |
|  - Almacén local de avisos oficiales (Room / Cache)       |
|  - Filtro por comunidad_id y sector_id                    |
+-----------------------------------------------------------+
                              |
                              v
+-----------------------------------------------------------+
|             NoticeReconciliationEngine                    |
|  - Cruza avisos oficiales con turnos del agricultor       |
|  - Genera recomendaciones sin mutar datos privados        |
|  - Alerta visual en calendario: conflicto o confirmación  |
+-----------------------------------------------------------+
```

### 1. Desacoplamiento de Tres Capas de Verdad
1. **Aviso Oficial de Comunidad (`OfficialNotice`):** Documento emitido por la entidad gestora con vigencia temporal. Es inmutable y compartido.
2. **Plan Personal de Riego (`FarmerWeeklyPlan`):** Configuración privada del agricultor (horarios nocturnos, hidrantes, dosis calculadas).
3. **Riego Efectivo (`Cuaderno Activity`):** Hecho consumado registrado por el agricultor con volumen y duración real.

Bajo ninguna circunstancia la llegada de un aviso oficial sobrescribirá automáticamente los datos privados del agricultor ni creará actividades ejecutadas en el cuaderno sin confirmación manual explícita.

---

## Mecanismos de Ingesta (Sin Push Remoto en V1)

Para evitar dependencias con Firebase Cloud Messaging (FCM) o servidores de notificación propietarios, se establecen tres vías de ingesta desacopladas:

### Opción A — Polling Ligero de Feeds Estándar (Atom / JSON Feed)
- Las comunidades o cooperativas publican un archivo JSON Feed o Atom estático en HTTPS (ej. `https://cr-sanisidro.es/feed-turnos.json`).
- El cliente móvil realiza consultas condicionales mediante cabeceras HTTP estándar `If-None-Match` (ETag) y `If-Modified-Since` al sincronizar o al entrar en la pestaña de Riego.
- **Consumo de red y batería:** Cero bytes transferidos si no hay modificaciones (código HTTP 304 Not Modified).

### Opción B — Ingesta por Importación de Boletín / QR
- Los agricultores reciben convocatorias de riego por WhatsApp, carteles de la caseta de riego o boletín municipal en PDF.
- Mediante un parser local (`BulletinNoticeParser`), el agricultor puede escanear un código QR estandarizado o importar el texto del bando de riego, extrayendo fecha, sector y franja horaria.

### Opción C — Webhook Receptor de Cooperativa (Fase Servidor)
- Para cooperativas socias de Mágina Olivo con ERP agrario, un webhook en el backend de Mágina Olivo ingesta la circular oficial y la distribuye en la siguiente sincronización ordinaria offline-first.

---

## Contrato de Datos Canónico (`OfficialNoticeMessage`)

```kotlin
package com.isivoltpro.maginaolivo.domain.irrigation.notices

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class NoticeType {
    SCHEDULED_TURN,       // Convocatoria ordinaria de turno
    TURN_CANCELLED,       // Turno suspendido por avería o caudal insuficiente
    EMERGENCY_RESTRICTION,// Restricción por sequía o corte general
    EMERGENCY_REPAIR,     // Reparación de rotura en red principal o secundarias
    SEASONAL_QUOTA_UPDATE // Actualización de cuota o canon de riego
}

data class OfficialNoticeMessage(
    val noticeId: String,
    val communityName: String,
    val publishedAt: Instant,
    val effectiveDate: LocalDate,
    val affectedSectors: List<String>,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val noticeType: NoticeType,
    val title: String,
    val bodyMarkdown: String,
    val sourceUrl: String? = null,
    val contentSignature: String? = null,
)
```

---

## Motor de Reconciliación y Alertas (`NoticeReconciliationEngine`)

Cuando el `IrrigationViewModel` proyecta el calendario semanal:
1. Consulta los avisos vigentes en `CommunityNoticeRepository` para la comunidad y sector de las parcelas de la finca.
2. Si detecta una coincidencia:
   - **Turno confirmado oficialmente:** Muestra un distintivo verde *"Turno confirmado por la comunidad"*.
   - **Conflicto / Suspensión:** Muestra un distintivo ámbar/rojo *"Aviso de corte en Sector 2"* con banner explicativo y botón rápido *"Suspender turno puntual en mi calendario"*.
3. El agricultor tiene siempre el control total para decidir si actualiza su planificación o conserva su configuración.

---

## Conclusión y Hoja de Ruta
Este diseño garantiza:
1. Respeto absoluto a la privacidad (el servidor de la comunidad de regantes nunca conoce qué fincas o parcelas tiene el usuario).
2. Autonomía completa offline (la aplicación no se bloquea ni ralentiza ante caídas de red de terceros).
3. Cero llamadas o esquemas invasivos en la versión actual (B.5), dejando el terreno preparado para la fase de integración externa.

