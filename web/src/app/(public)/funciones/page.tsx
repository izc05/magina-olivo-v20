import { FunctionsPage as PublicFunctionsPage } from "@/features/public/PublicContentPages";
import { publicMetadata } from "@/lib/public-metadata";

export const metadata = publicMetadata(
  "Funciones",
  "Conoce cómo Mágina Olivo organiza fincas, Cuaderno, campañas e información útil.",
  "/funciones",
);

export default function FunctionsRoute() {
  return <PublicFunctionsPage />;
}
