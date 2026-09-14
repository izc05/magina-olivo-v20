export const BUSINESS_PLANS = ["free", "featured", "premium", "sponsor"] as const;
export type BusinessPlan = (typeof BUSINESS_PLANS)[number];

export const BUSINESS_STATUSES = ["draft", "published", "archived"] as const;
export type BusinessStatus = (typeof BUSINESS_STATUSES)[number];

export const BUSINESS_VERIFICATION_STATUSES = [
  "unverified",
  "pending",
  "verified",
  "rejected",
] as const;
export type BusinessVerificationStatus =
  (typeof BUSINESS_VERIFICATION_STATUSES)[number];

export type BusinessDirectoryFilters = {
  query?: string;
  municipalityId?: string;
  placeId?: string;
  categorySlug?: string;
  featured?: boolean;
  sponsored?: boolean;
};

export type BusinessDirectoryItem = {
  id: string;
  slug: string;
  name: string;
  shortDescription: string | null;
  municipalityId: string | null;
  placeId: string | null;
  categorySlugs: string[];
  verificationStatus: BusinessVerificationStatus;
  commercialPlan: BusinessPlan;
  featured: boolean;
  sponsored: boolean;
  priority: number;
  logoUrl: string | null;
  coverImageUrl: string | null;
};

/**
 * Paid placement is a presentation concern with an explicit disclosure.
 * Consumers must never infer organic relevance solely from priority.
 */
export function requiresSponsoredDisclosure(
  business: Pick<BusinessDirectoryItem, "sponsored" | "commercialPlan">,
): boolean {
  return business.sponsored || business.commercialPlan === "sponsor";
}

export function isBusinessCampaignActive(
  now: Date,
  campaignStart: Date | null,
  campaignEnd: Date | null,
): boolean {
  if (campaignStart && now < campaignStart) return false;
  if (campaignEnd && now > campaignEnd) return false;
  return true;
}
