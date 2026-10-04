import { LegalPage } from "@/features/public/PublicContentPages";
import { publicMetadata } from "@/lib/public-metadata";

export const metadata = publicMetadata(
  "Privacidad",
  "Información provisional sobre la privacidad de Mágina Olivo, pendiente de revisión legal.",
  "/privacidad",
);

export default function PrivacyRoute() {
  return <LegalPage kind="privacidad" />;
}
