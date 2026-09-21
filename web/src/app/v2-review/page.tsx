import type { Metadata } from "next";
import { productionPriority, v2Keyframes } from "@/data/v2/keyframes";

export const metadata: Metadata = {
  title: "V2 Keyframe Review · Mágina Olivo",
  robots: {
    index: false,
    follow: false,
  },
};

const acts = [
  ["field", "Acto 1 · Campo"],
  ["phone", "Acto 2 · Campo → móvil"],
  ["product", "Acto 3 · Producto"],
  ["return", "Acto 4 · Regreso"],
] as const;

export default function V2ReviewPage() {
  const basePath = process.env.NEXT_PUBLIC_BASE_PATH || "";

  return (
    <main className="v2-review">
      <header className="v2-review-header">
        <div>
          <p>Mágina Olivo · Web V2</p>
          <h1>Keyframe review board</h1>
        </div>
        <div className="v2-review-legend" aria-label="Leyenda de estados">
          <span data-status="pilot">Pilot</span>
          <span data-status="candidate">Candidate</span>
          <span data-status="final">Final</span>
        </div>
      </header>

      <section className="v2-review-priority">
        <strong>Prioridad de producción</strong>
        <div>
          {productionPriority.map((id) => (
            <span key={id}>{id}</span>
          ))}
        </div>
      </section>

      {acts.map(([act, label]) => {
        const frames = v2Keyframes.filter((frame) => frame.act === act);

        return (
          <section className="v2-review-act" key={act}>
            <div className="v2-review-act-title">
              <span>{label}</span>
              <b>{frames.length} planos</b>
            </div>

            <div className="v2-review-grid">
              {frames.map((frame) => (
                <article className="v2-review-card" key={frame.id}>
                  <div className="v2-review-card-head">
                    <div>
                      <b>{frame.id}</b>
                      <h2>{frame.title}</h2>
                    </div>
                    <div className="v2-review-statuses">
                      <span data-status={frame.desktop.status}>
                        D · {frame.desktop.status}
                      </span>
                      <span data-status={frame.mobile.status}>
                        M · {frame.mobile.status}
                      </span>
                    </div>
                  </div>

                  <div className="v2-review-media">
                    <figure>
                      <div className="v2-review-desktop">
                        <img
                          src={`${basePath}${frame.desktop.src}`}
                          alt=""
                          style={{
                            objectPosition: `${frame.desktop.focalX * 100}% ${frame.desktop.focalY * 100}%`,
                          }}
                        />
                      </div>
                      <figcaption>Desktop</figcaption>
                    </figure>

                    <figure>
                      <div className="v2-review-mobile">
                        <img
                          src={`${basePath}${frame.mobile.src}`}
                          alt=""
                          style={{
                            objectPosition: `${frame.mobile.focalX * 100}% ${frame.mobile.focalY * 100}%`,
                          }}
                        />
                      </div>
                      <figcaption>Mobile</figcaption>
                    </figure>
                  </div>

                  {frame.copy && <p className="v2-review-copy">“{frame.copy}”</p>}

                  <div className="v2-review-continuity">
                    {frame.continuity.map((item) => (
                      <span key={item}>{item}</span>
                    ))}
                  </div>
                </article>
              ))}
            </div>
          </section>
        );
      })}
    </main>
  );
}
