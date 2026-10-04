import Link from "next/link";
import type { ReactNode } from "react";

type PublicPageFrameProps = Readonly<{
  eyebrow?: string;
  title: string;
  description: string;
  children: ReactNode;
}>;

function PublicPageFrame({
  eyebrow = "Mágina Olivo · Información",
  title,
  description,
  children,
}: PublicPageFrameProps) {
  return (
    <main className="public-content-page page-wrap">
      <header className="public-content-intro">
        <p className="page-eyebrow">{eyebrow}</p>
        <h1 className="page-title">{title}</h1>
        <p className="page-description">{description}</p>
      </header>
      {children}
    </main>
  );
}

function InfoCard({
  title,
  description,
  href,
  linkLabel = "Ver en demo",
}: Readonly<{
  title: string;
  description: string;
  href?: string;
  linkLabel?: string;
}>) {
  return (
    <article className="public-content-card">
      <h3>{title}</h3>
      <p>{description}</p>
      {href ? (
        <Link href={href}>
          {linkLabel} <span aria-hidden="true">→</span>
        </Link>
      ) : null}
    </article>
  );
}

function Section({
  title,
  description,
  id,
  children,
}: Readonly<{
  title: string;
  description?: string;
  id?: string;
  children: ReactNode;
}>) {
  return (
    <section className="public-content-section" id={id}>
      <div className="public-content-section-heading">
        <h2>{title}</h2>
        {description ? <p>{description}</p> : null}
      </div>
      {children}
    </section>
  );
}

export function FunctionsPage() {
  return (
    <PublicPageFrame
      title="Lo que necesitas para llevar el olivar al día"
      description="Fincas, cuaderno y campañas se reúnen con información útil para consultar y organizar el trabajo del olivar."
    >
      <Section
        title="El trabajo del olivar"
        description="Organiza tus fincas y consulta la actividad de cada campaña."
      >
        <div className="public-content-grid">
          <InfoCard
            title="Mi Campo"
            description="Fincas, parcelas y ubicación de la explotación en un mismo espacio."
            href="/mi/fincas"
          />
          <InfoCard
            title="Cuaderno"
            description="Registra trabajo, riego, tratamientos, pesadas, jornales y gastos."
            href="/mi/cuaderno"
          />
          <InfoCard
            title="Campaña"
            description="Consulta actividad, producción y costes organizados por temporada."
            href="/mi/campanas"
          />
        </div>
      </Section>
      <Section
        title="Información para decidir"
        description="Consulta referencias y resúmenes vinculados al campo."
      >
        <div className="public-content-grid">
          <InfoCard
            title="Tiempo y radar"
            description="Consulta la previsión y la lluvia de tu entorno."
            href="/mi/tiempo"
          />
          <InfoCard
            title="Mercado del aceite"
            description="Sigue precios de referencia y su evolución."
            href="/mi/mercado"
          />
          <InfoCard
            title="Documentos e informes"
            description="Consulta la preparación visual de documentos e informes de campaña."
            href="/mi/informes"
          />
        </div>
      </Section>
      <Section
        title="Territorio y comunidad"
        description="Información local junto a las herramientas de gestión agrícola."
      >
        <div className="public-content-grid">
          <InfoCard
            title="Avisos y noticias"
            description="Un espacio para comunicaciones útiles del territorio."
            href="/#territorio"
            linkLabel="Ver territorio"
          />
          <InfoCard
            title="Cooperativas"
            description="Consulta información y contacto de cooperativas de la zona."
            href="/mi/cooperativa"
          />
          <InfoCard
            title="Empresas locales"
            description="Conoce los espacios informativos para servicios vinculados al olivar."
            href="/anunciate"
            linkLabel="Información para empresas"
          />
        </div>
      </Section>
      <p className="public-demo-note">
        Las vistas privadas enlazadas son demostraciones visuales. No conectan
        una cuenta ni muestran datos personales.
      </p>
      <p className="public-content-cta">
        <Link href="/como-funciona">Ver cómo se organiza el trabajo →</Link>
      </p>
    </PublicPageFrame>
  );
}

const productSteps = [
  {
    title: "Finca",
    description: "Crea el espacio desde el que organizas tu explotación.",
  },
  {
    title: "Parcela",
    description: "Agrupa las parcelas que pertenecen a cada finca.",
  },
  {
    title: "Campaña",
    description: "Consulta cada temporada sin perder de vista el histórico.",
  },
  {
    title: "Registrar",
    description:
      "Anota trabajos, riegos, tratamientos, pesadas, jornales y gastos.",
  },
  {
    title: "Cosecha",
    description: "Reúne la actividad y las entregas de la temporada.",
  },
  {
    title: "Analizar",
    description:
      "Revisa los resúmenes disponibles de producción y costes por campaña.",
  },
  {
    title: "Informe",
    description:
      "Consulta la información de campaña en los formatos que estén disponibles.",
  },
];

export function HowItWorksPage() {
  return (
    <PublicPageFrame
      title="De la finca al informe, paso a paso"
      description="La app ordena el trabajo alrededor de tus fincas, sus parcelas y cada campaña."
    >
      <Section title="Un recorrido sencillo">
        <ol className="public-process-list">
          {productSteps.map((step, index) => (
            <li key={step.title}>
              <span className="public-process-number">
                {String(index + 1).padStart(2, "0")}
              </span>
              <div>
                <h3>{step.title}</h3>
                <p>{step.description}</p>
              </div>
            </li>
          ))}
        </ol>
      </Section>
      <aside className="public-demo-note">
        <strong>Hecha para el trabajo en el campo.</strong> La app prioriza el
        registro local y está pensada para que la jornada no dependa de tener
        conexión.
      </aside>
      <p className="public-content-cta">
        <Link href="/funciones">Explorar las funciones →</Link>
      </p>
    </PublicPageFrame>
  );
}

export function NewsPage() {
  return (
    <PublicPageFrame
      title="Novedades de Mágina Olivo"
      description="Este espacio reunirá información sobre cambios y mejoras del producto."
    >
      <section className="public-empty-state" aria-live="polite">
        <span className="public-empty-mark" aria-hidden="true">
          ✳
        </span>
        <h2>Aún no hay novedades publicadas</h2>
        <p>
          Cuando haya información confirmada sobre una actualización, se
          publicará aquí.
        </p>
      </section>
      <p className="public-content-cta">
        <Link href="/funciones">Conocer la app →</Link>
      </p>
    </PublicPageFrame>
  );
}

const helpTopics = [
  {
    id: "fincas",
    title: "Crear una finca o parcela",
    description:
      "La información se organiza desde la finca y sus parcelas. La vista web de consulta está marcada como demo.",
    href: "/mi/fincas",
  },
  {
    id: "catastro",
    title: "Catastro y mapa",
    description:
      "La referencia cartográfica ayuda a localizar parcelas. Revisa siempre la información de la finca antes de usarla.",
    href: "/mi/mapa",
  },
  {
    id: "campana",
    title: "Consultar una campaña",
    description:
      "Consulta los registros de trabajo y la información disponible de cada temporada.",
    href: "/mi/cuaderno",
  },
  {
    id: "pesada",
    title: "Registrar una pesada",
    description:
      "Consulta la orientación sobre entregas y pesadas dentro del Cuaderno.",
    href: "/mi/cuaderno",
  },
  {
    id: "jornales",
    title: "Registrar un jornal",
    description:
      "Los jornales se consultan como parte de la actividad de campaña.",
    href: "/mi/cuaderno",
  },
  {
    id: "maquinaria",
    title: "Maquinaria y gastos",
    description:
      "Consulta la actividad y los gastos registrados durante la campaña.",
    href: "/mi/campanas",
  },
  {
    id: "informacion",
    title: "Tiempo y mercado",
    description:
      "Las páginas de tiempo y mercado reúnen información de referencia. Comprueba la fecha y la fuente que muestre cada dato.",
    href: "/funciones",
  },
  {
    id: "cuenta",
    title: "Cuenta y cambio de móvil",
    description:
      "La cuenta y la recuperación de información se explicarán cuando sus pasos oficiales estén disponibles. No hay un flujo web conectado en esta demo.",
  },
  {
    id: "pdf",
    title: "Informes y PDF",
    description:
      "La disponibilidad de informes descargables se indicará cuando el formato y el canal estén confirmados.",
    href: "/mi/informes",
  },
];

export function HelpPage() {
  return (
    <PublicPageFrame
      title="Ayuda para el trabajo diario"
      description="Encuentra orientación por tarea. Las pantallas privadas que se abren desde esta web son demos, no una sesión de usuario."
    >
      <nav className="public-help-index" aria-label="Temas de ayuda">
        {helpTopics.map((topic) => (
          <a href={`#${topic.id}`} key={topic.id}>
            {topic.title}
          </a>
        ))}
      </nav>
      <div className="public-help-list">
        {helpTopics.map((topic) => (
          <section
            className="public-help-topic"
            id={topic.id}
            key={topic.id}
            aria-labelledby={`${topic.id}-title`}
          >
            <div>
              <h2 id={`${topic.id}-title`}>{topic.title}</h2>
              <p>{topic.description}</p>
            </div>
            {topic.href ? (
              <Link href={topic.href}>
                {topic.href.startsWith("/mi")
                  ? "Abrir demo visual"
                  : "Ver información"}{" "}
                <span aria-hidden="true">→</span>
              </Link>
            ) : null}
          </section>
        ))}
      </div>
    </PublicPageFrame>
  );
}

export function DownloadPage() {
  return (
    <PublicPageFrame
      title="Mágina Olivo para Android"
      description="Aquí se publicará la información del canal oficial de descarga cuando el enlace y la versión estén confirmados."
    >
      <Section title="Descarga oficial de Android">
        <div className="public-status-card" role="status">
          <span className="public-status-label">Estado del canal</span>
          <h2>El enlace oficial está pendiente de confirmación</h2>
          <p>
            Por ahora, esta página no ofrece un enlace de Google Play ni un APK.
            No descargues instaladores desde sitios no confirmados.
          </p>
        </div>
      </Section>
      <Section title="Versión y requisitos">
        <p>
          La versión, los requisitos de Android y el historial de cambios se
          mostrarán cuando exista una ficha pública de descarga verificada.
        </p>
      </Section>
      <p className="public-content-cta">
        <Link href="/funciones">Mientras tanto, conoce las funciones →</Link>
      </p>
    </PublicPageFrame>
  );
}

export function AdvertisePage() {
  return (
    <PublicPageFrame
      title="Anúnciate cerca de quienes trabajan el olivar"
      description="Mágina Olivo prepara espacios de información para empresas y servicios locales vinculados al sector."
    >
      <Section title="Espacios locales" id="empresas">
        <div className="public-content-grid">
          <InfoCard
            title="Publicidad local"
            description="La información comercial se distinguirá del contenido agrícola y editorial."
          />
          <InfoCard
            title="Ámbito municipal"
            description="La disponibilidad por municipio se confirmará antes de ofrecer cada espacio."
          />
          <InfoCard
            title="Formatos y condiciones"
            description="Los formatos, criterios y condiciones comerciales están pendientes de validación."
          />
        </div>
      </Section>
      <Section title="Pedir información" id="contacto">
        <div className="public-status-card">
          <span className="public-status-label">Canal comercial</span>
          <h2>Canal de contacto pendiente de confirmar</h2>
          <p>
            Publicaremos aquí el contacto comercial cuando esté confirmado. No
            se recogen datos personales ni se tramitan contrataciones desde esta
            página.
          </p>
        </div>
      </Section>
      <p className="public-privacy-note">
        No envíes información personal hasta que se publique el canal de
        contacto y su aviso de privacidad.
      </p>
    </PublicPageFrame>
  );
}

const legalCopy = {
  privacidad: {
    title: "Privacidad",
    description:
      "Información sobre el tratamiento de datos de este sitio y de Mágina Olivo.",
    topics: [
      "Responsable del tratamiento y datos de contacto.",
      "Datos que se recogen y finalidades.",
      "Base jurídica, plazos de conservación y destinatarios.",
      "Derechos de las personas y cómo ejercerlos.",
    ],
  },
  terminos: {
    title: "Términos de uso",
    description: "Condiciones de acceso y uso de los servicios Mágina Olivo.",
    topics: [
      "Titular del servicio y ámbito de aplicación.",
      "Condiciones de uso y responsabilidades.",
      "Disponibilidad, cambios y soporte.",
      "Propiedad intelectual y vías de contacto.",
    ],
  },
  aviso: {
    title: "Aviso legal",
    description: "Información sobre la titularidad de este sitio web.",
    topics: [
      "Titular, domicilio y datos registrales.",
      "Información de contacto y comunicaciones.",
      "Condiciones generales de uso del sitio.",
      "Responsabilidad y propiedad intelectual.",
    ],
  },
} as const;

export function LegalPage({
  kind,
}: Readonly<{ kind: keyof typeof legalCopy }>) {
  const copy = legalCopy[kind];

  return (
    <PublicPageFrame
      eyebrow="Información legal · provisional"
      title={copy.title}
      description={copy.description}
    >
      <aside className="public-legal-warning" role="note">
        <strong>Contenido provisional · pendiente de revisión legal.</strong>
        <span>
          Esta página no contiene todavía las condiciones definitivas ni debe
          utilizarse como texto legal final.
        </span>
      </aside>
      <Section title="Información pendiente de confirmar">
        <ul className="public-legal-list">
          {copy.topics.map((topic) => (
            <li key={topic}>
              <span>{topic}</span>
              <span className="public-pending-label">Pendiente</span>
            </li>
          ))}
        </ul>
      </Section>
    </PublicPageFrame>
  );
}
