import { LegalPage } from "@/features/public/PublicContentPages";
import { publicMetadata } from "@/lib/public-metadata";

export const metadata = publicMetadata(
  "Aviso legal",
  "Información provisional de titularidad del sitio, pendiente de revisión legal.",
  "/aviso-legal",
);

export default function LegalNoticeRoute() {
  return <LegalPage kind="aviso" />;
}
