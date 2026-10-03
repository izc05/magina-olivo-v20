import type { Metadata } from "next";
import { EditorialSection, PageCta, PageHero } from "@/components/MarketingPage";
import { SceneImage } from "@/components/SceneImage";
import { visualAssets } from "@/lib/visualAssets";
import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";

export const metadata: Metadata = {
  title: "Nuestra tierra",
  description: "Sierra Mágina, olivar y territorio: el contexto del que nace Mágina Olivo.",
  alternates: { canonical: "/territorio" },
};

export default function TerritorioPage() {
  return (
    <>
      <a className="skip-link" href="#contenido-principal">Saltar al contenido</a>
      <SiteHeader />
      <main id="contenido-principal">
      <PageHero
        eyebrow="Nuestra tierra"
        assetKey="territoryIntro"
        title={<>Sierra Mágina. Una forma de <em>vivir el olivar.</em></>}
        copy="Montaña, pueblos, olivares y campañas forman un paisaje que también es cultura, trabajo y conocimiento."
        aside="Más que olivos, nuestra tierra."
      />

      <EditorialSection
        eyebrow="Territorio"
        title={<>Una herramienta<br />con raíces.</>}
        copy="Mágina Olivo nace mirando al olivar de Jaén y a la realidad de quien trabaja con él. Esa cercanía marca la forma de diseñar la experiencia: lenguaje claro, utilidad inmediata y respeto por el conocimiento acumulado durante generaciones."
      />

      <section className="territory-gallery">
        <div className="shell territory-gallery-grid">
          <article className="territory-tile territory-tile-large">
            <div className="territory-tile-media" aria-hidden="true">
              <SceneImage asset={visualAssets.territoryIntro} sizes="(max-width: 900px) 100vw, 62vw" />
            </div>
            <div className="territory-tile-shade" />
            <span>01</span>
            <h3>Montaña y olivar</h3>
            <p>Un paisaje que condiciona accesos, clima, ritmos de trabajo y forma de gestionar.</p>
          </article>
          <article className="territory-tile">
            <div className="territory-tile-media" aria-hidden="true">
              <SceneImage asset={visualAssets.heritage} sizes="(max-width: 900px) 100vw, 32vw" />
            </div>
            <div className="territory-tile-shade" />
            <span>02</span>
            <h3>Pueblos y personas</h3>
            <p>El olivar también es economía local, cooperativas, familias y comunidad.</p>
          </article>
          <article className="territory-tile">
            <div className="territory-tile-media" aria-hidden="true">
              <SceneImage asset={visualAssets.benefits} sizes="(max-width: 900px) 100vw, 32vw" />
            </div>
            <div className="territory-tile-shade" />
            <span>03</span>
            <h3>Campañas</h3>
            <p>Cada año genera datos, experiencia y decisiones que merece la pena conservar.</p>
          </article>
        </div>
      </section>

      <EditorialSection
        eyebrow="Tecnología que acompaña"
        title={<>Innovar sin perder<br />la forma de hacer campo.</>}
        copy="La app debe adaptarse a la realidad del agricultor, y no al revés. Por eso el diseño prioriza movilidad, lectura rápida, funcionamiento sin conexión y una estructura que pueda crecer con nuevas necesidades."
        dark
      />

      <PageCta
        title="La misma tierra. Nuevas herramientas."
        copy="Mágina Olivo une el conocimiento de siempre con una forma más clara de organizarlo."
      />
      </main>
      <SiteFooter />
    </>
  );
}
