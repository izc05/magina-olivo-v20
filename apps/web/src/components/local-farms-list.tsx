'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import type { FieldRecord } from '@/lib/domain';
import { getLocalFields, removeLocalField } from '@/lib/local-prototype-store';
import { ArrowIcon, SproutIcon } from '@/components/icons';

export function LocalFarmsList() {
  const [fields, setFields] = useState<FieldRecord[]>([]);

  const refresh = useCallback(() => setFields(getLocalFields()), []);

  useEffect(() => {
    refresh();
    window.addEventListener('magina:prototype-data-changed', refresh);
    window.addEventListener('storage', refresh);
    return () => {
      window.removeEventListener('magina:prototype-data-changed', refresh);
      window.removeEventListener('storage', refresh);
    };
  }, [refresh]);

  if (!fields.length) return null;

  function remove(id: string) {
    removeLocalField(id);
    refresh();
  }

  return (
    <section className="section local-farms-section">
      <div className="section-head">
        <h2>Añadidas en este dispositivo</h2>
        <span className="local-only-pill">LOCAL</span>
      </div>
      <div className="farm-row">
        {fields.map((field) => (
          <article className="card farm-card local-farm-card" key={field.id}>
            <Link href={`/mi-campo/fincas/local?id=${encodeURIComponent(field.id)}`} className="local-farm-link">
              <div className="farm-image local-farm-image">
                <span className="farm-status">Demo local</span>
                <span className="local-farm-mark"><SproutIcon /></span>
              </div>
              <div className="farm-body">
                <div className="farm-card-head">
                  <div>
                    <h3>{field.name}</h3>
                    <div className="farm-meta">{field.oliveTrees ?? '—'} olivas · {field.municipality ?? 'Sin municipio'}</div>
                  </div>
                  <ArrowIcon />
                </div>
                <span className="status-pill">{field.variety ?? 'Variedad pendiente'} · {field.waterRegime ?? 'Régimen pendiente'}</span>
                <p className="local-farm-note">Abrir ficha local → registrar → consultar su propia historia.</p>
              </div>
            </Link>
            <button type="button" className="local-farm-remove floating" onClick={() => remove(field.id)} aria-label={`Eliminar ${field.name}`}>×</button>
          </article>
        ))}
      </div>
    </section>
  );
}
