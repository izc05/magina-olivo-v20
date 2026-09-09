import Link from 'next/link';

export function Topbar() {
  return (
    <header className="topbar">
      <Link href="/" className="brand">Mágina Olivo<small>TERRITORIO · PERSONAS · FUTURO</small></Link>
      <span aria-label="Notificaciones">♢</span>
    </header>
  );
}
