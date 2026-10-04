import type { Metadata } from "next";

export function publicMetadata(
  title: string,
  description: string,
  canonicalPath: string,
): Metadata {
  return {
    title,
    description,
    alternates: { canonical: canonicalPath },
  };
}
