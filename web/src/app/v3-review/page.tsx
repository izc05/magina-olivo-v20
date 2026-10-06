import type { Metadata } from "next";
import Image from "next/image";
import { BrandLogo } from "@/components/layouts/BrandLogo";

export const metadata: Metadata = {
  title: "Review storyboard V3 | Mágina Olivo",
  description: "Mesa de revisión interna para los keyframes de la Home V3.",
  robots: { index: false, follow: false },
};

const keyframes = [
  {
    id: "K01",
    act: "01 · El olivar",
    title: "Hero desktop",
    copy: "Tu olivar cambia cada día.",
    desktop:
      "Plano ancho entre olivos al amanecer, loma al fondo y espacio editorial para el titular.",
    mobile:
      "Composición vertical propia desde el camino; horizonte y copy dentro de zona segura.",
    continuity: "Luz natural cálida; sin persona ni teléfono en foco todavía.",
  },
  {
    id: "K02",
    act: "01 · El olivar",
    title: "Hero mobile",
    copy: "Tu olivar cambia cada día.",
    desktop: "Pareja de apertura del hero: mismo olivar y línea de horizonte.",
    mobile:
      "Vertical dedicada, no crop del desktop; espacio para el titular y CTA.",
    continuity: "Aprobar como pareja responsive con K01.",
  },
  {
    id: "K03",
    act: "02 · La decisión",
    title: "Rama y fruto",
    copy: "Lo ves.",
    desktop:
      "Detalle de rama en primer término con el agricultor fuera de foco.",
    mobile: "Rama y mano comparten eje vertical con espacio para copy.",
    continuity: "No atribuir variedad ni estado agronómico.",
  },
  {
    id: "K04",
    act: "02 · La decisión",
    title: "El agricultor",
    copy: "—",
    desktop:
      "Plano medio observando el olivar, gesto natural y aire editorial.",
    mobile: "Retrato vertical con el olivar reconocible detrás.",
    continuity:
      "Fijar persona adulta, vestuario, manos, mirada y luz para toda la secuencia.",
  },
  {
    id: "K05",
    act: "03 · El gesto",
    title: "Inicio del gesto móvil",
    copy: "Lo decides.",
    desktop: "La mano introduce el mismo teléfono desde el borde de escena.",
    mobile: "Teléfono emerge desde abajo; el campo aún domina el encuadre.",
    continuity: "Definir teléfono, funda, mano y orientación.",
  },
  {
    id: "K06",
    act: "03 · El gesto",
    title: "Móvil en mano",
    copy: "—",
    desktop: "Primer plano integrado en la escena, con reflejos naturales.",
    mobile: "Relación mano/teléfono legible en marco vertical.",
    continuity: "UI conceptual siempre rotulada Demo.",
  },
  {
    id: "K07",
    act: "04 · Campo → producto",
    title: "Móvil frontal",
    copy: "Y lo registras.",
    desktop: "El mismo teléfono gira hacia cámara y ocupa el eje visual.",
    mobile: "Frontal vertical, proporciones reales y margen seguro.",
    continuity: "Aprobar orientación y escala antes del takeover.",
  },
  {
    id: "K08",
    act: "04 · Campo → producto",
    title: "Takeover",
    copy: "Y lo registras.",
    desktop:
      "La pantalla crece mientras la fotografía pierde profundidad gradualmente.",
    mobile:
      "Composición vertical con transición visible y pantalla sin recortes.",
    continuity:
      "Punto principal de aprobación: giro, máscara y continuidad campo→producto.",
  },
  {
    id: "K09",
    act: "05 · Producto vivo",
    title: "Mi Campo",
    copy: "Mi Campo",
    desktop:
      "La interfaz toma el espacio sin presentarse como una captura aislada.",
    mobile: "Composición móvil propia y legible.",
    continuity:
      "UI DOM; contrastar con Android vigente cuando haya referencia canónica.",
  },
  {
    id: "K10",
    act: "05 · Producto vivo",
    title: "Campaña",
    copy: "Campaña",
    desktop: "Resumen de campaña con continuidad desde Mi Campo.",
    mobile: "Una columna, con una idea visible por momento.",
    continuity:
      "Sin cifras reales; cualquier contenido conceptual se marca Demo.",
  },
  {
    id: "K11",
    act: "06 · Tu olivar también en PC",
    title: "Transición a escritorio",
    copy: "Tu explotación, siempre bajo control.",
    desktop: "El teléfono abre el encuadre a producto en portátil.",
    mobile: "El escritorio se revela en panel vertical simplificado.",
    continuity: "Rotular Próximamente / Demo; no insinuar sync productivo.",
  },
  {
    id: "K12",
    act: "07 · Cierre",
    title: "Composición final",
    copy: "Tu olivar, claro y al día.",
    desktop: "Producto y olivar en composición limpia con aire para CTA.",
    mobile: "Cierre vertical con titulares y CTA legibles.",
    continuity: "Alinear jerarquía y CTA con #394.",
  },
];

const storyboardImage =
  process.env.GITHUB_PAGES === "true"
    ? "/magina-olivo-v20/v3-review/storyboard-12frames-concept.png"
    : "/v3-review/storyboard-12frames-concept.png";
const reviewBase =
  process.env.GITHUB_PAGES === "true" ? "/magina-olivo-v20" : "";

export default function V3ReviewPage() {
  return (
    <>
      <header className="v3-review-topline">
        <BrandLogo href="/" label="Mágina Olivo, inicio" />
        <span>Review interna · WEB-0C</span>
      </header>
      <main className="v3-review page-wrap">
        <p className="page-eyebrow">WEB V3 · Gate V3-A</p>
        <h1 className="page-title">Revisión de continuidad</h1>
        <p className="page-description">
          Storyboard para revisar la continuidad del hero, la persona, el gesto
          y el paso del campo al producto. El Visual Lock de #394 manda sobre
          esta propuesta.
        </p>
        <p className="v3-review-status" role="status">
          Home estática · 12 keyframes conceptuales generados · aprobación de
          continuidad pendiente
        </p>
        <section aria-labelledby="reference-title">
          <h2 id="reference-title">Referencias compartidas del proyecto</h2>
          <p className="page-description">
            Estas imágenes guían la Home pública y la futura zona privada. La
            pantalla completa sirve como referencia de diseño; no se usa como
            asset de producto ni como fuente de datos.
          </p>
          <div className="v3-reference-grid">
            <figure className="v3-reference">
              <Image
                src={`${reviewBase}/v3-review/reference-public-home.jpg`}
                alt="Referencia visual compartida para la Home pública: hero fotográfico, producto móvil y secciones editoriales en crema y verde oliva"
                width={711}
                height={1536}
                sizes="(max-width: 800px) 100vw, 58rem"
                loading="eager"
              />
              <figcaption>
                Home pública · referencia proporcionada por el proyecto.
              </figcaption>
            </figure>
            <figure className="v3-reference">
              <Image
                src={`${reviewBase}/v3-review/reference-dashboard.jpg`}
                alt="Referencia visual compartida para la futura zona privada: navegación lateral y dashboard de explotación"
                width={1280}
                height={960}
                sizes="(max-width: 800px) 100vw, 42rem"
              />
              <figcaption>
                Dashboard privado · referencia para #391, aún no implementado.
              </figcaption>
            </figure>
          </div>
        </section>
        <nav className="v3-review-index" aria-label="Índice de keyframes">
          {keyframes.map(({ id, title }) => (
            <a href={`#${id}`} key={id}>
              {id} <span>{title}</span>
            </a>
          ))}
        </nav>
        <figure className="v3-review-contact-sheet">
          <Image
            src={storyboardImage}
            alt="Lámina conceptual de 12 escenas en el olivar, desde el amanecer y el agricultor hasta el móvil, la aplicación y el cierre"
            width={1536}
            height={1024}
            sizes="(max-width: 800px) 100vw, 88rem"
            loading="eager"
          />
          <figcaption>
            Concepto visual asistido por IA · lectura de izquierda a derecha y
            de arriba abajo: K01–K12. Solo para revisión de tono y continuidad;
            no es fotografía de producción ni un asset aprobado. La UI y los
            textos reales se incorporarán como DOM.
          </figcaption>
        </figure>
        <div className="v3-review-grid">
          {keyframes.map((frame) => (
            <article className="v3-frame" id={frame.id} key={frame.id}>
              <header className="v3-frame-heading">
                <span className="v3-frame-id">{frame.id}</span>
                <div>
                  <p className="v3-frame-act">{frame.act}</p>
                  <h2>{frame.title}</h2>
                </div>
                <span className="v3-frame-state">Pendiente</span>
              </header>
              <div className="v3-frame-layouts">
                <section aria-label="Composición desktop">
                  <h3>Desktop</h3>
                  <p>{frame.desktop}</p>
                </section>
                <section aria-label="Composición mobile">
                  <h3>Móvil</h3>
                  <p>{frame.mobile}</p>
                </section>
              </div>
              <p className="v3-frame-copy">
                <span>Copy / UI</span> {frame.copy}
              </p>
              <p className="v3-frame-continuity">
                <span>Continuidad</span> {frame.continuity}
              </p>
              <footer className="v3-frame-footer">
                <span>Concepto: lámina IA · original V3 pendiente</span>
                <span>Revisión: pendiente</span>
              </footer>
            </article>
          ))}
        </div>
        <p className="v3-review-note">
          La Home disponible sigue siendo estática. Este storyboard permite
          revisar los conceptos K01–K12 y mantiene pendiente su aprobación, la
          continuidad de personaje y el encuadre móvil antes de construir una
          secuencia cinematográfica controlada por scroll.
        </p>
      </main>
    </>
  );
}
