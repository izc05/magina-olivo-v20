"use client";

import type { CinematicFrame } from "@/data/v2/cinematicSequence";
import { useEffect, useMemo, useRef, useState } from "react";

type CinematicScrollCanvasProps = {
  desktopFrames: CinematicFrame[];
  mobileFrames: CinematicFrame[];
  className?: string;
  reducedMotionPoster?: string;
};

const clamp = (value: number) => Math.min(1, Math.max(0, value));
const lerp = (from: number, to: number, amount: number) =>
  from + (to - from) * amount;

function drawCover(
  ctx: CanvasRenderingContext2D,
  image: HTMLImageElement,
  width: number,
  height: number,
  scale: number,
  focalX: number,
  focalY: number,
  alpha: number,
) {
  const baseScale = Math.max(width / image.naturalWidth, height / image.naturalHeight);
  const targetScale = baseScale * scale;
  const drawWidth = image.naturalWidth * targetScale;
  const drawHeight = image.naturalHeight * targetScale;
  const maxX = Math.max(0, drawWidth - width);
  const maxY = Math.max(0, drawHeight - height);
  const x = -maxX * focalX;
  const y = -maxY * focalY;

  ctx.save();
  ctx.globalAlpha = alpha;
  ctx.drawImage(image, x, y, drawWidth, drawHeight);
  ctx.restore();
}

export function CinematicScrollCanvas({
  desktopFrames,
  mobileFrames,
  className,
  reducedMotionPoster,
}: CinematicScrollCanvasProps) {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imageMapRef = useRef<Map<string, HTMLImageElement>>(new Map());
  const [ready, setReady] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);
  const [mobile, setMobile] = useState(false);

  const frames = useMemo(
    () => (mobile ? mobileFrames : desktopFrames),
    [desktopFrames, mobileFrames, mobile],
  );

  useEffect(() => {
    const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const viewport = window.matchMedia("(max-width: 820px)");

    const apply = () => {
      setReducedMotion(motion.matches);
      setMobile(viewport.matches);
    };

    apply();
    motion.addEventListener("change", apply);
    viewport.addEventListener("change", apply);

    return () => {
      motion.removeEventListener("change", apply);
      viewport.removeEventListener("change", apply);
    };
  }, []);

  useEffect(() => {
    if (!frames.length || reducedMotion) return;

    let cancelled = false;
    setReady(false);

    const uniqueSources = Array.from(new Set(frames.map((frame) => frame.src)));

    Promise.all(
      uniqueSources.map(
        (src) =>
          new Promise<[string, HTMLImageElement]>((resolve, reject) => {
            const existing = imageMapRef.current.get(src);
            if (existing?.complete && existing.naturalWidth > 0) {
              resolve([src, existing]);
              return;
            }

            const image = new Image();
            image.decoding = "async";
            image.onload = () => resolve([src, image]);
            image.onerror = reject;
            image.src = src;
          }),
      ),
    )
      .then((loaded) => {
        if (cancelled) return;
        loaded.forEach(([src, image]) => imageMapRef.current.set(src, image));
        setReady(true);
      })
      .catch(() => {
        if (!cancelled) setReady(false);
      });

    return () => {
      cancelled = true;
    };
  }, [frames, reducedMotion]);

  useEffect(() => {
    if (!ready || reducedMotion) return;

    const wrapper = wrapperRef.current;
    const canvas = canvasRef.current;
    if (!wrapper || !canvas) return;

    const ctx = canvas.getContext("2d", { alpha: false });
    if (!ctx) return;

    let raf = 0;

    const render = () => {
      raf = 0;
      const rect = wrapper.getBoundingClientRect();
      const viewport = window.innerHeight;
      const travel = Math.max(1, wrapper.offsetHeight - viewport);
      const progress = clamp(-rect.top / travel);

      const dpr = Math.min(window.devicePixelRatio || 1, mobile ? 1.5 : 1.75);
      const width = Math.max(1, Math.round(window.innerWidth));
      const height = Math.max(1, Math.round(viewport));

      if (
        canvas.width !== Math.round(width * dpr) ||
        canvas.height !== Math.round(height * dpr)
      ) {
        canvas.width = Math.round(width * dpr);
        canvas.height = Math.round(height * dpr);
        canvas.style.width = `${width}px`;
        canvas.style.height = `${height}px`;
      }

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, width, height);
      ctx.fillStyle = "#10251a";
      ctx.fillRect(0, 0, width, height);

      if (!frames.length) return;

      const frameFloat = progress * (frames.length - 1);
      const fromIndex = Math.min(frames.length - 1, Math.floor(frameFloat));
      const toIndex = Math.min(frames.length - 1, fromIndex + 1);
      const local = frameFloat - fromIndex;

      const fromMeta = frames[fromIndex] ?? frames[0];
      const toMeta = frames[toIndex] ?? fromMeta;
      const fromImage = imageMapRef.current.get(fromMeta.src);
      const toImage = imageMapRef.current.get(toMeta.src) ?? fromImage;
      if (!fromImage) return;

      const focalX = lerp(fromMeta.focalX, toMeta.focalX, local);
      const focalY = lerp(fromMeta.focalY, toMeta.focalY, local);
      const scale = lerp(fromMeta.scale, toMeta.scale, local);

      drawCover(
        ctx,
        fromImage,
        width,
        height,
        scale,
        focalX,
        focalY,
        1,
      );

      if (
        toImage &&
        toMeta.src !== fromMeta.src &&
        toIndex !== fromIndex &&
        local > 0.02
      ) {
        drawCover(
          ctx,
          toImage,
          width,
          height,
          toMeta.scale,
          focalX,
          focalY,
          local,
        );
      }

      wrapper.style.setProperty("--sequence-progress", String(progress));
      wrapper.dataset.frame = String(fromIndex + 1);
      wrapper.dataset.frames = String(frames.length);
    };

    const requestRender = () => {
      if (!raf) raf = requestAnimationFrame(render);
    };

    render();
    window.addEventListener("scroll", requestRender, { passive: true });
    window.addEventListener("resize", requestRender);

    return () => {
      window.removeEventListener("scroll", requestRender);
      window.removeEventListener("resize", requestRender);
      if (raf) cancelAnimationFrame(raf);
    };
  }, [frames, mobile, ready, reducedMotion]);

  const poster = reducedMotionPoster || frames[0]?.src;

  return (
    <div ref={wrapperRef} className={className}>
      {reducedMotion && poster ? (
        <img className="v2-sequence-poster" src={poster} alt="" aria-hidden="true" />
      ) : (
        <canvas
          ref={canvasRef}
          className="v2-sequence-canvas"
          aria-hidden="true"
          data-ready={ready ? "true" : "false"}
        />
      )}
    </div>
  );
}
