# Solicitud segura de despliegue a staging

Este mecanismo existe para poder lanzar `V20 staging deploy` desde `integrate/v20-beta-closure` sin tocar `main`.

## No ejecuta nada por defecto

El workflow solo se dispara por push cuando cambia exactamente:

```text
deploy/staging/STAGING_DEPLOY_REQUEST.json
```

El archivo `STAGING_DEPLOY_REQUEST.example.json` es solo una plantilla y no dispara ningún despliegue.

## Formato

Crear o actualizar `deploy/staging/STAGING_DEPLOY_REQUEST.json` con:

```json
{
  "expected_sha": "<sha-completo-de-40-caracteres>",
  "confirm": "DEPLOY-STAGING"
}
```

El workflow rechazará la solicitud si:

- `expected_sha` no es un SHA exacto de 40 caracteres en minúsculas;
- `confirm` no es exactamente `DEPLOY-STAGING`;
- el SHA no pertenece al historial de `integrate/v20-beta-closure`;
- el SHA no desciende de `feat/v20-visual-prototype`;
- Full Candidate, Browser E2E o Staging Readiness no están verdes para ese mismo SHA;
- falta el Environment `staging` o cualquiera de sus secretos/variables;
- el preflight del `.env`, SSH, deploy o smoke HTTPS falla.

## Environment requerido

Secrets:

```text
STAGING_ENV_FILE
STAGING_SSH_PRIVATE_KEY
STAGING_SSH_KNOWN_HOSTS
```

Variables:

```text
STAGING_WEB_URL
STAGING_HOST
STAGING_USER
STAGING_PORT
STAGING_PATH
```

No versionar secretos ni credenciales en este archivo ni en la solicitud JSON.

## Regla de Beta

Mientras V20 siga en Beta, no crear el fichero real de solicitud hasta que host, DNS/TLS, credenciales y proveedores externos estén preparados y se haya decidido explícitamente desplegar ese SHA.
