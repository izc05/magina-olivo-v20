import { getKeyframe } from "@/data/v2/keyframes";

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

const desktopAnchorSpec = [
  ["K02", 1.02],
  ["K02", 1.04],
  ["K05", 1.07],
  ["K03", 1.12],
  ["K04", 1.08],
  ["K04", 1.04],
  ["K07", 1.15],
  ["K08", 1.11],
  ["K09", 1.07],
  ["K10", 1.04],
  ["K11", 1.02],
  ["K12", 1.00],
] as const;

const mobileAnchorSpec = [
  ["K02", 1.08],
  ["K02", 1.10],
  ["K05", 1.13],
  ["K03", 1.18],
  ["K04", 1.13],
  ["K04", 1.09],
  ["K07", 1.20],
  ["K08", 1.15],
  ["K09", 1.10],
  ["K12", 1.06],
] as const;

const desktopAnchors: CinematicFrame[] = desktopAnchorSpec.map(([id, scale]) => {
  const frame = getKeyframe(id).desktop;
  return {
    src: frame.src,
    focalX: frame.focalX,
    focalY: frame.focalY,
    scale,
  };
});

const mobileAnchors: CinematicFrame[] = mobileAnchorSpec.map(([id, scale]) => {
  const frame = getKeyframe(id).mobile;
  return {
    src: frame.src,
    focalX: frame.focalX,
    focalY: frame.focalY,
    scale,
  };
});

export const pilotDesktopFrames = densifyFrames(desktopAnchors, 3);
export const pilotMobileFrames = densifyFrames(mobileAnchors, 3);

export const pilotMarkers: CinematicMarker[] = [
  { at: 0.08, label: "Mira." },
  { at: 0.34, label: "Cada detalle importa." },
  { at: 0.62, label: "Registra." },
  { at: 0.84, label: "Todo en tu mano." },
];
