import type { Metadata } from "next";
import { CinematicHomeV2 } from "@/components/CinematicHomeV2";
import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";

export const metadata: Metadata = {
  title: "Mágina Olivo",
  description:
    "Gestiona fincas, parcelas y campañas de olivar con una experiencia sencilla, visual y pensada para el trabajo real.",
  alternates: { canonical: "/" },
};

export default function HomePage() {
  return (
    <>
      <a className="skip-link" href="#contenido-principal">Saltar al contenido</a>
      <SiteHeader />
      <main id="contenido-principal">
        <CinematicHomeV2 />
      </main>
      <SiteFooter />
    </>
  );
}
