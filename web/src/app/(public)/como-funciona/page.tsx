import { HowItWorksPage as PublicHowItWorksPage } from "@/features/public/PublicContentPages";
import { publicMetadata } from "@/lib/public-metadata";

export const metadata = publicMetadata(
  "Cómo funciona",
  "Un recorrido por fincas, parcelas, campañas, registros y resúmenes de Mágina Olivo.",
  "/como-funciona",
);

export default function HowItWorksRoute() {
  return <PublicHowItWorksPage />;
}
