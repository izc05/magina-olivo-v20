import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  const basePath = process.env.NEXT_PUBLIC_BASE_PATH || "";

  return {
    name: "Mágina Olivo",
    short_name: "Mágina Olivo",
    description:
      "Gestión del olivar con una experiencia clara, sencilla y conectada al territorio.",
    start_url: basePath ? `${basePath}/` : "/",
    scope: basePath ? `${basePath}/` : "/",
    display: "standalone",
    background_color: "#f3efe3",
    theme_color: "#324634",
    lang: "es",
  };
}
