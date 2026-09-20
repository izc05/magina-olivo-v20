import type { ReactNode } from "react";
import { SceneImage } from "@/components/SceneImage";
import { visualAssets } from "@/lib/visualAssets";

type HeroAssetKey = keyof typeof visualAssets;

type PageHeroProps = {
  eyebrow: string;
  title: ReactNode;
  copy: string;
  aside?: string;
  assetKey?: HeroAssetKey;
};

export function PageHero({
  eyebrow,
  title,
  copy,
  aside,
  assetKey = "hero",
}: PageHeroProps) {
  return (
    <section className="page-hero">
      <div className="page-hero-scene" aria-hidden="true">
        <SceneImage asset={visualAssets[assetKey]} priority sizes="100vw" />
      </div>
      <div className="page-hero-overlay" />
      <div className="shell page-hero-inner">
        <div>
          <p className="eyebrow light">{eyebrow}</p>
          <h1>{title}</h1>
          <p>{copy}</p>
        </div>
        {aside && <p className="page-hero-note">{aside}</p>}
      </div>
    </section>
  );
}

type EditorialSectionProps = {
  eyebrow: string;
  title: ReactNode;
  copy: string;
  children?: ReactNode;
  dark?: boolean;
};

export function EditorialSection({
  eyebrow,
  title,
  copy,
  children,
  dark = false,
}: EditorialSectionProps) {
  return (
    <section className={`editorial-section ${dark ? "editorial-dark" : ""}`}>
      <div className="shell editorial-grid">
        <div>
          <p className={`eyebrow ${dark ? "light" : ""}`}>{eyebrow}</p>
          <h2>{title}</h2>
        </div>
        <div className="editorial-copy">
          <p>{copy}</p>
          {children}
        </div>
      </div>
    </section>
  );
}

export function FeatureList({
  items,
}: {
  items: Array<{ number: string; title: string; copy: string }>;
}) {
  return (
    <div className="page-feature-grid">
      {items.map((item) => (
        <article className="page-feature-card" key={item.number}>
          <span>{item.number}</span>
          <h3>{item.title}</h3>
          <p>{item.copy}</p>
        </article>
      ))}
    </div>
  );
}

export function PageCta({
  title,
  copy,
  href = "/#descarga",
  label = "Descubrir la app",
}: {
  title: string;
  copy: string;
  href?: string;
  label?: string;
}) {
  return (
    <section className="page-cta">
      <div className="shell page-cta-inner">
        <div>
          <p className="eyebrow light">Mágina Olivo</p>
          <h2>{title}</h2>
          <p>{copy}</p>
        </div>
        <a className="button button-light" href={href}>
          {label} <span aria-hidden="true">→</span>
        </a>
      </div>
    </section>
  );
}
