export type KeyframeId =
  | "K01" | "K02" | "K03" | "K04" | "K05" | "K06"
  | "K07" | "K08" | "K09" | "K10" | "K11" | "K12"
  | "K13" | "K14" | "K15" | "K16" | "K17" | "K18"
  | "K19" | "K20" | "K21" | "K22" | "K23" | "K24";

export type KeyframeStatus = "pilot" | "candidate" | "final";

export type KeyframeAsset = {
  src: string;
  status: KeyframeStatus;
  focalX: number;
  focalY: number;
};

export type KeyframeSpec = {
  id: KeyframeId;
  act: "field" | "phone" | "product" | "return";
  title: string;
  copy?: string;
  desktop: KeyframeAsset;
  mobile: KeyframeAsset;
  continuity: Array<"farmer" | "wardrobe" | "light" | "phone" | "hands" | "ui">;
};

const pilot = (
  src: string,
  focalX = 0.5,
  focalY = 0.5,
): KeyframeAsset => ({
  src,
  status: "pilot",
  focalX,
  focalY,
});

export const v2Keyframes: KeyframeSpec[] = [
  {
    id: "K01",
    act: "field",
    title: "Hero",
    copy: "Tu olivar en buenas manos.",
    desktop: {
      src: "/media/v2/hero-photo-clean.webp",
      status: "candidate",
      focalX: 0.67,
      focalY: 0.50,
    },
    mobile: pilot("/media/v2/hero-photo-clean.webp", 0.42, 0.50),
    continuity: ["farmer", "wardrobe", "light"],
  },
  {
    id: "K02",
    act: "field",
    title: "Caminar",
    copy: "Tu día empieza aquí.",
    desktop: pilot("/media/home/heritage-farmer.webp", 0.36, 0.52),
    mobile: pilot("/media/home/heritage-farmer.webp", 0.44, 0.54),
    continuity: ["farmer", "wardrobe", "light"],
  },
  {
    id: "K03",
    act: "field",
    title: "Mirar",
    copy: "Mira.",
    desktop: pilot("/media/home/benefits-olives.webp", 0.70, 0.48),
    mobile: pilot("/media/home/benefits-olives.webp", 0.62, 0.50),
    continuity: ["farmer", "wardrobe", "light", "hands"],
  },
  {
    id: "K04",
    act: "field",
    title: "Mano y aceitunas",
    copy: "Cada detalle importa.",
    desktop: pilot("/media/home/benefits-olives.webp", 0.68, 0.48),
    mobile: pilot("/media/home/benefits-olives.webp", 0.60, 0.50),
    continuity: ["farmer", "wardrobe", "light", "hands"],
  },
  {
    id: "K05",
    act: "field",
    title: "Decidir",
    copy: "Decide.",
    desktop: pilot("/media/home/heritage-farmer.webp", 0.39, 0.50),
    mobile: pilot("/media/home/heritage-farmer.webp", 0.46, 0.52),
    continuity: ["farmer", "wardrobe", "light"],
  },
  {
    id: "K06",
    act: "field",
    title: "Pausa",
    desktop: pilot("/media/home/heritage-farmer.webp", 0.40, 0.50),
    mobile: pilot("/media/home/heritage-farmer.webp", 0.47, 0.52),
    continuity: ["farmer", "wardrobe", "light"],
  },
  {
    id: "K07",
    act: "phone",
    title: "Mano al bolsillo",
    copy: "Registra.",
    desktop: pilot("/media/home/phone-in-hand.webp", 0.48, 0.56),
    mobile: pilot("/media/home/phone-in-hand.webp", 0.50, 0.57),
    continuity: ["farmer", "wardrobe", "light", "hands", "phone"],
  },
  {
    id: "K08",
    act: "phone",
    title: "Teléfono aparece",
    desktop: pilot("/media/home/phone-in-hand.webp", 0.49, 0.54),
    mobile: pilot("/media/home/phone-in-hand.webp", 0.50, 0.55),
    continuity: ["farmer", "wardrobe", "light", "hands", "phone"],
  },
  {
    id: "K09",
    act: "phone",
    title: "Móvil en mano",
    copy: "Todo en tu mano.",
    desktop: pilot("/media/home/phone-in-hand.webp", 0.50, 0.52),
    mobile: pilot("/media/home/phone-in-hand.webp", 0.50, 0.53),
    continuity: ["farmer", "wardrobe", "light", "hands", "phone"],
  },
  {
    id: "K10",
    act: "phone",
    title: "Acercamiento",
    desktop: pilot("/media/home/phone-in-hand.webp", 0.50, 0.51),
    mobile: pilot("/media/home/phone-in-hand.webp", 0.50, 0.52),
    continuity: ["hands", "phone", "light"],
  },
  {
    id: "K11",
    act: "phone",
    title: "Giro",
    desktop: pilot("/media/home/phone-in-hand.webp", 0.50, 0.50),
    mobile: pilot("/media/home/phone-in-hand.webp", 0.50, 0.50),
    continuity: ["hands", "phone", "light"],
  },
  {
    id: "K12",
    act: "phone",
    title: "Móvil frontal",
    copy: "Mágina Olivo.",
    desktop: pilot("/media/home/phone-in-hand.webp", 0.50, 0.48),
    mobile: pilot("/media/home/phone-in-hand.webp", 0.50, 0.48),
    continuity: ["phone", "ui"],
  },
  {
    id: "K13",
    act: "product",
    title: "Inicio",
    copy: "Todo tu olivar.",
    desktop: pilot("/media/home/phone-in-hand.webp", 0.50, 0.48),
    mobile: pilot("/media/home/phone-in-hand.webp", 0.50, 0.48),
    continuity: ["phone", "ui"],
  },
  {
    id: "K14",
    act: "product",
    title: "Fincas",
    copy: "Tus fincas.",
    desktop: pilot("/media/home/phone-in-hand.webp"),
    mobile: pilot("/media/home/phone-in-hand.webp"),
    continuity: ["phone", "ui"],
  },
  {
    id: "K15",
    act: "product",
    title: "Finca",
    copy: "Todo empieza por saber qué tienes.",
    desktop: pilot("/media/home/phone-in-hand.webp"),
    mobile: pilot("/media/home/phone-in-hand.webp"),
    continuity: ["phone", "ui"],
  },
  {
    id: "K16",
    act: "product",
    title: "Parcelas",
    copy: "Tus parcelas.",
    desktop: pilot("/media/home/phone-in-hand.webp"),
    mobile: pilot("/media/home/phone-in-hand.webp"),
    continuity: ["phone", "ui"],
  },
  {
    id: "K17",
    act: "product",
    title: "Mapa",
    copy: "Tu tierra, localizada.",
    desktop: pilot("/media/home/phone-in-hand.webp"),
    mobile: pilot("/media/home/phone-in-hand.webp"),
    continuity: ["phone", "ui"],
  },
  {
    id: "K18",
    act: "product",
    title: "Campaña",
    copy: "Tu campaña.",
    desktop: pilot("/media/home/phone-in-hand.webp"),
    mobile: pilot("/media/home/phone-in-hand.webp"),
    continuity: ["phone", "ui"],
  },
  {
    id: "K19",
    act: "product",
    title: "Cosecha",
    copy: "Tu cosecha.",
    desktop: pilot("/media/home/phone-in-hand.webp"),
    mobile: pilot("/media/home/phone-in-hand.webp"),
    continuity: ["phone", "ui"],
  },
  {
    id: "K20",
    act: "product",
    title: "Gastos",
    copy: "Tus números.",
    desktop: pilot("/media/home/phone-in-hand.webp"),
    mobile: pilot("/media/home/phone-in-hand.webp"),
    continuity: ["phone", "ui"],
  },
  {
    id: "K21",
    act: "product",
    title: "Tiempo",
    copy: "Decide con contexto.",
    desktop: pilot("/media/home/phone-in-hand.webp"),
    mobile: pilot("/media/home/phone-in-hand.webp"),
    continuity: ["phone", "ui"],
  },
  {
    id: "K22",
    act: "product",
    title: "Histórico",
    copy: "Aprende de cada campaña.",
    desktop: pilot("/media/home/phone-in-hand.webp"),
    mobile: pilot("/media/home/phone-in-hand.webp"),
    continuity: ["phone", "ui"],
  },
  {
    id: "K23",
    act: "return",
    title: "Volver al campo",
    copy: "Menos papeles. Más control.",
    desktop: pilot("/media/home/hero-farmer.webp", 0.70, 0.50),
    mobile: pilot("/media/home/hero-farmer.webp", 0.56, 0.52),
    continuity: ["farmer", "wardrobe", "light", "hands", "phone"],
  },
  {
    id: "K24",
    act: "return",
    title: "Cierre",
    copy: "Todo tu olivar. En un solo lugar.",
    desktop: pilot("/media/home/hero-farmer.webp", 0.67, 0.50),
    mobile: pilot("/media/home/hero-farmer.webp", 0.54, 0.52),
    continuity: ["farmer", "wardrobe", "light"],
  },
];

export const getKeyframe = (id: KeyframeId) => {
  const frame = v2Keyframes.find((item) => item.id === id);
  if (!frame) throw new Error(`Unknown V2 keyframe: ${id}`);
  return frame;
};

export const productionPriority: KeyframeId[] = [
  "K01",
  "K02",
  "K03",
  "K09",
  "K12",
  "K13",
  "K24",
];
