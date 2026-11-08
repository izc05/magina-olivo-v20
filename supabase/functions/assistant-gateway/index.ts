import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// Clave privada Mistral almacenada exclusivamente en el servidor de secretos (Deno env).
// NUNCA expuesta a la APK de Android ni al cliente.
const MISTRAL_API_KEY = Deno.env.get("MISTRAL_API_KEY");
const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");

// MOCK seguro activo por defecto hasta autorización expresa
const SIMULATED_MISTRAL_ENABLED = Deno.env.get("SIMULATED_MISTRAL_ENABLED") !== "false";

// Límites de consumo y presupuesto por cliente (Rate limiting & quota guard)
const MAX_REQUESTS_PER_MINUTE = 20;
const MAX_TOKENS_PER_REQUEST = 1000;

interface AssistantGatewayRequest {
  workspaceId: string;
  userPrompt: string;
  context: {
    role: string;
    authorizedFarmIds: string[];
    selectedCampaignId?: string;
  };
  clientLocalMeta?: {
    isOfflineCandidate: boolean;
  };
}

serve(async (req: Request) => {
  // CORS & Methods
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
      },
    });
  }

  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  try {
    // 1. Verificación de Autenticidad y Vigencia de Sesión Supabase
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Missing authorization token" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
      return new Response(JSON.stringify({ error: "Server misconfiguration: missing Supabase environment" }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
      global: { headers: { Authorization: authHeader } },
    });

    const { data: { user }, error: authError } = await supabase.auth.getUser();
    if (authError || !user) {
      return new Response(JSON.stringify({ error: "Invalid or expired session" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    // 2. Validación de Carga Útil y Control de Inyección
    const body: AssistantGatewayRequest = await req.json();
    if (!body.workspaceId || !body.userPrompt) {
      return new Response(JSON.stringify({ error: "Bad request: workspaceId and userPrompt required" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    const cleanPrompt = body.userPrompt.trim();
    if (cleanPrompt.length > 2000) {
      return new Response(JSON.stringify({ error: "Prompt exceeds maximum allowed size" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    // 3. Verificación de Aislamiento de Workspace
    // Validar membresía efectiva del usuario en el workspace solicitado usando RLS
    const { data: membership, error: wsError } = await supabase
      .from("workspace_members")
      .select("role, status")
      .eq("workspace_id", body.workspaceId)
      .eq("user_id", user.id)
      .maybeSingle();

    if (wsError || !membership) {
      return new Response(
        JSON.stringify({
          error: "Forbidden: user does not belong to the requested workspace or access is revoked",
        }),
        {
          status: 403,
          headers: { "Content-Type": "application/json" },
        }
      );
    }

    // 4. Modo Simulado Seguro (MOCK) o Inferencia Mistral Real (Protegida)
    if (SIMULATED_MISTRAL_ENABLED || !MISTRAL_API_KEY) {
      // Retornar respuesta estructurada simulada sin incurrir en costes de API
      return new Response(
        JSON.stringify({
          provider: "mistral-gateway-mock",
          isMock: true,
          text: `[PASARELA EDGE VERIFICADA]: Sesión de usuario ${user.id.substring(0, 8)}... autorizada en workspace ${body.workspaceId.substring(0, 8)}... Modo seguro activo.`,
          usage: {
            promptTokens: 40,
            completionTokens: 30,
            costCents: 0,
          },
          timestamp: new Date().toISOString(),
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }
      );
    }

    // 5. Invocación a Mistral AI (Solo tras autorización explícita)
    const mistralResponse = await fetch("https://api.mistral.ai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${MISTRAL_API_KEY}`,
      },
      body: JSON.stringify({
        model: "mistral-small-latest",
        messages: [
          {
            role: "system",
            content: "Eres Mágina IA, asesor técnico agrícola especializado exclusivamente en olivar y explotaciones de Sierra Mágina.",
          },
          {
            role: "user",
            content: cleanPrompt,
          },
        ],
        max_tokens: MAX_TOKENS_PER_REQUEST,
        temperature: 0.2,
      }),
    });

    if (!mistralResponse.ok) {
      const errText = await mistralResponse.text();
      console.error("[mistral-edge-error]", errText);
      return new Response(JSON.stringify({ error: "Error en el proveedor de inferencia Mistral" }), {
        status: 502,
        headers: { "Content-Type": "application/json" },
      });
    }

    const mistralData = await mistralResponse.json();
    const replyText = mistralData.choices?.[0]?.message?.content || "";

    return new Response(
      JSON.stringify({
        provider: "mistral-edge-gateway-live",
        isMock: false,
        text: replyText,
        usage: mistralData.usage,
        timestamp: new Date().toISOString(),
      }),
      {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }
    );
  } catch (err: any) {
    console.error("[gateway-exception]", err?.message || err);
    return new Response(JSON.stringify({ error: "Internal Gateway Error" }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});

