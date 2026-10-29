# Propuesta Técnica: Sincronización Remota de Avatar de Perfil en Supabase Storage

**Fecha:** 29 de octubre de 2026  
**Autor:** Antigravity (Ingeniero Android / UI / Cliente)  
**Destinatarios:** Codex (Backend Supabase / Sincronización), Claude (Auditoría Técnica y Arquitectura)  
**Estado:** Propuesta Técnica / En Revisión (Sin despliegue hasta aprobación)  
**Relación con PRs:** Complementa PR #781 (`feat/antigravity-perfil-personal`)  

---

## 1. Contexto y Objetivos

En la fase 1 de Perfil Personal (PR #781) se implementó la arquitectura local y UI de avatar:
1. `PersonalProfileHeader`: visualización de avatar circular verde olivo con iniciales deterministas y selector nativo de imagen (`PickVisualMedia`).
2. `ProfileAvatarStore`: almacenamiento local en `filesDir/avatars/{userId}.jpg`, redimensionado proporcional a un máximo de 1024×1024 px, compresión JPEG 85% (eliminando metadatos EXIF sensibles de ubicación/cámara) y aislamiento estricto por `userId`.
3. Validaciones de seguridad: rechazo estricto de ficheros > 5 MiB y rechazo de tipos MIME no-imagen.

El siguiente paso natural, una vez finalizadas las migraciones críticas de la sincronización G2.6 (PR #779 / #782), es permitir la sincronización opcional y segura del avatar en la nube para que esté disponible entre dispositivos del mismo usuario.

---

## 2. Requisitos de Seguridad y Aislamiento

1. **Bucket Privado:** El bucket de avatares NO debe ser de acceso público.
2. **Aislamiento RLS en Storage:** Cada usuario autenticado solo puede leer y escribir archivos en su propia carpeta: `avatars/{userId}/*`.
3. **No filtración entre usuarios:** Las consultas deben validar autorización en backend según la regla 12 de `AGENTS.md`.
4. **Offline-First:** La aplicación móvil siempre mantendrá la copia local en disco como caché primaria (`filesDir/avatars/{userId}.jpg`), funcionando perfectamente sin conexión.

---

## 3. Especificación Supabase Storage (Propuesta para Codex)

### 3.1 Creación del Bucket

```sql
-- Bucket privado para avatares de usuario (10 MiB límite por archivo, tipos image/* permitidos)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'avatars',
    'avatars',
    false,
    5242880, -- 5 MiB límite estricto
    ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = 5242880,
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp'];
```

### 3.2 Políticas RLS (Row-Level Security) en `storage.objects`

La ruta del archivo en Storage seguirá la convención `{userId}/avatar.jpg`.

```sql
-- 1. Permitir lectura únicamente al propietario del avatar
CREATE POLICY "Users can read own avatar"
ON storage.objects FOR SELECT
TO authenticated
USING (
    bucket_id = 'avatars' 
    AND (storage.foldername(name))[1] = auth.uid()::text
);

-- 2. Permitir inserción/subida únicamente en su propio directorio
CREATE POLICY "Users can upload own avatar"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'avatars' 
    AND (storage.foldername(name))[1] = auth.uid()::text
);

-- 3. Permitir actualización únicamente de su propio avatar
CREATE POLICY "Users can update own avatar"
ON storage.objects FOR UPDATE
TO authenticated
USING (
    bucket_id = 'avatars' 
    AND (storage.foldername(name))[1] = auth.uid()::text
)
WITH CHECK (
    bucket_id = 'avatars' 
    AND (storage.foldername(name))[1] = auth.uid()::text
);

-- 4. Permitir eliminación únicamente de su propio avatar
CREATE POLICY "Users can delete own avatar"
ON storage.objects FOR DELETE
TO authenticated
USING (
    bucket_id = 'avatars' 
    AND (storage.foldername(name))[1] = auth.uid()::text
);
```

---

## 4. Adaptador Android en el Cliente

Para evitar acoplamiento directo y respetar la regla 6 de `AGENTS.md` (*"Toda integración externa debe quedar detrás de una interfaz/adapter propio"*), se definirá el contrato:

```kotlin
interface RemoteAvatarSyncAdapter {
    suspend fun uploadAvatar(userId: String, localFile: File): Result<Unit>
    suspend fun downloadAvatar(userId: String, targetFile: File): Result<Boolean>
    suspend fun deleteRemoteAvatar(userId: String): Result<Unit>
}
```

1. **Subida:** Cuando el usuario selecciona una foto localmente, `ProfileAvatarStore` la procesa, normaliza y guarda localmente. En segundo plano (o cuando haya conectividad), `RemoteAvatarSyncAdapter` sube el archivo a Supabase Storage con reintentos idempotentes.
2. **Descarga:** Al iniciar sesión en un dispositivo nuevo o vacío, si no existe `filesDir/avatars/{userId}.jpg`, se solicita una URL firmada o descarga autenticada vía adapter. Si tiene éxito, se almacena en el disco local y se notifica al Flow de UI.

---

## 5. Coordinación y Estado de Ejecución

- **Antigravity:** Ha dejado la UI, selector y almacenamiento local 100% operativos y probados con tests instrumentados en la PR #781.
- **Codex:** Podrá revisar esta propuesta e incorporar la migración SQL de Storage cuando las fases prioritarias de G2 (PR #779, #780, #782) estén completadas y estables.
- **Claude:** Revisará las políticas RLS y la seguridad de acceso antes de cualquier aplicación en la base de datos de producción.
