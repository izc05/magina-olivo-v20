import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";

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
    ? "/magina-olivo-v20/v3-review/storyboard-concept.webp"
    : "/v3-review/storyboard-concept.webp";

export default function V3ReviewPage() {
  return (
    <>
      <header className="v3-review-topline">
        <Link className="brand" href="/">
          Mágina Olivo
        </Link>
        <span>Review interna · WEB-0C</span>
      </header>
      <main className="v3-review page-wrap">
        <p className="page-eyebrow">WEB V3 · Gate V3-A</p>
        <h1 className="page-title">Revisión de continuidad</h1>
        <p className="page-description">
          Storyboard para revisar hero, persona, gesto y takeover antes de
          producir la Home. El Visual Lock de #394 manda sobre esta propuesta.
        </p>
        <p className="v3-review-status" role="status">
          12 keyframes definidos · arte original pendiente · aprobación
          pendiente
        </p>
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
            alt="Lámina conceptual generada para revisar continuidad entre olivar, agricultor, móvil, producto y escritorio"
            width={1222}
            height={1287}
            sizes="(max-width: 800px) 100vw, 88rem"
            loading="eager"
          />
          <figcaption>
            Concepto visual asistido por IA para revisar tono y continuidad; no
            está aprobado como fotografía ni asigna un asset final a cada
            keyframe.
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
                <span>Fuente: producción original V3 pendiente</span>
                <span>Revisión: pendiente</span>
              </footer>
            </article>
          ))}
        </div>
        <p className="v3-review-note">
          Esta página documenta el storyboard; no contiene fotografías generadas
          o aprobadas ni registra decisiones. No se inicia la producción extensa
          hasta revisar hero desktop/móvil, continuidad y transición
          campo→móvil.
        </p>
      </main>
    </>
  );
}
