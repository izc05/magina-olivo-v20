# Contrato de integración contextual de Experiencias

Los módulos Rutas, Pueblos, Eventos y otros contenidos territoriales no necesitan depender internamente de Experiencias. Para recomendar o enlazar una experiencia basta usar sus endpoints públicos y conservar la procedencia comercial.

## Consulta
`GET /api/v1/public/experiences` permite descubrir experiencias publicadas por tipo, negocio y municipio.

## Atribución
Cuando otro módulo abra una reserva debe enviar:
- `sourceContext`: por ejemplo `route`, `place`, `event`, `home`, `search`.
- `sourceKey`: slug/id estable de la entidad origen.

La reserva crea un lead y un evento con esa misma procedencia. De esta forma Mágina Olivo podrá responder preguntas como:
- qué rutas generan más reservas;
- qué pueblos convierten mejor;
- qué evento llevó clientes a qué negocio;
- qué campaña territorial produce valor.

## Regla
La atribución no altera el ranking orgánico ni la verificación de la empresa. Un acuerdo comercial debe mostrarse de forma explícita cuando afecte a presentación o prioridad.
