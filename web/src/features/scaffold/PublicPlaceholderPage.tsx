type PublicPlaceholderPageProps = {
  title: string;
  description?: string;
  id?: string;
};

export function PublicPlaceholderPage({
  title,
  description,
  id,
}: PublicPlaceholderPageProps) {
  return (
    <main className="page-wrap" id={id}>
      <p className="page-eyebrow">Mágina Olivo</p>
      <h1 className="page-title">{title}</h1>
      {description ? <p className="page-description">{description}</p> : null}
      <p className="baseline-note">Sección en preparación.</p>
    </main>
  );
}
