import { HelpPage as PublicHelpPage } from "@/features/public/PublicContentPages";
import { publicMetadata } from "@/lib/public-metadata";

export const metadata = publicMetadata(
  "Ayuda",
  "Orientación por tareas para usar Mágina Olivo y mantener tus registros claros.",
  "/ayuda",
);

export default function HelpRoute() {
  return <PublicHelpPage />;
}
