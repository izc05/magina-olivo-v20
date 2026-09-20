import type { Metadata } from "next";
import { CinematicHome } from "@/components/CinematicHome";
import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";

export const metadata: Metadata = {
  title: "Mágina Olivo",
  description:
    "Tu olivar, tus campañas y la información que importa en una experiencia clara, cercana y conectada a Sierra Mágina.",
  alternates: { canonical: "/" },
};

export default function HomePage() {
  return (
    <main>
      <SiteHeader />
      <CinematicHome />
      <SiteFooter />
    </main>
  );
}
