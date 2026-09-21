"use client";

import { CinematicScrollCanvas } from "@/components/CinematicScrollCanvas";
import { PhoneScreen, type ScreenKind } from "@/components/CinematicHome";
import { SceneImage } from "@/components/SceneImage";
import { pilotDesktopFrames, pilotMobileFrames } from "@/data/v2/cinematicSequence";
import { visualAssets } from "@/lib/visualAssets";
import Image from "next/image";
import { useEffect, useRef, useState } from "react";

const clamp = (value: number) => Math.min(1, Math.max(0, value));
const phase = (value: number, start: number, end: number) =>
  clamp((value - start) / Math.max(0.001, end - start));

const productMoments: Array<{
  number: string;
  title: string;
  copy: string;
  screen: ScreenKind;
}> = [
  {
    number: "01",
    title: "Tus fincas.",
    copy: "Todo empieza por saber qué tienes.",
    screen: "farms",
  },
  {
    number: "02",
    title: "Tus parcelas.",
    copy: "Cada parcela con su información y su historia.",
    screen: "parcel",
  },
  {
    number: "03",
    title: "Tu mapa.",
    copy: "Tu tierra delante de ti, siempre localizada.",
    screen: "map",
  },
  {
    number: "04",
    title: "Tu campaña.",
    copy: "Cada labor queda registrada cuando ocurre.",
    screen: "campaign",
  },
  {
    number: "05",
    title: "Tu cosecha.",
    copy: "Producción, entregas y rendimiento sin perder el hilo.",
    screen: "harvest",
  },
  {
    number: "06",
    title: "Tus números.",
    copy: "Gastos y documentos para entender el resultado.",
    screen: "expenses",
  },
  {
    number: "07",
    title: "Tu tiempo.",
    copy: "Información útil cuando toca decidir.",
    screen: "weather",
  },
  {
    number: "08",
    title: "Tu histórico.",
    copy: "Compara campañas y aprende de cada año.",
    screen: "history",
  },
];

export function CinematicHomeV2() {
  const basePath = process.env.NEXT_PUBLIC_BASE_PATH || "";
  const heroRef = useRef<HTMLElement>(null);
  const storyRef = useRef<HTMLElement>(null);
  const productRef = useRef<HTMLElement>(null);
  const [activeProduct, setActiveProduct] = useState(0);

  useEffect(() => {
    let frame = 0;

    const update = () => {
      frame = 0;

      if (heroRef.current) {
        const rect = heroRef.current.getBoundingClientRect();
        const progress = clamp(-rect.top / Math.max(1, window.innerHeight));
        heroRef.current.style.setProperty("--v2-hero-progress", String(progress));
      }

      if (storyRef.current) {
        const rect = storyRef.current.getBoundingClientRect();
        const travel = Math.max(1, storyRef.current.offsetHeight - window.innerHeight);
        const progress = clamp(-rect.top / travel);
        const fieldOut = phase(progress, 0.18, 0.38);
        const detailIn = phase(progress, 0.18, 0.34);
        const detailOut = phase(progress, 0.48, 0.66);
        const phoneIn = phase(progress, 0.52, 0.72);
        const phoneFocus = phase(progress, 0.68, 0.92);
        const takeover = phase(progress, 0.72, 0.93);
        const canvasFade = phase(progress, 0.84, 0.98);

        storyRef.current.style.setProperty("--v2-story-progress", String(progress));
        storyRef.current.style.setProperty("--v2-field-opacity", String(1 - fieldOut * 0.92));
        storyRef.current.style.setProperty("--v2-detail-opacity", String(detailIn * (1 - detailOut)));
        storyRef.current.style.setProperty("--v2-phone-opacity", String(phoneIn));
        storyRef.current.style.setProperty("--v2-field-scale", String(1.02 + progress * 0.09));
        storyRef.current.style.setProperty("--v2-detail-scale", String(1.12 - detailIn * 0.08));
        storyRef.current.style.setProperty("--v2-phone-scale", String(1.12 - phoneFocus * 0.12));
        storyRef.current.style.setProperty("--v2-copy-a", String(1 - phase(progress, 0.12, 0.26)));
        storyRef.current.style.setProperty(
          "--v2-copy-b",
          String(phase(progress, 0.24, 0.38) * (1 - phase(progress, 0.50, 0.62))),
        );
        storyRef.current.style.setProperty("--v2-copy-c", String(phase(progress, 0.64, 0.80)));
        storyRef.current.style.setProperty("--v2-takeover", String(takeover));
        storyRef.current.style.setProperty("--v2-canvas-fade", String(canvasFade));
        storyRef.current.style.setProperty(
          "--v2-takeover-scale",
          String(0.72 + takeover * 0.28),
        );
        storyRef.current.style.setProperty(
          "--v2-takeover-rotate",
          `${-8 + takeover * 8}deg`,
        );
        storyRef.current.style.setProperty(
          "--v2-takeover-y",
          `${(1 - takeover) * 18}vh`,
        );
      }
    };

    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };

    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);

    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      if (frame) cancelAnimationFrame(frame);
    };
  }, []);

  useEffect(() => {
    const nodes = Array.from(
      document.querySelectorAll<HTMLElement>("[data-v2-product-step]"),
    );

    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];

        if (!visible) return;
        const index = Number((visible.target as HTMLElement).dataset.v2ProductStep);
        if (!Number.isNaN(index)) setActiveProduct(index);
      },
      {
        threshold: [0.25, 0.45, 0.62],
        rootMargin: "-12% 0px -24% 0px",
      },
    );

    nodes.forEach((node) => observer.observe(node));
    return () => observer.disconnect();
  }, []);

  return (
    <div className="v2-home">
      <section className="v2-hero" id="inicio" ref={heroRef}>
        <div className="v2-hero-media" aria-hidden="true">
          <SceneImage asset={visualAssets.hero} priority sizes="100vw" />
        </div>
        <div className="v2-hero-shade" />

        <div className="v2-hero-copy shell">
          <p className="v2-kicker">Tecnología sencilla para quien vive del olivar</p>
          <h1>Tu olivar<br />en buenas manos.</h1>
          <p className="v2-hero-summary">
            Fincas, parcelas y campañas en un solo lugar.
          </p>
          <div className="v2-hero-actions">
            <a className="v2-button v2-button-primary" href="#historia">
              Conocer la app <span aria-hidden="true">→</span>
            </a>
            <span className="v2-android-note">Android · Próximamente</span>
          </div>
        </div>

        <div className="v2-scroll-cue" aria-hidden="true">
          <span className="v2-scroll-mouse" />
          <span>Desliza para descubrir</span>
          <b>↓</b>
        </div>

        <div className="v2-hero-footer" aria-hidden="true">
          <span>01</span>
          <p>Del campo a tu móvil.<br />Todo tu olivar, en un solo lugar.</p>
          <i />
          <span>01 / 06</span>
        </div>
      </section>

      <section className="v2-intro" id="historia">
        <div className="shell v2-intro-inner">
          <p className="v2-kicker">Tu día empieza en el campo</p>
          <h2>Mira. Decide.<br />Registra.</h2>
          <p>
            Mágina Olivo acompaña lo que ya haces cada día, sin convertirlo en
            más trabajo.
          </p>
        </div>
      </section>

      <section className="v2-story" ref={storyRef} aria-label="Del campo a Mágina Olivo">
        <CinematicScrollCanvas
          className="v2-story-canvas-root"
          reducedMotionPoster={`${basePath}${visualAssets.phoneContext.finalSrc}`}
          desktopFrames={pilotDesktopFrames.map((frame) => ({
            ...frame,
            src: `${basePath}${frame.src}`,
          }))}
          mobileFrames={pilotMobileFrames.map((frame) => ({
            ...frame,
            src: `${basePath}${frame.src}`,
          }))}
        />
        <div className="v2-story-sticky">
          <div className="v2-story-overlay" />

          <div className="v2-story-copy v2-story-copy-a">
            <span>01</span>
            <h2>Tu tierra.</h2>
          </div>
          <div className="v2-story-copy v2-story-copy-b">
            <span>02</span>
            <h2>Cada detalle<br />importa.</h2>
          </div>
          <div className="v2-story-copy v2-story-copy-c">
            <span>03</span>
            <h2>Todo en<br />tu mano.</h2>
          </div>

          <div className="v2-story-device-takeover" aria-hidden="true">
            <div className="phone-shell v2-story-phone-shell">
              <div className="phone-camera" />
              <PhoneScreen kind="welcome" />
            </div>
          </div>
        </div>
      </section>

      <section className="v2-product v2-product-scrolly" id="producto" ref={productRef}>
        <div className="shell v2-product-head">
          <p className="v2-kicker">Una sola app</p>
          <h2>Todo tu olivar.</h2>
          <p>Una función cada vez. Justo cuando la necesitas.</p>
        </div>

        <div className="shell v2-product-story">
          <div className="v2-product-steps">
            {productMoments.map((moment, index) => (
              <article
                className={`v2-product-moment ${activeProduct === index ? "is-active" : ""}`}
                data-v2-product-step={index}
                key={moment.number}
              >
                <span>{moment.number}</span>
                <h3>{moment.title}</h3>
                <p>{moment.copy}</p>
              </article>
            ))}
          </div>

          <div className="v2-product-device-wrap" aria-label="Vista de la aplicación Mágina Olivo">
            <div className="v2-product-device">
              <div className="phone-shell phone-shell-small">
                <div className="phone-camera" />
                <div
                  className="v2-product-screen-transition"
                  key={productMoments[activeProduct]?.screen ?? "farms"}
                >
                  <PhoneScreen kind={productMoments[activeProduct]?.screen ?? "farms"} />
                </div>
              </div>
            </div>
            <div className="v2-product-progress" aria-hidden="true">
              <span>{String(activeProduct + 1).padStart(2, "0")}</span>
              <i>
                <b style={{ width: `${((activeProduct + 1) / productMoments.length) * 100}%` }} />
              </i>
              <span>{String(productMoments.length).padStart(2, "0")}</span>
            </div>

            <div
              className="v2-product-mobile-copy"
              key={`mobile-${productMoments[activeProduct]?.number ?? "01"}`}
              aria-hidden="true"
            >
              <span>{productMoments[activeProduct]?.number}</span>
              <h3>{productMoments[activeProduct]?.title}</h3>
              <p>{productMoments[activeProduct]?.copy}</p>
            </div>
          </div>
        </div>
      </section>

      <section className="v2-return">
        <div className="v2-return-media" aria-hidden="true">
          <SceneImage asset={visualAssets.fieldSequence} sizes="100vw" />
        </div>
        <div className="v2-return-overlay" />
        <div className="shell v2-return-copy">
          <p className="v2-kicker light">Menos papeles. Más control.</p>
          <h2>La tecnología desaparece.<br />Tu trabajo sigue.</h2>
        </div>
      </section>

      <section className="v2-final" id="descarga">
        <Image
          className="v2-final-mark"
          src={`${basePath}/brand/v2-mark.svg`}
          alt=""
          width={64}
          height={64}
        />
        <p className="v2-kicker light">Mágina Olivo</p>
        <h2>Todo tu olivar.<br />En un solo lugar.</h2>
        <p>La primera versión para Android está en desarrollo.</p>
        <a className="v2-button v2-button-light" href={`${basePath}/contacto`}>
          Quiero conocerla <span aria-hidden="true">→</span>
        </a>
      </section>
    </div>
  );
}
