import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

export const dynamic = "force-static";


export default function sitemap(): MetadataRoute.Sitemap {
  const base = SITE_URL;
  const primary = ["", "/producto", "/beneficios", "/territorio", "/contacto"];
  const legal = ["/privacidad", "/terminos", "/aviso-legal"];

  return [
    ...primary.map((route) => ({
      url: `${base}${route}`,
      lastModified: new Date(),
      changeFrequency: route === "" ? ("weekly" as const) : ("monthly" as const),
      priority: route === "" ? 1 : 0.8,
    })),
    ...legal.map((route) => ({
      url: `${base}${route}`,
      lastModified: new Date(),
      changeFrequency: "yearly" as const,
      priority: 0.2,
    })),
  ];
}
