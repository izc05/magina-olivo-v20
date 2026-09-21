import type { Metadata } from "next";
import { EditorialSection, FeatureList, PageCta, PageHero } from "@/components/MarketingPage";
import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";

export const metadata: Metadata = {
  title: "Producto",
  description: "Conoce cómo Mágina Olivo organiza fincas, parcelas, campañas, cosecha, gastos y datos útiles del olivar.",
  alternates: { canonical: "/producto" },
};

const features = [
  { number: "01", title: "Fincas y parcelas", copy: "Organiza tu explotación con una estructura clara: finca, parcela y campaña." },
  { number: "02", title: "Mapa y Catastro", copy: "Incorpora parcelas y conserva una representación visual útil del terreno." },
  { number: "03", title: "Actividad y campaña", copy: "Registra labores, fechas, fotografías, observaciones y seguimiento." },
  { number: "04", title: "Cosecha y entregas", copy: "Anota kilos, entregas, rendimientos y consulta el histórico de campaña." },
  { number: "05", title: "Gastos y documentos", copy: "Relaciona costes y documentación con la finca, parcela o campaña correspondiente." },
  { number: "06", title: "Tiempo y alertas", copy: "Añade contexto meteorológico y avisos útiles a las decisiones del día a día." },
];

export default function ProductoPage() {
  return (
    <>
      <a className="skip-link" href="#contenido-principal">Saltar al contenido</a>
      <SiteHeader />
      <main id="contenido-principal">
      <PageHero
        eyebrow="Producto"
        assetKey="phoneContext"
        title={<>Todo lo que tu olivar necesita, <em>en una sola experiencia.</em></>}
        copy="Mágina Olivo reúne las tareas que hoy viven repartidas entre cuadernos, fotos, mensajes y memoria."
        aside="Sencillo por fuera. Estructurado por dentro."
      />

      <EditorialSection
        eyebrow="Una herramienta de trabajo"
        title={<>Del primer dato<br />al histórico.</>}
        copy="La app se construye alrededor de una idea sencilla: que cualquier dato que registres hoy tenga sentido mañana. La información se organiza por finca, parcela y campaña para que consultar el pasado sea tan fácil como registrar el presente."
      />

      <section className="page-section">
        <div className="shell">
          <FeatureList items={features} />
        </div>
      </section>

      <EditorialSection
        eyebrow="Preparada para el campo"
        title={<>Útil incluso cuando<br />la cobertura falla.</>}
        copy="El núcleo Android está concebido con filosofía offline-first. El trabajo diario no debe depender de tener conexión permanente; cuando vuelva la red, la aplicación podrá sincronizar los cambios de forma controlada."
        dark
      />

      <PageCta
        title="Conoce Mágina Olivo desde el principio."
        copy="Vuelve a la experiencia cinematográfica y recorre cada función tal como la verá el agricultor."
        href="/#producto"
        label="Ver la experiencia"
      />
      </main>
      <SiteFooter />
    </>
  );
}
