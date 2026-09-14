import { apiFetch } from './api-client';

export const communityCategories = [
  { value: 'campo', label: 'Campo' },
  { value: 'preguntas', label: 'Preguntas' },
  { value: 'plagas', label: 'Plagas' },
  { value: 'maquinaria', label: 'Maquinaria' },
  { value: 'cosecha', label: 'Cosecha' },
  { value: 'pueblos', label: 'Pueblos' },
  { value: 'gastronomia', label: 'Gastronomía' },
  { value: 'rutas', label: 'Rutas' },
] as const;

export type CommunityCategory = (typeof communityCategories)[number]['value'];

export type CommunityPost = {
  id: string;
  category: CommunityCategory;
  body: string;
  media_url: string | null;
  created_at: string;
  edited_at: string | null;
  author_id: string;
  author_name: string;
  author_avatar_url: string | null;
  municipality_slug: string | null;
  municipality_name: string | null;
  reaction_count: number;
  comment_count: number;
  viewer_liked: boolean;
  viewer_bookmarked: boolean;
};

export type CommunityComment = {
  id: string;
  body: string;
  created_at: string;
  edited_at: string | null;
  author_id: string;
  author_name: string;
  author_avatar_url: string | null;
};

export type CommunityFeed = {
  items: CommunityPost[];
  next_cursor: string | null;
  categories: CommunityCategory[];
};

export async function loadCommunityFeed(input: {
  category?: CommunityCategory | null;
  municipality?: string | null;
  before?: string | null;
  limit?: number;
} = {}) {
  const params = new URLSearchParams();
  if (input.category) params.set('category', input.category);
  if (input.municipality) params.set('municipality', input.municipality);
  if (input.before) params.set('before', input.before);
  params.set('limit', String(input.limit ?? 20));
  return apiFetch<CommunityFeed>(`/api/v1/public/community?${params.toString()}`);
}

export async function loadCommunityComments(postId: string) {
  return apiFetch<{ items: CommunityComment[] }>(`/api/v1/public/community/posts/${postId}/comments`);
}

export async function createCommunityPost(input: {
  category: CommunityCategory;
  body: string;
  municipality_slug?: string | null;
}) {
  return apiFetch<{ id: string; status: string }>('/api/v1/community/posts', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export async function setCommunityLike(postId: string, active: boolean) {
  return apiFetch<{ active: boolean } | undefined>(`/api/v1/community/posts/${postId}/reactions`, {
    method: active ? 'POST' : 'DELETE',
  });
}

export async function setCommunityBookmark(postId: string, active: boolean) {
  return apiFetch<{ active: boolean } | undefined>(`/api/v1/community/posts/${postId}/bookmark`, {
    method: active ? 'POST' : 'DELETE',
  });
}

export async function createCommunityComment(postId: string, body: string) {
  return apiFetch<{ id: string; status: string }>(`/api/v1/community/posts/${postId}/comments`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  });
}

export async function reportCommunityTarget(input: {
  target_type: 'post' | 'comment';
  target_id: string;
  reason: 'spam' | 'abuse' | 'privacy' | 'dangerous' | 'misinformation' | 'other';
  details?: string | null;
}) {
  return apiFetch<{ id: string; status: string }>('/api/v1/community/reports', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}
