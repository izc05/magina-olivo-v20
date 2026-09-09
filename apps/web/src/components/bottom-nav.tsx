import Link from 'next/link';

const items = [
  ['/', '⌂', 'Inicio'],
  ['/mi-campo', '♧', 'Mi Campo'],
  ['/radar', '◉', 'Explorar'],
  ['/perfil', '•••', 'Más'],
] as const;

export function BottomNav({ active }: { active: string }) {
  return (
    <nav className="bottom-nav" aria-label="Navegación principal">
      {items.map(([href, icon, label]) => (
        <Link key={href} href={href} className={active === href ? 'active' : undefined}>
          <span aria-hidden>{icon}</span>{label}
        </Link>
      ))}
    </nav>
  );
}
