# Ayuda y preparación Google Play Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Una sola línea productiva; no abrir implementaciones competidoras.

**Goal:** Ayuda veraz, privacidad y eliminación operativas, expediente de publicación.
**Architecture:** Reutilizar Perfil/Ayuda y un recurso web público mínimo. Declaraciones de Play derivadas del inventario real del binario final, incluidos SDK y proveedores.
**Tech Stack:** Kotlin, Compose, Room, Supabase y QA existente.
**Spec:** docs/superpowers/specs/2026-10-23-cierre-1.0-perfil-riego-play.md

## Global Constraints
Conservar navegación, colores e historial. Guardar localmente primero. Cerrar G2 antes de ejecutar este bloque. Releer AGENTS.md y estado vigente sobre el SHA integrado. Sin candidata APK, despliegue persistente ni publicación automáticos.

## Review Focus
Cambio de cuenta sin filtración; caída de proceso/red sin perder cambios; migración conservando histórico; valores desconocidos sin inventar; permisos denegados y texto grande.

## Task 1: Inventario y ayuda
Modificar app/src/main/java/com/isivoltpro/maginaolivo/feature/profile/HelpScreens.kt y resolver ruta real de CHANGELOG-APP.md.
Crear docs/release/DATA-INVENTORY-1.0.md y docs/release/PLAY-READINESS-1.0.md.
- [ ] Inventariar dato/origen/propósito/destino local-remoto/proveedor/permiso/retención/borrado/evidencia.
- [ ] Auditar manifest, dependencias y tráfico real de Auth, fotos, ubicación, mapas, meteo, Catastro y cualquier SDK analítico.
- [ ] Tests primero: ayuda no afirma que no hay cuenta y no promete sincronización para entidades sin transporte.
- [ ] Actualizar guía de uso, offline, pendientes/conflictos, cambio de móvil, soporte, versión y novedades.
- [ ] Ejecutar unitarios de ReleaseNotes e instrumentadas ProfileReadabilityTest; comprobar enlaces y commit.

## Task 2: Privacidad y solicitud de eliminación
Crear docs/release/PRIVACY-CONTENT-DRAFT.md y ACCOUNT-DELETION-DESIGN.md. Modificar rutas en HelpScreens.kt/AccountScreen.kt.
Interfaces de diseño: requestAccountDeletion(): requestId; readDeletionStatus(requestId): estado. Backend valida identidad y propiedad; Android nunca posee credenciales administrativas.
- [ ] Recoger juntos los datos del titular enumerados en la spec. Borrador con pendientes explícitos; no inventar soporte, identidad ni plazos.
- [ ] Tests primero: A/B, solicitud repetida, sesión caducada, trabajo interrumpido, objetos huérfanos, dispositivos offline intentando reenviar tras borrado.
- [ ] Auditar dependencias y probar eliminación de Auth/perfil/avatar/filas/adjuntos/copias según política. Logout o bloquear acceso no equivale a borrar.
- [ ] Implementar ruta visible desde app y web pública identificando Mágina Olivo, utilizable sin instalar la app, con solicitud funcional y seguimiento.
- [ ] Confirmación destructiva explica alcance y retenciones reales; ofrecer exportación cuando exista. No borrar datos de otra cuenta.
- [ ] Probar solicitud completa con datos sintéticos en entorno aislado. Gate PRIVACIDAD exige URLs reales y borrado probado antes de publicar.

## Task 3: Expediente final
- [ ] Completar Data safety desde inventario, acceso de revisión con datos sintéticos, público objetivo/clasificación, ficha y soporte.
- [ ] Verificar en fecha de envío target API, AAB, firma/package/versionCode, bibliotecas nativas/dispositivos y declaraciones de permisos.
- [ ] Confirmar tipo y antigüedad de cuenta Play. Si aplica, 12 testers durante 14 días consecutivos antes de solicitar producción.
- [ ] Pruebas de upgrade sin desinstalar, red/reinicio/restore, privacidad A/B, permisos denegados y texto grande sobre SHA final.
- [ ] Revalidar fuentes oficiales de la spec. No considerar una checklist redactada como cumplimiento demostrado.
- [ ] Presentar candidata concreta y evidencia para autorización final; no subir ni publicar automáticamente.
- [ ] GO requiere Sync, Perfil, Riego y Privacidad aceptados; documentar NO-GO si alguno falta.
