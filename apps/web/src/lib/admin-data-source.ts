import { apiFetch } from './api-client';

export type PlatformAdminRole = 'super_admin' | 'admin' | 'editor' | 'support';
export type CmsEntryType = 'page' | 'news' | 'event' | 'place' | 'mill' | 'directory' | 'promotion' | 'alert';
export type CmsEntryStatus = 'draft' | 'published' | 'archived';

export type AdminSession = {
  user: { id: string; display_name: string; primary_email: string | null; avatar_url: string | null; status: string };
  platform_access: { userId: string; role: PlatformAdminRole; source: 'database' | 'bootstrap' };
};

export type AdminOverview = {
  metrics: { users: number; workspaces: number; active_fields: number; content_entries: number; published_entries: number };
  recent_audit: AdminAuditEntry[];
};

export type AdminUser = {
  id: string;
  primary_email: string | null;
  display_name: string;
  avatar_url: string | null;
  status: 'active' | 'suspended';
  created_at: string;
  last_login_at: string | null;
  workspace_count: number;
  platform_access: { user_id: string; role: PlatformAdminRole; status: 'active' | 'revoked' } | null;
};

export type CmsEntry = {
  id: string;
  type: CmsEntryType;
  slug: string;
  title: string;
  summary: string | null;
  content_json: unknown;
  status: CmsEntryStatus;
  featured: boolean;
  starts_at: string | null;
  ends_at: string | null;
  media_url: string | null;
  external_url: string | null;
  sort_order: number;
  updated_at: string;
  published_at: string | null;
};

export type SiteSetting = {
  key: string;
  value_json: unknown;
  description: string | null;
  is_public: boolean;
  updated_by: string;
  updated_at: string;
};

export type AdminAuditEntry = {
  id: string;
  actor_user_id: string | null;
  actor_role: PlatformAdminRole | 'bootstrap';
  action: string;
  target_type: string;
  target_id: string | null;
  metadata?: unknown;
  created_at: string;
};

export const adminApi = {
  session: () => apiFetch<AdminSession>('/api/v1/admin/session'),
  overview: () => apiFetch<AdminOverview>('/api/v1/admin/overview'),
  users: (q = '') => apiFetch<{ users: AdminUser[] }>(`/api/v1/admin/users${q ? `?q=${encodeURIComponent(q)}` : ''}`),
  content: () => apiFetch<{ entries: CmsEntry[] }>('/api/v1/admin/content'),
  settings: () => apiFetch<{ settings: SiteSetting[] }>('/api/v1/admin/settings'),
  audit: () => apiFetch<{ entries: AdminAuditEntry[] }>('/api/v1/admin/audit'),
  setUserStatus: (userId: string, status: 'active' | 'suspended') => apiFetch(`/api/v1/admin/users/${userId}`, {
    method: 'PATCH', body: JSON.stringify({ status }),
  }),
  setPlatformAccess: (userId: string, role: PlatformAdminRole, status: 'active' | 'revoked' = 'active') => apiFetch(`/api/v1/admin/platform-access/${userId}`, {
    method: 'PUT', body: JSON.stringify({ role, status }),
  }),
  createContent: (payload: Record<string, unknown>) => apiFetch<{ entry: CmsEntry }>('/api/v1/admin/content', {
    method: 'POST', body: JSON.stringify(payload),
  }),
  updateContent: (id: string, payload: Record<string, unknown>) => apiFetch<{ entry: CmsEntry }>(`/api/v1/admin/content/${id}`, {
    method: 'PUT', body: JSON.stringify(payload),
  }),
  archiveContent: (id: string) => apiFetch<void>(`/api/v1/admin/content/${id}`, { method: 'DELETE' }),
  saveSetting: (key: string, value_json: unknown, description: string | null, is_public: boolean) => apiFetch(`/api/v1/admin/settings/${encodeURIComponent(key)}`, {
    method: 'PUT', body: JSON.stringify({ value_json, description, is_public }),
  }),
};
