'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { adminApi, type AdminMediaAsset } from '../lib/admin-data-source';
import { apiBaseUrl } from '../lib/api-client';

type Props = {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  label?: string;
};

function assetUrl(asset: AdminMediaAsset) {
  return `${apiBaseUrl}${asset.public_path}`;
}

export function AdminMediaPicker({ value, onChange, disabled = false, label = 'Imagen' }: Props) {
  const [open, setOpen] = useState(false);
  const [assets, setAssets] = useState<AdminMediaAsset[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const uploaded = useMemo(() => assets.filter((asset) => asset.status === 'uploaded'), [assets]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const payload = await adminApi.media();
      setAssets(payload.assets);
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido cargar la biblioteca multimedia.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!open) return;
    void load();
  }, [open, load]);

  return (
    <div className="admin-media-picker-field">
      <div className="admin-media-picker-labelrow">
        <span>{label}</span>
        <button type="button" className="site-admin-btn secondary compact" disabled={disabled} onClick={() => setOpen(true)}>Elegir de Multimedia</button>
      </div>
      <input disabled={disabled} placeholder="https://… o selecciona una imagen" value={value} onChange={(event) => onChange(event.target.value)} />
      {value ? <div className="admin-media-picker-current"><img src={value} alt="Vista previa del recurso seleccionado" /><button type="button" className="site-admin-btn secondary compact" disabled={disabled} onClick={() => onChange('')}>Quitar</button></div> : null}

      {open ? <div className="admin-media-picker-backdrop" role="dialog" aria-modal="true" aria-label="Seleccionar imagen de la biblioteca">
        <div className="admin-media-picker-modal">
          <div className="admin-media-picker-head"><div><strong>Biblioteca multimedia</strong><small>Selecciona una imagen ya subida a Mágina.</small></div><button type="button" className="site-admin-btn secondary compact" onClick={() => setOpen(false)}>Cerrar</button></div>
          {error ? <div className="site-admin-notice error">{error}</div> : null}
          {loading ? <div className="site-admin-empty">Cargando imágenes…</div> : null}
          {!loading && !uploaded.length ? <div className="site-admin-empty">No hay imágenes listas. <a href="/admin/media" target="_blank">Subir una imagen</a>.</div> : null}
          {!loading && uploaded.length ? <div className="admin-media-picker-grid">{uploaded.map((asset) => {
            const url = assetUrl(asset);
            const selected = value === url;
            return <button type="button" className={`admin-media-picker-item ${selected ? 'selected' : ''}`} key={asset.id} onClick={() => { onChange(url); setOpen(false); }}>
              <img src={url} alt={asset.original_filename} loading="lazy" />
              <span>{asset.original_filename}</span>
              {selected ? <b>Seleccionada</b> : null}
            </button>;
          })}</div> : null}
          <div className="admin-media-picker-foot"><a className="site-admin-btn secondary" href="/admin/media" target="_blank">Abrir biblioteca completa</a><button type="button" className="site-admin-btn secondary" disabled={loading} onClick={() => void load()}>Actualizar</button></div>
        </div>
      </div> : null}
    </div>
  );
}
