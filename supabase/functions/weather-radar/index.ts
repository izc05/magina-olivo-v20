// Supabase Edge Function `weather-radar` (verify_jwt = true; callers send the anon key).

import { handleRadar } from "./handler.ts";

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
  const result = await handleRadar(body, { fetch: (input, init) => fetch(input, init), now: () => new Date() });
  return Response.json(result.body, { status: result.status, headers: { "cache-control": "no-store" } });
});
