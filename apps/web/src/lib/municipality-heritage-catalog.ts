export type MunicipalityHeritageSeed = {
  municipalitySlug: string;
  municipalityName: string;
  slug: string;
  title: string;
  role: 'heritage' | 'nature' | 'tourism';
  summary: string;
  body: string;
  sourceUrl: string;
  sourceLabel: string;
  verifiedAt: string;
};

const VERIFIED_AT = '2026-09-13';

export const MUNICIPALITY_HERITAGE_CATALOG: MunicipalityHeritageSeed[] = [
  {
    municipalitySlug: 'albanchez-de-magina', municipalityName: 'Albanchez de Mágina',
    slug: 'castillo-de-albanchez-de-magina', title: 'Castillo de Albanchez de Mágina', role: 'heritage',
    summary: 'Fortificación medieval situada sobre el núcleo urbano y uno de los elementos patrimoniales más representativos de Albanchez de Mágina.',
    body: 'El Ayuntamiento presenta el Castillo de Albanchez de Mágina como el monumento más representativo del municipio. La fortificación ocupa una posición dominante sobre la localidad y forma parte de su patrimonio histórico medieval.',
    sourceUrl: 'https://www.albanchezdemagina.es/turismo/patrimonio-historico/', sourceLabel: 'Ayuntamiento de Albanchez de Mágina', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'bedmar-y-garciez', municipalityName: 'Bedmar y Garcíez',
    slug: 'castillos-viejo-y-nuevo-de-bedmar', title: 'Castillos Viejo y Nuevo de Bedmar', role: 'heritage',
    summary: 'Dos fortificaciones históricas vinculadas al carácter fronterizo de Bedmar y declaradas Bien de Interés Cultural.',
    body: 'El patrimonio histórico municipal destaca los castillos Viejo y Nuevo de Bedmar, fortificaciones relacionadas con la defensa de este territorio fronterizo. Ambos figuran como Bien de Interés Cultural.',
    sourceUrl: 'https://www.bedmargarciez.es/turismo/patrimonio-historico/', sourceLabel: 'Ayuntamiento de Bedmar y Garcíez', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'belmez-de-la-moraleda', municipalityName: 'Bélmez de la Moraleda',
    slug: 'castillo-de-belmez', title: 'Castillo de Bélmez', role: 'heritage',
    summary: 'Fortaleza histórica de Bélmez de la Moraleda integrada en el patrimonio defensivo de Sierra Mágina.',
    body: 'El Ayuntamiento incluye el Castillo de Bélmez entre los principales elementos de su patrimonio histórico. La fortaleza constituye una referencia del pasado defensivo del municipio y del territorio de Sierra Mágina.',
    sourceUrl: 'https://www.belmezdelamoraleda.es/turismo/patrimonio-historico/', sourceLabel: 'Ayuntamiento de Bélmez de la Moraleda', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'cabra-del-santo-cristo', municipalityName: 'Cabra del Santo Cristo',
    slug: 'parroquia-santuario-santo-cristo-de-burgos', title: 'Parroquia-Santuario del Santo Cristo de Burgos', role: 'heritage',
    summary: 'Templo iniciado en el siglo XVI y enriquecido durante los siglos XVII y XVIII, uno de los principales referentes patrimoniales del municipio.',
    body: 'La guía turística municipal identifica la Parroquia-Santuario del Santo Cristo de Burgos entre los bienes patrimoniales destacados de Cabra del Santo Cristo. Su construcción se inició en el siglo XVI y el conjunto fue enriquecido en los siglos posteriores.',
    sourceUrl: 'https://aytocabradelsantocristo.com/wp-content/uploads/2024/11/GUIA-DE-CABRA-2023_compressed.pdf', sourceLabel: 'Ayuntamiento de Cabra del Santo Cristo · Guía turística municipal', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'cambil', municipalityName: 'Cambil',
    slug: 'castillo-de-mata-bejid', title: 'Castillo de Mata-Bejid', role: 'heritage',
    summary: 'Fortificación de origen medieval situada en un enclave estratégico del término municipal de Cambil.',
    body: 'La información patrimonial del Ayuntamiento sitúa el Castillo de Mata-Bejid en el contexto medieval de la zona y destaca su posición estratégica. Sus restos forman parte del patrimonio histórico de Cambil y Arbuniel.',
    sourceUrl: 'https://cambil-arbuniel.es/patrimonio-historico/', sourceLabel: 'Ayuntamiento de Cambil', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'campillo-de-arenas', municipalityName: 'Campillo de Arenas',
    slug: 'castillo-de-arenas', title: 'Castillo de Arenas', role: 'heritage',
    summary: 'Restos de una fortificación histórica vinculada al origen y al paisaje cultural de Campillo de Arenas.',
    body: 'El Ayuntamiento incluye el Castillo de Arenas entre los elementos del patrimonio histórico local. El enclave recuerda el papel defensivo del territorio y forma parte de la identidad histórica del municipio.',
    sourceUrl: 'https://www.campillodearenas.es/turismo/patrimonio-historico/', sourceLabel: 'Ayuntamiento de Campillo de Arenas', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'carcheles', municipalityName: 'Cárcheles',
    slug: 'ruinas-del-castillejo-de-carchel', title: 'Ruinas del Castillejo de Cárchel', role: 'heritage',
    summary: 'Restos arqueológicos de origen islámico situados en el entorno de Cárchel.',
    body: 'El Ayuntamiento recoge las Ruinas del Castillejo entre el patrimonio histórico de Cárcheles y relaciona el enclave con el poblamiento de época musulmana. Sus restos conservan el valor arqueológico e histórico del lugar.',
    sourceUrl: 'https://www.carcheles.es/turismo/patrimonio-historico/', sourceLabel: 'Ayuntamiento de Cárcheles', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'la-guardia-de-jaen', municipalityName: 'La Guardia de Jaén',
    slug: 'castillo-de-la-guardia-de-jaen', title: 'Castillo de La Guardia de Jaén', role: 'heritage',
    summary: 'Conjunto fortificado que domina La Guardia de Jaén, con origen medieval y transformaciones posteriores de carácter residencial.',
    body: 'La información turística municipal señala el Castillo de La Guardia de Jaén como uno de los principales hitos del municipio. Su origen se remonta al periodo de ocupación musulmana y posteriormente fue transformado en residencia palaciega.',
    sourceUrl: 'https://laguardiadejaen.com/tu-ciudad/informacion-turistica/', sourceLabel: 'Ayuntamiento de La Guardia de Jaén', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'huelma', municipalityName: 'Huelma',
    slug: 'castillo-de-solera', title: 'Castillo de Solera', role: 'heritage',
    summary: 'Fortaleza de origen islámico en Solera, vinculada a la historia fronteriza del término municipal de Huelma.',
    body: 'El Ayuntamiento de Huelma documenta el Castillo de Solera como una fortificación de origen islámico. El enclave pasó a manos cristianas en el siglo XV y está protegido como Bien de Interés Cultural.',
    sourceUrl: 'https://www.aytohuelma.es/solera/', sourceLabel: 'Ayuntamiento de Huelma', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'jimena', municipalityName: 'Jimena',
    slug: 'cueva-de-la-graja', title: 'Cueva de la Graja', role: 'heritage',
    summary: 'Abrigo con pinturas rupestres esquemáticas y una de las referencias arqueológicas más destacadas de Jimena.',
    body: 'El Ayuntamiento destaca la Cueva de la Graja por sus manifestaciones de arte rupestre esquemático y su importancia arqueológica. El enclave cuenta con protección patrimonial y constituye uno de los testimonios prehistóricos más relevantes del municipio.',
    sourceUrl: 'https://www.jimena.es/turismo/patrimonio-historico/', sourceLabel: 'Ayuntamiento de Jimena', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'jodar', municipalityName: 'Jódar',
    slug: 'castillo-de-jodar', title: 'Castillo de Jódar', role: 'heritage',
    summary: 'Conjunto defensivo histórico formado por las torres del castillo de Jódar, referencia patrimonial del municipio.',
    body: 'El Ayuntamiento dedica una ficha específica al Castillo de Jódar dentro de su patrimonio histórico. El conjunto conserva las torres que testimonian la importancia defensiva y estratégica de la localidad a lo largo de su historia.',
    sourceUrl: 'https://www.jodar.es/turismo/patrimonio-historico/castillo-de-jodar/', sourceLabel: 'Ayuntamiento de Jódar', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'larva', municipalityName: 'Larva',
    slug: 'cerro-de-castellon', title: 'Cerro de Castellón', role: 'heritage',
    summary: 'Yacimiento fortificado con ocupación ibérica y continuidad en época romana documentado en la historia local de Larva.',
    body: 'La historia municipal sitúa en el Cerro de Castellón un asentamiento fortificado de época ibérica, con ocupación entre los siglos II y I a. C. y continuidad durante la etapa romana. Es una de las referencias arqueológicas del término de Larva.',
    sourceUrl: 'https://larva.es/el-municipio/historia/', sourceLabel: 'Ayuntamiento de Larva', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'mancha-real', municipalityName: 'Mancha Real',
    slug: 'iglesia-san-juan-evangelista-mancha-real', title: 'Iglesia Parroquial de San Juan Evangelista', role: 'heritage',
    summary: 'Principal referencia monumental de Mancha Real, declarada Bien de Interés Cultural.',
    body: 'El Ayuntamiento identifica la Iglesia Parroquial de San Juan Evangelista como uno de los bienes culturales más significativos de Mancha Real. Su arquitectura está vinculada al Renacimiento jiennense y el templo cuenta con protección como Bien de Interés Cultural.',
    sourceUrl: 'https://www.manchareal.es/patrimonio-natural-e-historico', sourceLabel: 'Ayuntamiento de Mancha Real', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'noalejo', municipalityName: 'Noalejo',
    slug: 'iglesia-nuestra-senora-de-la-asuncion-noalejo', title: 'Iglesia de Nuestra Señora de la Asunción', role: 'heritage',
    summary: 'Templo parroquial del siglo XVI y uno de los principales elementos del patrimonio histórico de Noalejo.',
    body: 'La información turística del Ayuntamiento recoge la Iglesia de Nuestra Señora de la Asunción como una pieza destacada del patrimonio histórico local. El templo tiene su origen en el siglo XVI y forma parte esencial del paisaje urbano de Noalejo.',
    sourceUrl: 'https://www.noalejo.es/turismo/patrimonio-historico/', sourceLabel: 'Ayuntamiento de Noalejo', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'pegalajar', municipalityName: 'Pegalajar',
    slug: 'fuente-de-la-reja-charca-y-huerta', title: 'Fuente de la Reja, Charca y Huerta', role: 'heritage',
    summary: 'Sistema hidráulico y paisaje cultural estrechamente ligado a la identidad histórica de Pegalajar.',
    body: 'El Ayuntamiento presenta la Fuente de la Reja, la Charca y la Huerta como elementos inseparables del patrimonio de Pegalajar. El conjunto refleja la relación histórica del municipio con el agua, el regadío tradicional y su paisaje cultural.',
    sourceUrl: 'https://ayto-pegalajar.org/descubre-pegalajar/patrimonio-historico/', sourceLabel: 'Ayuntamiento de Pegalajar', verifiedAt: VERIFIED_AT,
  },
  {
    municipalitySlug: 'torres', municipalityName: 'Torres',
    slug: 'palacio-marqueses-de-camarasa', title: 'Palacio de los Marqueses de Camarasa', role: 'heritage',
    summary: 'Edificio histórico también conocido como Casa de los Cobos y uno de los monumentos destacados de Torres.',
    body: 'El Ayuntamiento incluye el Palacio de los Marqueses de Camarasa, también relacionado con la Casa de los Cobos, entre los principales monumentos del municipio. El edificio forma parte del patrimonio histórico-artístico de Torres.',
    sourceUrl: 'https://www.ayuntamientodetorres.com/el-municipio/', sourceLabel: 'Ayuntamiento de Torres', verifiedAt: VERIFIED_AT,
  },
];
