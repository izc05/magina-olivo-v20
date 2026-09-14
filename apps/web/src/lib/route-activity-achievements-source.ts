import { apiFetch } from './api-client';

export type RouteActivityAchievement = {
  id: string;
  label: string;
  description: string;
  metric: 'activities' | 'distance_m' | 'elevation_gain_m' | 'longest_activity_m';
  target: number;
  value: number;
  unlocked: boolean;
  progress_percent: number;
};

export type RouteActivityAchievementSummary = {
  summary: {
    completed_activities: number;
    recorded_distance_m: number;
    recorded_active_seconds: number;
    longest_activity_m: number;
    recorded_elevation_gain_m: number;
  };
  achievements: RouteActivityAchievement[];
  notice: string;
};

export function loadRouteActivityAchievements() {
  return apiFetch<RouteActivityAchievementSummary>('/api/v1/activities/me/achievements');
}
