import assert from 'node:assert/strict';
import {
  buildAemetDailyForecastUrl,
  parseAemetDailyForecast,
  validateAemetMunicipalityCode,
} from '../weather/aemet.js';

assert.equal(validateAemetMunicipalityCode('23044'), true);
assert.equal(validateAemetMunicipalityCode('2304'), false);
assert.equal(validateAemetMunicipalityCode('../44'), false);

const endpoint = new URL(buildAemetDailyForecastUrl('23044'));
assert.equal(endpoint.origin, 'https://opendata.aemet.es');
assert.equal(endpoint.pathname, '/opendata/api/prediccion/especifica/municipio/diaria/23044');

const forecast = parseAemetDailyForecast('23044', [
  {
    elaborado: '2026-09-10T06:00:00',
    nombre: 'Huelma',
    provincia: 'Jaén',
    prediccion: {
      dia: [
        {
          fecha: '2026-09-10',
          probPrecipitacion: [
            { value: 15, periodo: '00-12' },
            { value: 35, periodo: '12-24' },
            { value: 25, periodo: '00-24' },
          ],
          temperatura: { minima: 13, maxima: 28 },
          viento: [
            { velocidad: [5, 10, 15] },
            { velocidad: [20] },
          ],
        },
        {
          fecha: '2026-09-11',
          probPrecipitacion: [
            { value: 40, periodo: '00-12' },
            { value: 60, periodo: '12-24' },
          ],
          temperatura: { minima: '12', maxima: '24' },
          viento: [{ velocidad: ['10', '25'] }],
        },
        {
          fecha: '2026-09-12',
          probPrecipitacion: [],
          viento: [],
        },
      ],
    },
  },
]);

assert.equal(forecast.provider, 'AEMET OpenData');
assert.equal(forecast.municipalityCode, '23044');
assert.equal(forecast.municipalityName, 'Huelma');
assert.equal(forecast.province, 'Jaén');
assert.equal(forecast.days.length, 3);
assert.deepEqual(forecast.days[0], {
  date: '2026-09-10',
  precipitationProbabilityPercent: 25,
  temperatureMinC: 13,
  temperatureMaxC: 28,
  windMaxKmh: 20,
});
assert.equal(forecast.days[1]?.precipitationProbabilityPercent, 60);
assert.equal(forecast.days[1]?.windMaxKmh, 25);
assert.deepEqual(forecast.days[2], {
  date: '2026-09-12',
  precipitationProbabilityPercent: null,
  temperatureMinC: null,
  temperatureMaxC: null,
  windMaxKmh: null,
});

assert.throws(() => parseAemetDailyForecast('bad', []), /INVALID_AEMET_MUNICIPALITY_CODE/);

console.log('WEATHER_ADAPTER_SMOKE_OK');
