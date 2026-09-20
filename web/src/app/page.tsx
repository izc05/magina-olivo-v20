import { CinematicHome } from "@/components/CinematicHome";
import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";

export default function HomePage() {
  return (
    <main>
      <SiteHeader />
      <CinematicHome />
      <SiteFooter />
    </main>
  );
}
