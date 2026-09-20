import type { Metadata } from "next";
import { EditorialSection, FeatureList, PageCta, PageHero } from "@/components/MarketingPage";
import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";

export const metadata: Metadata = {
  title: "Beneficios",
  description: "Descubre los beneficios de organizar el olivar con Mágina Olivo.",
};

const benefits = [
  { number: "01", title: "Menos tiempo buscando", copy: "La información queda agrupada donde corresponde, sin depender de chats, carpetas o recuerdos." },
  { number: "02", title: "Más claridad", copy: "Cada finca, parcela y campaña mantiene su propio contexto y su histórico." },
  { number: "03", title: "Decisiones con datos", copy: "Producción, rendimiento, gastos y actuaciones se pueden revisar juntos." },
  { number: "04", title: "Continuidad entre campañas", copy: "Lo registrado este año ayuda a entender el siguiente y evita empezar de cero." },
  { number: "05", title: "Trabajo de campo primero", copy: "La interfaz se diseña para registrar rápido, con pocos pasos y sin burocracia innecesaria." },
  { number: "06", title: "Una base que puede crecer", copy: "El núcleo está preparado para incorporar nuevas herramientas sin rehacer la gestión básica." },
];

export default function BeneficiosPage() {
  return (
    <main>
      <SiteHeader />
      <PageHero
        eyebrow="Beneficios"
        title={<>Más control, menos ruido, <em>mejores campañas.</em></>}
        copy="La tecnología aporta valor cuando desaparece del camino y deja más tiempo para entender el olivar."
        aside="Un campo mejor organizado se entiende mejor."
      />

      <EditorialSection
        eyebrow="Para el día a día"
        title={<>Que la información<br />trabaje contigo.</>}
        copy="Mágina Olivo no busca convertir al agricultor en administrativo. Busca reducir fricción: registrar lo necesario en el momento adecuado y recuperar la información cuando realmente hace falta."
      />

      <section className="page-section">
        <div className="shell">
          <FeatureList items={benefits} />
        </div>
      </section>

      <EditorialSection
        eyebrow="De campaña en campaña"
        title={<>El histórico es<br />parte del producto.</>}
        copy="Kilos, fechas, entregas, rendimientos, gastos y actuaciones ganan valor cuando pueden compararse. La experiencia está pensada para que una campaña no borre la anterior, sino que se apoye en ella."
        dark
      />

      <PageCta
        title="Un olivar más claro empieza por una buena base."
        copy="Descubre cómo se organizan las funciones dentro de Mágina Olivo."
        href="/producto"
        label="Ver el producto"
      />
      <SiteFooter />
    </main>
  );
}
