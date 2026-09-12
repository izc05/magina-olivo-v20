import type { Metadata } from 'next';
import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import styles from './help.module.css';

export const metadata: Metadata = {
  title: 'Ayuda y primeros pasos | Mágina Olivo',
  description: 'Guía rápida para empezar a organizar fincas, trabajos, documentos y campaña en Mágina Olivo.',
};

const steps = [
  { n: '1', title: 'Empieza por tus fincas', text: 'Mi Campo es el punto de partida. Crea la finca tal como tú la reconoces y completa solo los datos que conozcas.', href: '/mi-campo', cta: 'Abrir Mi Campo' },
  { n: '2', title: 'Añade una finca sin inventar datos', text: 'Nombre, ubicación y referencias se pueden completar de forma progresiva. Si un vínculo GIS todavía no está confirmado, no hace falta fingirlo.', href: '/mi-campo/fincas/nueva', cta: 'Nueva finca' },
  { n: '3', title: 'Registra lo que realmente ocurrió', text: 'Usa Registrar para trabajos y hechos de campaña. Mantén separados entrega, rendimiento, liquidación y cobro: pueden ocurrir en días distintos.', href: '/mi-campo/registrar', cta: 'Registrar actividad' },
  { n: '4', title: 'Planifica lo que todavía no ha ocurrido', text: 'Planificar sirve para tareas futuras. Una tarea prevista no debe confundirse con un trabajo ya ejecutado.', href: '/mi-campo/planificar', cta: 'Ir a Planificar' },
  { n: '5', title: 'Guarda documentos y revisa el OCR', text: 'El archivo original se conserva. Si usas OCR, la lectura es una propuesta: revísala antes de convertir cualquier dato en un registro agrícola.', href: '/mi-campo/documentos', cta: 'Ver Documentos' },
  { n: '6', title: 'Revisa la campaña con datos confirmados', text: 'Campaña reúne producción, rendimiento y economía a partir de registros reales. Lo pendiente debe seguir apareciendo como pendiente.', href: '/mi-campo/campana', cta: 'Abrir Campaña' },
] as const;

const shortcuts = [
  { title: 'Trabajo para terceros', text: 'Clientes, presupuestos, facturas y cobros están en Profesional.', href: '/mi-campo/profesional' },
  { title: 'Cuenta y permisos', text: 'Revisa identidad, preferencias y permisos desde Perfil.', href: '/perfil' },
  { title: 'Información pública', text: 'Explorar separa territorio y contenido público de los datos privados de tus fincas.', href: '/explorar' },
] as const;

const faqs = [
  ['¿Una finca tiene que estar completa para empezar?', 'No. Registra primero los datos que conoces. Los datos técnicos o referencias que todavía no estén confirmados pueden completarse después.'],
  ['¿Planificar crea un trabajo realizado?', 'No. Planificar representa una tarea futura. El trabajo real se registra cuando se ejecuta.'],
  ['¿El OCR guarda automáticamente kilos, importes o fechas?', 'No. El OCR propone una lectura del documento. La revisión humana y la creación o vinculación del registro siguen siendo pasos separados.'],
  ['¿Entrega, rendimiento, liquidación y cobro son lo mismo?', 'No. Son hechos distintos y pueden confirmarse en momentos diferentes. Separarlos mantiene la trazabilidad de la campaña.'],
  ['¿Trabajar para otros obliga a mezclar sus datos con mis fincas?', 'No. Profesional permite gestionar clientes y trabajos para terceros manteniendo su contexto diferenciado.'],
  ['¿Explorar publica mis fincas?', 'No. Explorar usa superficies públicas. Que exista información territorial pública no significa publicar geometrías, cosechas, costes ni documentos privados de tus fincas.'],
] as const;

export default function HelpPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className={styles.page}>
        <section className={styles.hero}>
          <span className={styles.eyebrow}>PRIMEROS PASOS</span>
          <h1>Empieza por lo sencillo</h1>
          <p>En Mágina Olivo no necesitas rellenarlo todo el primer día. Empieza por tu finca y añade información cuando realmente la tengas.</p>
          <div className={styles.rule} role="note"><strong>Regla útil:</strong> dato confirmado, dato guardado. Si algo todavía no lo sabes, déjalo pendiente.</div>
        </section>

        <section className={styles.section} aria-labelledby="steps-title">
          <div className={styles.heading}><span className={styles.eyebrow}>RECORRIDO RECOMENDADO</span><h2 id="steps-title">Seis pasos para empezar</h2></div>
          <div className={styles.steps}>
            {steps.map((step) => <article className={styles.step} key={step.n} data-testid="help-step"><span className={styles.number}>{step.n}</span><div><h3>{step.title}</h3><p>{step.text}</p><Link href={step.href}>{step.cta} <span aria-hidden="true">→</span></Link></div></article>)}
          </div>
        </section>

        <section className={styles.section} aria-labelledby="shortcut-title">
          <div className={styles.heading}><span className={styles.eyebrow}>SEGÚN LO QUE NECESITES</span><h2 id="shortcut-title">Accesos rápidos</h2></div>
          <div className={styles.shortcuts}>{shortcuts.map((item) => <Link className={styles.shortcut} href={item.href} key={item.title}><strong>{item.title}</strong><span>{item.text}</span><b aria-hidden="true">→</b></Link>)}</div>
        </section>

        <section className={styles.section} aria-labelledby="faq-title">
          <div className={styles.heading}><span className={styles.eyebrow}>DUDAS FRECUENTES</span><h2 id="faq-title">Antes de registrar</h2></div>
          <div className={styles.faqs}>{faqs.map(([question, answer]) => <details className={styles.faq} key={question}><summary>{question}</summary><p>{answer}</p></details>)}</div>
        </section>

        <aside className={styles.footerNote}><strong>La aplicación organiza; no adivina.</strong><p>Cuando un documento, una medición o un dato técnico no exista todavía, conserva la diferencia entre lo confirmado y lo pendiente.</p></aside>
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
