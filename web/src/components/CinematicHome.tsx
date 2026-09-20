"use client";

import { useEffect, useState } from "react";
import Image from "next/image";

type ScreenKind =
  | "welcome"
  | "farms"
  | "map"
  | "campaign"
  | "harvest"
  | "weather";

type StoryStep = {
  id: string;
  number: string;
  eyebrow: string;
  title: string;
  copy: string;
  note: string;
  bullets: string[];
  screen: ScreenKind;
};

const storySteps: StoryStep[] = [
  {
    id: "bienvenida",
    number: "01",
    eyebrow: "Empieza tu camino",
    title: "Una app pensada desde el campo.",
    copy:
      "Mágina Olivo nace para que la tecnología acompañe al agricultor sin complicar su forma de trabajar.",
    note: "Más que olivos, nuestra tierra.",
    bullets: ["Clara desde el primer uso", "Información siempre localizada", "Diseñada para acompañar cada campaña"],
    screen: "welcome",
  },
  {
    id: "fincas",
    number: "02",
    eyebrow: "Fincas y parcelas",
    title: "Todo tu olivar, organizado.",
    copy:
      "Agrupa fincas, parcelas y campañas en un solo lugar. Entra desde el móvil y encuentra lo que necesitas sin perder tiempo.",
    note: "Raíces que dan futuro.",
    bullets: ["Vista general de tus fincas", "Parcelas y superficies", "Campañas e histórico"],
    screen: "farms",
  },
  {
    id: "catastro",
    number: "03",
    eyebrow: "Mapa y Catastro",
    title: "Tu terreno, de un vistazo.",
    copy:
      "Importa parcelas, consulta límites y superficies y mantén una visión visual de la explotación directamente sobre el mapa.",
    note: "Conoce cada metro de tu olivar.",
    bullets: ["Importación catastral", "Geometrías y superficies", "Consulta visual en campo"],
    screen: "map",
  },
  {
    id: "campana",
    number: "04",
    eyebrow: "Actividad y campaña",
    title: "Cada labor queda registrada.",
    copy:
      "Anota actuaciones, fechas, fotografías y costes. La campaña deja de depender de la memoria y empieza a construir un histórico útil.",
    note: "El campo cambia. Tu información permanece.",
    bullets: ["Labores y tratamientos", "Fotos y notas", "Seguimiento paso a paso"],
    screen: "campaign",
  },
  {
    id: "cosecha",
    number: "05",
    eyebrow: "Cosecha, gastos y documentos",
    title: "De la recolección al resultado.",
    copy:
      "Controla kilos, entregas, rendimientos, gastos y documentos para saber qué ocurrió y poder comparar cada campaña.",
    note: "Información para decidir mejor.",
    bullets: ["Producción y rendimiento", "Gastos y entregas", "Documentación siempre a mano"],
    screen: "harvest",
  },
  {
    id: "tiempo",
    number: "06",
    eyebrow: "Tiempo, mercado y alertas",
    title: "Anticípate a lo que viene.",
    copy:
      "Consulta previsión, avisos y contexto del mercado para unir la gestión de tu olivar con la información del día a día.",
    note: "Todo listo para empezar.",
    bullets: ["Previsión y radar", "Mercado del aceite", "Alertas útiles"],
    screen: "weather",
  },
];

function Icon({ children }: { children: React.ReactNode }) {
  return <span className="mini-icon" aria-hidden="true">{children}</span>;
}

function PhoneScreen({ kind }: { kind: ScreenKind }) {
  return (
    <div className={`phone-screen phone-screen-${kind}`}>
      <div className="phone-topline">
        <span>9:41</span>
        <span className="phone-status">● ◔ 100%</span>
      </div>

      <div className="phone-brand">
        <span className="phone-leaf" aria-hidden="true" />
        <span><strong>Mágina</strong><strong>Olivo</strong></span>
      </div>

      {kind === "welcome" && (
        <div className="screen-content screen-welcome">
          <p className="screen-kicker">SIERRA MÁGINA · JAÉN</p>
          <h3>Bienvenido a<br />Mágina Olivo</h3>
          <p>Tu app para gestionar tu olivar de forma clara, sencilla y pensada para el agricultor.</p>
          <div className="screen-landscape">
            <span className="mountain mountain-a" />
            <span className="mountain mountain-b" />
            <span className="olive-field" />
          </div>
          <span className="screen-script">Más que olivos,<br />nuestra tierra</span>
          <button tabIndex={-1}>Siguiente <span>→</span></button>
        </div>
      )}

      {kind === "farms" && (
        <div className="screen-content">
          <h3>Tus fincas y parcelas</h3>
          <p>Organiza toda tu explotación en un solo lugar.</p>
          <div className="farm-card primary-card">
            <div className="farm-photo" />
            <div>
              <small>FINCA</small>
              <strong>La Solana</strong>
              <span>Huelma, Jaén</span>
              <em>42,6 ha · 3 parcelas</em>
            </div>
          </div>
          {["Parcela Norte", "Parcela Central", "Parcela Sur"].map((name, index) => (
            <div className="parcel-row" key={name}>
              <Icon>♣</Icon>
              <span><strong>{name}</strong><small>Variedad {index === 2 ? "Hojiblanca" : "Picual"}</small></span>
              <b>{[12.4, 18.6, 11.6][index].toFixed(1).replace(".", ",")} ha</b>
            </div>
          ))}
        </div>
      )}

      {kind === "map" && (
        <div className="screen-content">
          <h3>Mapa y Catastro</h3>
          <p>Importa parcelas y consulta tu terreno de forma visual.</p>
          <div className="map-panel">
            <div className="map-search">⌕ &nbsp; Buscar parcela…</div>
            <span className="map-road r1" />
            <span className="map-road r2" />
            <span className="map-road r3" />
            <div className="map-parcel">
              <span>2,34 ha</span>
            </div>
            <div className="map-controls">◇<br />⌖<br />＋<br />−</div>
          </div>
          <div className="selected-parcel">
            <Icon>⌖</Icon>
            <span><small>Parcela seleccionada</small><strong>2,34 ha</strong><em>Pol. 12 · Parc. 48</em></span>
          </div>
        </div>
      )}

      {kind === "campaign" && (
        <div className="screen-content">
          <h3>Actividad y campaña</h3>
          <p>Registra actuaciones, fotos, costes y fechas.</p>
          <div className="campaign-card">
            <strong>Campaña 2026/27</strong>
            {[
              ["⌁", "Labores de campo", true],
              ["▣", "Fotos", true],
              ["▤", "Costes", false],
              ["□", "Fechas", false],
              ["▥", "Seguimiento", false],
            ].map(([icon, label, done]) => (
              <div className="campaign-row" key={String(label)}>
                <Icon>{icon}</Icon>
                <span>{label}</span>
                <b className={done ? "done" : ""}>{done ? "✓" : ""}</b>
              </div>
            ))}
          </div>
          <div className="calendar-card">
            <strong>NOV 2026</strong>
            <span> L &nbsp; M &nbsp; X &nbsp; J &nbsp; V </span>
            <small>10 &nbsp; 11 &nbsp; 12 &nbsp; 13 &nbsp; <b>14</b></small>
          </div>
        </div>
      )}

      {kind === "harvest" && (
        <div className="screen-content">
          <h3>Cosecha, gastos<br />y documentos</h3>
          <p>Controla producción, rendimientos y toda tu documentación.</p>
          <div className="stat-grid">
            <div><small>Producción total</small><strong>12.450 kg</strong><em>↑ 12%</em></div>
            <div><small>Rendimiento medio</small><strong>18,3%</strong><em>↑ 1,4%</em></div>
            <div><small>Gastos</small><strong>3.120 €</strong><em>↓ 8%</em></div>
            <div><small>Entregas</small><strong>5</strong><em>Ver detalle</em></div>
          </div>
          <div className="documents-card">
            <strong>Mis documentos</strong>
            {["Albarán cooperativa.pdf", "Gastos campaña.xlsx", "Contrato finca.pdf"].map((file) => (
              <div key={file}><Icon>▤</Icon><span>{file}</span><b>⋮</b></div>
            ))}
          </div>
        </div>
      )}

      {kind === "weather" && (
        <div className="screen-content">
          <h3>Tiempo, mercado<br />y alertas</h3>
          <p>Información útil para tomar mejores decisiones.</p>
          <div className="weather-card">
            <div>
              <small>Previsión en tu zona</small>
              <strong>☀ 18°C</strong>
              <span>Huelma · Jaén</span>
            </div>
            <div className="forecast"><span>LU<br />24°</span><span>MA<br />22°</span><span>MI<br />19°</span><span>JU<br />21°</span></div>
          </div>
          <div className="market-card">
            <strong>Mercado del aceite</strong>
            <div><span>AOVE<b>4,32 €/kg</b></span><span>Virgen<b>3,89 €/kg</b></span><span>Lampante<b>3,12 €/kg</b></span></div>
          </div>
          <div className="alerts-card">
            <strong>Alertas útiles</strong>
            <span>☂ Aviso meteorológico</span>
            <span>♧ Riesgo de plaga</span>
            <span>▤ Novedades PAC</span>
          </div>
        </div>
      )}
    </div>
  );
}

export function CinematicHome() {
  const [activeStep, setActiveStep] = useState(0);

  useEffect(() => {
    const nodes = Array.from(
      document.querySelectorAll<HTMLElement>("[data-story-step]"),
    );

    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];

        if (!visible) return;
        const index = Number((visible.target as HTMLElement).dataset.storyStep);
        if (!Number.isNaN(index)) setActiveStep(index);
      },
      { threshold: [0.25, 0.45, 0.62], rootMargin: "-10% 0px -24% 0px" },
    );

    nodes.forEach((node) => observer.observe(node));
    return () => observer.disconnect();
  }, []);

  const current = storySteps[activeStep];

  return (
    <>
      <section className="cinema-hero" id="inicio">
        <div className="hero-scene">
          <Image
            src="/brand/hero-scene.svg"
            alt=""
            fill
            priority
            className="hero-scene-image"
            sizes="100vw"
          />
          <div className="hero-vignette" />
        </div>

        <div className="shell cinema-hero-content">
          <div className="cinema-copy">
            <p className="eyebrow light">Sierra Mágina · Jaén</p>
            <h1>
              Tu olivar
              <span>en buenas manos.</span>
            </h1>
            <p>
              Una forma más clara, sencilla y cercana de organizar tu olivar y
              tomar decisiones con toda la información en la mano.
            </p>
            <div className="hero-actions">
              <a className="button button-primary" href="#historia">Descubrir Mágina Olivo</a>
              <a className="button button-glass" href="#funciones">Ver la app</a>
            </div>
          </div>

          <div className="hero-caption" aria-hidden="true">
            <span>Más que olivos,</span>
            <strong>nuestra tierra</strong>
          </div>
        </div>

        <a className="scroll-cue" href="#historia" aria-label="Continuar bajando">
          <span>DESLIZA PARA DESCUBRIR</span>
          <i>↓</i>
        </a>
      </section>

      <section className="story-intro" id="historia">
        <div className="shell story-intro-grid">
          <div>
            <p className="eyebrow">Una herramienta real para personas reales</p>
            <h2>Del campo<br />a tu mano.</h2>
          </div>
          <div>
            <p>
              Estás entre olivos, revisas una parcela, compruebas una rama,
              recuerdas una labor pendiente. Sacas el móvil. Ahí empieza
              Mágina Olivo.
            </p>
            <p>
              La web seguirá ese mismo recorrido: del paisaje a la persona, de
              la persona al móvil y del móvil a cada herramienta.
            </p>
          </div>
        </div>

        <div className="story-film shell" aria-hidden="true">
          <div className="film-frame film-field">
            <span className="frame-label">01 · EL OLIVAR</span>
          </div>
          <div className="film-frame film-detail">
            <span className="frame-label">02 · OBSERVAR</span>
          </div>
          <div className="film-frame film-phone">
            <span className="frame-label">03 · DECIDIR</span>
          </div>
        </div>
      </section>

      <section className="scrolly" id="funciones">
        <div className="shell scrolly-grid">
          <div className="story-steps">
            {storySteps.map((step, index) => (
              <article
                className={`story-step ${activeStep === index ? "is-active" : ""}`}
                data-story-step={index}
                id={step.id}
                key={step.id}
              >
                <div className="story-step-inner">
                  <p className="story-number">{step.number}</p>
                  <p className="eyebrow">{step.eyebrow}</p>
                  <h2>{step.title}</h2>
                  <p className="story-copy">{step.copy}</p>
                  <ul>
                    {step.bullets.map((bullet) => <li key={bullet}>{bullet}</li>)}
                  </ul>
                  <p className="hand-note">{step.note}</p>
                </div>
              </article>
            ))}
          </div>

          <aside className="phone-stage" aria-live="polite">
            <div className="phone-stage-glow" />
            <div className="phone-orbit phone-orbit-a" />
            <div className="phone-orbit phone-orbit-b" />
            <div className="phone-shell">
              <div className="phone-camera" />
              <PhoneScreen kind={current.screen} />
            </div>
            <div className="stage-meta">
              <span>{current.number} / 06</span>
              <strong>{current.eyebrow}</strong>
            </div>
            <div className="stage-dots" aria-hidden="true">
              {storySteps.map((step, index) => (
                <span className={activeStep === index ? "active" : ""} key={step.id} />
              ))}
            </div>
          </aside>
        </div>
      </section>

      <section className="benefits-section" id="beneficios">
        <div className="shell">
          <div className="section-heading wide">
            <div>
              <p className="eyebrow">Más valor para tu olivar</p>
              <h2>Menos ruido.<br />Más control.</h2>
            </div>
            <p>
              Una herramienta que te ayuda a ahorrar tiempo, conservar el
              histórico y entender mejor cada campaña sin convertir tu trabajo
              en una pantalla llena de formularios.
            </p>
          </div>

          <div className="benefit-grid">
            {[
              ["◷", "Ahorra tiempo", "Toda la información importante en un solo lugar."],
              ["☷", "Organización clara", "Fincas, parcelas y campañas siempre localizadas."],
              ["▥", "Decisiones con datos", "Compara producción, rendimientos y costes."],
              ["▣", "Control de campaña", "Registra lo que ocurre paso a paso."],
              ["◎", "Histórico útil", "Cada campaña construye conocimiento para la siguiente."],
              ["♧", "Pensada para el campo", "Sencilla, legible y preparada para acompañarte."],
            ].map(([icon, title, text]) => (
              <article className="benefit-card" key={title}>
                <span>{icon}</span>
                <h3>{title}</h3>
                <p>{text}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="territory-section" id="territorio">
        <div className="territory-scene" aria-hidden="true">
          <Image src="/brand/hero-scene.svg" alt="" fill sizes="100vw" />
        </div>
        <div className="territory-overlay" />
        <div className="shell territory-content">
          <p className="eyebrow light">Nuestra tierra</p>
          <h2>Sierra Mágina,<br />mucho más que un paisaje.</h2>
          <p>
            El proyecto nace entre olivos, pueblos y campañas reales. Por eso la
            tecnología no sustituye la forma de vivir el campo: la acompaña.
          </p>
          <a className="button button-glass" href="#descarga">Conocer el proyecto</a>
        </div>
      </section>

      <section className="download-section" id="descarga">
        <div className="shell download-grid">
          <div>
            <p className="eyebrow">Mágina Olivo</p>
            <h2>El futuro de tu olivar empieza hoy.</h2>
            <p>
              Estamos construyendo una aplicación Android pensada para gestionar
              el olivar con claridad, incluso cuando estás lejos del escritorio.
            </p>
            <div className="store-row">
              <span className="store-badge">ANDROID<br /><strong>Google Play</strong></span>
              <span className="store-badge muted">PRÓXIMAMENTE</span>
            </div>
          </div>

          <div className="download-phone">
            <div className="phone-shell phone-shell-small">
              <div className="phone-camera" />
              <PhoneScreen kind="welcome" />
            </div>
          </div>
        </div>
      </section>

      <section className="contact-strip" id="contacto">
        <div className="shell contact-strip-inner">
          <div>
            <p className="eyebrow light">Seguimos creciendo</p>
            <h2>Una web y una app con la misma raíz.</h2>
          </div>
          <a className="button button-light" href="mailto:hola@maginaolivo.es">
            Contactar <span aria-hidden="true">→</span>
          </a>
        </div>
      </section>
    </>
  );
}
