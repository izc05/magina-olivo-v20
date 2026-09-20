export type VisualAssetKey =
  | "hero"
  | "territoryIntro"
  | "heritage"
  | "fieldSequence"
  | "phoneContext"
  | "benefits"
  | "territoryFinal"
  | "ctaFinal";

export type VisualAsset = {
  key: VisualAssetKey;
  finalSrc: string;
  fallbackSrc: string;
  alt: string;
  focalPoint: string;
  status: "placeholder" | "final";
};

export const visualAssets: Record<VisualAssetKey, VisualAsset> = {
  hero: {
    key: "hero",
    finalSrc: "/media/home/hero-farmer.webp",
    fallbackSrc: "/brand/hero-scene.svg",
    alt: "Agricultor consultando Mágina Olivo entre olivos de Sierra Mágina",
    focalPoint: "68% 45%",
    status: "final",
  },
  territoryIntro: {
    key: "territoryIntro",
    finalSrc: "/media/home/territory-intro.webp",
    fallbackSrc: "/brand/hero-scene.svg",
    alt: "Olivar y paisaje de Sierra Mágina",
    focalPoint: "48% 48%",
    status: "final",
  },
  heritage: {
    key: "heritage",
    finalSrc: "/media/home/heritage-farmer.webp",
    fallbackSrc: "/brand/hero-scene.svg",
    alt: "Agricultor caminando entre olivos",
    focalPoint: "36% 52%",
    status: "final",
  },
  fieldSequence: {
    key: "fieldSequence",
    finalSrc: "/media/home/hero-farmer.webp",
    fallbackSrc: "/brand/hero-scene.svg",
    alt: "Agricultor trabajando en el olivar antes de consultar el móvil",
    focalPoint: "70% 50%",
    status: "final",
  },
  phoneContext: {
    key: "phoneContext",
    finalSrc: "/media/home/phone-in-hand.webp",
    fallbackSrc: "/brand/hero-scene.svg",
    alt: "Mágina Olivo en la mano del agricultor",
    focalPoint: "50% 50%",
    status: "final",
  },
  benefits: {
    key: "benefits",
    finalSrc: "/media/home/benefits-olives.webp",
    fallbackSrc: "/brand/hero-scene.svg",
    alt: "Rama de olivo con aceitunas",
    focalPoint: "70% 45%",
    status: "final",
  },
  territoryFinal: {
    key: "territoryFinal",
    finalSrc: "/media/home/territory-intro.webp",
    fallbackSrc: "/brand/hero-scene.svg",
    alt: "Panorámica de Sierra Mágina y sus olivares",
    focalPoint: "56% 48%",
    status: "final",
  },
  ctaFinal: {
    key: "ctaFinal",
    finalSrc: "/media/home/benefits-olives.webp",
    fallbackSrc: "/brand/hero-scene.svg",
    alt: "Ramas de olivo en el cierre de Mágina Olivo",
    focalPoint: "70% 45%",
    status: "final",
  },
};

export const visualAssetOrder: VisualAssetKey[] = [
  "hero",
  "territoryIntro",
  "heritage",
  "fieldSequence",
  "phoneContext",
  "benefits",
  "territoryFinal",
  "ctaFinal",
];
