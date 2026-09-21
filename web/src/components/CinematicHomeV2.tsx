"use client";

import { SceneImage } from "@/components/SceneImage";
import { visualAssets } from "@/lib/visualAssets";
import Image from "next/image";
import { useEffect, useRef } from "react";

const clamp = (value: number) => Math.min(1, Math.max(0, value));
const phase = (value: number, start: number, end: number) =>
  clamp((value - start) / Math.max(0.001, end - start));

export function CinematicHomeV2() {
  const basePath = process.env.NEXT_PUBLIC_BASE_PATH || "";
  const heroRef = useRef<HTMLElement>(null);
  const storyRef = useRef<HTMLElement>(null);

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

  return (
    <div className="v2-home">
      <section className="v2-hero" id="inicio" ref={heroRef}>
        <div className="v2-hero-media" aria-hidden="true">
          <SceneImage asset={visualAssets.hero} priority sizes="100vw" />
        </div>
        <div className="v2-hero-shade" />

        <div className="v2-hero-brand">
          <Image
            src={`${basePath}/brand/v2-lockup.svg`}
            alt="Mágina Olivo"
            width={360}
            height={96}
            priority
          />
        </div>

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
        <div className="v2-story-sticky">
          <div className="v2-story-scene v2-story-scene-field" aria-hidden="true">
            <SceneImage asset={visualAssets.heritage} sizes="100vw" />
          </div>
          <div className="v2-story-scene v2-story-scene-detail" aria-hidden="true">
            <SceneImage asset={visualAssets.benefits} sizes="100vw" />
          </div>
          <div className="v2-story-scene v2-story-scene-phone" aria-hidden="true">
            <SceneImage asset={visualAssets.phoneContext} sizes="100vw" />
          </div>
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
        </div>
      </section>

      <section className="v2-product" id="producto">
        <div className="shell v2-product-head">
          <p className="v2-kicker">Una sola app</p>
          <h2>Todo tu olivar.</h2>
          <p>Sin menús interminables. Sin perder el hilo de la campaña.</p>
        </div>

        <div className="v2-product-flow shell">
          {[
            ["01", "Tus fincas", "Todo empieza por saber qué tienes y dónde está."],
            ["02", "Tus parcelas", "Cada parcela con su información y su historia."],
            ["03", "Tu campaña", "Labores, cosecha y evolución en contexto."],
            ["04", "Tus números", "Gastos, documentos y resultados sin perder nada."],
            ["05", "Tu tiempo", "Información útil cuando toca decidir."],
            ["06", "Tu histórico", "Comparar campañas para entender mejor tu olivar."],
          ].map(([number, title, copy]) => (
            <article className="v2-product-step" key={number}>
              <span>{number}</span>
              <h3>{title}</h3>
              <p>{copy}</p>
            </article>
          ))}
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
        <a className="v2-button v2-button-light" href="/contacto">
          Quiero conocerla <span aria-hidden="true">→</span>
        </a>
      </section>
    </div>
  );
}
