import type { FastifyInstance } from 'fastify';
import type { DatabaseClient } from '../db/client.js';
import { registerAdminRouteAdventureBulkRoutes } from './admin-route-adventure-bulk.js';
import { registerAdminRouteAdventureCandidateRoutes } from './admin-route-adventure-candidates.js';
import { registerAdminRouteAdventureProgressionRoutes } from './admin-route-adventure-progression.js';
import { registerAdminRouteAdventureReadinessRoutes } from './admin-route-adventure-readiness.js';
import { registerAdminRouteAdventureRoutes } from './admin-route-adventure.js';
import { registerAdminRouteKmlRoutes } from './admin-route-kml.js';
import { registerRouteActivityAchievementRoutes } from './route-activity-achievements.js';
import { registerRouteActivityInsightRoutes } from './route-activity-insights.js';
import { registerRouteActivityRoutes } from './route-activity.js';
import { registerRouteAdventureHubRoutes } from './route-adventure-hub.js';
import { registerRouteAdventureRewardRoutes } from './route-adventure-rewards.js';
import { registerRouteAdventureRoutes } from './route-adventure.js';

export function registerAdventureRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  registerRouteAdventureRoutes(app, db);
  registerRouteAdventureHubRoutes(app, db);
  registerRouteAdventureRewardRoutes(app, db);
  registerRouteActivityRoutes(app, db);
  registerRouteActivityInsightRoutes(app, db);
  registerRouteActivityAchievementRoutes(app, db);
  registerAdminRouteAdventureRoutes(app, db);
  registerAdminRouteAdventureBulkRoutes(app, db);
  registerAdminRouteAdventureReadinessRoutes(app, db);
  registerAdminRouteAdventureCandidateRoutes(app, db);
  registerAdminRouteAdventureProgressionRoutes(app, db);
  registerAdminRouteKmlRoutes(app, db);
}
