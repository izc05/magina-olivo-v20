'use client';

import { useEffect, useMemo, useState } from 'react';
import { apiFetch } from '@/lib/api-client';

type MyBusiness = {
  id: string;
  name: string;
  role: string;
  status: string;
  municipality_name: string | null;
};

type RewardUnlock = {
  rewardId: string;
  title: string;
  status: string;
  requiredLevel: number;
  requiredLevelName: string;
  minXp: number;
};

const LEVELS = [
  { level: 1, name: 'Brote', xp: 0 },
  { level: 2, name: 'Rama nueva', xp: 100 },
  { level: 3, name: 'Olivo joven', xp: 250 },
  { level: 4, name: 'Olivo arraigado', xp: 450 },
  { level: 5, name: 'Olivo en flor', xp: 700 },
  { level: 6, name: 'Olivo de cosecha', xp: 1000 },
  { level: 7, name: 'Olivo maduro', xp: 1400 },
  { level: 8, name: 'Olivo centenario', xp: 1900 },
  { level: 9, name: 'Guardián del Olivar', xp: 2500 },
  { level: 10, name: 'Leyenda de Mágina', xp: 3200 },
] as const;

export function AlmazaraRewardUnlockManager() {
  const [businesses, setBusinesses] = useState<MyBusiness[]>([]);
  const [businessId, setBusinessId] = useState('');
  const [role, setRole] = useState<string | null>(null);
  const [rewards, setRewards] = useState<RewardUnlock[]>([]);
  const [drafts, setDrafts] = useState<Record<string, number>>({});
  const [saving, setSaving] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const canEdit = useMemo(() => role === 'owner' || role === 'manager' || role === 'editor', [role]);

  useEffect(() => {
    let cancelled = false;
    apiFetch<{ businesses: MyBusiness[] }>('/api/v1/my/businesses')
      .then((result) => {
        if (cancelled) return;
        setBusinesses(result.businesses);
        setBusinessId((current) => current || result.businesses[0]?.id || '');
      })
      .catch(() => { if (!cancelled) setMessage('No se pudieron cargar tus almazaras.'); });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!businessId) {
      setRewards([]);
      setRole(null);
      return;
    }
    let cancelled = false;
    setMessage(null);
    apiFetch<{ role: string; rewards: RewardUnlock[] }>(`/api/v1/my/businesses/${businessId}/almazara-reward-unlocks`)
      .then((result) => {
        if (cancelled) return;
        setRole(result.role);
        setRewards(result.rewards);
        setDrafts(Object.fromEntries(result.rewards.map((item) => [item.rewardId, item.requiredLevel])));
      })
      .catch(() => { if (!cancelled) setMessage('No se pudieron cargar los requisitos de nivel.'); });
    return () => { cancelled = true; };
  }, [businessId]);

  async function save(item: RewardUnlock) {
    const requiredLevel = drafts[item.rewardId] ?? item.requiredLevel;
    setSaving(item.rewardId);
    setMessage(null);
    try {
      const result = await apiFetch<{ reward: { id: string; requiredLevel: number; requiredLevelName: string; minXp: number } }>(
        `/api/v1/my/businesses/${businessId}/almazara-rewards/${item.rewardId}/unlock`,
        { method: 'PUT', body: JSON.stringify({ requiredLevel }) },
      );
      setRewards((current) => current.map((reward) => reward.rewardId === item.rewardId ? {
        ...reward,
        requiredLevel: result.reward.requiredLevel,
        requiredLevelName: result.reward.requiredLevelName,
        minXp: result.reward.minXp,
      } : reward));
      setMessage(`Requisito actualizado: ${item.title} se desbloquea en nivel ${result.reward.requiredLevel}.`);
    } catch {
      setMessage('No se pudo actualizar el nivel mínimo del premio.');
    } finally {
      setSaving(null);
    }
  }

  return <section className="card" style={{ maxWidth: 1080, margin: '0 auto 80px', padding: 20 }}>
    <span>MI OLIVO · DESBLOQUEOS</span>
    <h2>Niveles mínimos de los premios</h2>
    <p>El nivel se calcula con XP histórico y nunca baja al gastar aceitunas. Un usuario necesita cumplir <strong>el nivel mínimo y el coste en aceitunas</strong> para reservar un premio.</p>

    {message ? <p role="status">{message}</p> : null}

    <label htmlFor="unlock-business">Entidad</label>
    <select id="unlock-business" value={businessId} onChange={(event) => setBusinessId(event.target.value)}>
      <option value="">Selecciona una entidad</option>
      {businesses.map((business) => <option key={business.id} value={business.id}>{business.name}{business.municipality_name ? ` · ${business.municipality_name}` : ''}</option>)}
    </select>
    {role ? <p><small>Permiso actual: {role}</small></p> : null}

    {!rewards.length && businessId ? <p>No hay premios configurados en esta entidad.</p> : null}
    {rewards.length ? <div style={{ overflowX: 'auto', marginTop: 16 }}>
      <table>
        <thead><tr><th>Premio</th><th>Estado</th><th>Nivel mínimo</th><th>XP mínimo</th><th>Acción</th></tr></thead>
        <tbody>{rewards.map((item) => {
          const selected = LEVELS.find((level) => level.level === (drafts[item.rewardId] ?? item.requiredLevel)) ?? LEVELS[0];
          return <tr key={item.rewardId}>
            <td>{item.title}</td>
            <td>{item.status}</td>
            <td><select
              aria-label={`Nivel mínimo ${item.title}`}
              value={drafts[item.rewardId] ?? item.requiredLevel}
              disabled={!canEdit}
              onChange={(event) => setDrafts((current) => ({ ...current, [item.rewardId]: Number(event.target.value) }))}
            >{LEVELS.map((level) => <option key={level.level} value={level.level}>Nivel {level.level} · {level.name}</option>)}</select></td>
            <td>{selected.xp} XP</td>
            <td>{canEdit ? <button type="button" disabled={saving === item.rewardId} onClick={() => void save(item)}>{saving === item.rewardId ? 'Guardando…' : 'Guardar nivel'}</button> : 'Solo lectura'}</td>
          </tr>;
        })}</tbody>
      </table>
    </div> : null}
  </section>;
}
