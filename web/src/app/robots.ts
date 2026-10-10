import type { MetadataRoute } from "next";

export const dynamic = "force-static";

const siteUrl = process.env.SITE_URL;
const indexable =
  process.env.ALLOW_INDEXING === "true" &&
  process.env.GITHUB_PAGES !== "true" &&
  Boolean(siteUrl);

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: indexable ? "/" : undefined,
      disallow: indexable ? undefined : "/",
    },
    sitemap:
      indexable && siteUrl
        ? new URL("/sitemap.xml", siteUrl).toString()
        : undefined,
  };
}
