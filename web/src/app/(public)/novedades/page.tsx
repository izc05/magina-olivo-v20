import { NewsPage as PublicNewsPage } from "@/features/public/PublicContentPages";
import { publicMetadata } from "@/lib/public-metadata";

export const metadata = publicMetadata(
  "Novedades",
  "Avisos confirmados sobre cambios y mejoras de Mágina Olivo.",
  "/novedades",
);

export default function NewsRoute() {
  return <PublicNewsPage />;
}
