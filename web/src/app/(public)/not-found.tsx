import Link from "next/link";

export default function PublicNotFound() {
  return (
    <main className="page-wrap">
      <p className="page-eyebrow">Mágina Olivo</p>
      <h1 className="page-title">No encontramos esa página.</h1>
      <Link className="button-secondary" href="/">
        Volver al inicio
      </Link>
    </main>
  );
}
