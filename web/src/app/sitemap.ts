import type { MetadataRoute } from "next";

export const dynamic = "force-static";

export default function sitemap(): MetadataRoute.Sitemap {
  const siteUrl = process.env.SITE_URL;
  if (
    process.env.ALLOW_INDEXING !== "true" ||
    process.env.GITHUB_PAGES === "true" ||
    !siteUrl
  )
    return [];

  const publicRoutes = [
    "/",
    "/funciones",
    "/como-funciona",
    "/novedades",
    "/ayuda",
    "/privacidad",
    "/terminos",
    "/aviso-legal",
    "/descargar",
    "/anunciate",
  ];

  return publicRoutes.map((route) => ({
    url: new URL(route, siteUrl).toString(),
  }));
}
