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
    finalSrc: "https://images.pexels.com/photos/38773372/pexels-photo-38773372.jpeg?auto=compress&cs=tinysrgb&w=2200",
    fallbackSrc: "/media/home/territory-intro.webp",
    alt: "Olivar mediterráneo con luz natural",
    focalPoint: "50% 48%",
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
    finalSrc: "https://images.pexels.com/photos/5035605/pexels-photo-5035605.jpeg?auto=compress&cs=tinysrgb&w=2200",
    fallbackSrc: "/media/home/phone-in-hand.webp",
    alt: "Agricultor en un olivar mediterráneo",
    focalPoint: "58% 48%",
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
    finalSrc: "https://images.pexels.com/photos/38773372/pexels-photo-38773372.jpeg?auto=compress&cs=tinysrgb&w=2200",
    fallbackSrc: "/media/home/territory-intro.webp",
    alt: "Panorámica de un olivar mediterráneo",
    focalPoint: "52% 48%",
    status: "final",
  },
  ctaFinal: {
    key: "ctaFinal",
    finalSrc: "https://images.pexels.com/photos/31694875/pexels-photo-31694875.jpeg?auto=compress&cs=tinysrgb&w=2000",
    fallbackSrc: "/media/home/benefits-olives.webp",
    alt: "Detalle de aceitunas y trabajo de cosecha",
    focalPoint: "50% 58%",
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
