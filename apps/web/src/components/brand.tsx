import Image from 'next/image';
import Link from 'next/link';

export function Brand({ compact = false }: { compact?: boolean }) {
  const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? '';

  return (
    <Link href="/" className={compact ? 'brand brand-compact' : 'brand'} aria-label="Mágina Olivo, inicio">
      <Image
        src={`${basePath}${compact ? '/assets/brand/magina-olivo-symbol.png' : '/assets/brand/magina-olivo-horizontal.png'}`}
        width={compact ? 460 : 1100}
        height={compact ? 415 : 325}
        alt=""
        className={compact ? 'brand-symbol' : 'brand-lockup'}
        priority
      />
    </Link>
  );
}
