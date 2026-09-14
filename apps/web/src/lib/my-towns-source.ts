import { apiFetch } from './api-client';

export type FollowedTown = {
  id: string;
  slug: string;
  name: string;
  created_at: string;
};

export async function loadFollowedTowns(): Promise<FollowedTown[]> {
  const payload = await apiFetch<{ towns: FollowedTown[] }>('/api/v1/me/towns');
  return payload.towns;
}

export async function followTown(slug: string) {
  return apiFetch(`/api/v1/me/towns/${encodeURIComponent(slug)}`, { method: 'PUT' });
}

export async function unfollowTown(slug: string) {
  return apiFetch(`/api/v1/me/towns/${encodeURIComponent(slug)}`, { method: 'DELETE' });
}
