import {
  buildWeatherVisualModel,
  type BuildWeatherVisualModelOptions,
  type WeatherVisualModel,
} from '@magina/weather/visual';
import type { AdventureWeatherState } from '../../../../lib/weather-source';

export function buildWeatherSceneModel(
  weather: AdventureWeatherState,
  options: BuildWeatherVisualModelOptions = {},
): WeatherVisualModel {
  return buildWeatherVisualModel(weather, options);
}
