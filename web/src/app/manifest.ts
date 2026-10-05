import type { MetadataRoute } from "next";

const publicBase =
  process.env.GITHUB_PAGES === "true" ? "/magina-olivo-v20" : "";

export const dynamic = "force-static";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Mágina Olivo",
    short_name: "Mágina Olivo",
    description: "Gestión clara del olivar.",
    start_url: `${publicBase}/`,
    scope: `${publicBase}/`,
    display: "standalone",
    background_color: "#f6f3e9",
    theme_color: "#263b2b",
    icons: [
      {
        src: `${publicBase}/brand/app-icon.png`,
        sizes: "341x339",
        type: "image/png",
      },
    ],
  };
}
