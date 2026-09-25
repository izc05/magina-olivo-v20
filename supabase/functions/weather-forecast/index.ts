// Supabase Edge Function `weather-forecast` (verify_jwt = true: callers send the project's
// public anon key). Secrets: AEMET_API_KEY (Edge Functions > Secrets). Never logged.

import { handleForecast } from "./handler.ts";

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") {
    return Response.json({ error: "method_not_allowed" }, { status: 405 });
  }
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "invalid_request" }, { status: 400 });
  }
  const result = await handleForecast(body, {
    fetch: (input, init) => fetch(input, init),
    aemetApiKey: Deno.env.get("AEMET_API_KEY") || undefined,
    now: () => new Date(),
    log: (message) => console.log(`[weather-forecast] ${message}`),
  });
  return Response.json(result.body, {
    status: result.status,
    headers: { "cache-control": "no-store" },
  });
});
