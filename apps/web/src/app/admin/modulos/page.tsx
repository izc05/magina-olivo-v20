import type { Metadata } from 'next';
import Link from 'next/link';
import moduleRegistry from './admin-modules.json';
import { AdminModulesDirectory, type AdminModule } from './admin-modules-directory';
import '../admin.css';
import './modules.css';

export const metadata: Metadata = {
  title: 'Módulos de administración · Mágina Olivo',
  description: 'Inventario unificado de superficies administrativas de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

const modules = moduleRegistry as AdminModule[];

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

      <AdminModulesDirectory modules={modules} />
    </main>
  );
}
