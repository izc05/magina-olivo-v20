'use client';

import { useEffect, useState } from 'react';
import { ArrowIcon } from './icons';
import { getPublishedContent } from '../lib/public-content-source';
import type { CmsEntry } from '../lib/admin-data-source';

const iconByType: Record<CmsEntry['type'], string> = {
  page: '📄',
  news: '📰',
  event: '📅',
  place: '📍',
  mill: '🫒',
  directory: '🏪',
  promotion: '✨',
  alert: '⚠️',
};

function ManagedCard({ entry }: { entry: CmsEntry }) {
  const content = (
    <>
      <span className="explore-icon">{iconByType[entry.type]}</span>
      <div>
        <h3>{entry.title}</h3>
        <p>{entry.summary ?? 'Información publicada desde Mágina Olivo.'}</p>
      </div>
      <ArrowIcon />
    </>
  );

  if (entry.external_url) {
    return <a className="card explore-card" href={entry.external_url} target="_blank" rel="noreferrer">{content}</a>;
  }
  return <article className="card explore-card">{content}</article>;
}

export function ManagedExploreContent() {
  const [entries, setEntries] = useState<CmsEntry[]>([]);

  useEffect(() => {
    let active = true;
    void getPublishedContent()
      .then((payload) => {
        if (active) setEntries(payload.entries.slice(0, 6));
      })
      .catch(() => {
        if (active) setEntries([]);
      });
    return () => { active = false; };
  }, []);

  if (!entries.length) return null;

  return (
    <section className="section" aria-labelledby="managed-magina-title">
      <div className="section-head"><h2 id="managed-magina-title">Publicado en Mágina</h2><span>Actualizado desde administración</span></div>
      <div className="explore-grid">
        {entries.map((entry) => <ManagedCard key={entry.id} entry={entry} />)}
      </div>
    </section>
  );
}
