import type { Metadata } from "next";
import { PageHero } from "@/components/MarketingPage";
import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";

export const metadata: Metadata = {
  title: "Contacto",
  description: "Contacta con el proyecto Mágina Olivo.",
  alternates: { canonical: "/contacto" },
};

export default function ContactoPage() {
  return (
    <>
      <a className="skip-link" href="#contenido-principal">Saltar al contenido</a>
      <SiteHeader />
      <main id="contenido-principal">
      <PageHero
        eyebrow="Contacto"
        assetKey="heritage"
        title={<>Hablemos de un olivar <em>mejor organizado.</em></>}
        copy="El proyecto está creciendo. Las dudas, ideas y experiencias reales del campo ayudan a construir una herramienta más útil."
        aside="Pensado desde el campo."
      />

      <section className="contact-page">
        <div className="shell contact-page-grid">
          <div className="contact-copy">
            <p className="eyebrow">Mágina Olivo</p>
            <h2>Cuéntanos qué necesitas.</h2>
            <p>
              Para consultas sobre el proyecto, colaboración o futuras pruebas
              de la aplicación, puedes escribirnos directamente.
            </p>
            <a className="contact-mail" href="mailto:hola@maginaolivo.es">
              hola@maginaolivo.es <span aria-hidden="true">→</span>
            </a>
            <div className="contact-facts">
              <span><strong>Origen</strong>Jaén · pensado para cualquier olivar</span>
              <span><strong>Producto</strong>Android · en desarrollo</span>
              <span><strong>Web</strong>Experiencia pública del proyecto</span>
            </div>
          </div>

          <div className="contact-card">
            <p className="eyebrow">Antes de escribir</p>
            <h3>¿Qué tipo de mensaje quieres enviar?</h3>
            <a href="mailto:hola@maginaolivo.es?subject=Mágina%20Olivo%20-%20Quiero%20probar%20la%20app">
              <span>01</span><strong>Quiero probar la app</strong><b>→</b>
            </a>
            <a href="mailto:hola@maginaolivo.es?subject=Mágina%20Olivo%20-%20Tengo%20una%20idea">
              <span>02</span><strong>Tengo una idea o propuesta</strong><b>→</b>
            </a>
            <a href="mailto:hola@maginaolivo.es?subject=Mágina%20Olivo%20-%20Colaboración">
              <span>03</span><strong>Colaboración</strong><b>→</b>
            </a>
            <a href="mailto:hola@maginaolivo.es?subject=Mágina%20Olivo%20-%20Consulta">
              <span>04</span><strong>Otra consulta</strong><b>→</b>
            </a>
          </div>
        </div>
      </section>

      </main>
      <SiteFooter />
    </>
  );
}
