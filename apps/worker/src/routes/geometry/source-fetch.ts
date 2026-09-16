export type RouteSourceFetchResponse = {
  status: number;
  ok: boolean;
  headers: { get(name: string): string | null };
  arrayBuffer(): Promise<ArrayBuffer>;
};

export type RouteSourceFetch = (
  url: string,
  init: { headers: Record<string, string> },
) => Promise<RouteSourceFetchResponse>;

export type RouteSourceFetchResult =
  | {
      status: 'fetched';
      httpStatus: number;
      content: Uint8Array;
      contentType: string | null;
      attempts: number;
    }
  | {
      status: 'blocked';
      httpStatus: number;
      content: null;
      contentType: string | null;
      attempts: number;
    }
  | {
      status: 'failed';
      httpStatus: number;
      content: null;
      contentType: string | null;
      attempts: number;
    };

export type FetchOfficialRouteAssetInput = {
  url: string;
  fetchImpl?: RouteSourceFetch;
  maxAttempts?: number;
  retryDelayMs?: number;
};

const OFFICIAL_ASSET_HEADERS = {
  Accept: 'application/vnd.google-earth.kmz, application/vnd.google-earth.kml+xml, application/gml+xml, application/xml, text/xml, */*;q=0.1',
  'User-Agent': 'MaginaAventuraRouteIngestor/1.0',
};

const RETRYABLE_HTTP_STATUSES = new Set([408, 425, 429, 500, 502, 503, 504]);

function sleep(milliseconds: number) {
  if (milliseconds <= 0) return Promise.resolve();
  return new Promise<void>((resolve) => setTimeout(resolve, milliseconds));
}

export async function fetchOfficialRouteAsset(
  input: FetchOfficialRouteAssetInput,
): Promise<RouteSourceFetchResult> {
  const fetchImpl = input.fetchImpl ?? (globalThis.fetch as unknown as RouteSourceFetch);
  const maxAttempts = Math.max(1, Math.min(5, Math.trunc(input.maxAttempts ?? 3)));
  const retryDelayMs = Math.max(0, Math.trunc(input.retryDelayMs ?? 500));
  let lastHttpStatus = 0;
  let lastContentType: string | null = null;

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      const response = await fetchImpl(input.url, { headers: OFFICIAL_ASSET_HEADERS });
      lastHttpStatus = response.status;
      lastContentType = response.headers.get('content-type');

      if (response.status === 401 || response.status === 403) {
        return {
          status: 'blocked',
          httpStatus: response.status,
          content: null,
          contentType: lastContentType,
          attempts: attempt,
        };
      }

      if (response.ok) {
        return {
          status: 'fetched',
          httpStatus: response.status,
          content: new Uint8Array(await response.arrayBuffer()),
          contentType: lastContentType,
          attempts: attempt,
        };
      }

      if (!RETRYABLE_HTTP_STATUSES.has(response.status) || attempt === maxAttempts) {
        return {
          status: 'failed',
          httpStatus: response.status,
          content: null,
          contentType: lastContentType,
          attempts: attempt,
        };
      }
    } catch {
      if (attempt === maxAttempts) {
        return {
          status: 'failed',
          httpStatus: lastHttpStatus,
          content: null,
          contentType: lastContentType,
          attempts: attempt,
        };
      }
    }

    await sleep(retryDelayMs * attempt);
  }

  return {
    status: 'failed',
    httpStatus: lastHttpStatus,
    content: null,
    contentType: lastContentType,
    attempts: maxAttempts,
  };
}
