import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";

export function LegalPage({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <main>
      <SiteHeader />
      <section className="legal-hero">
        <div className="shell">
          <p className="eyebrow">{eyebrow}</p>
          <h1>{title}</h1>
          <p>Última actualización: 20 de septiembre de 2026</p>
        </div>
      </section>
      <section className="legal-content">
        <div className="shell legal-prose">{children}</div>
      </section>
      <SiteFooter />
    </main>
  );
}
