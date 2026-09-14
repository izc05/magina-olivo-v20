import { apiFetch } from './api-client';

export type ExperienceType = 'aove_tasting'|'mill_visit'|'guided_tour'|'workshop'|'gastronomy'|'nature'|'culture'|'family'|'wellness'|'other';
export type BookingStatus = 'pending'|'confirmed'|'declined'|'cancelled'|'completed'|'no_show';
export type ExperienceStatus = 'draft'|'published'|'archived';
export type ExperienceSlotStatus = 'open'|'full'|'cancelled'|'hidden';

export type ExperienceListItem = {
  id:string; slug:string; title:string; summary:string|null; experience_type:ExperienceType;
  duration_minutes:number|null; min_party_size:number; max_party_size:number|null; price_cents:number|null; currency:string;
  booking_mode:'request'|'external'|'contact'; cover_image_url:string|null; business_id:string; business_slug:string; business_name:string;
  municipality_name:string|null; next_slot:string|null; available_slots:number;
};

export type ExperienceSlot = { id:string; starts_at:string; ends_at:string|null; capacity:number; confirmed_count:number; price_override_cents:number|null; status:ExperienceSlotStatus; availablePlaces:number };
export type ExperienceDetail = ExperienceListItem & {
  description:string|null; booking_url:string|null; meeting_point_text:string|null; location:{latitude:number;longitude:number}|null;
  languages:string[]; includes:string[]; excludes:string[]; cancellation_policy:string|null; business_logo:string|null; slots:ExperienceSlot[];
};

export type ExperienceAdminItem = Record<string,unknown> & {
  id:string; business_id:string; business_name:string; business_slug:string; slug:string; title:string; status:ExperienceStatus; experience_type:ExperienceType;
  summary?:string|null; description?:string|null; duration_minutes?:number|null; min_party_size?:number; max_party_size?:number|null; price_cents?:number|null; currency?:string;
  booking_mode?:'request'|'external'|'contact'; booking_url?:string|null; meeting_point_text?:string|null; cancellation_policy?:string|null; cover_image_url?:string|null;
};
export type ExperienceAdminSlot = Record<string,unknown> & {id:string;experience_id:string;experience_title:string;business_name:string;starts_at:string;ends_at?:string|null;capacity:number;confirmed_count:number;price_override_cents?:number|null;status:ExperienceSlotStatus};
export type ExperienceAdminBooking = Record<string,unknown> & {id:string;experience_id:string;experience_title:string;business_name:string;contact_name:string;contact_email:string|null;contact_phone:string|null;party_size:number;status:BookingStatus;created_at:string;slot_starts_at:string|null};
export type ExperienceAdminCatalog = { experiences:ExperienceAdminItem[]; slots:ExperienceAdminSlot[]; bookings:ExperienceAdminBooking[] };

export type MyExperienceCatalog = {
  role:'owner'|'manager'|'editor'|'analyst';
  experiences:ExperienceAdminItem[];
  slots:ExperienceAdminSlot[];
  bookings:ExperienceAdminBooking[];
};

export type ExperienceCreateInput = {
  slug:string; title:string; summary?:string|null; description?:string|null; experienceType?:ExperienceType;
  durationMinutes?:number|null; minPartySize?:number; maxPartySize?:number|null; priceCents?:number|null; currency?:string;
  bookingMode?:'request'|'external'|'contact'; bookingUrl?:string|null; meetingPointText?:string|null;
  languages?:string[]; includes?:string[]; excludes?:string[]; cancellationPolicy?:string|null; coverImageUrl?:string|null;
  status?:ExperienceStatus; sortOrder?:number;
};
export type ExperiencePatchInput = Partial<Omit<ExperienceCreateInput,'slug'>>;
export type SlotPatchInput = {startsAt?:string;endsAt?:string|null;capacity?:number;priceOverrideCents?:number|null;status?:ExperienceSlotStatus};

export async function loadExperiences(filters:{q?:string;type?:ExperienceType;businessSlug?:string}={}) {
  const params=new URLSearchParams();
  if(filters.q)params.set('q',filters.q); if(filters.type)params.set('type',filters.type); if(filters.businessSlug)params.set('businessSlug',filters.businessSlug);
  return apiFetch<{experiences:ExperienceListItem[];meta:{count:number}}>(`/api/v1/public/experiences${params.size?`?${params}`:''}`);
}
export async function loadExperience(businessSlug:string,slug:string){
  const payload=await apiFetch<{experience:ExperienceDetail}>(`/api/v1/public/businesses/${encodeURIComponent(businessSlug)}/experiences/${encodeURIComponent(slug)}`); return payload.experience;
}
export async function requestExperienceBooking(businessSlug:string,slug:string,input:{slotId?:string|null;contactName:string;contactEmail?:string|null;contactPhone?:string|null;partySize:number;requestedFor?:string|null;message?:string|null;consentBusinessContact:true;sourceContext?:string|null;sourceKey?:string|null}){
  return apiFetch<{booking:{id:string;status:string};leadId:string;message:string}>(`/api/v1/public/businesses/${encodeURIComponent(businessSlug)}/experiences/${encodeURIComponent(slug)}/bookings`,{method:'POST',body:JSON.stringify(input)});
}

export const experienceAdminApi={
  catalog:()=>apiFetch<ExperienceAdminCatalog>('/api/v1/admin/business-experiences'),
  create:(businessId:string,input:ExperienceCreateInput)=>apiFetch<{experience:{id:string}}>(`/api/v1/admin/businesses/${encodeURIComponent(businessId)}/experiences`,{method:'POST',body:JSON.stringify(input)}),
  update:(id:string,input:ExperiencePatchInput)=>apiFetch<{ok:true}>(`/api/v1/admin/business-experiences/${encodeURIComponent(id)}`,{method:'PATCH',body:JSON.stringify(input)}),
  createSlot:(experienceId:string,input:{startsAt:string;endsAt?:string|null;capacity:number;priceOverrideCents?:number|null;status?:ExperienceSlotStatus})=>apiFetch<{slot:{id:string}}>(`/api/v1/admin/business-experiences/${encodeURIComponent(experienceId)}/slots`,{method:'POST',body:JSON.stringify(input)}),
  updateSlot:(id:string,input:SlotPatchInput)=>apiFetch<{ok:true}>(`/api/v1/admin/business-experience-slots/${encodeURIComponent(id)}`,{method:'PATCH',body:JSON.stringify(input)}),
  bookingStatus:(id:string,status:BookingStatus)=>apiFetch<{ok:true}>(`/api/v1/admin/business-experience-bookings/${encodeURIComponent(id)}`,{method:'PATCH',body:JSON.stringify({status})}),
};

export const myExperienceApi={
  catalog:(businessId:string)=>apiFetch<MyExperienceCatalog>(`/api/v1/my/businesses/${encodeURIComponent(businessId)}/experiences`),
  create:(businessId:string,input:ExperienceCreateInput)=>apiFetch<{experience:{id:string}}>(`/api/v1/my/businesses/${encodeURIComponent(businessId)}/experiences`,{method:'POST',body:JSON.stringify(input)}),
  update:(businessId:string,id:string,input:ExperiencePatchInput)=>apiFetch<{ok:true}>(`/api/v1/my/businesses/${encodeURIComponent(businessId)}/experiences/${encodeURIComponent(id)}`,{method:'PATCH',body:JSON.stringify(input)}),
  createSlot:(businessId:string,experienceId:string,input:{startsAt:string;endsAt?:string|null;capacity:number;priceOverrideCents?:number|null;status?:ExperienceSlotStatus})=>apiFetch<{slot:{id:string}}>(`/api/v1/my/businesses/${encodeURIComponent(businessId)}/experiences/${encodeURIComponent(experienceId)}/slots`,{method:'POST',body:JSON.stringify(input)}),
  updateSlot:(businessId:string,id:string,input:SlotPatchInput)=>apiFetch<{ok:true}>(`/api/v1/my/businesses/${encodeURIComponent(businessId)}/experience-slots/${encodeURIComponent(id)}`,{method:'PATCH',body:JSON.stringify(input)}),
  bookingStatus:(businessId:string,id:string,status:BookingStatus)=>apiFetch<{ok:true}>(`/api/v1/my/businesses/${encodeURIComponent(businessId)}/experience-bookings/${encodeURIComponent(id)}`,{method:'PATCH',body:JSON.stringify({status})}),
};
