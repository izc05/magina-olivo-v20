import type { FastifyInstance } from 'fastify';
import type { DatabaseClient } from '../db/client.js';
import { registerRoutesExploreRoutes as registerRoutesExploreCoreRoutes } from './routes-explore-core.js';
import { registerRouteAdventureRoutes } from './route-adventure.js';
import { registerRouteAdventureHubRoutes } from './route-adventure-hub.js';
import { registerRouteActivityRoutes } from './route-activity.js';

export function registerRoutesExploreRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  registerRoutesExploreCoreRoutes(app, db);
  registerRouteAdventureRoutes(app, db);
  registerRouteAdventureHubRoutes(app, db);
  registerRouteActivityRoutes(app, db);
}
