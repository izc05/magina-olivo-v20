const basePath = process.env.GITHUB_PAGES === "true" ? "/magina-olivo-v20" : "";

export function staticAssetPath(path: `/${string}`): string {
  return `${basePath}${path}`;
}
