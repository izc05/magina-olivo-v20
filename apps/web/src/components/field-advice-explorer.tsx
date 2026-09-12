'use client';

import { useMemo, useState } from 'react';
import { adviceTopics, fieldAdviceCatalog, type AdviceTopic } from '@/lib/field-advice';
import { requestMiOlivoTracking } from './mi-olivo-activity-tracker';
import styles from './field-advice-explorer.module.css';

const ALL_TOPICS = 'Todos' as const;
type TopicFilter = AdviceTopic | typeof ALL_TOPICS;

function searchableText(advice: (typeof fieldAdviceCatalog)[number]) {
  return [
    advice.title,
    advice.summary,
    advice.topic,
    advice.moment,
    ...advice.observe,
    ...advice.record,
    ...advice.askForHelp,
  ]
    .join(' ')
    .toLocaleLowerCase('es');
}

export function FieldAdviceExplorer() {
  const [topic, setTopic] = useState<TopicFilter>(ALL_TOPICS);
  const [query, setQuery] = useState('');

  const visibleAdvice = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase('es');

    return fieldAdviceCatalog.filter((advice) => {
      const matchesTopic = topic === ALL_TOPICS || advice.topic === topic;
      const matchesQuery = !normalizedQuery || searchableText(advice).includes(normalizedQuery);
      return matchesTopic && matchesQuery;
    });
  }, [query, topic]);

  const clearFilters = () => {
    setTopic(ALL_TOPICS);
    setQuery('');
  };

  return (
    <div className={styles.surface}>
      <section className={styles.hero} aria-labelledby="advice-title">
        <span className={styles.eyebrow}>CUADERNO PRÁCTICO · OLIVAR</span>
        <h1 id="advice-title">Consejos del campo</h1>
        <p className={styles.lead}>
          Qué mirar, qué anotar y cuándo pedir ayuda antes de tomar una decisión en la finca.
        </p>
        <div className={styles.safetyNote} role="note" data-testid="advice-safety-note">
          <strong>Orientación general, no una receta.</strong>
          <span>
            Cada finca es distinta. Estas guías ayudan a observar y registrar; no sustituyen un diagnóstico,
            asesoramiento técnico, etiqueta de producto, formación de seguridad ni normativa aplicable.
          </span>
        </div>
      </section>

      <section className={styles.tools} aria-label="Buscar y filtrar consejos">
        <label className={styles.searchLabel} htmlFor="advice-search">
          <span>Buscar en las guías</span>
          <input
            id="advice-search"
            className={styles.searchInput}
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Ej. cosecha, riego, fotos…"
            autoComplete="off"
          />
        </label>

        <div className={styles.filterWrap}>
          <span className={styles.filterTitle}>Tema</span>
          <div className={styles.filters} role="group" aria-label="Filtrar por tema">
            {[ALL_TOPICS, ...adviceTopics].map((item) => (
              <button
                key={item}
                type="button"
                className={styles.filterButton}
                aria-pressed={topic === item}
                onClick={() => setTopic(item)}
              >
                {item}
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className={styles.results} aria-live="polite" aria-label="Guías disponibles">
        <div className={styles.resultHead}>
          <div>
            <span className={styles.eyebrow}>GUÍAS</span>
            <h2>{visibleAdvice.length === 1 ? '1 consejo disponible' : `${visibleAdvice.length} consejos disponibles`}</h2>
          </div>
          {(query || topic !== ALL_TOPICS) && (
            <button type="button" className={styles.clearButton} onClick={clearFilters}>
              Limpiar filtros
            </button>
          )}
        </div>

        {visibleAdvice.length > 0 ? (
          <div className={styles.grid}>
            {visibleAdvice.map((advice) => (
              <article className={styles.card} key={advice.id} data-testid="advice-card">
                <div className={styles.cardMeta}>
                  <span>{advice.topic}</span>
                  <span>{advice.moment}</span>
                </div>
                <h3>{advice.title}</h3>
                <p>{advice.summary}</p>

                <details
                  className={styles.details}
                  onToggle={(event) => {
                    if (!event.currentTarget.open) return;
                    requestMiOlivoTracking({
                      eventType: 'learning_completed',
                      sourceId: `consejo:${advice.id}`,
                    });
                  }}
                >
                  <summary aria-label={`Ver guía: ${advice.title}`}>Ver guía práctica</summary>
                  <div className={styles.detailBody}>
                    <section>
                      <h4>Qué observar</h4>
                      <ul>{advice.observe.map((item) => <li key={item}>{item}</li>)}</ul>
                    </section>
                    <section>
                      <h4>Qué anotar</h4>
                      <ul>{advice.record.map((item) => <li key={item}>{item}</li>)}</ul>
                    </section>
                    <section>
                      <h4>Cuándo pedir ayuda</h4>
                      <ul>{advice.askForHelp.map((item) => <li key={item}>{item}</li>)}</ul>
                    </section>
                    {advice.caution && <p className={styles.caution}>{advice.caution}</p>}
                  </div>
                </details>
              </article>
            ))}
          </div>
        ) : (
          <div className={styles.empty} data-testid="advice-empty">
            <strong>No hay una guía con esos filtros.</strong>
            <span>Prueba otra palabra o vuelve a ver todos los temas.</span>
            <button type="button" onClick={clearFilters}>Ver todos los consejos</button>
          </div>
        )}
      </section>

      <aside className={styles.boundary} aria-label="Límites de estos consejos">
        <strong>Cuando falte un dato, no lo inventes.</strong>
        <p>
          Si no conoces una cantidad, una causa, una intensidad o un diagnóstico, registra la observación y deja el
          dato como pendiente. La trazabilidad mejora cuando distingue hechos, estimaciones y decisiones posteriores.
        </p>
      </aside>
    </div>
  );
}
