import { apiFetch } from '@/lib/api-client';

export type WorkPartyOption = { id: string; display_name: string; roles?: string[] };
export type CustomerSiteOption = { id: string; customer_party_id: string; name: string; municipality?: string | null; customer_name?: string };

export async function loadWorkDirectory(workspaceId: string) {
  const [parties, sites] = await Promise.all([
    apiFetch<{ parties: WorkPartyOption[] }>('/api/v1/parties', { workspaceId }),
    apiFetch<{ customer_sites: CustomerSiteOption[] }>('/api/v1/customer-sites', { workspaceId }),
  ]);
  return { parties: parties.parties, sites: sites.customer_sites };
}

export async function createCustomer(workspaceId: string, displayName: string) {
  const response = await apiFetch<{ party: WorkPartyOption }>('/api/v1/parties', {
    method: 'POST', workspaceId,
    body: JSON.stringify({
      client_operation_id: crypto.randomUUID(),
      kind: 'person',
      display_name: displayName,
      roles: ['customer'],
    }),
  });
  return response.party;
}

export async function createCustomerSite(workspaceId: string, input: { customerPartyId: string; name: string; municipality?: string }) {
  const response = await apiFetch<{ customer_site: CustomerSiteOption }>('/api/v1/customer-sites', {
    method: 'POST', workspaceId,
    body: JSON.stringify({
      client_operation_id: crypto.randomUUID(),
      customer_party_id: input.customerPartyId,
      name: input.name,
      municipality: input.municipality || undefined,
    }),
  });
  return response.customer_site;
}

export async function createWork(workspaceId: string, payload: Record<string, unknown>) {
  return apiFetch('/api/v1/works', {
    method: 'POST', workspaceId,
    body: JSON.stringify({ client_operation_id: crypto.randomUUID(), ...payload }),
  });
}
