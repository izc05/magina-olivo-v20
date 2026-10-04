import { DownloadPage as PublicDownloadPage } from "@/features/public/PublicContentPages";
import { publicMetadata } from "@/lib/public-metadata";

export const metadata = publicMetadata(
  "Descargar Android",
  "Consulta el estado del enlace oficial, la versión y los requisitos de Mágina Olivo.",
  "/descargar",
);

export default function DownloadRoute() {
  return <PublicDownloadPage />;
}
