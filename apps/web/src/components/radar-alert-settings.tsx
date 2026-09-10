'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';
import { PushNotificationSettings } from './push-notification-settings';

type AlertRuleValues = {
  enabled: boolean;
  radius_km: number;
  min_dbz: number;
  cooldown_minutes: number;
};

type AlertRulePayload = {
  field_id: string;
  rule: (AlertRuleValues & { id: string }) | null;
  defaults?: AlertRuleValues;
  semantics: 'observed_reflectivity_only';
};

const FALLBACK_DEFAULTS: AlertRuleValues = {
  enabled: true,
  radius_km: 10,
  min_dbz: 12,
  cooldown_minutes: 60,
};

export function RadarAlertSettings({ fieldId }: { fieldId: string }) {
  const { selectedWorkspaceId, preferences } = useAuth();
  const [values, setValues] = useState<AlertRuleValues>(FALLBACK_DEFAULTS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState('');

  useEffect(() => {
    if (!selectedWorkspaceId) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    void apiFetch<AlertRulePayload>(`/api/v1/fields/${fieldId}/radar/alert-rule`, {
      workspaceId: selectedWorkspaceId,
    }).then((payload) => {
      if (cancelled) return;
      setValues(payload.rule ?? payload.defaults ?? FALLBACK_DEFAULTS);
      setLoading(false);
    }).catch(() => {
      if (!cancelled) {
        setStatus('No se ha podido cargar la regla.');
        setLoading(false);
      }
    });
    return () => { cancelled = true; };
  }, [fieldId, selectedWorkspaceId]);

  async function saveRule() {
    if (!selectedWorkspaceId || saving) return;
    setSaving(true);
    setStatus('');
    try {
      const payload = await apiFetch<AlertRulePayload>(`/api/v1/fields/${fieldId}/radar/alert-rule`, {
        method: 'PUT',
        workspaceId: selectedWorkspaceId,
        body: JSON.stringify(values),
      });
      if (payload.rule) setValues(payload.rule);
      setStatus('Aviso guardado.');
    } catch {
      setStatus('No se ha podido guardar el aviso.');
    } finally {
      setSaving(false);
    }
  }

  return <section className="section card rain-settings">
    <div>
      <span className="eyebrow dark">AVISO DE RADAR</span>
      <h2>Avísame si aparece un eco cerca</h2>
      <p>Se basa en observaciones de AEMET. No predice cuándo llegará la lluvia.</p>
    </div>

    {preferences?.weather_alerts === false ? <div className="radar-alert-global-off">
      Los avisos meteorológicos generales están desactivados. Puedes preparar esta regla, pero no generará avisos hasta activarlos en <Link href="/perfil">Perfil</Link>.
    </div> : null}

    {loading ? <p className="radar-alert-status">Cargando configuración…</p> : <div className="radar-alert-form">
      <label className="radar-alert-toggle">
        <span><strong>Activar para esta finca</strong><small>Solo genera un aviso cuando la observación cumple tus condiciones.</small></span>
        <input
          type="checkbox"
          checked={values.enabled}
          onChange={(event) => setValues((current) => ({ ...current, enabled: event.target.checked }))}
        />
      </label>

      <div className="radar-alert-fields">
        <label className="radar-alert-field">
          <span>Distancia</span>
          <select value={values.radius_km} onChange={(event) => setValues((current) => ({ ...current, radius_km: Number(event.target.value) }))}>
            <option value={5}>Muy cerca · 5 km</option>
            <option value={10}>Cerca · 10 km</option>
            <option value={20}>Alrededor · 20 km</option>
            <option value={40}>Amplio · 40 km</option>
          </select>
        </label>

        <label className="radar-alert-field">
          <span>Sensibilidad</span>
          <select value={values.min_dbz} onChange={(event) => setValues((current) => ({ ...current, min_dbz: Number(event.target.value) }))}>
            <option value={12}>Alta · desde 12 dBZ</option>
            <option value={18}>Media · desde 18 dBZ</option>
            <option value={24}>Selectiva · desde 24 dBZ</option>
            <option value={30}>Muy selectiva · desde 30 dBZ</option>
          </select>
        </label>

        <label className="radar-alert-field">
          <span>Repetir como máximo</span>
          <select value={values.cooldown_minutes} onChange={(event) => setValues((current) => ({ ...current, cooldown_minutes: Number(event.target.value) }))}>
            <option value={60}>Cada 1 hora</option>
            <option value={180}>Cada 3 horas</option>
            <option value={360}>Cada 6 horas</option>
            <option value={720}>Cada 12 horas</option>
          </select>
        </label>
      </div>

      <div className="radar-alert-help">
        <b>Qué significa sensibilidad:</b> es un umbral de reflectividad del radar. Mágina no convierte este valor automáticamente en mm/h ni en una hora estimada de llegada.
      </div>

      <div className="radar-alert-actions">
        <button type="button" className="radar-alert-save" onClick={() => void saveRule()} disabled={saving}>
          {saving ? 'Guardando…' : 'Guardar aviso'}
        </button>
        {status ? <span className="radar-alert-status">{status}</span> : null}
      </div>
    </div>}

    <PushNotificationSettings fieldId={fieldId} />
  </section>;
}
