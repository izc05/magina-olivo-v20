import Link from "next/link";
import {
  DEMO_DATA_LABEL,
  demoDashboard,
  demoDocuments,
  demoNotebookEntries,
  miReadAdapter,
} from "@/data/mock/mi-demo";

type PageHeaderProps = Readonly<{
  title: string;
  subtitle: string;
  action?: { href: string; label: string };
}>;

function PageHeader({ title, subtitle, action }: PageHeaderProps) {
  return (
    <div className="mi-page-header">
      <div>
        <p className="mi-page-kicker">Mi Mágina Olivo · {DEMO_DATA_LABEL}</p>
        <h1 className="mi-page-title">{title}</h1>
        <p className="mi-page-subtitle">{subtitle}</p>
      </div>
      {action ? (
        <Link className="mi-secondary-link" href={action.href}>
          {action.label}
        </Link>
      ) : null}
    </div>
  );
}

function Panel({
  title,
  href,
  linkLabel = "Ver detalle",
  className = "",
  children,
}: Readonly<{
  title: string;
  href?: string;
  linkLabel?: string;
  className?: string;
  children: React.ReactNode;
}>) {
  return (
    <section className={`mi-panel ${className}`.trim()}>
      <div className="mi-panel-heading">
        <h2>{title}</h2>
        {href ? (
          <Link href={href}>{linkLabel} →</Link>
        ) : (
          <span className="mi-demo-pill">Demo</span>
        )}
      </div>
      {children}
    </section>
  );
}

function DemoNote({ children }: Readonly<{ children: React.ReactNode }>) {
  return <p className="mi-panel-note">{children}</p>;
}

function SchematicFarmMap() {
  return (
    <div
      aria-label="Representación esquemática de las fincas Estacas y Salinillas, datos Demo; no son límites catastrales"
      className="mi-schematic-map"
      role="img"
    >
      <span className="mi-map-chip mi-map-chip-one">Estacas · 0,76 ha</span>
      <span className="mi-map-chip mi-map-chip-two">Salinillas · 0,38 ha</span>
    </div>
  );
}

function ProductionChart() {
  return (
    <div
      aria-label={`Producción ilustrativa por campaña: ${demoDashboard.production.map(({ year, value }) => `${year}, ${value}`).join("; ")}`}
      className="mi-chart"
      role="img"
    >
      {demoDashboard.production.map((item) => (
        <div className="mi-chart-column" key={item.year}>
          <div className="mi-chart-bar" style={{ height: `${item.height}%` }} />
          <span>{item.year}</span>
        </div>
      ))}
    </div>
  );
}

function Sparkline() {
  return (
    <svg
      aria-hidden="true"
      className="mi-sparkline"
      preserveAspectRatio="none"
      viewBox="0 0 220 54"
    >
      <path
        d="M0 42 13 37 27 39 40 30 54 33 68 21 82 28 96 18 110 24 124 11 138 15 152 8 166 14 180 5 193 10 207 3 220 7"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2.4"
      />
    </svg>
  );
}

export function MiDashboardPage() {
  const farms = miReadAdapter.getFarms();
  const campaign = miReadAdapter.getCampaign("demo");

  return (
    <div className="mi-page mi-dashboard-page">
      <PageHeader
        title="Panel general"
        subtitle="Vista global de tu explotación. Todo lo importante, en un solo lugar."
      />
      <div className="mi-kpi-grid">
        {demoDashboard.kpis.map((kpi) => (
          <article className="mi-kpi-card" key={kpi.label}>
            <span className="mi-kpi-label">{kpi.label}</span>
            <strong className="mi-kpi-value">{kpi.value}</strong>
            <span className="mi-kpi-note">{kpi.note}</span>
          </article>
        ))}
      </div>
      <div className="mi-dashboard-grid">
        <Panel
          className="mi-panel-weather"
          href="/mi/tiempo"
          linkLabel="Ver previsión"
          title="Tiempo en tu zona"
        >
          <div className="mi-weather-current">
            <span aria-hidden="true" className="mi-weather-icon">
              ☂
            </span>
            <div>
              <strong className="mi-weather-temperature">
                {demoDashboard.weather.temperature}
              </strong>
              <span className="mi-weather-condition">
                {demoDashboard.weather.condition} · sensación 21°
              </span>
            </div>
            <div className="mi-weather-facts">
              <span>◌ {demoDashboard.weather.rain} lluvia</span>
              <span>↗ {demoDashboard.weather.wind} viento</span>
              <span>◍ {demoDashboard.weather.humidity} humedad</span>
            </div>
          </div>
          <section
            aria-label="Previsión ilustrativa de cinco días"
            className="mi-weather-days"
          >
            {["Hoy", "Mañana", "Mar", "Mié", "Jue"].map((day, index) => (
              <div className="mi-weather-day" key={day}>
                <strong>{day}</strong>
                <i aria-hidden="true">
                  {index === 0 || index === 3 ? "☂" : "☁"}
                </i>
                <span>
                  {
                    [
                      "17° / 23°",
                      "15° / 24°",
                      "15° / 25°",
                      "17° / 19°",
                      "13° / 18°",
                    ][index]
                  }
                </span>
              </div>
            ))}
          </section>
          <DemoNote>
            {demoDashboard.location} · {demoDashboard.weather.updated}
          </DemoNote>
        </Panel>

        <Panel
          className="mi-panel-radar"
          href="/mi/tiempo#radar"
          linkLabel="Ver mapa completo"
          title="Radar de lluvia"
        >
          <div
            aria-label="Vista esquemática de radar de lluvia; datos de demostración, sin proveedor meteorológico conectado"
            className="mi-schematic-map"
            role="img"
          >
            <span className="mi-map-chip mi-map-chip-one">Bedmar</span>
            <span className="mi-map-chip mi-map-chip-two">Jódar</span>
          </div>
          <div className="mi-map-caption">
            <span>Intensidad de lluvia · Demo</span>
            <span>Sin fuente conectada</span>
          </div>
        </Panel>

        <Panel
          className="mi-panel-farms"
          href="/mi/fincas"
          linkLabel="Ver todas"
          title="Mis fincas"
        >
          <SchematicFarmMap />
          <DemoNote>
            Plano orientativo de demostración, sin geometría real.
          </DemoNote>
        </Panel>

        <Panel
          className="mi-panel-production"
          href="/mi/campanas"
          linkLabel="Campañas"
          title="Producción total"
        >
          <div className="mi-chart-summary">
            <strong>{campaign.harvestKg} kg</strong>
            <span>Total campaña {campaign.name}</span>
          </div>
          <ProductionChart />
          <DemoNote>Evolución ilustrativa · datos ficticios.</DemoNote>
        </Panel>

        <Panel
          className="mi-panel-farm-summary"
          href="/mi/fincas"
          linkLabel="Ver fincas"
          title="Resumen por fincas"
        >
          <div className="mi-table-wrap">
            <table className="mi-data-table">
              <thead>
                <tr>
                  <th>Finca</th>
                  <th>Superficie</th>
                  <th>Parcelas</th>
                  <th>Kg pesados</th>
                  <th>Campaña</th>
                </tr>
              </thead>
              <tbody>
                {farms.map((farm) => (
                  <tr key={farm.id}>
                    <td>{farm.name}</td>
                    <td>{farm.surfaceHa} ha</td>
                    <td>{farm.parcelCount}</td>
                    <td>{farm.campaignKg}</td>
                    <td>{farm.campaignName}</td>
                  </tr>
                ))}
                <tr>
                  <td>Total</td>
                  <td>1,14 ha</td>
                  <td>3</td>
                  <td>3.150 kg</td>
                  <td>Demo</td>
                </tr>
              </tbody>
            </table>
          </div>
          <DemoNote>
            Resumen de ejemplo · no procede de una cuenta real.
          </DemoNote>
        </Panel>

        <Panel
          className="mi-panel-market"
          href="/mi/mercado"
          linkLabel="Ver mercado"
          title="Mercado del aceite"
        >
          <span className="mi-market-price">{demoDashboard.market.price}</span>
          <span className="mi-market-change">
            {demoDashboard.market.change} · variación demo
          </span>
          <Sparkline />
          <DemoNote>{demoDashboard.market.source}</DemoNote>
        </Panel>

        <Panel
          className="mi-panel-notebook"
          href="/mi/cuaderno"
          linkLabel="Ver cuaderno"
          title="Cuaderno de hoy"
        >
          <div className="mi-quick-actions">
            <Link className="mi-quick-action" href="/mi/cuaderno">
              <strong>Trabajo</strong>
              <span>Consulta de actividad</span>
            </Link>
            <Link className="mi-quick-action" href="/mi/cuaderno">
              <strong>Riego</strong>
              <span>Ver registros Demo</span>
            </Link>
            <Link className="mi-quick-action" href="/mi/cuaderno">
              <strong>Tratamiento</strong>
              <span>Ver registros Demo</span>
            </Link>
            <Link className="mi-quick-action" href="/mi/campanas/demo">
              <strong>Pesada</strong>
              <span>Resumen de campaña</span>
            </Link>
            <Link className="mi-quick-action" href="/mi/cuaderno">
              <strong>Jornal</strong>
              <span>Ver actividad Demo</span>
            </Link>
            <Link className="mi-quick-action" href="/mi/campanas/demo">
              <strong>Gasto</strong>
              <span>Ver resumen Demo</span>
            </Link>
          </div>
          <DemoNote>
            Accesos de consulta · la edición no está conectada.
          </DemoNote>
        </Panel>

        <Panel
          className="mi-panel-activity"
          href="/mi/cuaderno"
          linkLabel="Ver todas"
          title="Jornales y maquinaria"
        >
          <div className="mi-activity-list">
            {demoNotebookEntries.map((entry) => (
              <div className="mi-activity-item" key={entry.id}>
                <div className="mi-activity-copy">
                  <strong>{entry.title}</strong>
                  <span>
                    {entry.detail} · {entry.value}
                  </span>
                </div>
                <time>{entry.date}</time>
              </div>
            ))}
          </div>
          <DemoNote>
            Actividad ilustrativa · sin edición ni sincronización.
          </DemoNote>
        </Panel>

        <Panel
          className="mi-panel-community"
          href="/mi/cooperativa"
          linkLabel="Ver territorio"
          title="Territorio y avisos"
        >
          <div className="mi-context-stack">
            <div className="mi-context-card">
              <strong>Noticias locales</strong>
              <span>
                El contenido informativo no está conectado en esta Demo.
              </span>
            </div>
            <div className="mi-context-card">
              <strong>Cooperativa</strong>
              <span>No hay ninguna cooperativa asignada.</span>
            </div>
            <div className="mi-context-card">
              <strong>Avisos</strong>
              <span>
                Sin avisos reales; esta vista no consulta servicios externos.
              </span>
            </div>
          </div>
        </Panel>
      </div>
    </div>
  );
}

function ListPageHeader({ title, subtitle, action }: PageHeaderProps) {
  return <PageHeader action={action} subtitle={subtitle} title={title} />;
}

function EntityCard({
  title,
  subtitle,
  meta,
  href,
  label,
}: Readonly<{
  title: string;
  subtitle: string;
  meta: readonly string[];
  href: string;
  label: string;
}>) {
  return (
    <article className="mi-list-card">
      <div className="mi-list-card-footer mi-card-title-row">
        <h2>{title}</h2>
        <span className="mi-demo-pill">Demo</span>
      </div>
      <p>{subtitle}</p>
      <div className="mi-card-meta">
        {meta.map((item) => (
          <span key={item}>{item}</span>
        ))}
      </div>
      <div className="mi-list-card-footer">
        <span>Campaña 2026–2027</span>
        <Link className="mi-primary-link" href={href}>
          {label}
        </Link>
      </div>
    </article>
  );
}

export function FarmsPage() {
  const farms = miReadAdapter.getFarms();
  return (
    <div className="mi-page">
      <ListPageHeader
        action={{ href: "/mi/mapa", label: "Ver mapa" }}
        subtitle="Consulta las fincas de la explotación Demo y accede a su resumen."
        title="Fincas"
      />

      <div className="mi-detail-grid">
        <div className="mi-detail-card">
          <span>Superficie total</span>
          <strong>1,14 ha</strong>
        </div>
        <div className="mi-detail-card">
          <span>Fincas</span>
          <strong>2</strong>
        </div>
        <div className="mi-detail-card">
          <span>Parcelas</span>
          <strong>3</strong>
        </div>
      </div>
      <div className="mi-content-grid">
        {farms.map((farm) => (
          <EntityCard
            key={farm.id}
            href={`/mi/fincas/${farm.id}`}
            label="Ver finca"
            meta={[
              `${farm.surfaceHa} ha`,
              `${farm.parcelCount} parcelas`,
              `${farm.oliveCount} olivos`,
            ]}
            subtitle={`${farm.campaignKg} kg · campaña ${farm.campaignName}`}
            title={farm.name}
          />
        ))}
      </div>
    </div>
  );
}

export function FarmDetailPage({ id }: { id: string }) {
  const farm = miReadAdapter.getFarm(id);
  const parcels = miReadAdapter
    .getParcels()
    .filter((parcel) => parcel.farmId === farm.id);
  return (
    <div className="mi-page">
      <PageHeader
        action={{ href: "/mi/fincas", label: "Volver a fincas" }}
        subtitle="Resumen visual de una finca ficticia, sin geometría catastral real."
        title={`Finca ${farm.name}`}
      />
      <div className="mi-detail-grid">
        <div className="mi-detail-card">
          <span>Superficie</span>
          <strong>{farm.surfaceHa} ha</strong>
        </div>
        <div className="mi-detail-card">
          <span>Parcelas</span>
          <strong>{farm.parcelCount}</strong>
        </div>
        <div className="mi-detail-card">
          <span>Olivos</span>
          <strong>{farm.oliveCount}</strong>
        </div>
      </div>
      <section className="mi-panel">
        <div className="mi-panel-heading">
          <h2>Parcelas de {farm.name}</h2>
          <span className="mi-demo-pill">Demo</span>
        </div>
        <div className="mi-content-grid">
          {parcels.map((parcel) => (
            <EntityCard
              key={parcel.id}
              href={`/mi/parcelas/${parcel.id}`}
              label="Ver parcela"
              meta={[
                `${parcel.surfaceHa} ha`,
                `${parcel.oliveCount} olivos`,
                parcel.variety,
              ]}
              subtitle={`${parcel.status} · ${farm.name}`}
              title={parcel.name}
            />
          ))}
        </div>
      </section>
      <section className="mi-section-heading">
        <h2>Campaña</h2>
      </section>
      <div className="mi-detail-grid">
        <div className="mi-detail-card">
          <span>Campaña</span>
          <strong>{farm.campaignName}</strong>
        </div>
        <div className="mi-detail-card">
          <span>Kg campaña</span>
          <strong>{farm.campaignKg}</strong>
        </div>
        <div className="mi-detail-card">
          <span>Datos</span>
          <strong>Demo</strong>
        </div>
      </div>
    </div>
  );
}

export function ParcelsPage() {
  return (
    <div className="mi-page">
      <PageHeader
        action={{ href: "/mi/mapa", label: "Ver mapa" }}
        subtitle="Parcelas de las fincas Demo. La geometría real no está conectada."
        title="Parcelas"
      />

      <div className="mi-content-grid">
        {miReadAdapter.getParcels().map((parcel) => (
          <EntityCard
            key={parcel.id}
            href={`/mi/parcelas/${parcel.id}`}
            label="Ver parcela"
            meta={[
              `${parcel.surfaceHa} ha`,
              `${parcel.oliveCount} olivos`,
              parcel.variety,
            ]}
            subtitle={`Finca ${parcel.farmName} · ${parcel.status}`}
            title={parcel.name}
          />
        ))}
      </div>
    </div>
  );
}

export function ParcelDetailPage({ id }: { id: string }) {
  const parcel = miReadAdapter.getParcel(id);
  return (
    <div className="mi-page">
      <PageHeader
        action={{ href: "/mi/parcelas", label: "Volver a parcelas" }}
        subtitle={`Finca ${parcel.farmName} · ficha visual con información ficticia.`}
        title={`Parcela ${parcel.name}`}
      />
      <div className="mi-detail-grid">
        <div className="mi-detail-card">
          <span>Superficie</span>
          <strong>{parcel.surfaceHa} ha</strong>
        </div>
        <div className="mi-detail-card">
          <span>Olivos</span>
          <strong>{parcel.oliveCount}</strong>
        </div>
        <div className="mi-detail-card">
          <span>Variedad</span>
          <strong>{parcel.variety}</strong>
        </div>
      </div>
      <section className="mi-panel">
        <div className="mi-panel-heading">
          <h2>Mapa de parcela</h2>
          <span className="mi-demo-pill">Vista esquemática Demo</span>
        </div>
        <div
          className="mi-placeholder-map"
          role="img"
          aria-label={`Mapa esquemático de ${parcel.name}; no representa geometría real`}
        >
          <div className="mi-placeholder-map-inner">
            <strong>{parcel.name}</strong>
            <span>No hay geometría real conectada</span>
          </div>
        </div>
      </section>
      <section className="mi-panel mi-record-panel">
        <div className="mi-panel-heading">
          <h2>Historial</h2>
          <span className="mi-demo-pill">Demo</span>
        </div>
        <div className="mi-empty-state">
          <strong>Actividad de ejemplo</strong>
          <span>Los trabajos asociados se muestran en el Cuaderno Demo.</span>
          <Link className="mi-text-link" href="/mi/cuaderno">
            Consultar Cuaderno →
          </Link>
        </div>
      </section>
    </div>
  );
}

export function MapPage() {
  return (
    <div className="mi-page">
      <PageHeader
        subtitle="Mapa de referencia de las parcelas Demo. No consulta Catastro ni servicios de mapas."
        title="Mapa de parcelas"
      />
      <section className="mi-panel">
        <div className="mi-panel-heading">
          <h2>Vista de mapa Demo</h2>
          <span className="mi-demo-pill">Sin cartografía conectada</span>
        </div>
        <div
          className="mi-placeholder-map"
          role="img"
          aria-label="Mapa esquemático de fincas y parcelas Demo, sin límites geográficos reales"
        >
          <div className="mi-placeholder-map-inner">
            <strong>Representación esquemática</strong>
            <span>Los límites no son parcelarios ni catastrales.</span>
          </div>
        </div>
        <div className="mi-map-caption">
          <span>2 fincas · 3 parcelas</span>
          <span>Datos de demostración</span>
        </div>
      </section>
      <section className="mi-section-heading">
        <h2>Fincas representadas</h2>
      </section>

      <div className="mi-content-grid">
        {miReadAdapter.getFarms().map((farm) => (
          <EntityCard
            key={farm.id}
            href={`/mi/fincas/${farm.id}`}
            label="Ver finca"
            meta={[`${farm.surfaceHa} ha`, `${farm.parcelCount} parcelas`]}
            subtitle="Representación ficticia; no incluye geometría real."
            title={farm.name}
          />
        ))}
      </div>
    </div>
  );
}

export function CampaignsPage() {
  return (
    <div className="mi-page">
      <PageHeader
        subtitle="Campaña activa e histórico ilustrativo. Sin cálculos productivos en esta demo."
        title="Campañas"
      />

      <div className="mi-content-grid">
        {miReadAdapter.getCampaigns().map((campaign) => (
          <article className="mi-list-card" key={campaign.id}>
            <div className="mi-list-card-footer mi-card-title-row">
              <h2>{campaign.name}</h2>
              <span className="mi-state-pill">
                {campaign.state === "Activa"
                  ? "Campaña activa"
                  : campaign.state}
              </span>
            </div>
            <p>Resumen de campaña · datos de demostración</p>
            <div className="mi-detail-grid">
              <div className="mi-detail-card">
                <span>Kg pesados</span>
                <strong>{campaign.harvestKg}</strong>
              </div>
              <div className="mi-detail-card">
                <span>Rendimiento</span>
                <strong>{campaign.grossYield}</strong>
              </div>
              <div className="mi-detail-card">
                <span>Coste/kg</span>
                <strong>{campaign.costPerKg}</strong>
              </div>
            </div>
            <div className="mi-list-card-footer">
              <span>{campaign.farmCount} fincas · Demo</span>
              <Link
                className="mi-primary-link"
                href={`/mi/campanas/${campaign.id}`}
              >
                Ver campaña
              </Link>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}

export function CampaignDetailPage({ id }: { id: string }) {
  const campaign = miReadAdapter.getCampaign(id);
  return (
    <div className="mi-page">
      <PageHeader
        action={{ href: "/mi/campanas", label: "Volver a campañas" }}
        subtitle={`Campaña ${campaign.state.toLowerCase()} · indicadores ficticios, no calculados por la aplicación.`}
        title={`Resumen de campaña ${campaign.name}`}
      />
      <div className="mi-detail-grid">
        <div className="mi-detail-card">
          <span>Pesadas</span>
          <strong>{campaign.harvestKg} kg</strong>
        </div>
        <div className="mi-detail-card">
          <span>Rendimiento</span>
          <strong>{campaign.grossYield}</strong>
        </div>
        <div className="mi-detail-card">
          <span>Coste/kg</span>
          <strong>{campaign.costPerKg}</strong>
        </div>
        <div className="mi-detail-card">
          <span>Gastos</span>
          <strong>{campaign.expenses}</strong>
        </div>
        <div className="mi-detail-card">
          <span>Fincas</span>
          <strong>{campaign.farmCount}</strong>
        </div>
        <div className="mi-detail-card">
          <span>Estado</span>
          <strong>{campaign.state}</strong>
        </div>
      </div>
      <section className="mi-panel">
        <div className="mi-panel-heading">
          <h2>Pesadas</h2>
          <span className="mi-demo-pill">Datos Demo</span>
        </div>
        <div className="mi-record-list">
          {demoNotebookEntries
            .filter((entry) => entry.kind === "Pesada")
            .map((entry) => (
              <div className="mi-record-row" key={entry.id}>
                <div className="mi-record-copy">
                  <strong>{entry.title}</strong>
                  <span>
                    {entry.detail} · {entry.value}
                  </span>
                </div>
                <time>{entry.date}</time>
              </div>
            ))}
        </div>
        <DemoNote>
          Resumen de solo lectura; no representa una campaña real.
        </DemoNote>
      </section>
    </div>
  );
}

export function NotebookPage() {
  return (
    <div className="mi-page">
      <PageHeader
        subtitle="Consulta cronológica de actividad ficticia. La edición se mantiene desconectada."
        title="Cuaderno"
      />
      <section className="mi-panel">
        <div className="mi-panel-heading">
          <h2>Actividad reciente</h2>
          <span className="mi-demo-pill">Consulta · Demo</span>
        </div>
        <div className="mi-record-list">
          {demoNotebookEntries.map((entry) => (
            <article className="mi-record-row" key={entry.id}>
              <div className="mi-record-copy">
                <strong>{entry.title}</strong>
                <span>
                  {entry.kind} · {entry.detail} · {entry.value}
                </span>
              </div>
              <time>{entry.date}</time>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}

export function DocumentsPage() {
  return (
    <div className="mi-page">
      <PageHeader
        subtitle="Solo se muestran metadatos de ejemplo; no hay archivos adjuntos ni almacenamiento conectado."
        title="Documentos"
      />
      <section className="mi-panel">
        <div className="mi-panel-heading">
          <h2>Documentos de demo</h2>
          <span className="mi-demo-pill">Sin archivos</span>
        </div>
        <div className="mi-record-list">
          {demoDocuments.map((document) => (
            <article className="mi-record-row" key={document.id}>
              <div className="mi-record-copy">
                <strong>{document.title}</strong>
                <span>{document.kind}</span>
              </div>
              <time>{document.date}</time>
            </article>
          ))}
        </div>
        <DemoNote>
          Los elementos son metadata ficticia. No se puede abrir o descargar
          ningún documento.
        </DemoNote>
      </section>
    </div>
  );
}

export function ReportsPage() {
  return (
    <div className="mi-page">
      <PageHeader
        subtitle="Centro de informes de finca, parcela y campaña, preparado para revisión visual."
        title="Informes"
      />

      <div className="mi-content-grid">
        {["Informe de campaña", "Resumen de finca", "Ficha de parcela"].map(
          (title) => (
            <article className="mi-list-card" key={title}>
              <span className="mi-demo-pill">PDF de ejemplo</span>
              <h2>{title}</h2>
              <p>Vista de ejemplo con datos Demo. El PDF no está disponible.</p>
              <div className="mi-list-card-footer">
                <span>2026–2027 · Demo</span>
                <span className="mi-state-pill">Sin archivo</span>
              </div>
            </article>
          ),
        )}
      </div>
    </div>
  );
}

export function MarketPage() {
  return (
    <div className="mi-page">
      <PageHeader
        subtitle="Referencia visual del mercado del aceite. No representa una cotización ni se conecta a una fuente real."
        title="Mercado del aceite"
      />
      <div className="mi-detail-grid">
        <div className="mi-detail-card">
          <span>Precio de referencia · Demo</span>
          <strong>{demoDashboard.market.price}</strong>
        </div>
        <div className="mi-detail-card">
          <span>Variación ilustrativa</span>
          <strong>{demoDashboard.market.change}</strong>
        </div>
        <div className="mi-detail-card">
          <span>Fuente</span>
          <strong>Fuente demo</strong>
        </div>
      </div>
      <section className="mi-panel">
        <div className="mi-panel-heading">
          <h2>Histórico ilustrativo</h2>
          <span className="mi-demo-pill">Sin cotización real</span>
        </div>
        <Sparkline />
        <DemoNote>
          {demoDashboard.market.source}. No usar para tomar decisiones
          comerciales.
        </DemoNote>
      </section>
    </div>
  );
}

export function WeatherPage() {
  return (
    <div className="mi-page">
      <PageHeader
        subtitle={`Previsión ilustrativa para ${demoDashboard.location}. Sin conexión con proveedor meteorológico.`}
        title="Tiempo y radar"
      />
      <div className="mi-detail-grid">
        <div className="mi-detail-card">
          <span>Temperatura · Demo</span>
          <strong>{demoDashboard.weather.temperature}</strong>
        </div>
        <div className="mi-detail-card">
          <span>Precipitación ilustrativa</span>
          <strong>{demoDashboard.weather.rain}</strong>
        </div>
        <div className="mi-detail-card">
          <span>Humedad · Demo</span>
          <strong>{demoDashboard.weather.humidity}</strong>
        </div>
      </div>
      <section className="mi-panel">
        <div className="mi-panel-heading">
          <h2>Previsión demo</h2>
          <span className="mi-demo-pill">Sin fuente meteorológica</span>
        </div>
        <p className="mi-page-subtitle">
          Condiciones y datos de ejemplo para mostrar la estructura de la
          pantalla.
        </p>
      </section>
      <section className="mi-panel mi-record-panel" id="radar">
        <div className="mi-panel-heading">
          <h2>Radar de lluvia</h2>
          <span className="mi-demo-pill">Vista esquemática Demo</span>
        </div>
        <div
          aria-label="Vista esquemática de radar; no corresponde a mediciones meteorológicas"
          className="mi-schematic-map"
          role="img"
        >
          <span className="mi-map-chip mi-map-chip-one">Bedmar · Demo</span>
          <span className="mi-map-chip mi-map-chip-two">Jaén · Demo</span>
        </div>
        <DemoNote>
          Representación decorativa sin datos de radar reales.
        </DemoNote>
      </section>
    </div>
  );
}

export function CooperativePage() {
  return (
    <div className="mi-page">
      <PageHeader
        subtitle="Esta demo no conoce ni presupone la cooperativa del agricultor."
        title="Cooperativa"
      />
      <div className="mi-empty-state">
        <strong>Sin cooperativa asignada</strong>
        <span>
          Los datos de demostración no vinculan a ninguna organización real.
        </span>
        <span>Datos de demostración · sin contacto ni avisos externos.</span>
      </div>
    </div>
  );
}

export function ProfilePage() {
  return (
    <div className="mi-page">
      <PageHeader
        subtitle="Identidad ficticia para revisar la composición visual del perfil."
        title="Perfil de demostración"
      />
      <div className="mi-detail-grid">
        <div className="mi-detail-card">
          <span>Nombre</span>
          <strong>Usuario Demo</strong>
        </div>
        <div className="mi-detail-card">
          <span>Municipio</span>
          <strong>Bedmar · Demo</strong>
        </div>
        <div className="mi-detail-card">
          <span>Provincia</span>
          <strong>Jaén · Demo</strong>
        </div>
      </div>
      <p className="mi-panel-note">
        No hay identidad real, cooperativa ni preferencias guardadas en esta
        vista.
      </p>
    </div>
  );
}

export function AccountPage() {
  return (
    <div className="mi-page">
      <PageHeader
        subtitle="Preparación visual de cuenta. No hay sesión ni sincronización conectadas."
        title="Cuenta de demostración"
      />
      <div className="mi-detail-grid">
        <div className="mi-detail-card">
          <span>Sesión</span>
          <strong>Sin sesión</strong>
        </div>
        <div className="mi-detail-card">
          <span>Sync</span>
          <strong>Sync no conectado</strong>
        </div>
        <div className="mi-detail-card">
          <span>Privacidad</span>
          <strong>Demo local</strong>
        </div>
      </div>
      <div className="mi-empty-state">
        <strong>Sin cuenta conectada</strong>
        <span>
          El acceso, recuperación y sincronización pertenecen a una fase
          posterior.
        </span>
      </div>
    </div>
  );
}
