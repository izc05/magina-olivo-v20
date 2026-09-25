// Generic municipality resolution (replaces the old four-town allowlist).
// Source: AEMET's master list `maestro/municipios` (every Spanish municipality with its INE
// code and coordinates). Matching is by normalized name within the province when given.

import type { Place } from "./contract.ts";

export interface MasterEntry {
  id: string; // "id23019"
  nombre: string; // "Bedmar y Garcíez"
  latitud_dec?: string;
  longitud_dec?: string;
}

/** INE province code by normalized province name (official and common spellings). */
const PROVINCES: Record<string, string> = {
  "alava": "01", "araba": "01", "araba/alava": "01", "albacete": "02", "alicante": "03", "alacant": "03",
  "almeria": "04", "avila": "05", "badajoz": "06", "baleares": "07", "illes balears": "07", "islas baleares": "07",
  "barcelona": "08", "burgos": "09", "caceres": "10", "cadiz": "11", "castellon": "12", "castello": "12",
  "ciudad real": "13", "cordoba": "14", "a coruna": "15", "la coruna": "15", "coruna": "15", "cuenca": "16",
  "girona": "17", "gerona": "17", "granada": "18", "guadalajara": "19", "gipuzkoa": "20", "guipuzcoa": "20",
  "huelva": "21", "huesca": "22", "jaen": "23", "leon": "24", "lleida": "25", "lerida": "25", "la rioja": "26",
  "rioja": "26", "lugo": "27", "madrid": "28", "malaga": "29", "murcia": "30", "navarra": "31", "nafarroa": "31",
  "ourense": "32", "orense": "32", "asturias": "33", "palencia": "34", "las palmas": "35", "pontevedra": "36",
  "salamanca": "37", "santa cruz de tenerife": "38", "tenerife": "38", "cantabria": "39", "segovia": "40",
  "sevilla": "41", "soria": "42", "tarragona": "43", "teruel": "44", "toledo": "45", "valencia": "46",
  "valladolid": "47", "bizkaia": "48", "vizcaya": "48", "zamora": "49", "zaragoza": "50", "ceuta": "51",
  "melilla": "52",
};

const PROVINCE_NAMES: Record<string, string> = {
  "01": "Araba/Álava", "02": "Albacete", "03": "Alicante", "04": "Almería", "05": "Ávila", "06": "Badajoz",
  "07": "Illes Balears", "08": "Barcelona", "09": "Burgos", "10": "Cáceres", "11": "Cádiz", "12": "Castellón",
  "13": "Ciudad Real", "14": "Córdoba", "15": "A Coruña", "16": "Cuenca", "17": "Girona", "18": "Granada",
  "19": "Guadalajara", "20": "Gipuzkoa", "21": "Huelva", "22": "Huesca", "23": "Jaén", "24": "León",
  "25": "Lleida", "26": "La Rioja", "27": "Lugo", "28": "Madrid", "29": "Málaga", "30": "Murcia",
  "31": "Navarra", "32": "Ourense", "33": "Asturias", "34": "Palencia", "35": "Las Palmas", "36": "Pontevedra",
  "37": "Salamanca", "38": "Santa Cruz de Tenerife", "39": "Cantabria", "40": "Segovia", "41": "Sevilla",
  "42": "Soria", "43": "Tarragona", "44": "Teruel", "45": "Toledo", "46": "Valencia", "47": "Valladolid",
  "48": "Bizkaia", "49": "Zamora", "50": "Zaragoza", "51": "Ceuta", "52": "Melilla",
};

export function normalize(text: string): string {
  return text
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9ñ/ ]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/** "Carolina, La" and "La Carolina" are the same town. */
function variants(name: string): string[] {
  const plain = normalize(name);
  const comma = /^(.*), *(el|la|los|las|l'|o|a|os|as|es|sa|ses)$/i.exec(name.trim());
  return comma ? [plain, normalize(`${comma[2]} ${comma[1]}`)] : [plain];
}

export function provinceCode(province: string | undefined | null): string | null {
  if (!province) return null;
  const key = normalize(province);
  if (/^\d{2}$/.test(key)) return key;
  return PROVINCES[key] ?? null;
}

export function provinceName(code: string): string | null {
  return PROVINCE_NAMES[code.slice(0, 2)] ?? null;
}

function toPlace(entry: MasterEntry): Place {
  const code = entry.id.replace(/^id/, "");
  const lat = Number(entry.latitud_dec);
  const lon = Number(entry.longitud_dec);
  return {
    code,
    name: entry.nombre,
    province: provinceName(code),
    latitude: Number.isFinite(lat) && entry.latitud_dec ? lat : null,
    longitude: Number.isFinite(lon) && entry.longitud_dec ? lon : null,
  };
}

export type Resolution =
  | { kind: "found"; place: Place }
  | { kind: "not_found" }
  | { kind: "ambiguous"; candidates: string[] };

/**
 * Finds one municipality. Exact name first, then "Bedmar" -> "Bedmar y Garcíez" (the typed
 * name is the start of exactly one official name). Several matches are never guessed.
 */
export function resolveMunicipality(
  master: MasterEntry[],
  query: { municipalityCode?: string; municipality?: string; province?: string },
): Resolution {
  const code = query.municipalityCode?.trim();
  if (code) {
    const entry = master.find((e) => e.id.replace(/^id/, "") === code);
    return entry ? { kind: "found", place: toPlace(entry) } : { kind: "not_found" };
  }
  const wanted = query.municipality ? normalize(query.municipality) : "";
  if (!wanted) return { kind: "not_found" };
  const prov = provinceCode(query.province);
  const pool = prov ? master.filter((e) => e.id.replace(/^id/, "").startsWith(prov)) : master;

  const exact = pool.filter((e) => variants(e.nombre).includes(wanted));
  if (exact.length === 1) return { kind: "found", place: toPlace(exact[0]) };
  if (exact.length > 1) return { kind: "ambiguous", candidates: exact.map((e) => e.nombre) };

  const prefix = pool.filter((e) => variants(e.nombre).some((v) => v.startsWith(`${wanted} `)));
  if (prefix.length === 1) return { kind: "found", place: toPlace(prefix[0]) };
  if (prefix.length > 1) return { kind: "ambiguous", candidates: prefix.map((e) => e.nombre) };
  return { kind: "not_found" };
}
