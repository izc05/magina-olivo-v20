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

const lerp = (from: number, to: number, amount: number) =>
  from + (to - from) * amount;

function densifyFrames(
  anchors: CinematicFrame[],
  subdivisions: number,
): CinematicFrame[] {
  if (anchors.length <= 1 || subdivisions <= 1) return anchors;

  const frames: CinematicFrame[] = [];

  for (let index = 0; index < anchors.length - 1; index += 1) {
    const from = anchors[index];
    const to = anchors[index + 1];

    for (let step = 0; step < subdivisions; step += 1) {
      const local = step / subdivisions;
      frames.push({
        src: from.src,
        focalX: lerp(from.focalX, to.focalX, local),
        focalY: lerp(from.focalY, to.focalY, local),
        scale: lerp(from.scale, to.scale, local),
      });
    }
  }

  frames.push(anchors[anchors.length - 1]);
  return frames;
}

const desktopAnchors: CinematicFrame[] = [
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

const mobileAnchors: CinematicFrame[] = [
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

export const pilotDesktopFrames = densifyFrames(desktopAnchors, 3);
export const pilotMobileFrames = densifyFrames(mobileAnchors, 3);

export const pilotMarkers: CinematicMarker[] = [
  { at: 0.08, label: "Mira." },
  { at: 0.34, label: "Cada detalle importa." },
  { at: 0.62, label: "Registra." },
  { at: 0.84, label: "Todo en tu mano." },
];
