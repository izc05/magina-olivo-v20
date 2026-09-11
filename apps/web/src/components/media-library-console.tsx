'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { adminApi, type AdminMediaAsset, type AdminSession, type PlatformAdminRole } from '../lib/admin-data-source';
import { apiBaseUrl } from '../lib/api-client';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';

const allowedMimeTypes = new Set<AdminMediaAsset['mime_type']>(['image/jpeg', 'image/png', 'image/webp', 'image/avif']);
const maxBytes = 10 * 1024 * 1024;

function canEdit(role: PlatformAdminRole) {
  return role === 'super_admin' || role === 'admin' || role === 'editor';
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toLocaleString('es-ES', { maximumFractionDigits: 1 })} KB`;
  return `${(bytes / (1024 * 1024)).toLocaleString('es-ES', { maximumFractionDigits: 1 })} MB`;
}

function publicUrl(asset: AdminMediaAsset) {
  return `${apiBaseUrl}${asset.public_path}`;
}

async function sha256Hex(file: File) {
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

export function MediaLibraryConsole() {
  const auth = useAuth();
  const [session, setSession] = useState<AdminSession | null>(null);
  const [denied, setDenied] = useState(false);
  const [assets, setAssets] = useState<AdminMediaAsset[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState(0);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const role = session?.platform_access.role ?? null;
  const editable = role ? canEdit(role) : false;

  const load = useCallback(async () => {
    const [adminSession, mediaPayload] = await Promise.all([adminApi.session(), adminApi.media()]);
    setSession(adminSession);
    setAssets(mediaPayload.assets);
  }, []);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    setDenied(false);
    setError(null);
    void load().catch((caught: unknown) => {
      const status = typeof caught === 'object' && caught && 'status' in caught ? Number((caught as { status?: unknown }).status) : 0;
      if (status === 403) {
        setDenied(true);
        setSession(null);
        return;
      }
      setError('No se ha podido cargar la biblioteca multimedia.');
    });
  }, [auth.status, load]);

  const uploadedAssets = useMemo(() => assets.filter((asset) => asset.status === 'uploaded'), [assets]);
  const pendingAssets = useMemo(() => assets.filter((asset) => asset.status !== 'uploaded'), [assets]);

  function chooseFile(file: File | null) {
    setMessage(null);
    setError(null);
    if (!file) {
      setSelectedFile(null);
      return;
    }
    if (!allowedMimeTypes.has(file.type as AdminMediaAsset['mime_type'])) {
      setSelectedFile(null);
      setError('Formato no permitido. Usa JPEG, PNG, WebP o AVIF.');
      return;
    }
    if (file.size <= 0 || file.size > maxBytes) {
      setSelectedFile(null);
      setError('La imagen debe ocupar como máximo 10 MB.');
      return;
    }
    setSelectedFile(file);
  }

  async function uploadSelected() {
    if (!selectedFile || !editable) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    setProgress(10);
    try {
      const sha256 = await sha256Hex(selectedFile);
      setProgress(30);
      const reserved = await adminApi.reserveMedia({
        original_filename: selectedFile.name,
        mime_type: selectedFile.type as AdminMediaAsset['mime_type'],
        byte_size: selectedFile.size,
        sha256,
      });
      setProgress(45);

      const uploadResponse = await fetch(reserved.upload.uploadUrl, {
        method: reserved.upload.method,
        headers: reserved.upload.headers,
        body: selectedFile,
      });
      if (!uploadResponse.ok) throw new Error(`Object upload failed (${uploadResponse.status})`);
      setProgress(80);

      const completed = await adminApi.completeMedia(reserved.asset.id);
      setProgress(100);
      setSelectedFile(null);
      setAssets((current) => [completed.asset, ...current.filter((asset) => asset.id !== completed.asset.id)]);
      setMessage('Imagen subida y disponible para la web.');
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido completar la subida. Comprueba que el almacenamiento de Mágina está configurado y vuelve a intentarlo.');
    } finally {
      setBusy(false);
      window.setTimeout(() => setProgress(0), 450);
    }
  }

  async function archive(asset: AdminMediaAsset) {
    if (!editable) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await adminApi.archiveMedia(asset.id);
      setAssets((current) => current.filter((item) => item.id !== asset.id));
      setMessage('Imagen retirada de la biblioteca.');
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido archivar la imagen.');
    } finally {
      setBusy(false);
    }
  }

  async function copyUrl(asset: AdminMediaAsset) {
    try {
      await navigator.clipboard.writeText(publicUrl(asset));
      setMessage('URL de imagen copiada. Puedes pegarla en el editor de la web.');
      setError(null);
    } catch {
      setError('El navegador no ha permitido copiar la URL automáticamente.');
    }
  }

  if (auth.status === 'loading') {
    return <main className="media-admin-login"><div><strong>Comprobando acceso corporativo…</strong></div></main>;
  }

  if (auth.status === 'anonymous') {
    return <main className="media-admin-login"><div><span className="media-admin-eyebrow">Mágina Olivo · Multimedia</span><h1>Acceso corporativo</h1><p>Inicia sesión con una cuenta autorizada para gestionar las imágenes públicas.</p><GoogleSignInButton /></div></main>;
  }

  if (denied) {
    return <main className="media-admin-login"><div><span className="media-admin-eyebrow">Acceso restringido</span><h1>Sin permisos de plataforma</h1><p>Esta cuenta no puede administrar la biblioteca multimedia.</p><button className="media-admin-btn secondary" onClick={() => void auth.logout()}>Usar otra cuenta</button></div></main>;
  }

  if (!session || !role) {
    return <main className="media-admin-login"><div><strong>Cargando biblioteca…</strong>{error ? <p>{error}</p> : null}</div></main>;
  }

  return (
    <main className="media-admin-shell">
      <header className="media-admin-topbar">
        <div><a href="/admin">← Centro de control</a><h1>Biblioteca multimedia</h1><p className="media-admin-help">Imágenes propias de Mágina Olivo, listas para Inicio, noticias, eventos, cooperativas y promociones.</p></div>
        <div className="media-admin-actions"><a className="media-admin-btn secondary" href="/admin/web">Editar web</a><button className="media-admin-btn secondary" disabled={busy} onClick={() => void load()}>Actualizar</button></div>
      </header>

      {message ? <div className="media-admin-notice success" style={{ maxWidth: 1380, marginInline: 'auto' }}>{message}</div> : null}
      {error ? <div className="media-admin-notice error" style={{ maxWidth: 1380, marginInline: 'auto' }}>{error}</div> : null}

      <div className="media-admin-main">
        <aside className="media-admin-card">
          <h2>Subir imagen</h2>
          <p className="media-admin-help">JPEG, PNG, WebP o AVIF · máximo 10 MB. La API valida formato, tamaño e integridad.</p>
          <label className="media-admin-drop">
            <input disabled={!editable || busy} type="file" accept="image/jpeg,image/png,image/webp,image/avif" onChange={(event) => chooseFile(event.target.files?.[0] ?? null)} />
            <strong>{editable ? 'Seleccionar imagen' : 'Tu rol es de consulta'}</strong>
            <span>Pulsa aquí para elegir un archivo</span>
          </label>
          {selectedFile ? <div className="media-admin-file"><strong>{selectedFile.name}</strong><span>{selectedFile.type} · {formatBytes(selectedFile.size)}</span></div> : null}
          {progress > 0 ? <div className="media-admin-progress" aria-label={`Subida ${progress}%`}><span style={{ width: `${progress}%` }} /></div> : null}
          {editable ? <button className="media-admin-btn" style={{ marginTop: 14 }} disabled={!selectedFile || busy} onClick={() => void uploadSelected()}>{busy ? 'Procesando…' : 'Subir a Mágina'}</button> : null}
          <hr style={{ border: 0, borderTop: '1px solid #e6e2d8', margin: '20px 0' }} />
          <h3>Cómo usarla</h3>
          <p className="media-admin-help">Cuando una imagen esté subida, pulsa <strong>Copiar URL</strong> y pégala en “Imagen” dentro de <a href="/admin/web">Editar web</a>. La URL permanece bajo el dominio de la API de Mágina; el archivo real sigue privado en el almacenamiento.</p>
          {pendingAssets.length ? <p className="media-admin-help"><strong>{pendingAssets.length}</strong> subida{pendingAssets.length === 1 ? '' : 's'} pendiente{pendingAssets.length === 1 ? '' : 's'} o fallida{pendingAssets.length === 1 ? '' : 's'}.</p> : null}
        </aside>

        <section className="media-admin-card">
          <div className="media-admin-toolbar"><div><h2 style={{ marginBottom: 4 }}>Imágenes disponibles</h2><p>{uploadedAssets.length} archivo{uploadedAssets.length === 1 ? '' : 's'} listo{uploadedAssets.length === 1 ? '' : 's'}</p></div></div>
          {uploadedAssets.length ? <div className="media-admin-grid">{uploadedAssets.map((asset) => {
            const url = publicUrl(asset);
            return <article className="media-admin-item" key={asset.id}>
              <div className="media-admin-thumb"><img src={url} alt={asset.original_filename} loading="lazy" /></div>
              <div className="media-admin-meta">
                <strong title={asset.original_filename}>{asset.original_filename}</strong>
                <small>{formatBytes(asset.byte_size)} · {asset.mime_type.replace('image/', '').toUpperCase()}</small>
                <span className={`media-admin-status ${asset.status}`}>Lista</span>
                <div className="media-admin-code">{url}</div>
                <div className="media-admin-copyrow"><button className="media-admin-btn secondary" onClick={() => void copyUrl(asset)}>Copiar URL</button>{editable ? <button className="media-admin-btn danger" disabled={busy} onClick={() => void archive(asset)}>Archivar</button> : null}</div>
              </div>
            </article>;
          })}</div> : <div className="media-admin-empty">Aún no hay imágenes subidas desde Administración.</div>}
        </section>
      </div>
    </main>
  );
}
