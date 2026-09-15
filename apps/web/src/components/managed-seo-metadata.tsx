'use client';

import { useEffect } from 'react';
import { getPublicSiteSettings } from '../lib/public-content-source';

type SeoSetting = {
  title?: string;
  description?: string;
  og_image?: string;
  robots_index?: boolean;
};

function ensureMeta(selector: string, attributes: Record<string, string>) {
  let node = document.head.querySelector<HTMLMetaElement>(selector);
  if (!node) {
    node = document.createElement('meta');
    document.head.appendChild(node);
  }
  for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, value);
}

export function ManagedSeoMetadata() {
  useEffect(() => {
    let active = true;
    void getPublicSiteSettings().then(({ settings }) => {
      if (!active) return;
      const value = settings['site.seo'];
      if (!value || typeof value !== 'object' || Array.isArray(value)) return;
      const seo = value as SeoSetting;
      if (seo.title?.trim()) {
        document.title = seo.title.trim();
        ensureMeta('meta[property="og:title"]', { property: 'og:title', content: seo.title.trim() });
      }
      if (seo.description?.trim()) {
        ensureMeta('meta[name="description"]', { name: 'description', content: seo.description.trim() });
        ensureMeta('meta[property="og:description"]', { property: 'og:description', content: seo.description.trim() });
      }
      if (seo.og_image?.trim()) ensureMeta('meta[property="og:image"]', { property: 'og:image', content: seo.og_image.trim() });
      ensureMeta('meta[name="robots"]', { name: 'robots', content: seo.robots_index === false ? 'noindex,nofollow' : 'index,follow' });
    }).catch(() => undefined);
    return () => { active = false; };
  }, []);

  return null;
}
