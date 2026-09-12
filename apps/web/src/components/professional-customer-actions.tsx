'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';

export function ProfessionalCustomerActions() {
  const customerId = useSearchParams().get('id');
  if (!customerId) return null;

  const query = encodeURIComponent(customerId);
  return <section className="card register-principle">
    <div><strong>¿Qué necesitas hacer con este cliente?</strong><small>Presupuesto, trabajo y factura siguen siendo pasos distintos, pero puedes empezar cualquiera desde su ficha.</small></div>
    <div className="record-actions">
      <Link className="primary action-link" href={`/mi-campo/profesional/presupuestos?customerId=${query}`}>Nuevo presupuesto</Link>
      <Link className="secondary-action action-link" href={`/mi-campo/registrar/trabajo?customerId=${query}`}>Registrar trabajo</Link>
      <Link className="secondary-action action-link" href={`/mi-campo/profesional/facturas/nueva?customerId=${query}`}>Nueva factura</Link>
    </div>
  </section>;
}
