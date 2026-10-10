import type { NextConfig } from "next";

const isGitHubPages = process.env.GITHUB_PAGES === "true";

const nextConfig: NextConfig = {
  output: isGitHubPages ? "export" : "standalone",
  basePath: isGitHubPages ? "/magina-olivo-v20" : undefined,
  trailingSlash: isGitHubPages,
  images: { unoptimized: true },
  poweredByHeader: false,
};

export default nextConfig;
