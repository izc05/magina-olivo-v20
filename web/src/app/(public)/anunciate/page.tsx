import { AdvertisePage as PublicAdvertisePage } from "@/features/public/PublicContentPages";
import { publicMetadata } from "@/lib/public-metadata";

export const metadata = publicMetadata(
  "Anúnciate",
  "Información provisional sobre espacios locales para empresas vinculadas al olivar.",
  "/anunciate",
);

export default function AdvertiseRoute() {
  return <PublicAdvertisePage />;
}
