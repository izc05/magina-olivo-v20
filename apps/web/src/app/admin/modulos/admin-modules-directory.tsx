'use client';

import Link from 'next/link';
import { useMemo, useState } from 'react';

export type ModuleStatus = 'available' | 'implemented';
export type ModuleArea = 'Plataforma' | 'Territorio' | 'Negocio' | 'Experiencia';

export type AdminModule = {
  id: string;
  title: string;
  description: string;
  status: ModuleStatus;
  href?: string;
  sourceBranch?: string;
  targetHref?: string;
  area: ModuleArea;
};

const areas: ModuleArea[] = ['Plataforma', 'Territorio', 'Negocio', 'Experiencia'];

function searchable(value: string | undefined) {
  return (value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('es');
}

function ModuleCard({ module }: { module: AdminModule }) {
  const content = (
    <>
      <div className="admin-module-card-heading">
        <h3>{module.title}</h3>
        <span className={`admin-module-status ${module.status}`}>
          {module.status === 'available' ? 'Disponible aquí' : 'Implementado en rama'}
        </span>
      </div>
      <p>{module.description}</p>
      {module.status === 'implemented' ? (
        <div className="admin-module-meta" aria-label={`Origen de ${module.title}`}>
          <span>Rama <code>{module.sourceBranch}</code></span>
          <span>Ruta prevista <code>{module.targetHref}</code></span>
        </div>
      ) : null}
      <small>{module.status === 'available' ? 'Abrir módulo →' : 'Sin enlace hasta su absorción'}</small>
    </>
  );

  return module.href ? (
    <Link className="admin-module-card" href={module.href}>{content}</Link>
  ) : (
    <article className="admin-module-card pending">{content}</article>
  );
}

export function AdminModulesDirectory({ modules }: { modules: AdminModule[] }) {
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<'all' | ModuleStatus>('all');
  const [area, setArea] = useState<'all' | ModuleArea>('all');

  const filtered = useMemo(() => {
    const normalizedQuery = searchable(query.trim());
    return modules.filter((module) => {
      if (status !== 'all' && module.status !== status) return false;
      if (area !== 'all' && module.area !== area) return false;
      if (!normalizedQuery) return true;
      const haystack = searchable([
        module.id,
        module.title,
        module.description,
        module.area,
        module.sourceBranch,
        module.targetHref,
      ].filter(Boolean).join(' '));
      return haystack.includes(normalizedQuery);
    });
  }, [area, modules, query, status]);

  const hasFilters = Boolean(query.trim()) || status !== 'all' || area !== 'all';

  function resetFilters() {
    setQuery('');
    setStatus('all');
    setArea('all');
  }

  return (
    <>
      <section className="admin-modules-filters" aria-labelledby="admin-modules-filter-title">
        <div className="admin-modules-filter-heading">
          <div>
            <h2 id="admin-modules-filter-title">Encontrar un módulo</h2>
            <p>Busca por nombre, función, rama o ruta y filtra por disponibilidad o área.</p>
          </div>
          <span className="admin-modules-result-count" aria-live="polite">
            {filtered.length} de {modules.length}
          </span>
        </div>
        <div className="admin-modules-filter-grid">
          <label className="admin-modules-search">
            <span>Buscar</span>
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Ej. empresas, rutas, documentos…"
              autoComplete="off"
            />
          </label>
          <label>
            <span>Estado</span>
            <select value={status} onChange={(event) => setStatus(event.target.value as 'all' | ModuleStatus)}>
              <option value="all">Todos</option>
              <option value="available">Disponible aquí</option>
              <option value="implemented">Implementado en rama</option>
            </select>
          </label>
          <label>
            <span>Área</span>
            <select value={area} onChange={(event) => setArea(event.target.value as 'all' | ModuleArea)}>
              <option value="all">Todas</option>
              {areas.map((candidate) => <option value={candidate} key={candidate}>{candidate}</option>)}
            </select>
          </label>
          <button className="admin-modules-reset" type="button" disabled={!hasFilters} onClick={resetFilters}>
            Limpiar filtros
          </button>
        </div>
      </section>

      {filtered.length === 0 ? (
        <section className="admin-modules-empty" role="status">
          <h2>No hay módulos que coincidan</h2>
          <p>Prueba otro término o elimina alguno de los filtros.</p>
          <button type="button" onClick={resetFilters}>Mostrar todos</button>
        </section>
      ) : null}

      {areas.map((candidateArea) => {
        const areaModules = filtered.filter((module) => module.area === candidateArea);
        if (!areaModules.length) return null;
        return (
          <section className="admin-modules-area" key={candidateArea}>
            <div className="admin-modules-area-heading">
              <h2>{candidateArea}</h2>
              <span>{areaModules.length} {areaModules.length === 1 ? 'módulo' : 'módulos'}</span>
            </div>
            <div className="admin-modules-grid">
              {areaModules.map((module) => <ModuleCard module={module} key={module.id} />)}
            </div>
          </section>
        );
      })}
    </>
  );
}
