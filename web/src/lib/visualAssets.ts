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
    finalSrc: "https://images.pexels.com/photos/5035605/pexels-photo-5035605.jpeg?auto=compress&cs=tinysrgb&w=2200",
    fallbackSrc: "/media/v2/hero-photo-clean.webp",
    alt: "Agricultor en un olivar mediterráneo",
    focalPoint: "58% 48%",
    status: "final",
  },
  territoryIntro: {
    key: "territoryIntro",
    finalSrc: "/media/home/territory-intro.webp",
    fallbackSrc: "/brand/hero-scene.svg",
    alt: "Olivar mediterráneo al amanecer",
    focalPoint: "48% 48%",
    status: "final",
  },
  heritage: {
    key: "heritage",
    finalSrc: "https://images.pexels.com/photos/5035605/pexels-photo-5035605.jpeg?auto=compress&cs=tinysrgb&w=2200",
    fallbackSrc: "/media/home/heritage-farmer.webp",
    alt: "Agricultor en un olivar mediterráneo",
    focalPoint: "58% 48%",
    status: "final",
  },
  fieldSequence: {
    key: "fieldSequence",
    finalSrc: "https://images.pexels.com/photos/5035605/pexels-photo-5035605.jpeg?auto=compress&cs=tinysrgb&w=2200",
    fallbackSrc: "/media/home/hero-farmer.webp",
    alt: "Agricultor trabajando entre olivos",
    focalPoint: "58% 48%",
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
    finalSrc: "https://images.pexels.com/photos/31694875/pexels-photo-31694875.jpeg?auto=compress&cs=tinysrgb&w=2000",
    fallbackSrc: "/media/home/benefits-olives.webp",
    alt: "Detalle de manos y aceitunas durante la cosecha",
    focalPoint: "50% 58%",
    status: "final",
  },
  territoryFinal: {
    key: "territoryFinal",
    finalSrc: "/media/home/territory-intro.webp",
    fallbackSrc: "/brand/hero-scene.svg",
    alt: "Panorámica de un olivar mediterráneo",
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
