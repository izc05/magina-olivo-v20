package com.isivoltpro.maginaolivo.domain.registry

/**
 * Where a land-registry parcel is, as the registry itself says (Spain: Catastro's municipality
 * and province for a reference). Used to fill an empty municipality/province; the farmer can
 * always change it.
 */
data class RegistryLocation(val municipality: String, val province: String?)
