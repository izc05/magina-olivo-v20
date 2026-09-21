"use client";

import { useEffect, useRef, useState } from "react";

type SequenceImage = {
  src: string;
  focalX?: number;
  focalY?: number;
};

type CinematicScrollCanvasProps = {
  images: SequenceImage[];
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
  images,
  className,
  reducedMotionPoster,
}: CinematicScrollCanvasProps) {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const loadedRef = useRef<HTMLImageElement[]>([]);
  const [ready, setReady] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);

  useEffect(() => {
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const apply = () => setReducedMotion(media.matches);
    apply();
    media.addEventListener("change", apply);
    return () => media.removeEventListener("change", apply);
  }, []);

  useEffect(() => {
    if (!images.length || reducedMotion) return;

    let cancelled = false;
    const loaded: HTMLImageElement[] = [];

    Promise.all(
      images.map(
        (item) =>
          new Promise<HTMLImageElement>((resolve, reject) => {
            const image = new Image();
            image.decoding = "async";
            image.onload = () => resolve(image);
            image.onerror = reject;
            image.src = item.src;
          }),
      ),
    )
      .then((result) => {
        if (cancelled) return;
        loaded.push(...result);
        loadedRef.current = loaded;
        setReady(true);
      })
      .catch(() => {
        if (!cancelled) setReady(false);
      });

    return () => {
      cancelled = true;
    };
  }, [images, reducedMotion]);

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

      const dpr = Math.min(window.devicePixelRatio || 1, 1.75);
      const width = Math.max(1, Math.round(window.innerWidth));
      const height = Math.max(1, Math.round(viewport));

      if (canvas.width !== Math.round(width * dpr) || canvas.height !== Math.round(height * dpr)) {
        canvas.width = Math.round(width * dpr);
        canvas.height = Math.round(height * dpr);
        canvas.style.width = `${width}px`;
        canvas.style.height = `${height}px`;
      }

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, width, height);
      ctx.fillStyle = "#10251a";
      ctx.fillRect(0, 0, width, height);

      const frames = loadedRef.current;
      if (!frames.length) return;

      const segmentFloat = progress * (frames.length - 1);
      const fromIndex = Math.min(frames.length - 1, Math.floor(segmentFloat));
      const toIndex = Math.min(frames.length - 1, fromIndex + 1);
      const local = segmentFloat - fromIndex;

      const fromMeta = images[fromIndex] ?? images[0];
      const toMeta = images[toIndex] ?? fromMeta;

      const focalX = lerp(fromMeta.focalX ?? 0.5, toMeta.focalX ?? 0.5, local);
      const focalY = lerp(fromMeta.focalY ?? 0.5, toMeta.focalY ?? 0.5, local);
      const scale = 1.035 + progress * 0.085;

      drawCover(
        ctx,
        frames[fromIndex],
        width,
        height,
        scale,
        focalX,
        focalY,
        1,
      );

      if (toIndex !== fromIndex && local > 0.02) {
        drawCover(
          ctx,
          frames[toIndex],
          width,
          height,
          scale + 0.012,
          focalX,
          focalY,
          local,
        );
      }
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
  }, [images, ready, reducedMotion]);

  const poster = reducedMotionPoster || images[0]?.src;

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
