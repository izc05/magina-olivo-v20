"use client";

import { SceneImage } from "@/components/SceneImage";
import { visualAssets } from "@/lib/visualAssets";
import { useEffect, useRef } from "react";

const clamp01 = (value: number) => Math.min(1, Math.max(0, value));
const phase = (value: number, start: number, end: number) =>
  clamp01((value - start) / (end - start));

export function FieldToPhoneSequence() {
  const sectionRef = useRef<HTMLElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    if (reducedMotion.matches) return;

    let frame = 0;

    const update = () => {
      frame = 0;
      const section = sectionRef.current;
      const stage = stageRef.current;
      if (!section || !stage) return;

      const rect = section.getBoundingClientRect();
      const travel = Math.max(1, section.offsetHeight - window.innerHeight);
      const progress = clamp01(-rect.top / travel);

      const walkIn = phase(progress, 0.10, 0.22);
      const walkOut = phase(progress, 0.28, 0.40);
      const walkOpacity = walkIn * (1 - walkOut);

      const detailIn = phase(progress, 0.30, 0.42);
      const detailOut = phase(progress, 0.48, 0.58);
      const detailOpacity = detailIn * (1 - detailOut);

      const contextIn = phase(progress, 0.48, 0.62);
      const contextOut = phase(progress, 0.70, 0.82);
      const contextOpacity = contextIn * (1 - contextOut);

      const deviceEnter = phase(progress, 0.68, 0.84);
      const deviceFocus = phase(progress, 0.74, 0.90);
      const uiReveal = phase(progress, 0.82, 0.94);
      const finalReveal = phase(progress, 0.88, 0.985);

      stage.style.setProperty("--landscape-scale", String(1 + progress * 0.11));
      stage.style.setProperty("--landscape-x", `${-progress * 3}%`);
      stage.style.setProperty("--landscape-opacity", String(1 - phase(progress, 0.17, 0.33) * 0.36));

      stage.style.setProperty("--walk-opacity", String(walkOpacity));
      stage.style.setProperty("--walk-scale", String(1.08 - walkIn * 0.06 + walkOut * 0.04));
      stage.style.setProperty("--walk-x", `${(1 - walkIn) * 5 - walkOut * 3}%`);

      stage.style.setProperty("--detail-opacity", String(detailOpacity));
      stage.style.setProperty("--detail-scale", String(1.18 - detailIn * 0.13 + detailOut * 0.05));

      stage.style.setProperty("--context-opacity", String(contextOpacity));
      stage.style.setProperty("--context-scale", String(1.10 - contextIn * 0.10 + contextOut * 0.04));
      stage.style.setProperty("--context-y", `${(1 - contextIn) * 10 - contextOut * 4}vh`);

      stage.style.setProperty("--device-opacity", String(deviceEnter));
      stage.style.setProperty("--device-y", `${(1 - deviceEnter) * 54}vh`);
      stage.style.setProperty("--device-scale", String(0.62 + deviceFocus * 0.52));
      stage.style.setProperty("--device-rotate", `${-9 + deviceFocus * 9}deg`);
      stage.style.setProperty("--device-ui-opacity", String(uiReveal));

      stage.style.setProperty("--copy-a-opacity", String(1 - phase(progress, 0.12, 0.24)));
      stage.style.setProperty(
        "--copy-b-opacity",
        String(phase(progress, 0.34, 0.46) * (1 - phase(progress, 0.62, 0.74))),
      );
      stage.style.setProperty("--copy-c-opacity", String(finalReveal));
      stage.style.setProperty("--film-wash", String(phase(progress, 0.78, 0.96) * 0.86));
      stage.style.setProperty("--progress-width", `${progress * 100}%`);
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
    <section className="field-phone-film" ref={sectionRef} aria-label="Del campo a Mágina Olivo">
      <div className="field-phone-stage" ref={stageRef}>
        <div className="film-landscape-layer" aria-hidden="true">
          <SceneImage asset={visualAssets.fieldSequence} sizes="100vw" />
        </div>

        <div className="film-walk-layer" aria-hidden="true">
          <SceneImage asset={visualAssets.heritage} sizes="100vw" />
        </div>

        <div className="film-detail-layer" aria-hidden="true">
          <SceneImage asset={visualAssets.benefits} sizes="100vw" />
        </div>

        <div className="film-phone-context" aria-hidden="true">
          <SceneImage
            asset={visualAssets.phoneContext}
            sizes="(max-width: 820px) 84vw, 44vw"
          />
        </div>

        <div className="film-light-layer" aria-hidden="true" />

        {visualAssets.fieldSequence.status !== "final" && (
          <div className="film-person-layer" aria-hidden="true">
            <span className="film-head" />
            <span className="film-hat" />
            <span className="film-body" />
            <span className="film-arm film-arm-left" />
            <span className="film-arm film-arm-right" />
          </div>
        )}

        {visualAssets.phoneContext.status !== "final" && (
          <div className="film-hand-layer" aria-hidden="true">
            <span className="film-hand" />
          </div>
        )}

        <div className="film-device" aria-hidden="true">
          <span className="film-device-camera" />
          <div className="film-device-ui">
            <div className="film-device-logo">
              <span className="phone-leaf" />
              <strong>Mágina<br />Olivo</strong>
            </div>
            <p>Bienvenido a</p>
            <h3>Mágina Olivo</h3>
            <span className="film-device-landscape" />
            <b>Comenzar&nbsp;&nbsp;→</b>
          </div>
        </div>

        <div className="film-copy film-copy-a">
          <p className="eyebrow light">Una mañana cualquiera</p>
          <h2>El campo<br />primero.</h2>
          <p>Observas el olivar, recorres la finca y compruebas cómo está cada zona.</p>
        </div>

        <div className="film-copy film-copy-b">
          <p className="eyebrow light">De observar a decidir</p>
          <h2>Todo empieza<br />con lo que ves.</h2>
          <p>Una rama, una parcela, una labor pendiente. La información aparece cuando la necesitas.</p>
        </div>

        <div className="film-copy film-copy-c">
          <p className="eyebrow">Ahí empieza Mágina Olivo</p>
          <h2>Tu olivar.<br />Todo en contexto.</h2>
          <a href="#funciones">Descubrir las funciones <span aria-hidden="true">↓</span></a>
        </div>

        <div className="film-progress" aria-hidden="true">
          <span />
        </div>
      </div>
    </section>
  );
}
