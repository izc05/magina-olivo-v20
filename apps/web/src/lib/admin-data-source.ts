import { apiFetch } from './api-client';

export type PlatformAdminRole = 'super_admin' | 'admin' | 'editor' | 'support';
export type CmsEntryType = 'page' | 'news' | 'event' | 'place' | 'mill' | 'directory' | 'promotion' | 'alert';
export type CmsEntryStatus = 'draft' | 'published' | 'archived';
export type TerritoryPlaceKind = 'municipal_seat' | 'locality' | 'hamlet' | 'other';
export type AdminSourceState = 'ok' | 'attention' | 'error' | 'unknown' | 'unmonitored';
export type AdminSourceTelemetryMode = 'cache_health' | 'pipeline_status' | 'usage_only';
export type AdminDatasetId =
  | 'users'
  | 'workspaces'
  | 'fields'
  | 'campaigns'
  | 'work'
  | 'irrigation'
  | 'treatments'
  | 'fertilization'
  | 'pruning'
  | 'expenses'
  | 'harvest'
  | 'settlements'
  | 'collections'
  | 'agenda'
  | 'parties'
  | 'machinery'
  | 'materials'
  | 'documents'
  | 'ocr'
  | 'content'
  | 'invoices'
  | 'quotes'
  | 'plans'
  | 'territory'
  | 'market';

export type AdminSession = {
  user: { id: string; display_name: string; primary_email: string | null; avatar_url: string | null; status: string };
  platform_access: { userId: string; role: PlatformAdminRole; source: 'database' | 'bootstrap' };
};

export type AdminOverview = {
  metrics: { users: number; workspaces: number; active_fields: number; content_entries: number; published_entries: number };
  recent_audit: AdminAuditEntry[];
};

export type AdminOperationsMetrics = {
  users_total: number;
  users_active: number;
  users_suspended: number;
  users_new_30d: number;
  workspaces_total: number;
  workspaces_professional: number;
  fields_active: number;
  field_area_ha: number;
  campaigns_active: number;
  work_records_total: number;
  irrigation_total: number;
  treatment_total: number;
  fertilization_total: number;
  pruning_total: number;
  expenses_total: number;
  expenses_eur: number;
  harvest_kg: number;
  settlements_total: number;
  settlements_net_eur: number;
  collections_total: number;
  collections_eur: number;
  scheduled_open: number;
  parties_active: number;
  documents_active: number;
  document_bytes: number;
  ocr_pending: number;
  ocr_failed: number;
  content_draft: number;
  content_published: number;
  invoices_issued: number;
  invoiced_eur: number;
  quotes_open: number;
  quotes_accepted: number;
  plans_pro: number;
  plans_professional: number;
  market_observations: number;
  market_latest_period: string | null;
  weather_stale: number;
  admin_actions_24h: number;
};

export type AdminExternalAppConfig = {
  enabled: boolean;
  name: string;
  description?: string | null;
  url: string;
  mode: 'new_tab' | 'embedded';
  health_url?: string | null;
};

export type AdminDatasetDescriptor = {
  id: AdminDatasetId;
  label: string;
  count: number | null;
  sensitivity: 'restricted' | 'platform' | 'financial' | 'public';
};

export type AdminOperationsSnapshot = {
  generated_at: string;
  metrics: AdminOperationsMetrics;
  datasets: AdminDatasetDescriptor[];
  external_app: AdminExternalAppConfig;
};

export type AdminDatasetResponse = {
  dataset: AdminDatasetId;
  label: string;
  rows: Record<string, unknown>[];
  generated_at: string;
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

export type AdminMediaAsset = {
  id: string;
  original_filename: string;
  mime_type: 'image/jpeg' | 'image/png' | 'image/webp' | 'image/avif';
  byte_size: number;
  status: 'reserved' | 'uploaded' | 'failed' | 'archived';
  created_by: string | null;
  created_at: string;
  uploaded_at: string | null;
  public_path: string;
};

export type MediaUploadReservation = {
  storageKey: string;
  uploadUrl: string;
  method: 'PUT';
  headers: Record<string, string>;
  expiresAt: string;
};

export type AdminTerritoryMunicipality = {
  id: string;
  ine_code: string;
  aemet_code: string | null;
  name: string;
  slug: string;
  province_code: string;
  province_name: string;
  active: boolean;
  weather_enabled: boolean;
  center: unknown | null;
  place_count: number;
  public_place_count: number;
};

export type AdminTerritoryPlace = {
  id: string;
  municipality_id: string;
  municipality_name: string;
  municipality_slug: string;
  name: string;
  slug: string;
  kind: TerritoryPlaceKind;
  center: unknown | null;
  is_default_for_municipality: boolean;
  public_enabled: boolean;
  hero_asset_key: string | null;
  field_count: number;
  editorial_count: number;
};

export type AdminSourceTelemetry = {
  id: 'aemet_forecast' | 'aemet_radar' | 'ocr' | 'catastro' | 'sigpac';
  name: string;
  provider: string;
  telemetry: AdminSourceTelemetryMode;
  state: AdminSourceState;
  state_reason: string;
  metrics: Record<string, number>;
  timestamps: Record<string, string | null>;
  last_error_code: string | null;
};

export type AdminSourcesSnapshot = {
  generated_at: string;
  sources: AdminSourceTelemetry[];
};

export const adminApi = {
  session: () => apiFetch<AdminSession>('/api/v1/admin/session'),
  overview: () => apiFetch<AdminOverview>('/api/v1/admin/overview'),
  operations: () => apiFetch<AdminOperationsSnapshot>('/api/v1/admin/operations'),
  dataset: (dataset: AdminDatasetId, limit = 50) => apiFetch<AdminDatasetResponse>(`/api/v1/admin/data/${dataset}?limit=${limit}`),
  externalApp: () => apiFetch<{ config: AdminExternalAppConfig }>('/api/v1/admin/external-app'),
  saveExternalApp: (config: AdminExternalAppConfig) => apiFetch<{ config: AdminExternalAppConfig; updated_at: string }>('/api/v1/admin/external-app', {
    method: 'PUT', body: JSON.stringify(config),
  }),
  users: (q = '') => apiFetch<{ users: AdminUser[] }>(`/api/v1/admin/users${q ? `?q=${encodeURIComponent(q)}` : ''}`),
  content: () => apiFetch<{ entries: CmsEntry[] }>('/api/v1/admin/content'),
  settings: () => apiFetch<{ settings: SiteSetting[] }>('/api/v1/admin/settings'),
  audit: () => apiFetch<{ entries: AdminAuditEntry[] }>('/api/v1/admin/audit'),
  media: () => apiFetch<{ assets: AdminMediaAsset[] }>('/api/v1/admin/media'),
  sources: () => apiFetch<AdminSourcesSnapshot>('/api/v1/admin/sources'),
  territoryCatalog: () => apiFetch<{ municipalities: AdminTerritoryMunicipality[]; places: AdminTerritoryPlace[] }>('/api/v1/admin/territory/catalog'),
  updateTerritoryPlace: (id: string, payload: { public_enabled?: boolean; kind?: TerritoryPlaceKind }) => apiFetch<{ place: AdminTerritoryPlace | null }>(`/api/v1/admin/territory/places/${id}`, {
    method: 'PATCH', body: JSON.stringify(payload),
  }),
  reserveMedia: (payload: { original_filename: string; mime_type: AdminMediaAsset['mime_type']; byte_size: number; sha256: string }) => apiFetch<{ asset: AdminMediaAsset; upload: MediaUploadReservation }>('/api/v1/admin/media/reserve', {
    method: 'POST', body: JSON.stringify(payload),
  }),
  completeMedia: (id: string) => apiFetch<{ replayed: boolean; asset: AdminMediaAsset }>(`/api/v1/admin/media/${id}/complete`, { method: 'POST' }),
  archiveMedia: (id: string) => apiFetch<void>(`/api/v1/admin/media/${id}`, { method: 'DELETE' }),
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
