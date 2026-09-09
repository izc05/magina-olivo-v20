'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ActivityRecord, ActivityType } from '@/lib/domain';
import { activitySymbols } from '@/lib/domain';
import { getLocalActivities, removeLocalActivity } from '@/lib/local-prototype-store';

function formatDate(value: string) {
  if (!value) return 'Sin fecha';
  const date = new Date(`${value}T12:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric' }).format(date);
}

type Props = {
  fieldId: string;
  mode: 'history' | 'costs' | 'calendar' | 'type';
  activityType?: ActivityType;
};

export function LocalRecordsPanel({ fieldId, mode, activityType }: Props) {
  const [records, setRecords] = useState<ActivityRecord[]>([]);

  const refresh = useCallback(() => {
    setRecords(getLocalActivities(fieldId));
  }, [fieldId]);

  useEffect(() => {
    refresh();
    window.addEventListener('magina:prototype-data-changed', refresh);
    window.addEventListener('storage', refresh);
    return () => {
      window.removeEventListener('magina:prototype-data-changed', refresh);
      window.removeEventListener('storage', refresh);
    };
  }, [refresh]);

  const visible = useMemo(() => {
    if (mode === 'type' && activityType) return records.filter((record) => record.type === activityType);
    if (mode === 'calendar') return records.filter((record) => Boolean(record.followUpOn));
    if (mode === 'costs') return records.filter((record) => typeof record.costEur === 'number');
    return records;
  }, [records, mode, activityType]);

  if (!visible.length) return null;

  const totalCost = mode === 'costs'
    ? visible.reduce((sum, record) => sum + (record.costEur ?? 0), 0)
    : 0;

  function remove(id: string) {
    removeLocalActivity(id);
    refresh();
  }

  return (
    <section className="section local-records-section">
      <div className="section-head module-section-head">
        <h2>{mode === 'calendar' ? 'Programado desde tus registros' : mode === 'costs' ? 'Costes añadidos en esta demo' : 'Guardado en este dispositivo'}</h2>
        <span className="local-only-pill">LOCAL</span>
      </div>
      {mode === 'costs' && <div className="card local-cost-total"><span>NUEVOS COSTES LOCALES</span><strong>+{totalCost.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} €</strong></div>}
      <div className="card local-record-list">
        {visible.map((record) => (
          <article className="local-record-row" key={record.id}>
            <span className="history-symbol">{activitySymbols[record.type]}</span>
            <div className="local-record-copy">
              <strong>{mode === 'calendar' ? record.title : record.title}</strong>
              <small>{mode === 'calendar' ? formatDate(record.followUpOn ?? '') : formatDate(record.occurredOn)}{mode === 'calendar' && record.followUpTime ? ` · ${record.followUpTime}` : ''}</small>
              <p>{record.summary}{record.costEur !== undefined ? ` · ${record.costEur.toLocaleString('es-ES')} €` : ''}</p>
            </div>
            <button type="button" onClick={() => remove(record.id)} aria-label={`Eliminar ${record.title}`}>×</button>
          </article>
        ))}
      </div>
      <p className="local-storage-note">Estos datos están solo en este navegador y sirven para probar el flujo antes de conectar la base de datos.</p>
    </section>
  );
}
