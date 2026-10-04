import Image from "next/image";
import Link from "next/link";
import { staticAssetPath } from "@/lib/static-asset-path";

type BrandLogoProps = Readonly<{
  href: string;
  label: string;
  className?: string;
}>;

export function BrandLogo({ href, label, className = "" }: BrandLogoProps) {
  return (
    <Link
      aria-label={label}
      className={`brand brand-logo ${className}`.trim()}
      href={href}
    >
      <Image
        alt=""
        className="brand-logo-image"
        height={343}
        priority
        src={staticAssetPath("/brand/logo-horizontal.png")}
        width={1275}
      />
    </Link>
  );
}
