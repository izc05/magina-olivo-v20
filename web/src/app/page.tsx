const features = [
  {
    number: "01",
    title: "Mi Campo",
    copy: "Fincas, parcelas, campañas, actuaciones, cosecha y gastos con una lectura rápida y humana.",
  },
  {
    number: "02",
    title: "Tiempo y avisos",
    copy: "Información meteorológica y alertas pensadas para tomar decisiones útiles en el olivar.",
  },
  {
    number: "03",
    title: "Mercado del aceite",
    copy: "Una visión clara de precios, tendencias y contexto sin convertir los datos en ruido.",
  },
];

export default function HomePage() {
  return (
    <main>
      <header className="site-header">
        <div className="shell header-inner">
          <a className="brand" href="#inicio" aria-label="Mágina Olivo, inicio">
            <span className="brand-mark" aria-hidden="true" />
            <span>Mágina Olivo</span>
          </a>

          <nav className="nav" aria-label="Navegación principal">
            <a href="#campo">Mi Campo</a>
            <a href="#territorio">Territorio</a>
            <a href="#proyecto">Proyecto</a>
            <a className="nav-cta" href="#campo">
              Conocer la app
            </a>
          </nav>
        </div>
      </header>

      <section className="hero" id="inicio">
        <div className="shell hero-grid">
          <div>
            <p className="eyebrow">El olivar, bien organizado</p>
            <h1>
              Tu campo.
              <span>Con otra mirada.</span>
            </h1>
            <p className="hero-copy">
              Mágina Olivo reúne la gestión diaria de tu olivar y la información
              que realmente importa en una experiencia sencilla, visual y
              conectada al territorio.
            </p>

            <div className="hero-actions">
              <a className="button button-primary" href="#campo">
                Descubrir Mágina Olivo
              </a>
              <a className="button button-secondary" href="#proyecto">
                Ver el proyecto
              </a>
            </div>
          </div>

          <div className="hero-visual" aria-label="Vista conceptual del paisaje de olivar">
            <div className="visual-card">
              <small>Campaña actual</small>
              <strong>Todo tu olivar, en contexto.</strong>
            </div>
          </div>
        </div>
      </section>

      <div className="shell">
        <section className="metrics" aria-label="Principios del producto">
          <div className="metric">
            <strong>1 lugar</strong>
            <span>para organizar finca, parcela y campaña.</span>
          </div>
          <div className="metric">
            <strong>Offline</strong>
            <span>pensado para seguir siendo útil en el campo.</span>
          </div>
          <div className="metric">
            <strong>Claro</strong>
            <span>sin convertir la gestión agrícola en burocracia.</span>
          </div>
        </section>
      </div>

      <section className="section" id="campo">
        <div className="shell">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Una sola experiencia</p>
              <h2>Del día a día al histórico.</h2>
            </div>
            <p>
              La web presenta el ecosistema Mágina Olivo y prepara una superficie
              pública útil. La gestión agrícola privada sigue viviendo en el
              producto Android, respetando su baseline RC1.2.
            </p>
          </div>

          <div className="feature-grid">
            {features.map((feature) => (
              <article className="feature-card" key={feature.number}>
                <span className="feature-number">{feature.number}</span>
                <h3>{feature.title}</h3>
                <p>{feature.copy}</p>
              </article>
            ))}
          </div>

          <section className="territory" id="territorio">
            <div className="territory-grid">
              <h2>Hecho desde el olivar. Pensado para durar.</h2>
              <p>
                Mágina Olivo nace con identidad propia: tonos tierra y olivo,
                fotografía de territorio, tipografía editorial y una interfaz
                operativa muy limpia. La web seguirá exactamente esa misma línea
                visual para que app y sitio se sientan como un único producto.
              </p>
            </div>
          </section>
        </div>
      </section>

      <section className="section" id="proyecto">
        <div className="shell">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Web · Fase 0</p>
              <h2>Una base preparada para crecer.</h2>
            </div>
            <p>
              A partir de esta portada incorporaremos navegación completa,
              contenido real, mercado, tiempo, cooperativas, noticias, SEO,
              analítica, rendimiento y despliegue, siempre por fases y sin
              mezclar el código web con el núcleo Android.
            </p>
          </div>
        </div>
      </section>

      <footer className="site-footer">
        <div className="shell footer-inner">
          <a className="brand" href="#inicio">
            <span className="brand-mark" aria-hidden="true" />
            <span>Mágina Olivo</span>
          </a>
          <span>Proyecto en construcción · Sierra Mágina, Jaén</span>
        </div>
      </footer>
    </main>
  );
}
