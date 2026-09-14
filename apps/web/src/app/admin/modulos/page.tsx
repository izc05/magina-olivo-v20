import type { Metadata } from 'next';
import Link from 'next/link';
import '../admin.css';
import './modules.css';

export const metadata: Metadata = {
  title: 'Módulos de administración · Mágina Olivo',
  description: 'Inventario unificado de superficies administrativas de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

type ModuleStatus = 'available' | 'integration';

type AdminModule = {
  id: string;
  title: string;
  description: string;
  status: ModuleStatus;
  href?: string;
  area: 'Plataforma' | 'Territorio' | 'Negocio' | 'Experiencia';
};

const modules: AdminModule[] = [
  { id: 'operations', title: 'Operaciones', description: 'Salud operativa, actividad y control transversal de plataforma.', status: 'available', href: '/admin/operaciones', area: 'Plataforma' },
  { id: 'analytics', title: 'Analítica', description: 'Histórico, métricas agregadas y lectura de actividad.', status: 'available', href: '/admin/analitica', area: 'Plataforma' },
  { id: 'management', title: 'Gestión', description: 'Workspaces, miembros, fincas y soporte de cuentas.', status: 'available', href: '/admin/gestion', area: 'Plataforma' },
  { id: 'campaigns', title: 'Campañas y planes', description: 'Planes, campañas y gobierno comercial ya integrado.', status: 'available', href: '/admin/campanas-planes', area: 'Plataforma' },
  { id: 'agenda', title: 'Agenda', description: 'Agenda global y coordinación editorial/operativa.', status: 'available', href: '/admin/agenda', area: 'Plataforma' },
  { id: 'jobs', title: 'Trabajos', description: 'Actividad agrícola y trabajos gestionados desde Admin.', status: 'available', href: '/admin/trabajos', area: 'Plataforma' },
  { id: 'documents', title: 'Documentos / OCR', description: 'Soporte documental, cargas y procesos OCR.', status: 'available', href: '/admin/documentos', area: 'Plataforma' },
  { id: 'professional', title: 'Profesional', description: 'Soporte comercial y superficies profesionales.', status: 'available', href: '/admin/profesional', area: 'Negocio' },
  { id: 'sources', title: 'Fuentes', description: 'Estado de fuentes, trazabilidad y calidad de datos.', status: 'available', href: '/admin/fuentes', area: 'Plataforma' },
  { id: 'territory', title: 'Territorio', description: 'Catálogo territorial y directorio canónico.', status: 'available', href: '/admin/territorio', area: 'Territorio' },
  { id: 'media', title: 'Multimedia', description: 'Biblioteca corporativa de imágenes y recursos.', status: 'available', href: '/admin/media', area: 'Plataforma' },
  { id: 'web', title: 'Web / CMS', description: 'Contenido editorial y configuración pública de la web.', status: 'available', href: '/admin/web', area: 'Plataforma' },
  { id: 'municipalities', title: 'Ayuntamientos', description: 'Ficha, contenido, portada, patrimonio, actualidad, cobertura y vista pública por municipio.', status: 'available', href: '/admin/ayuntamientos', area: 'Territorio' },
  { id: 'businesses', title: 'Empresas', description: 'Directorio, rendimiento, reclamaciones, ofertas, leads y gobierno comercial. Rama funcional pendiente de absorción.', status: 'integration', area: 'Negocio' },
  { id: 'experiences', title: 'Experiencias y reservas', description: 'Sesiones, aforo, solicitudes y reservas empresariales. Pendiente de absorción desde su rama funcional.', status: 'integration', area: 'Experiencia' },
  { id: 'magina-pass', title: 'Mágina Pass', description: 'Programas, negocios participantes, QR, puntos y recompensas. Pendiente de absorción.', status: 'integration', area: 'Experiencia' },
  { id: 'routes', title: 'Rutas', description: 'Gestión editorial/técnica de rutas y catálogo senderista. Pendiente de absorción desde Rutas V20.', status: 'integration', area: 'Territorio' },
  { id: 'route-community', title: 'Comunidad de rutas', description: 'Moderación de reseñas, fotos, condiciones y denuncias.', status: 'integration', area: 'Experiencia' },
  { id: 'route-sponsorships', title: 'Patrocinios de rutas', description: 'Campañas, posiciones patrocinadas y rendimiento sin alterar información técnica.', status: 'integration', area: 'Negocio' },
  { id: 'adventure', title: 'Mágina Aventura', description: 'Progreso, retos, colecciones y misiones del modo aventura. Se incorporará cuando cierre su contrato Admin.', status: 'integration', area: 'Experiencia' },
];

const areas: AdminModule['area'][] = ['Plataforma', 'Territorio', 'Negocio', 'Experiencia'];

export default function AdminModulesPage() {
  const available = modules.filter((module) => module.status === 'available').length;
  const integration = modules.length - available;

  return (
    <main className="admin-modules-shell">
      <header className="admin-modules-hero">
        <div>
          <span className="admin-eyebrow">Mágina Olivo V20 · Gobierno Admin</span>
          <h1>Centro unificado de módulos</h1>
          <p>Una sola vista para saber qué puede administrarse hoy y qué módulos siguen todavía en ramas funcionales antes de su absorción.</p>
        </div>
        <Link className="admin-modules-back" href="/admin">Volver al centro de control</Link>
      </header>

      <section className="admin-modules-summary" aria-label="Resumen de integración">
        <article><strong>{modules.length}</strong><span>Módulos registrados</span></article>
        <article><strong>{available}</strong><span>Disponibles en esta rama</span></article>
        <article><strong>{integration}</strong><span>En integración</span></article>
      </section>

      <div className="admin-modules-rule">
        <strong>Regla de integración</strong>
        <p>Un módulo nuevo no se considera cerrado para V20 hasta que declare su superficie administrativa y aparezca en este directorio. Los módulos no absorbidos no generan enlaces rotos.</p>
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
                      {module.status === 'available' ? 'Disponible' : 'En integración'}
                    </span>
                  </div>
                  <p>{module.description}</p>
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
