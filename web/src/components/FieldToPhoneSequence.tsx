"use client";

import Image from "next/image";
import { useEffect, useRef } from "react";

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
      const progress = Math.min(1, Math.max(0, -rect.top / travel));
      stage.style.setProperty("--film-progress", progress.toFixed(4));
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
