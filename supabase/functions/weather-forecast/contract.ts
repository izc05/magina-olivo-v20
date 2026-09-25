// Contract of `weather-forecast` (Magina Olivo, Phase 20B, CR-006).
// Request (POST JSON), any of:
//   { "municipalityCode": "23019" }                      INE code (5 digits)
//   { "municipality": "Bedmar", "province": "Jaén" }     names as the farmer typed them
//   optional "latitude"/"longitude" help the fallback when the municipality list is unreachable.
// Response 200: WeatherResponse. Errors: { "error": <code>, "detail"?: string }.
// Provider order: AEMET -> MET Norway. The app keeps its own cache for the rest of the chain
// (last valid value -> "no disponible"), so this function never invents a value.

export type Condition =
  | "CLEAR"
  | "PARTLY_CLOUDY"
  | "CLOUDY"
  | "RAIN"
  | "STORM"
  | "SNOW"
  | "FOG"
  | "HAZE";

export type ProviderId = "AEMET" | "MET_NORWAY";

export interface Current {
  validAt: string;
  temperatureC: number;
  condition: Condition;
  rainProbabilityPercent: number | null;
  windKmh: number | null;
}

export interface Place {
  code: string;
  name: string;
  province: string | null;
  latitude: number | null;
  longitude: number | null;
}

export interface WeatherResponse {
  provider: ProviderId;
  providerName: string;
  attribution: string;
  /** When the provider produced this forecast. */
  updatedAt: string;
  /** When this function fetched it. */
  fetchedAt: string;
  location: { code: string; name: string; province: string | null };
  current: Current;
}

export interface ForecastRequest {
  municipalityCode?: string;
  municipality?: string;
  province?: string;
  latitude?: number;
  longitude?: number;
}

export const PROVIDERS: Record<ProviderId, { name: string; attribution: string }> = {
  AEMET: {
    name: "AEMET",
    attribution: "© AEMET. Información elaborada por la Agencia Estatal de Meteorología.",
  },
  MET_NORWAY: {
    name: "MET Norway",
    attribution: "Datos de MET Norway (Instituto Meteorológico de Noruega), licencia CC BY 4.0.",
  },
};

export class ProviderError extends Error {
  readonly reason: string;
  constructor(reason: string) {
    super(reason);
    this.reason = reason;
  }
}
