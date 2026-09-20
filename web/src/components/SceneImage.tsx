"use client";

import { useState } from "react";
import Image from "next/image";
import type { VisualAsset } from "@/lib/visualAssets";

type SceneImageProps = {
  asset: VisualAsset;
  priority?: boolean;
  className?: string;
  sizes?: string;
};

export function SceneImage({
  asset,
  priority = false,
  className,
  sizes = "100vw",
}: SceneImageProps) {
  const [failed, setFailed] = useState(false);
  const useFinal = asset.status === "final" && !failed;

  return (
    <Image
      src={useFinal ? asset.finalSrc : asset.fallbackSrc}
      alt={asset.alt}
      fill
      priority={priority}
      className={className}
      sizes={sizes}
      style={{ objectPosition: asset.focalPoint }}
      onError={() => setFailed(true)}
    />
  );
}
