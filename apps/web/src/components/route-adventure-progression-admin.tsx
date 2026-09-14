'use client';

import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../lib/api-client';

type ProgressionMode = 'free' | 'linear';

export function RouteAdventureProgressionAdmin({ routeId, editable, busy }: { routeId: string; editable: boolean; busy: boolean }) {
  const [mode, setMode] = useState<ProgressionMode>('free');
  const [loading, setLoading] = useState(true);
  const [localBusy, setLocalBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await apiFetch<{ progression_mode: ProgressionMode }>(`/api/v1/admin/routes/${routeId}/adventure/progression`);
      setMode(response.progression_mode);
      setError(null);
    } catch {
      setError('No se ha podido cargar el modo de recorrido.');
    } finally {
      setLoading(false);
    }
  }, [routeId]);

  useEffect(() => { void load(); }, [load]);

  async function save(next: ProgressionMode) {
    const previous = mode;
    setMode(next);
    setLocalBusy(true);
    setMessage(null);
    setError(null);
    try {
      await apiFetch(`/api/v1/admin/routes/${routeId}/adventure/progression`, {
        method: 'PATCH', body: JSON.stringify({ progression_mode: next }),
      });
      setMessage(next === 'linear' ? 'Recorrido por etapas activado.' : 'Recorrido libre activado.');
    } catch {
      setMode(previous);
      setError('No se ha podido cambiar el modo de recorrido.');
    } finally {
      setLocalBusy(false);
    }
  }

  return <div className="routes-admin-card">
    <div className="routes-admin-card-title">
      <div><span>Mecánica de juego</span><h2>Orden del recorrido</h2></div>
      <strong>{loading ? 'Cargando…' : mode === 'linear' ? 'Por etapas' : 'Libre'}</strong>
    </div>
    {message ? <div className="routes-admin-notice ok">{message}</div> : null}
    {error ? <div className="routes-admin-notice error">{error}</div> : null}
    <div className="routes-admin-form-grid">
      <label>Modo
        <select disabled={!editable || busy || localBusy || loading} value={mode} onChange={(event) => void save(event.target.value as ProgressionMode)}>
          <option value="free">Libre · descubre en cualquier orden</option>
          <option value="linear">Por etapas · desbloquea el recorrido</option>
        </select>
      </label>
    </div>
    <p className="routes-admin-help">En modo por etapas, un reto queda bloqueado mientras exista una etapa obligatoria anterior sin completar. Los extras no bloquean el avance. El orden se toma del campo “Orden” de cada checkpoint.</p>
  </div>;
}
