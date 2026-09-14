'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { apiFetch, ApiRequestError } from '../lib/api-client';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';

type Candidate = {
  route_id: string;
  slug: string;
  route_name: string;
  route_status: string;
  track_status: string;
  distance_m: number | null;
  difficulty: string | null;
  municipality_name: string | null;
  validated_track_count: number;
  active_poi_count: number;
  checkpoint_count: number;
  required_checkpoint_count: number;
  adventure_enabled: boolean;
  adventure_title: string | null;
  candidate_score: number;
  can_seed_from_poi: boolean;
  ready_to_publish: boolean;
  blockers: string[];
};

function blockerLabel(blocker: string) {
  if (blocker === 'route_not_published') return 'ruta sin publicar';
  if (blocker === 'validated_track_missing') return 'track sin validar';
  if (blocker === 'route_poi_missing') return 'sin POI geolocalizados';
  if (blocker === 'adventure_checkpoint_missing') return 'sin etapas de aventura';
  if (blocker === 'required_checkpoint_missing') return 'sin etapa obligatoria';
  return blocker;
}

function km(value: number | null) {
  return value == null ? '—' : `${(Number(value) / 1000).toFixed(1)} km`;
}

export function RouteAdventureCandidates() {
  const auth = useAuth();
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await apiFetch<{ candidates: Candidate[] }>('/api/v1/admin/adventures/candidates');
      setCandidates(response.candidates);
      setError(null);
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.status === 403) setError('Esta cuenta no tiene acceso al catálogo de candidatas.');
      else setError('No se han podido analizar las rutas candidatas.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (auth.status === 'authenticated') void load();
  }, [auth.status, load]);

  async function prepare(candidate: Candidate) {
    setBusyId(candidate.route_id);
    setError(null);
    setMessage(null);
    try {
      await apiFetch(`/api/v1/admin/routes/${candidate.route_id}/adventure`, {
        method: 'PUT',
        body: JSON.stringify({
          enabled: false,
          title: candidate.adventure_title || `Aventura · ${candidate.route_name}`,
          intro: null,
          completion_message: null,
        }),
      });
      const result = await apiFetch<{ created_count: number; skipped_count: number }>(`/api/v1/admin/routes/${candidate.route_id}/adventure/import-route-points`, {
        method: 'POST',
        body: JSON.stringify({ unlock_radius_m: 60, points: 100, is_required: true }),
      });
      setMessage(`${candidate.route_name}: ${result.created_count} etapas creadas desde POI reales${result.skipped_count ? ` y ${result.skipped_count} ya existentes omitidas` : ''}. La aventura permanece oculta hasta revisión.`);
      await load();
    } catch (caught) {
      console.error(caught);
      setError(`No se ha podido preparar ${candidate.route_name}.`);
    } finally {
      setBusyId(null);
    }
  }

  if (auth.status === 'loading') return <main className="routes-admin-login"><strong>Comprobando acceso…</strong></main>;
  if (auth.status === 'anonymous') return <main className="routes-admin-login"><div><h1>Candidatas de Mágina Aventura</h1><p>Inicia sesión con una cuenta autorizada.</p><GoogleSignInButton /></div></main>;

  const ideal = candidates.filter((item) => item.can_seed_from_poi && item.route_status === 'published');
  const ready = candidates.filter((item) => item.ready_to_publish);
  const poiTotal = candidates.reduce((sum, item) => sum + item.active_poi_count, 0);

  return <main className="routes-admin-shell">
    <header className="routes-admin-header">
      <div><Link href="/admin/rutas">← Gestión de rutas</Link><span>Mágina Aventura · selección basada en datos reales</span><h1>Candidatas para aventura</h1><p>Prioriza rutas publicadas, tracks validados y POI geolocalizados ya existentes.</p></div>
      <button disabled={loading} onClick={() => void load()}>Reanalizar rutas</button>
    </header>

    {message ? <div className="routes-admin-notice ok">{message}</div> : null}
    {error ? <div className="routes-admin-notice error">{error}</div> : null}

    <section className="routes-admin-layout" style={{gridTemplateColumns:'1fr'}}>
      <div className="routes-admin-card">
        <div className="routes-admin-card-title"><div><span>Inventario real</span><h2>Radar de candidatas</h2></div><strong>{candidates.length} rutas analizadas</strong></div>
        <div className="routes-admin-metrics">
          <article><span>Ideales para sembrar</span><strong>{ideal.length}</strong></article>
          <article><span>Listas para publicar</span><strong>{ready.length}</strong></article>
          <article><span>POI reutilizables</span><strong>{poiTotal}</strong></article>
        </div>
        <p className="routes-admin-help">La puntuación no inventa contenido ni calidad turística: solo prioriza preparación técnica. Publicada + track validado + POI reales obtiene mayor puntuación.</p>
      </div>

      <div className="routes-admin-card">
        <div className="routes-admin-card-title"><div><span>Prioridad automática</span><h2>Rutas disponibles</h2></div></div>
        {loading ? <p>Cruzando rutas, tracks, POI y checkpoints…</p> : null}
        {!loading && candidates.length === 0 ? <p>No hay rutas no archivadas que analizar.</p> : null}
        <div className="routes-admin-tracks">
          {candidates.map((candidate) => <article key={candidate.route_id}>
            <div style={{flex:'1 1 420px'}}>
              <strong>{candidate.candidate_score}/100 · {candidate.route_name}</strong>
              <span>{candidate.municipality_name ?? 'Sierra Mágina'} · {km(candidate.distance_m)} · {candidate.route_status} · track {candidate.track_status}</span>
              <span>{candidate.validated_track_count} track validado · {candidate.active_poi_count} POI reales · {candidate.checkpoint_count} etapas · {candidate.required_checkpoint_count} obligatorias</span>
              <span>{candidate.ready_to_publish ? '✓ Lista para publicación' : candidate.blockers.map(blockerLabel).join(' · ')}</span>
              {candidate.adventure_title ? <span>Aventura: {candidate.adventure_title}{candidate.adventure_enabled ? ' · activa' : ' · oculta'}</span> : null}
            </div>
            <div>
              <button className="secondary" disabled={!candidate.can_seed_from_poi || busyId === candidate.route_id} onClick={() => void prepare(candidate)}>{busyId === candidate.route_id ? 'Preparando…' : candidate.checkpoint_count ? 'Importar POI nuevos' : 'Preparar aventura'}</button>
              <Link className="admin-button" href="/admin/rutas">Abrir gestor</Link>
            </div>
          </article>)}
        </div>
      </div>
    </section>
  </main>;
}
