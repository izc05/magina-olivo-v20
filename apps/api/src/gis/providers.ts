import type { CatastroBbox, CatastroParcel } from './catastro.js';
import { fetchCatastroParcelByReference, fetchCatastroParcels } from './catastro.js';
import type { SigpacBbox, SigpacRecinto } from './sigpac.js';
import { fetchSigpacRecintoById, fetchSigpacRecintos } from './sigpac.js';

export interface CatastroProvider {
  parcelsByBbox(bbox: CatastroBbox): Promise<CatastroParcel[]>;
  parcelByReference(reference: string): Promise<CatastroParcel>;
}

export interface SigpacProvider {
  recintosByBbox(bbox: SigpacBbox): Promise<SigpacRecinto[]>;
  recintoById(featureId: string): Promise<SigpacRecinto>;
}

export type GisProviders = {
  catastro: CatastroProvider;
  sigpac: SigpacProvider;
};

export const remoteGisProviders: GisProviders = {
  catastro: {
    parcelsByBbox: fetchCatastroParcels,
    parcelByReference: fetchCatastroParcelByReference,
  },
  sigpac: {
    recintosByBbox: fetchSigpacRecintos,
    recintoById: fetchSigpacRecintoById,
  },
};
