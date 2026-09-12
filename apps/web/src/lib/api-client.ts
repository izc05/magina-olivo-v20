export const apiBaseUrl = (process.env.NEXT_PUBLIC_API_URL ?? '').replace(/\/$/, '');
export const previewModeEnabled = process.env.NEXT_PUBLIC_PREVIEW_MODE === 'true';

export class ApiUnavailableError extends Error {
  constructor() {
    super('NEXT_PUBLIC_API_URL is not configured.');
    this.name = 'ApiUnavailableError';
  }
}

export class ApiRequestError extends Error {
  status: number;
  payload: unknown;

  constructor(status: number, payload: unknown) {
    super(`API request failed with status ${status}`);
    this.name = 'ApiRequestError';
    this.status = status;
    this.payload = payload;
  }
}

export type ApiOptions = RequestInit & {
  workspaceId?: string | null;
};

export async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  if (!apiBaseUrl) throw new ApiUnavailableError();

  const headers = new Headers(options.headers);
  if (options.body && !headers.has('content-type')) headers.set('content-type', 'application/json');
  if (options.workspaceId) headers.set('x-workspace-id', options.workspaceId);

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers,
    credentials: 'include',
  });

  if (response.status === 204) return undefined as T;

  const contentType = response.headers.get('content-type') ?? '';
  const payload: unknown = contentType.includes('application/json')
    ? await response.json()
    : await response.text();

  if (!response.ok) throw new ApiRequestError(response.status, payload);
  return payload as T;
}
