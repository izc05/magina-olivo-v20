"use client";

import Image from "next/image";
import { useEffect, useRef } from "react";

const clamp01 = (value: number) => Math.min(1, Math.max(0, value));
const phase = (value: number, start: number, end: number) =>
  clamp01((value - start) / (end - start));

export function FieldToPhoneSequence() {
  const sectionRef = useRef<HTMLElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let frame = 0;

    const update = () => {
      frame = 0;
      const section = sectionRef.current;
      const stage = stageRef.current;
      if (!section || !stage) return;

      const rect = section.getBoundingClientRect();
      const travel = Math.max(1, section.offsetHeight - window.innerHeight);
      const progress = clamp01(-rect.top / travel);

      const personFocus = phase(progress, 0.08, 0.42);
      const deviceEnter = phase(progress, 0.30, 0.64);
      const deviceFocus = phase(progress, 0.55, 0.82);
      const uiReveal = phase(progress, 0.72, 0.91);
      const finalReveal = phase(progress, 0.82, 0.98);

      stage.style.setProperty("--landscape-scale", String(1 + progress * 0.13));
      stage.style.setProperty("--landscape-x", `${-progress * 4}%`);
      stage.style.setProperty("--person-scale", String(1 + personFocus * 0.46));
      stage.style.setProperty("--person-x", `${personFocus * -17}vw`);
      stage.style.setProperty("--person-opacity", String(1 - phase(progress, 0.64, 0.86) * 0.64));
      stage.style.setProperty("--hand-opacity", String(deviceEnter));
      stage.style.setProperty("--hand-y", `${(1 - deviceEnter) * 24}vh`);
      stage.style.setProperty("--device-opacity", String(deviceEnter));
      stage.style.setProperty("--device-y", `${(1 - deviceEnter) * 58}vh`);
      stage.style.setProperty("--device-scale", String(0.62 + deviceFocus * 0.52));
      stage.style.setProperty("--device-rotate", `${-10 + deviceFocus * 10}deg`);
      stage.style.setProperty("--device-ui-opacity", String(uiReveal));
      stage.style.setProperty("--copy-a-opacity", String(1 - phase(progress, 0.17, 0.34)));
      stage.style.setProperty("--copy-b-opacity", String(phase(progress, 0.26, 0.42) * (1 - phase(progress, 0.55, 0.70))));
      stage.style.setProperty("--copy-c-opacity", String(finalReveal));
      stage.style.setProperty("--film-wash", String(phase(progress, 0.72, 0.95) * 0.84));
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
          <Image src="/brand/hero-scene.svg" alt="" fill sizes="100vw" />
        </div>

        <div className="film-light-layer" aria-hidden="true" />

        <div className="film-person-layer" aria-hidden="true">
          <span className="film-head" />
          <span className="film-hat" />
          <span className="film-body" />
          <span className="film-arm film-arm-left" />
          <span className="film-arm film-arm-right" />
        </div>

        <div className="film-hand-layer" aria-hidden="true">
          <span className="film-hand" />
        </div>

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
          <p>Observas, comparas, recuerdas lo que hiciste y piensas qué toca después.</p>
        </div>

        <div className="film-copy film-copy-b">
          <p className="eyebrow light">En el momento adecuado</p>
          <h2>La información<br />aparece en tu mano.</h2>
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
