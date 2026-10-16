# Traspaso coordinado — 17 de octubre de 2026

Petición del propietario: cerrar G1 con Codex y preparar trabajo paralelo de Claude (meteorología) y Antigravity (web/CMS). Este documento organiza la siguiente sesión; no envía mensajes a otros agentes ni autoriza fusiones, despliegues, cambios productivos persistentes, APK candidata o G2. Las decisiones del propietario de esta sesión prevalecen sobre marcadores históricos de fases en CURRENT-STATE; no reactivar fases antiguas por ese archivo.

## Punto de partida obligatorio

Verificar main remoto antes de crear ramas. Último comprobado: `21826292263f099c529da270093780a827f1b5e3`, Android CI38917404688 SUCCESS del propio main. Releer AGENTS, baseline/product lock, #727 y #744. No compartir checkout, rama, archivos modificados, AVD, puerto adb o instancia de pruebas. Cada agente usa un worktree distinto y sólo su lista permitida. Un archivo fuera de esa lista se solicita al propietario técnico antes de tocarlo; Codex integra cualquier cambio transversal en PR independiente.

## Reparto sin archivos compartidos

| Responsable | Trabajo y rama | Rutas de escritura permitidas | Rutas excluidas |
|---|---|---|---|
| Codex | G1b #757, QA Auth #758 y preparación G1c #759, cada una en su worktree actual | Archivos actuales de esas PR; `app/**/account/**`, sus pruebas; `.github/**` de gates; `supabase/tests/g1c-native/**`, `supabase/proposals/g1c-reservations.sql`; documentación G1 y este traspaso | Meteorología, web/CMS, ramas de otros agentes |
| Claude | Continuar #345 reutilizando #734; rama existente `fix/weather-forecast-aemet-resilience`, con propietario humano confirmado antes de editarla | `supabase/functions/weather-forecast/**`; documentación nueva sólo `docs/07-plans/claude-weather-2026-10-17/**` | `app/**`, `supabase/config.toml`, migrations, proposals, auth, package/lock, workflows, weather-radar, docs compartidas |
| Antigravity | Preparación web/CMS #731, reconciliando #732/#733 y código histórico; nueva rama `docs/antigravity-web-cms-20261017` desde main real | Archivos nuevos sólo `docs/07-plans/antigravity-web-cms-2026-10-17/**`, incluidos prototipos locales autocontenidos | Todo `app/**`, `supabase/**`, `.github/**`, package/lock raíz, baseline/CURRENT-STATE y este traspaso |

Las listas son disjuntas. Los rangos amplios de Codex no dan permiso para modificar otro módulo: mantener el alcance concreto de cada PR. Si Claude necesita UI nativa o Antigravity necesita una base web ejecutable, entregar primero contrato/cambio solicitado en su carpeta; Codex reserva archivos en una siguiente PR, sin edición simultánea. No iniciar un segundo backend Auth/CMS ni una segunda cola de sincronización.

## Claude: primera entrega útil

PR #734 ya existe, HEAD inspeccionado `21447ab34facb0fa62f7f2c2c42e164aea82376e`: aemet.ts, handler.ts y forecast.test.ts. No duplicar su corrección. Verificar diff contra main y estado de CI actual. Reproducir fixtures de AEMET con códigos27/28 y periodos agrupados; comprobar null de probabilidad frente0, fallback MET Norway, fecha de validez, huso horario y datos caducados. Entregar pruebas y riesgos en su carpeta. Sin llamada con claves reales, despliegue Edge ni cambios Home/Radar. Cualquier ampliación fuera del forecast necesita reserva de archivos previa.

## Antigravity: primera entrega útil

Main nativo no contiene base web desplegable; las ramas WEB-0 históricas y el antiguo V20 son referencia, no integración autorizada. Reutilizar la auditoría/CR de #732/#733 antes de proponer otro Admin. Entregar matriz KEEP/ADAPT/DROP con commit/ruta, contrato mínimo de roles de plataforma separado de permisos agrícolas y un prototipo local de estados401/403/sin datos/salud técnica. No introducir otra tabla de usuarios/sesiones, impersonación ni visibilidad general de fincas, documentos, NIF o gastos. Para shell productivo posterior se exige cerrar G0 de #731, escoger base web explícita y validar Auth/RLS. Esta preparación no autoriza reactivar ni fusionar la cadena WEB-0.

## Codex: orden de cierre

1. #757: exact HEAD, rotación real, tres regresiones, dos cuentas, consentimiento de protección del conjunto local, offline y recuperación. READY sólo tras todos los controles y revisión de evidencia. Pedir autorización específica; no fusionar automáticamente.
2. #758: exact HEAD sobre main; sólo pruebas/docs. Conservar intento ADB fallido y prueba de infraestructura recuperada. Revisión y autorización separadas.
3. #759 depende de G1b: diario/contrato/RPC todavía sin consumidor ni HTTP instalado. Validar interrupción de proceso y preparar PostgreSQL/gateway efímero; no atribuirle evidencia de G1b ni de G0. SQL permanece en proposals. Destino remoto durable, evidencia de consentimiento servidor y validación Android siguen siendo gates antes de activar.
4. Tras cada fusión autorizada, validar el SHA integrado propio de main y actualizar dependencias. G2 permanece inactivo.

## Entrega y coordinación

Cada agente entrega rama, SHA completo, archivos, comandos/resultados, CI por SHA, riesgos y siguiente tarea. Sólo Codex actualiza #744/#727 y el traspaso para evitar escrituras simultáneas; Claude/Antigravity entregan sus resultados en sus PR/documentos. El propietario autoriza cada fusión y cualquier despliegue por separado. Nunca borrar cachés/AVD/checkouts ajenos para resolver recursos locales.

## Checkpoint adicional de ejecución nativa

El arnés preparado en esta PR se ejecutó en GitHub run38961135139 sobre ef8161884106f3f2e5885f56fcd2b67e80036fc1:5/5 pruebas PostgreSQL multiconexión y gateway con fixturesHTTPSsintéticas PASS. No demuestra emisión GoTrue HTTPS real ni configuración asimétrica. Este resultado sustituye la limitación local de Docker del plan inicial; no convierte G1c en integración activada. Mantener validación propia del HEAD final y las dependencias de #757.
