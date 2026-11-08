# Supabase Edge Function: assistant-gateway

Pasarela de inferencia segura para el proyecto **Mágina IA**.

## Principios de Seguridad

1. **Aislamiento de Secretos:** La clave `MISTRAL_API_KEY` reside exclusivamente en los secretos de la Edge Function (`supabase secrets set MISTRAL_API_KEY=...`). Jamás se incluye en la aplicación Android.
2. **Autenticación Fuerte:** Valida el JWT de sesión del agricultor mediante `supabase.auth.getUser()`. Si la sesión caduca o es inválida, se devuelve HTTP 401.
3. **Control de Membresía Multi-Tenant:** Comprueba mediante RLS en `workspace_members` que el agricultor tiene un rol activo en el `workspace_id` consultado antes de procesar cualquier inferencia (HTTP 403 en caso contrario).
4. **Modo MOCK Seguro por Defecto:** Si la variable `SIMULATED_MISTRAL_ENABLED` es `true` o no existe `MISTRAL_API_KEY`, responde localmente sin facturación ni peticiones a la API externa de Mistral.
5. **Rate Limiting & Token Budget:** Limita el tamaño del prompt (máx. 2.000 caracteres) y tokens de respuesta (máx. 1.000) para garantizar el control de presupuesto.
6. **No Desplegado:** Este archivo forma parte del código de diseño de la Fase D y no se despliega en DEV ni en producción sin autorización explícita.

