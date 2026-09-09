import Image from 'next/image';
import Link from 'next/link';

export function Brand({ compact = false }: { compact?: boolean }) {
  const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? '';

  return (
    <Link href="/" className={compact ? 'brand brand-compact' : 'brand'} aria-label="Mágina Olivo, inicio">
      <Image src={`${basePath}/assets/olive-sprig.svg`} width={60} height={25} alt="" className="brand-sprig" priority />
      <span>Mágina Olivo</span>
      {!compact && <small>TERRITORIO · PERSONAS · FUTURO</small>}
    </Link>
  );
}
