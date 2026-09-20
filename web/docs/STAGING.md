# Mágina Olivo · Staging web

Esta guía levanta únicamente la superficie web de `/web`. No modifica la aplicación Android ni sus datos.

## Requisitos

- Docker 29+ con Compose
- puerto local 3000 libre
- túnel/reverse proxy opcional para exponer el staging

## Arranque

```bash
cd web
docker compose -f docker-compose.staging.yml up -d --build
```

Comprobación local:

```bash
curl http://127.0.0.1:3000/api/health
```

Respuesta esperada:

```json
{"status":"ok","service":"magina-olivo-web","timestamp":"..."}
```

## Variables de entorno

El contenedor usa dos variables para URL pública e indexación:

```env
SITE_URL=http://localhost:3000
ALLOW_INDEXING=false
```

En staging, `ALLOW_INDEXING` debe permanecer en `false`. La web publica `robots.txt` con bloqueo global y metadatos `noindex`.

Solo en producción, después de confirmar el dominio definitivo:

```env
SITE_URL=https://maginaolivo.es
ALLOW_INDEXING=true
```

## Cloudflare Tunnel

La configuración por defecto publica el contenedor solamente en `127.0.0.1:3000`.

Si `cloudflared` se ejecuta en el host, el servicio del túnel puede apuntar a:

```
http://localhost:3000
```

Si `cloudflared` se ejecuta dentro de Docker, no uses `localhost` entre contenedores. En ese caso conecta ambos servicios a una red Docker compartida y apunta el túnel a:

```
http://magina-olivo-web:3000
```

## Actualización

```bash
git pull
cd web
docker compose -f docker-compose.staging.yml up -d --build
```

## Parada

```bash
docker compose -f docker-compose.staging.yml down
```

## Criterio de promoción a producción

No promocionar la web hasta verificar:

1. navegación completa en escritorio y móvil;
2. secuencia cinematográfica sin saltos visibles;
3. imágenes correctas y sin texto duplicado;
4. rutas públicas y legales;
5. `/api/health` estable;
6. CI Web verde;
7. dominio/canonical definitivos antes de indexar producción.
