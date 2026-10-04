import { LegalPage } from "@/features/public/PublicContentPages";
import { publicMetadata } from "@/lib/public-metadata";

export const metadata = publicMetadata(
  "Términos de uso",
  "Condiciones provisionales de acceso y uso, pendientes de revisión legal.",
  "/terminos",
);

export default function TermsRoute() {
  return <LegalPage kind="terminos" />;
}
