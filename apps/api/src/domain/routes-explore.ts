export const ROUTE_TYPES = ["hiking", "mtb", "cycling", "trail", "family", "mixed"] as const;
export type RouteType = (typeof ROUTE_TYPES)[number];

export const ROUTE_DIFFICULTIES = ["easy", "moderate", "hard", "very_hard"] as const;
export type RouteDifficulty = (typeof ROUTE_DIFFICULTIES)[number];

export const ROUTE_STATUSES = ["draft", "review", "published", "archived"] as const;
export type RouteStatus = (typeof ROUTE_STATUSES)[number];

export const ROUTE_TRACK_STATUSES = ["missing", "uploaded", "validated", "rejected"] as const;
export type RouteTrackStatus = (typeof ROUTE_TRACK_STATUSES)[number];

export const ROUTE_MEDIA_ORIGINS = ["real", "official", "licensed", "ai_generated"] as const;
export type RouteMediaOrigin = (typeof ROUTE_MEDIA_ORIGINS)[number];

export type RouteExploreFilters = {
  query?: string;
  municipalityId?: string;
  placeId?: string;
  type?: RouteType;
  difficulty?: RouteDifficulty;
  circular?: boolean;
  familyFriendly?: boolean;
  maxDistanceM?: number;
  maxDurationMinutes?: number;
};

export type RouteSummary = {
  id: string;
  slug: string;
  name: string;
  routeType: RouteType;
  difficulty: RouteDifficulty | null;
  distanceM: number | null;
  durationMinutes: number | null;
  elevationGainM: number | null;
  circular: boolean;
  familyFriendly: boolean;
  trackStatus: RouteTrackStatus;
  municipalityId: string | null;
  placeId: string | null;
};

export type ElevationSample = {
  order: number;
  distanceM: number;
  elevationM: number;
  longitude?: number;
  latitude?: number;
  gradePercent?: number;
};

export type RouteMediaDisclosure = {
  origin: RouteMediaOrigin;
  aiGenerated: boolean;
  disclosure: string | null;
};

export function canPublishNavigableRoute(
  route: Pick<RouteSummary, "trackStatus">,
): boolean {
  return route.trackStatus === "validated";
}

export function requiresAiDisclosure(media: RouteMediaDisclosure): boolean {
  return media.origin === "ai_generated" || media.aiGenerated;
}
