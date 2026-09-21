"use client";

import { useEffect, useRef, useState } from "react";

type CinematicVideoScrubProps = {
  src: string;
  poster: string;
  className?: string;
  objectPosition?: string;
};

const clamp = (value: number) => Math.min(1, Math.max(0, value));

export function CinematicVideoScrub({
  src,
  poster,
  className,
  objectPosition = "50% 50%",
}: CinematicVideoScrubProps) {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);
  const [saveData, setSaveData] = useState(false);

  useEffect(() => {
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const connection = (
      navigator as Navigator & {
        connection?: {
          saveData?: boolean;
          addEventListener?: (type: string, listener: () => void) => void;
          removeEventListener?: (type: string, listener: () => void) => void;
        };
      }
    ).connection;

    const apply = () => {
      setReducedMotion(media.matches);
      setSaveData(Boolean(connection?.saveData));
    };

    apply();
    media.addEventListener("change", apply);
    connection?.addEventListener?.("change", apply);

    return () => {
      media.removeEventListener("change", apply);
      connection?.removeEventListener?.("change", apply);
    };
  }, []);

  useEffect(() => {
    if (reducedMotion || saveData || failed) return;

    const wrapper = wrapperRef.current;
    const video = videoRef.current;
    if (!wrapper || !video) return;

    let raf = 0;
    let duration = 0;

    const sync = () => {
      raf = 0;
      if (!duration || !Number.isFinite(duration)) return;

      const rect = wrapper.getBoundingClientRect();
      const travel = Math.max(1, wrapper.offsetHeight - window.innerHeight);
      const progress = clamp(-rect.top / travel);
      const safeDuration = Math.max(0, duration - 0.08);
      const target = progress * safeDuration;

      if (Math.abs(video.currentTime - target) > 1 / 30) {
        try {
          video.currentTime = target;
        } catch {
          // Browser may temporarily reject seeks while metadata/ranges load.
        }
      }

      wrapper.dataset.videoProgress = progress.toFixed(3);
      wrapper.dataset.videoTime = target.toFixed(3);
    };

    const requestSync = () => {
      if (!raf) raf = requestAnimationFrame(sync);
    };

    const onMetadata = () => {
      duration = video.duration;
      if (Number.isFinite(duration) && duration > 0) {
        setReady(true);
        video.pause();
        sync();
      }
    };

    video.addEventListener("loadedmetadata", onMetadata);
    video.addEventListener("durationchange", onMetadata);
    video.addEventListener("error", () => setFailed(true), { once: true });
    window.addEventListener("scroll", requestSync, { passive: true });
    window.addEventListener("resize", requestSync);

    if (video.readyState >= 1) onMetadata();

    return () => {
      video.removeEventListener("loadedmetadata", onMetadata);
      video.removeEventListener("durationchange", onMetadata);
      window.removeEventListener("scroll", requestSync);
      window.removeEventListener("resize", requestSync);
      if (raf) cancelAnimationFrame(raf);
    };
  }, [failed, reducedMotion, saveData]);

  return (
    <div
      ref={wrapperRef}
      className={className}
      data-video-ready={ready ? "true" : "false"}
      data-video-failed={failed ? "true" : "false"}
      data-video-save-data={saveData ? "true" : "false"}
    >
      {reducedMotion || saveData || failed ? (
        <img
          className="v2-video-scrub-poster"
          src={poster}
          alt=""
          aria-hidden="true"
          style={{ objectPosition }}
        />
      ) : (
        <video
          ref={videoRef}
          className="v2-video-scrub-media"
          src={src}
          poster={poster}
          preload="metadata"
          muted
          playsInline
          aria-hidden="true"
          tabIndex={-1}
          style={{ objectPosition }}
        />
      )}
    </div>
  );
}
