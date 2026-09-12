import type { MunicipalityForecast } from './aemet.js';
import { fetchAemetDailyForecast } from './aemet.js';

export interface MunicipalityWeatherProvider {
  dailyForecast(municipalityCode: string): Promise<MunicipalityForecast>;
}

export const remoteAemetWeatherProvider: MunicipalityWeatherProvider = {
  dailyForecast: fetchAemetDailyForecast,
};
