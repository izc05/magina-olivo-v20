import Link from 'next/link';
import { MI_OLIVO_DISCOVERY_WORLDS } from '../lib/mi-olivo-discovery';
import styles from './mi-olivo-discovery-compass.module.css';

export function MiOlivoDiscoveryCompass() {
  return (
    <section className={styles.section} aria-labelledby="mi-olivo-discovery-title">
      <div className={styles.heading}>
        <div>
          <span className={styles.kicker}>TU PASAPORTE VIVO DE MÁGINA</span>
          <h2 id="mi-olivo-discovery-title">Haz crecer tu olivo descubriendo toda la comarca</h2>
        </div>
        <p>
          Mi Olivo no depende de tener una finca. Conocer pueblos, almazaras, negocios, experiencias,
          actualidad, mercado y clima deja una huella permanente en tu progreso.
        </p>
      </div>

      <div className={styles.grid}>
        {MI_OLIVO_DISCOVERY_WORLDS.map((world) => (
          <Link key={world.id} href={world.href} className={styles.card}>
            <span className={styles.icon} aria-hidden="true">{world.icon}</span>
            <span className={styles.eyebrow}>{world.eyebrow}</span>
            <strong>{world.title}</strong>
            <span className={styles.description}>{world.description}</span>
            <span className={styles.cta}>Descubrir →</span>
          </Link>
        ))}
      </div>

      <div className={styles.passCallout}>
        <div>
          <span className={styles.kicker}>MÁGINA PASS</span>
          <strong>Tu crecimiento permanece. Tus recompensas se canjean aparte.</strong>
          <p>
            Mi Olivo conserva tu historia y nivel; Mágina Pass gestiona los beneficios, negocios participantes
            y futuros canjes con QR sin hacer retroceder tu olivo.
          </p>
        </div>
        <Link href="/magina-pass" className={styles.passButton}>Ver Mágina Pass</Link>
      </div>
    </section>
  );
}
