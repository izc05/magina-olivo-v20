'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { useAuth } from '@/components/auth-provider';
import {
  fallbackPlanCatalog,
  loadCurrentPlan,
  loadPlanCatalog,
  registerPlanInterest,
  type CurrentPlanPayload,
  type PlanCode,
} from '@/lib/plans-data-source';
import styles from './planes.module.css';

const planOrder: PlanCode[] = ['free', 'pro', 'professional'];

export default function PlansPage() {
  const { status, workspaces, selectedWorkspaceId } = useAuth();
  const [catalog, setCatalog] = useState(fallbackPlanCatalog);
  const [currentPlan, setCurrentPlan] = useState<CurrentPlanPayload | null>(null);
  const [currentLoading, setCurrentLoading] = useState(false);
  const [currentError, setCurrentError] = useState<string | null>(null);
  const [actionPlan, setActionPlan] = useState<PlanCode | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const selectedWorkspace = useMemo(
    () => workspaces.find((workspace) => workspace.workspace_id === selectedWorkspaceId) ?? null,
    [selectedWorkspaceId, workspaces],
  );

  useEffect(() => {
    let active = true;
    void loadPlanCatalog().then((payload) => {
      if (active) setCatalog(payload);
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    setActionMessage(null);
    setCurrentError(null);

    if (status !== 'authenticated' || !selectedWorkspaceId) {
      setCurrentPlan(null);
      setCurrentLoading(false);
      return () => { active = false; };
    }

    setCurrentLoading(true);
    void loadCurrentPlan(selectedWorkspaceId)
      .then((payload) => {
        if (active) setCurrentPlan(payload);
      })
      .catch(() => {
        if (active) {
          setCurrentPlan(null);
          setCurrentError('No hemos podido comprobar el plan de este espacio. Tus datos y funciones Beta no se ven afectados.');
        }
      })
      .finally(() => {
        if (active) setCurrentLoading(false);
      });

    return () => { active = false; };
  }, [selectedWorkspaceId, status]);

  const registeredInterests = new Set(currentPlan?.interests.map((interest) => interest.target_plan) ?? []);

  async function handleInterest(targetPlan: 'pro' | 'professional') {
    if (!selectedWorkspaceId || !currentPlan?.can_manage_plan) return;
    setActionPlan(targetPlan);
    setActionMessage(null);
    try {
      const result = await registerPlanInterest(selectedWorkspaceId, targetPlan);
      const refreshed = await loadCurrentPlan(selectedWorkspaceId);
      setCurrentPlan(refreshed);
      setActionMessage(result.already_registered
        ? 'Tu interés ya estaba registrado. No se ha creado ningún cobro.'
        : 'Interés registrado. No se ha creado ningún cobro ni suscripción.');
    } catch {
      setActionMessage('No se ha podido registrar el interés. No se ha creado ningún cobro ni suscripción.');
    } finally {
      setActionPlan(null);
    }
  }

  return (
    <main className="app-shell">
      <Topbar />
      <div className={`page ${styles.page}`}>
        <section className={styles.hero}>
          <span className={styles.eyebrow}>Mágina Olivo · Planes</span>
          <h1>Elige cómo quieres crecer, sin pagar nada durante la Beta</h1>
          <p>Las funciones que ya existen en V20 siguen disponibles durante la Beta. Pro y Profesional todavía no tienen precio ni checkout: puedes registrar interés, pero eso no crea una compra.</p>
          <div className={styles.betaNotice} role="note">
            <strong>Beta sin cobros</strong>
            <span>Sin tarjeta, sin renovación automática y sin activar una suscripción comercial.</span>
          </div>
        </section>

        {status === 'authenticated' ? (
          <section className={styles.workspaceCard} aria-live="polite">
            <div>
              <span className={styles.label}>Espacio activo</span>
              <strong>{selectedWorkspace?.workspace_name ?? 'Espacio de trabajo'}</strong>
              <small>{currentLoading ? 'Comprobando plan…' : currentPlan ? `Plan actual: ${currentPlan.effective_plan === 'free' ? 'Campo' : currentPlan.effective_plan === 'pro' ? 'Pro' : 'Profesional'}` : 'Plan no disponible temporalmente'}</small>
            </div>
            <Link href="/perfil">Cambiar espacio</Link>
          </section>
        ) : (
          <section className={styles.workspaceCard}>
            <div>
              <span className={styles.label}>Sin iniciar sesión</span>
              <strong>Puedes comparar los planes sin cuenta</strong>
              <small>Para registrar interés necesitarás entrar y ser propietario o administrador del espacio.</small>
            </div>
            <Link href="/perfil">Ir a Perfil</Link>
          </section>
        )}

        {currentError ? <p className={styles.error} role="alert">{currentError}</p> : null}
        {actionMessage ? <p className={styles.feedback} role="status">{actionMessage}</p> : null}

        <section className={styles.grid} aria-label="Planes de Mágina Olivo">
          {planOrder.map((code) => {
            const plan = catalog.plans.find((entry) => entry.code === code);
            if (!plan) return null;
            const isCurrent = currentPlan?.effective_plan === plan.code;
            const interestRegistered = plan.code !== 'free' && registeredInterests.has(plan.code);
            const targetPlan = plan.code === 'pro' || plan.code === 'professional' ? plan.code : null;
            const canRequest = Boolean(currentPlan?.can_manage_plan && selectedWorkspaceId);

            return (
              <article
                key={plan.code}
                data-testid="plan-card"
                className={`${styles.planCard} ${isCurrent ? styles.current : ''}`}
              >
                <div className={styles.planHead}>
                  <div>
                    <span className={styles.label}>{plan.commercial_state === 'available' ? 'Disponible' : 'Próximamente'}</span>
                    <h2>{plan.name}</h2>
                  </div>
                  {isCurrent ? <span className={styles.currentBadge}>Plan actual</span> : null}
                </div>
                <p className={styles.price}>{plan.price_label}</p>
                <p className={styles.summary}>{plan.summary}</p>
                <ul>
                  {plan.highlights.map((highlight) => <li key={highlight}>{highlight}</li>)}
                </ul>

                {plan.code === 'free' ? (
                  <button className={styles.secondaryButton} type="button" disabled>
                    {isCurrent ? 'Plan actual' : 'Incluido durante la Beta'}
                  </button>
                ) : status !== 'authenticated' ? (
                  <Link className={styles.primaryLink} href="/perfil">Entra para registrar interés</Link>
                ) : interestRegistered ? (
                  <button className={styles.secondaryButton} type="button" disabled>Interés registrado ✓</button>
                ) : !canRequest ? (
                  <button className={styles.secondaryButton} type="button" disabled>Solo propietario o admin</button>
                ) : isCurrent ? (
                  <button className={styles.secondaryButton} type="button" disabled>Plan actual</button>
                ) : targetPlan ? (
                  <button
                    className={styles.primaryButton}
                    type="button"
                    disabled={actionPlan !== null}
                    onClick={() => void handleInterest(targetPlan)}
                  >
                    {actionPlan === targetPlan ? 'Registrando…' : 'Avísame cuando esté disponible'}
                  </button>
                ) : null}
              </article>
            );
          })}
        </section>

        <section className={styles.explainer}>
          <h2>Qué estamos preparando</h2>
          <p>Este módulo deja preparado el modelo de planes por espacio de trabajo, los permisos y el interés comercial. El cobro real se conectará más adelante a un proveedor de facturación y solo entonces podrá existir una suscripción de pago.</p>
          <p>Hasta ese momento, <strong>registrar interés no cambia tu plan ni limita las funciones actuales de la Beta.</strong></p>
        </section>
      </div>
      <BottomNav active="/perfil" />
    </main>
  );
}
