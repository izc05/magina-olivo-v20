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
        src: `${publicBase}/brand/app-icon-192.png`,
        sizes: "192x192",
        type: "image/png",
      },
      {
        src: `${publicBase}/brand/app-icon-512.png`,
        sizes: "512x512",
        type: "image/png",
      },
    ],
  };
}
