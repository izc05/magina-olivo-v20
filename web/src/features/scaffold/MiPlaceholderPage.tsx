type MiPlaceholderPageProps = {
  title: string;
};

export function MiPlaceholderPage({ title }: MiPlaceholderPageProps) {
  return (
    <>
      <p className="page-eyebrow">Mi Mágina Olivo · Demo</p>
      <h1>{title}</h1>
      <p className="page-description">
        Pantalla de preparación. No muestra datos de una cuenta real.
      </p>
    </>
  );
}
