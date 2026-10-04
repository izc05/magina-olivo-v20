import Image from "next/image";
import Link from "next/link";

const features = [
  {
    mark: "MC",
    title: "Mi Campo",
    description: "Fincas y parcelas reunidas en un mismo lugar.",
    href: "/mi/fincas",
    preview: "farm",
  },
  {
    mark: "C",
    title: "Cuaderno",
    description: "Trabajos, riegos, tratamientos y gastos de campaña.",
    href: "/mi/cuaderno",
    preview: "notebook",
  },
  {
    mark: "CA",
    title: "Campaña",
    description: "Una visión clara de la actividad de cada temporada.",
    href: "/mi/campanas",
    preview: "campaign",
  },
  {
    mark: "TR",
    title: "Tiempo y radar",
    description: "Consulta la previsión y la lluvia de tu entorno.",
    href: "/mi/tiempo",
    preview: "radar",
  },
  {
    mark: "MA",
    title: "Mercado del aceite",
    description: "Sigue la referencia del aceite de oliva.",
    href: "/mi/mercado",
    preview: "market",
  },
  {
    mark: "AV",
    title: "Avisos",
    description: "Información útil para estar al día en el campo.",
    href: "/ayuda",
    preview: "notice",
  },
];

const steps = [
  {
    number: "01",
    title: "En tu finca",
    description: "Anota lo que haces cuando sucede.",
    scene: "field",
    image: "step-branch.webp",
  },
  {
    number: "02",
    title: "En la app",
    description: "Consulta tu información organizada.",
    scene: "phone",
    image: "step-phone.webp",
  },
  {
    number: "03",
    title: "Mejores decisiones",
    description: "Mira tu campaña con perspectiva.",
    scene: "hills",
    image: "step-grove.webp",
  },
];

const community = [
  {
    title: "Cooperativas",
    description: "Información y contacto de la zona.",
    image: "territory-cooperative.webp",
  },
  {
    title: "Empresas locales",
    description: "Servicios y suministros para el olivar.",
    image: "territory-oil.webp",
  },
  {
    title: "Noticias del territorio",
    description: "Actualidad y comunicaciones locales.",
    image: "step-grove.webp",
  },
  {
    title: "Eventos",
    description: "Jornadas y actividades del mundo del olivar.",
    image: "territory-community.webp",
  },
];
const mediaPath = (file: string) =>
  `${process.env.GITHUB_PAGES === "true" ? "/magina-olivo-v20" : ""}/images/v3/${file}`;
const heroImage =
  process.env.GITHUB_PAGES === "true"
    ? "/magina-olivo-v20/images/v3/home-hero.webp"
    : "/images/v3/home-hero.webp";

export default function HomePage() {
  return (
    <main className="home-v3">
      <section className="home-hero-v3" aria-labelledby="home-title">
        <Image
          className="home-hero-image"
          src={heroImage}
          alt=""
          fill
          priority
          sizes="100vw"
          aria-hidden="true"
        />
        <div className="home-hero-shade" aria-hidden="true" />
        <div className="home-hero-copy">
          <p className="home-eyebrow">El olivar en tus manos</p>
          <h1 id="home-title">
            Tu olivar,
            <br />
            claro y al día.
          </h1>
          <p className="home-hero-description">
            Gestiona tus fincas, campañas, pesadas, jornales, gastos, el tiempo
            y el mercado del aceite, todo en una sola app.
          </p>
          <div className="home-actions">
            <Link className="home-button home-button-light" href="/descargar">
              <span aria-hidden="true">↓</span> Descargar Android
            </Link>
            <Link
              className="home-button home-button-outline"
              href="/como-funciona"
            >
              <span aria-hidden="true">▶</span> Ver cómo funciona
            </Link>
          </div>
          <ul className="home-promises" aria-label="Mágina Olivo">
            <li>
              <span aria-hidden="true">✦</span> Pensada para el olivar
            </li>
            <li>
              <span aria-hidden="true">⌁</span> Información útil y clara
            </li>
            <li>
              <span aria-hidden="true">◉</span> Cerca de nuestro territorio
            </li>
          </ul>
        </div>
        <p className="home-hero-signature">
          Nuestra tierra,
          <br />
          nuestro aceite
        </p>
        <a className="home-scroll-cue" href="#del-campo">
          Descubre Mágina Olivo <span aria-hidden="true">↓</span>
        </a>
      </section>

      <section
        className="home-section home-story"
        id="del-campo"
        aria-labelledby="story-title"
      >
        <div className="home-section-heading">
          <p className="home-eyebrow">Así de fácil</p>
          <h2 id="story-title">Del campo al móvil</h2>
          <p>
            La tecnología que entiende el olivar. Información a mano, allí donde
            estés.
          </p>
        </div>
        <div className="home-steps">
          {steps.map((step) => (
            <article
              className={`home-step home-step-${step.scene}`}
              key={step.number}
            >
              <div className="home-step-scene">
                <Image
                  className="home-step-photo"
                  src={mediaPath(step.image)}
                  alt=""
                  fill
                  sizes="(max-width: 620px) 40vw, 28vw"
                  aria-hidden="true"
                />
              </div>
              <div className="home-step-copy">
                <span className="home-step-number">{step.number}</span>
                <div>
                  <h3>{step.title}</h3>
                  <p>{step.description}</p>
                </div>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section
        className="home-section home-features"
        id="funcionalidades"
        aria-labelledby="features-title"
      >
        <div className="home-section-heading home-section-heading-wide">
          <div>
            <p className="home-eyebrow">Funcionalidades</p>
            <h2 id="features-title">Todo lo importante en una sola app</h2>
          </div>
          <p>
            Herramientas diseñadas para que saques más valor a tu olivar cada
            día.
          </p>
        </div>
        <div className="home-feature-grid">
          {features.map((feature) => (
            <Link
              className="home-feature-card"
              href={feature.href}
              key={feature.title}
            >
              <span className="home-feature-mark" aria-hidden="true">
                {feature.mark}
              </span>
              <span className="home-feature-copy">
                <strong>{feature.title}</strong>
                <span className="feature-description">
                  {feature.description}
                </span>
              </span>
              <span
                className={`home-feature-preview preview-${feature.preview}`}
                aria-hidden="true"
              >
                <i />
                <i />
                <i />
              </span>
              <span className="home-feature-arrow" aria-hidden="true">
                ↗
              </span>
            </Link>
          ))}
        </div>
      </section>

      <section
        className="home-section home-desktop"
        aria-labelledby="desktop-title"
      >
        <div className="home-desktop-copy">
          <p className="home-eyebrow">Pensada para decidir rápido</p>
          <h2 id="desktop-title">
            Tu explotación,
            <br />
            siempre bajo control
          </h2>
          <p>
            Visualiza tus fincas, consulta el estado de campaña y revisa tu
            información con una visión más amplia.
          </p>
          <Link className="home-button home-button-dark" href="/mi">
            Ver demo web
          </Link>
        </div>
        <section
          className="home-dashboard-preview"
          aria-labelledby="preview-heading"
        >
          <div className="preview-topbar">
            <span className="preview-brand">Mágina Olivo</span>
            <span className="demo-label">Demo visual</span>
          </div>
          <div className="preview-body">
            <div className="preview-sidebar">
              <i />
              <i />
              <i />
              <i />
              <i />
            </div>
            <div className="preview-content">
              <h3 className="preview-kicker" id="preview-heading">
                Resumen de campaña
              </h3>
              <div className="preview-kpis">
                <i />
                <i />
                <i />
              </div>
              <div className="preview-panels">
                <div className="preview-chart">
                  <i />
                  <i />
                  <i />
                  <i />
                  <i />
                </div>
                <div className="preview-map">
                  <i className="map-outline" />
                  <i className="map-outline" />
                </div>
              </div>
            </div>
          </div>
        </section>
      </section>

      <section
        className="home-section home-territory"
        id="territorio"
        aria-labelledby="territory-title"
      >
        <div className="home-section-heading home-section-heading-wide">
          <div>
            <p className="home-eyebrow">Territorio y comunidad</p>
            <h2 id="territory-title">
              Más que una app,
              <br />
              un territorio que avanza
            </h2>
          </div>
          <p>
            Conecta con cooperativas, empresas locales y la actualidad de Sierra
            Mágina. Porque el olivar también es gente, pueblo y futuro.
          </p>
        </div>
        <div className="home-community-grid">
          {community.map((item, index) => (
            <article
              className={`home-community-card home-community-card-${index + 1}`}
              key={item.title}
            >
              <span className="community-art">
                <Image
                  className="community-photo"
                  src={mediaPath(item.image)}
                  alt=""
                  fill
                  sizes="(max-width: 620px) 26vw, 20vw"
                  aria-hidden="true"
                />
              </span>
              <div>
                <h3>{item.title}</h3>
                <p>{item.description}</p>
              </div>
              <span className="home-feature-arrow" aria-hidden="true">
                →
              </span>
            </article>
          ))}
        </div>
      </section>

      <section className="home-final-cta" aria-labelledby="final-cta-title">
        <div className="cta-branch" aria-hidden="true">
          ✳
        </div>
        <div>
          <p className="home-eyebrow">El olivar tiene futuro</p>
          <h2 id="final-cta-title">
            Descarga Mágina Olivo
            <br />y lleva tu olivar más lejos.
          </h2>
          <ul>
            <li>Gratis en Android</li>
            <li>Fácil de usar</li>
            <li>Hecha para nuestro territorio</li>
          </ul>
        </div>
        <div className="home-actions">
          <Link className="home-button home-button-dark" href="/descargar">
            ↓ Descargar Android
          </Link>
          <Link className="home-button home-button-quiet" href="/mi">
            Ver demo web
          </Link>
        </div>
      </section>
    </main>
  );
}
