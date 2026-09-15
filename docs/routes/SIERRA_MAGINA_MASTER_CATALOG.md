# Catálogo maestro de senderismo — Sierra Mágina

Fecha de auditoría inicial: 2026-09-13
Rama: `feat/v20-routes-explore`

## Objetivo

Este documento es la fuente de verdad del frente de catalogación de senderismo. El módulo de Rutas no se considerará cerrado hasta que exista cobertura territorial trazable, fuente verificable y estado técnico explícito para cada ruta.

## Regla de publicación

Una ruta solo puede marcarse como `published` cuando:

1. existe una fuente identificable;
2. el trazado canónico real está cargado;
3. el trazado ha sido validado;
4. los datos técnicos no son inventados;
5. el estado de acceso/restricciones ha sido revisado;
6. existe fecha `last_verified_at`.

Los contenidos promocionales o generados con IA nunca sustituyen a tracks, distancias, desniveles, accesos, restricciones ni avisos reales.

## Cobertura territorial de Sierra Mágina

El catálogo territorial objetivo trabaja con 16 municipios: Albanchez de Mágina, Bedmar y Garcíez, Bélmez de la Moraleda, Cabra del Santo Cristo, Cambil, Campillo de Arenas, Cárcheles, Huelma, Jimena, Jódar, La Guardia de Jaén, Larva, Mancha Real, Noalejo, Pegalajar y Torres.

La migración territorial histórica `0013_territory_catalog.sql` solo contiene cinco municipios (Albanchez de Mágina, Bedmar y Garcíez, Huelma, Jimena y Jódar), por lo que la catalogación integral de rutas debe tratar la ampliación territorial como dependencia explícita y no ocultarla.

## Núcleo oficial confirmado — Parque Natural Sierra Mágina

La página oficial de Ventana del Visitante del Parque Natural enumera actualmente 15 senderos señalizados. Este conjunto constituye el nivel `official_core` del catálogo:

| # | Sendero | Municipio principal | Fuente | Estado catálogo |
|---|---|---|---|---|
| 1 | Adelfal de Cuadros | Bedmar y Garcíez | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 2 | Caño del Aguadero | Bedmar y Garcíez | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 3 | Castillo de Albanchez | Albanchez de Mágina | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 4 | Castillo de Mata Bejid | Cambil | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 5 | El Peralejo | Cambil | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 6 | Fuenmayor | Torres | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 7 | Gibralberca | Cambil | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 8 | La Cueva de la Graja | Jimena | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 9 | Las Viñas | Bedmar y Garcíez | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 10 | Pinar de Cánava | Jimena | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 11 | Puerto de la Mata | Cambil | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 12 | Sierra de la Cruz | Jódar | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 13 | Subida al Hoyo de la Laguna | Bélmez de la Moraleda | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 14 | Subida a Pico Mágina y Miramundos | Huelma | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |
| 15 | Veredón–Mojón Blanco | Pegalajar / entorno occidental | Junta de Andalucía · Ventana del Visitante | localizada / fuente oficial |

## Estados que debe medir Admin

Para cada ruta:

- `discovered`: ruta localizada en una fuente;
- `documented`: ficha mínima contrastada;
- `source_verified`: fuente oficial o secundaria clasificada;
- `track_found`: existe GPX/KML/GML/GeoJSON real;
- `track_validated`: geometría revisada y aceptada;
- `technical_complete`: distancia, duración, desniveles, dificultad y tipo de recorrido disponibles o justificadamente N/D;
- `safety_complete`: acceso, restricciones y notas de seguridad revisadas;
- `media_complete`: recursos reales/licenciados suficientes;
- `publishable`: cumple la regla de publicación.

## Capas del catálogo

1. `official_core`: senderos señalizados por Junta de Andalucía en Parque Natural Sierra Mágina.
2. `official_comarca`: rutas publicadas por Diputación/Jaén Paraíso Interior, ADR, ayuntamientos y otros organismos públicos de los 16 municipios.
3. `federated`: GR, PR y SL homologados, con referencia federativa verificable.
4. `community`: rutas comunitarias de interés, siempre separadas de las oficiales y sujetas a revisión.

## Criterio de completitud

No usar la palabra “completo” solo porque la interfaz funcione. El cierre exige un informe reproducible con:

- 16/16 municipios auditados;
- fuentes oficiales revisadas por municipio;
- duplicados resueltos;
- rutas clasificadas por capa;
- track real localizado cuando exista;
- estado de validación visible;
- pendientes documentados;
- fecha de última auditoría.

## Hallazgo operativo importante

El 13/09/2026 la ficha oficial de **Adelfal de Cuadros** figura como `CERRADO TEMPORALMENTE`. Los avisos operativos de una ruta deben poder bloquear/recomendar no iniciar el recorrido sin borrar la ficha histórica.

## Próximo barrido obligatorio

El núcleo oficial del parque no equivale todavía al catálogo completo de la comarca. El siguiente barrido debe cubrir, municipio por municipio:

- Jaén Paraíso Interior / Diputación Provincial;
- ADR Sierra Mágina;
- webs oficiales de ayuntamientos;
- Federación Andaluza de Deportes de Montaña, Escalada y Senderismo para GR/PR/SL;
- fuentes oficiales específicas de itinerarios, equipamientos y planes turísticos;
- Circuito Intermodal de Sierra Mágina cuando su inventario/track público esté disponible.

Cada alta nueva debe incorporarse también a `data/routes/sierra-magina-master-catalog.json`.
