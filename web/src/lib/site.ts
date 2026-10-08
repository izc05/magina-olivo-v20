export const SITE_URL =
  process.env.SITE_URL?.replace(/\/$/, "") || "https://maginaolivo.es";

export const ALLOW_INDEXING =
  process.env.ALLOW_INDEXING?.toLowerCase() === "true";
