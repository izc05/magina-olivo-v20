import type { Metadata } from 'next';
import Link from 'next/link';
import moduleRegistry from './admin-modules.json';
import '../admin.css';
import './modules.css';

export const metadata: Metadata = {
  title: 'Módulos de administración · Mágina Olivo',
  description: 'Inventario unificado de superficies administrativas de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

type ModuleStatus = 'available' | 'implemented';
type ModuleArea = 'Plataforma' | 'Territorio' | 'Negocio' | 'Experiencia';

type AdminModule = {
  id: string;
  title: string;
  description: string;
  status: ModuleStatus;
  href?: string;
  sourceBranch?: string;
  targetHref?: string;
  area: ModuleArea;
};

const modules = moduleRegistry as AdminModule[];
const areas: ModuleArea[] = ['Plataforma', 'Territorio', 'Negocio', 'Experiencia'];

export default function AdminModulesPage() {
  const available = modules.filter((module) => module.status === 'available').length;
  const implemented = modules.filter((module) => module.status === 'implemented').length;
  const covered = available + implemented;

  return (
    <main className="admin-modules-shell">
      <header className="admin-modules-hero">
        <div>
          <span className="admin-eyebrow">Mágina Olivo V20 · Gobierno Admin</span>
          <h1>Centro unificado de módulos</h1>
          <p>Los {modules.length} módulos registrados ya tienen superficie administrativa: {available} están absorbidos y disponibles en esta rama y {implemented} ya están implementados en sus ramas funcionales, pendientes únicamente de integración.</p>
        </div>
        <Link className="admin-modules-back" href="/admin">Volver al centro de control</Link>
      </header>

      <section className="admin-modules-summary" aria-label="Resumen de cobertura e integración">
        <article><strong>{modules.length}</strong><span>Módulos registrados</span></article>
        <article><strong>{covered}/{modules.length}</strong><span>Con superficie Admin</span></article>
        <article><strong>{available}</strong><span>Disponibles aquí</span></article>
        <article><strong>{implemented}</strong><span>Implementados en ramas</span></article>
      </section>

      <div className="admin-modules-rule">
        <strong>Regla de integración</strong>
        <p>Un módulo nuevo no se considera cerrado para V20 hasta que declare su superficie administrativa y aparezca en este directorio. Que esté implementado en otra rama demuestra cobertura, pero no habilita su enlace aquí hasta que su código real sea absorbido.</p>
      </div>

      {areas.map((area) => (
        <section className="admin-modules-area" key={area}>
          <div className="admin-modules-area-heading">
            <h2>{area}</h2>
            <span>{modules.filter((module) => module.area === area).length} módulos</span>
          </div>
          <div className="admin-modules-grid">
            {modules.filter((module) => module.area === area).map((module) => {
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
                <Link className="admin-module-card" href={module.href} key={module.id}>{content}</Link>
              ) : (
                <article className="admin-module-card pending" key={module.id}>{content}</article>
              );
            })}
          </div>
        </section>
      ))}
    </main>
  );
}
