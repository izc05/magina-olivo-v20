export type CinematicFrame = {
  src: string;
  focalX: number;
  focalY: number;
  scale: number;
};

export type CinematicMarker = {
  at: number;
  label: string;
};

export const pilotDesktopFrames: CinematicFrame[] = [
  { src: "/media/home/heritage-farmer.webp", focalX: 0.34, focalY: 0.52, scale: 1.02 },
  { src: "/media/home/heritage-farmer.webp", focalX: 0.37, focalY: 0.51, scale: 1.04 },
  { src: "/media/home/heritage-farmer.webp", focalX: 0.40, focalY: 0.50, scale: 1.07 },
  { src: "/media/home/benefits-olives.webp", focalX: 0.72, focalY: 0.48, scale: 1.12 },
  { src: "/media/home/benefits-olives.webp", focalX: 0.69, focalY: 0.48, scale: 1.08 },
  { src: "/media/home/benefits-olives.webp", focalX: 0.65, focalY: 0.49, scale: 1.04 },
  { src: "/media/home/phone-in-hand.webp", focalX: 0.50, focalY: 0.54, scale: 1.15 },
  { src: "/media/home/phone-in-hand.webp", focalX: 0.50, focalY: 0.52, scale: 1.11 },
  { src: "/media/home/phone-in-hand.webp", focalX: 0.50, focalY: 0.50, scale: 1.07 },
  { src: "/media/home/phone-in-hand.webp", focalX: 0.50, focalY: 0.49, scale: 1.04 },
  { src: "/media/home/phone-in-hand.webp", focalX: 0.50, focalY: 0.48, scale: 1.02 },
  { src: "/media/home/phone-in-hand.webp", focalX: 0.50, focalY: 0.47, scale: 1.00 },
];

export const pilotMobileFrames: CinematicFrame[] = [
  { src: "/media/home/heritage-farmer.webp", focalX: 0.42, focalY: 0.54, scale: 1.08 },
  { src: "/media/home/heritage-farmer.webp", focalX: 0.44, focalY: 0.53, scale: 1.10 },
  { src: "/media/home/heritage-farmer.webp", focalX: 0.46, focalY: 0.52, scale: 1.13 },
  { src: "/media/home/benefits-olives.webp", focalX: 0.64, focalY: 0.50, scale: 1.18 },
  { src: "/media/home/benefits-olives.webp", focalX: 0.61, focalY: 0.50, scale: 1.13 },
  { src: "/media/home/benefits-olives.webp", focalX: 0.58, focalY: 0.50, scale: 1.09 },
  { src: "/media/home/phone-in-hand.webp", focalX: 0.50, focalY: 0.55, scale: 1.20 },
  { src: "/media/home/phone-in-hand.webp", focalX: 0.50, focalY: 0.53, scale: 1.15 },
  { src: "/media/home/phone-in-hand.webp", focalX: 0.50, focalY: 0.51, scale: 1.10 },
  { src: "/media/home/phone-in-hand.webp", focalX: 0.50, focalY: 0.49, scale: 1.06 },
];

export const pilotMarkers: CinematicMarker[] = [
  { at: 0.08, label: "Mira." },
  { at: 0.34, label: "Cada detalle importa." },
  { at: 0.62, label: "Registra." },
  { at: 0.84, label: "Todo en tu mano." },
];
