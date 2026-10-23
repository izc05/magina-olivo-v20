# Perfil personal sincronizado Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Una sola línea productiva; no abrir implementaciones competidoras.

**Goal:** Nombre y foto privados recuperables en otro móvil, correo de Auth y sesión visible.
**Architecture:** Identidad por auth user UUID, separada de profile_settings por workspace. Reutilizar AccountRepository y el transporte integrado; avatar en Storage privado.
**Tech Stack:** Kotlin, Compose, Room, Supabase y QA existente.
**Spec:** docs/superpowers/specs/2026-10-23-cierre-1.0-perfil-riego-play.md

## Global Constraints
Conservar navegación, colores e historial. Guardar localmente primero. Cerrar G2 antes de ejecutar este bloque. Releer AGENTS.md y estado vigente sobre el SHA integrado. Sin candidata APK, despliegue persistente ni publicación automáticos.

## Review Focus
Cambio de cuenta sin filtración; caída de proceso/red sin perder cambios; migración conservando histórico; valores desconocidos sin inventar; permisos denegados y texto grande.

## Task 1: Datos personales y cabecera
Files existentes bajo app/src/main/java/com/isivoltpro/maginaolivo/: feature/profile/ProfileScreen.kt, feature/profile/MyProfile.kt, feature/account/AccountScreen.kt y data/local/entity/ProfileSettingsEntity.kt (solo auditar el alcance workspace).
Crear feature/profile/PersonalProfile.kt y data/repository/PersonalProfileRepository.kt.
Interfaces propuestas: PersonalProfile(userId: UUID, displayName: String, avatarObjectPath: String?, remoteVersion: Long?); observePersonalProfile(userId: UUID): Flow<PersonalProfile?>; saveDisplayName(userId: UUID, name: String): AppResult<Unit>.
- [ ] Tests primero en PersonalProfileRepositoryTest: vacío/81 caracteres rechazados, recorte de extremos, A→logout→B sin mostrar nombre anterior, correo tomado de Auth y sesión offline.
- [ ] Ejecutar gradle testDevDebugUnitTest --tests '*PersonalProfileRepositoryTest*'; registrar fallo esperado antes de implementar.
- [ ] Implementar nombre 1–80, iniciales, correo y acceso a Cuenta/logout; no crear otro gestor de sesión ni editar el correo en user_profiles.
- [ ] Cache/cola por userId con migración aditiva; no cambiar silenciosamente perfil de workspace a identidad de usuario.
- [ ] Extender MyProfileScreenTest y ProfileReadabilityTest; verificar que logout conserva registros agrícolas y oculta identidad.
- [ ] Repetir pruebas, revisar diff y commit.

## Task 2: Foto privada
Crear feature/profile/ProfilePhotoPicker.kt y data/repository/ProfileAvatarStore.kt; reutilizar infraestructura de adjuntos después de inspeccionarla.
Interfaces: chooseAvatar(userId: UUID, uri: Uri): AppResult<Unit>; removeAvatar(userId: UUID): AppResult<Unit>.
- [ ] Tests primero: cancelar, archivo no imagen, >5 MiB, decodificación de imagen enorme, EXIF GPS, red perdida y cambio de cuenta durante subida.
- [ ] Implementar selector del sistema, entrada acotada, máximo propuesto 1024 px, eliminación de EXIF y fallback accesible.
- [ ] Preparar ampliación de user_profiles y Storage privado; RLS A/B debe impedir lectura, reemplazo y borrado cruzados. Nada de service_role en Android.
- [ ] Subir objeto nuevo y confirmar referencia antes de retirar el anterior. Error conserva foto y pendiente durable; quitar foto no debe resucitar tras reinicio.
- [ ] Ejecutar instrumentadas y tests nativos efímeros de RLS; commit.

## Task 3: Recuperación entre móviles
Crear PersonalProfileSyncContractTest y reutilizar fronteras de G2 sin otra cola paralela.
- [ ] Tests primero: edición offline, muerte de proceso, ACK perdido, dos móviles con versiones distintas, logout durante subida.
- [ ] Implementar reintento/versionado por usuario; conflicto explícito sin sobrescritura silenciosa. Permisos Android son locales al dispositivo.
- [ ] Dos instalaciones: editar en A y recuperar nombre/foto en B; luego sentido inverso. Registrar SHA, campos y resultado.
- [ ] CI del HEAD, upgrade sin desinstalar y evidencia física. Gate PERFIL solo pasa con recuperación real; commit.
