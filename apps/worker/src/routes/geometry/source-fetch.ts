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
};

const OFFICIAL_ASSET_HEADERS = {
  Accept: 'application/vnd.google-earth.kmz, application/vnd.google-earth.kml+xml, application/gml+xml, application/xml, text/xml, */*;q=0.1',
  'User-Agent': 'MaginaAventuraRouteIngestor/1.0',
};

export async function fetchOfficialRouteAsset(
  input: FetchOfficialRouteAssetInput,
): Promise<RouteSourceFetchResult> {
  const fetchImpl = input.fetchImpl ?? (globalThis.fetch as unknown as RouteSourceFetch);
  const response = await fetchImpl(input.url, { headers: OFFICIAL_ASSET_HEADERS });
  const contentType = response.headers.get('content-type');

  if (response.status === 401 || response.status === 403) {
    return {
      status: 'blocked',
      httpStatus: response.status,
      content: null,
      contentType,
      attempts: 1,
    };
  }

  if (!response.ok) {
    return {
      status: 'failed',
      httpStatus: response.status,
      content: null,
      contentType,
      attempts: 1,
    };
  }

  return {
    status: 'fetched',
    httpStatus: response.status,
    content: new Uint8Array(await response.arrayBuffer()),
    contentType,
    attempts: 1,
  };
}
