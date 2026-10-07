# IUWS 3.11.4 — transporte y autorización verificados

Issue #536. Slice de preparación, 2026-10-07. No implementación productiva, no cierre de #536.
Base main: `34dccddd61fcc35b972a6eb1c57e087b45de08a8`.

## Evidencia y alcance

Descargado y extraído directamente el DOCX oficial FEGA, no una descripción de terceros:
https://www.fega.gob.es/sites/default/files/files/document/2025.11.19-ANEXO%20VI.Interfaz%20%C3%9Anico%20Com%C3%BAn_V3_11_4.docx

SHA-256 del documento: `8acd60aca62e002533ac8c4aa45d2ffb820fa94c849f72d11bff63474f418d5d`.
Título/versión interior: Diseño del Sistema de importación y exportación REA-CUE, 3.11.4, noviembre 2025.
Referencias precisas: §2.5.1, §3.1.1–3.1.7 y anexos internos 1, 3, 4, 6, 7 y 8. Los anexos internos del DOCX no se confunden con los anexos V–X de documentación SIEX.

Portal que lo publica: https://www.fega.gob.es/es/siex/documentacion-tecnica-agricola-siex
La página enlazada de Anexo VI termina en `anexo-vi-interfaz-unico-comun-3111`, aunque su contenido enlaza el documento **3.11.4**: versionar por documento y checksum, no por slug de página.

Fuente autonómica verificada:
https://www.juntadeandalucia.es/organismos/agriculturapescaaguaydesarrollorural/areas/agricultura/cuaderno-explotacion.html
Publica REAFA como prerrequisito, dos vías (habilitado/productor), alta del software con certificado y remisión de parte pública; sustituye host del Anexo VI por `https://ws108.juntadeandalucia.es/`. No se han realizado peticiones autenticadas a ese host.

## Resultado de la auditoría de autenticación

**El token de sesión lo genera IUWS.** No se especificará el protocolo como «el CUE firma un JWT propio ES256/RS256 y lo envía» basándose en artículos secundarios.

Flujo habilitado, §3.1.1 y anexo interno 6:

1. El backend solicita `comunicacion` identificando el nombre del CUE registrado.
2. Se presenta certificado cliente, `certificate-sscc` y `data-signed` según el contrato.
3. `certificate-sscc` contiene el certificado público en Base64 sin delimitadores PEM ni saltos de línea. No contiene private key.
4. `data-signed` acredita el NIF/CIF mediante firma con clave privada. El anexo remite al algoritmo del certificado; no fija en estos apartados un nombre único de algoritmo, padding ni formato binario/Base64 de firma. Ese detalle requiere descriptor/Swagger/sandbox antes de implementación criptográfica.
5. El servidor valida autorización y devuelve JWT de sesión. Las operaciones posteriores transportan token en `Authorization`; no generar JWT de sesión en Mágina.

Los claims mínimos descritos son sub, exp y cif/dominio según certificado; no inventar duración exp, clock skew, nonce, refresh token, JWKS ni esquema OAuth. El DOCX menciona Authorization sin fijar en el texto revisado el prefijo Bearer: corroborar formato exacto con Swagger/sandbox.

El anexo interno 6 muestra certificado cliente y configuración TLS con key/trust stores. El anexo 7 distingue infraestructura que transmite certificado desde balanceador y alternativa de cabecera. Por tanto **no declarar mTLS obligatorio en cada llamada ni descartarlo**: confirmar qué configuración opera Andalucía en autenticación. §3.1.1 indica que tras JWT no se requieren certificate-sscc/data-signed ni certificado en las operaciones de datos. El anexo 4 contiene wording histórico más amplio y ejemplos sin `/api`; priorizar §3.1 y registrar discrepancia para sandbox.

Certificados, anexo 3: contempla sello de entidad y certificados servidor DV/OV/EV, con autenticación de cliente; recomienda sello de entidad. No convertir esta recomendación general en aceptación de un certificado concreto por Andalucía sin alta #555. La clave privada y password del almacén permanecen exclusivamente en backend/secret manager; audit guarda fingerprint/credentialId, nunca clave, token ni firma.

## Productor individual

§3.1.2 y anexo interno 8 describen redirección a la plataforma del organismo pagador:

- `comunicacionProductor/{nombreAppCue}/{peticion}` inicia validación del productor.
- El software registra previamente callback con la CA/entidad.
- El callback recibe `token` y `peticion`; peticion identifica de forma única la solicitud.
- El OP autentica al productor y comprueba que es titular; productor sin explotación es rechazado.
- En esta petición el productor no presenta certificado cliente; no equivale a omitir alta comercial o autorizaciones.
- JWT incluye NIF del productor como cif. Al exportar no se informa CIF de gestora; al crear, UnidadGestora es NIF del titular.

Diseño interno propuesto: callback backend, correlación peticion de un solo uso vinculada a workspace/usuario/holding, TTL local y comprobación de sesión iniciadora. No copiar el servlet de ejemplo que imprime token. Redactar query en proxy/access logs, consumir token en servidor y redirigir a URL limpia; token no vuelve a APK. La protección callback concreta y mecanismos de verificación del token siguen siendo gate de seguridad antes de sandbox. No afirmar OAuth/Cl@ve/PKCE ni continuidad automática de sesión: el contrato revisado no los establece.

## Operaciones verificadas

Base Andalucía propuesta a partir de sustitución de host oficial; paths de §3.1. HTTP de lectura se corrobora por Swagger antes de habilitar adaptador; `crear` está explícitamente documentado como POST.

| Operación | Path de §3.1 | Semántica |
| --- | --- | --- |
| Comunicación habilitado | `/IUWS/api/comunicacion/{nombreAppCue}` | Autenticar y recibir token de sesión |
| Comunicación productor | `/IUWS/api/comunicacionProductor/{nombreAppCue}/{peticion}` | Redirección y callback |
| Exportación REA | `/IUWS/api/exportarREA/{NIFtitular}_{CIFgestora}` | Descarga explotación; gestora no informada para productor |
| Exportación CUE | `/IUWS/api/exportar/{numeroExplotacion}_{CIFgestora}` | Descarga actividades completas, incluidas de otros CUE con origen |
| Importación | `/IUWS/api/crear/` | POST JSON; crear/modificar/borrar actividades, alta DGC y cambio cultivo |
| Estado | `/IUWS/api/comprobarEstado/{idPeticion}` | Poll del proceso batch y resultados por actividad |
| Versión | `/IUWS/api/version` | Consulta no securizada según documento |

No asumir si el guion bajo final se elimina o permanece cuando no hay CIF gestora: comprobarlo en sandbox. Swagger de integración publicado en anexo 4: `https://sga.entornointegracion.es/IUWS/swagger-ui.html`; su publicación no acredita acceso, credenciales ni que sea sandbox andaluz vigente. No se ha usado para enviar datos.

## Envío, corrección y errores

`crear` recibe uno o varios cuadernos, cada uno con explotación y actividades. Un envío debe pertenecer a una misma gestora. Recibir idPeticion indica recepción, **no aceptación**. El procesamiento es batch y puede ser parcial. El backend debe reconciliar cada actividad; no marcar un lote ACCEPTED al recibir HTTP 200/resultado OK de crear.

El id externo de actividad permite correlacionar errores y editar actividad previa. `Borrar` solicita borrado según descriptor. Las actividades cerradas/enviadas a SIEX pueden rechazar modificación; no resolver un error administrativo reabriendo el trabajo local a PLANNED. El texto de §3.1.5 y el listado de detalle no son completamente claros sobre reutilización del identificador obtenido: conservar ambos ids (local externo/administrativo devuelto) y verificar edición con sandbox.

| Resultado/código documentado | Mapping interno propuesto |
| --- | --- |
| crear OK con id proceso | SUBMITTING, conservar receipt; después poll |
| estado pendiente, detalle código 3 | SUBMITTING; no confundir con éxito 3 de actividad |
| EXITO/3 por actividad y esValida | ACCEPTED solo esa actividad |
| PARCIAL | Estados por actividad; no repetir las ya aceptadas |
| 1 estructura / 5 campo / 9 faltantes | REJECTED, error de validación accionable |
| 2 credenciales / HTTP 401 | AUTHORIZATION_REQUIRED o SESSION_EXPIRED según evidencia; no error genérico de red |
| 4 explotación no asignada / 8 titular | AUTHORIZATION_REQUIRED; revisar vinculación |
| 6 máquina / 7 asesor / 10 DGC / 13 cultivo | REJECTED, referencias oficiales/contexto |
| 14 cerrada / 17 no modificar / 18 no borrar | REJECTED, no sobrescribir snapshot aceptado |
| 15 varias gestoras / 16 organismo / 19 id inválido | REJECTED, mapping/partición/identificador |

Un código numérico se interpreta junto con operación y nivel; no enum global «3=ACCEPTED». Desconocido conserva raw y requiere revisión. Mensajes pueden contener datos personales; raw de intercambio bajo acceso restringido y logs técnicos redactados.

## Puerto interno y audit propuestos

`CueGateway` vive en backend y consume revisión cloud canónica después de #335/#330. No consume outbox Android ni DTO IUWS desde APK.

- `beginAuthorization(context, mode)` produce URL/correlationId internos, sin token en cliente.
- `completeAuthorization(correlationId, callback)` consume una vez y almacena sesión backend.
- `downloadHolding(context)` produce observación versionada de explotación; mappers por protocolo.
- `submitRevision(context, revision, idempotencyKey)` produce receipt/proceso o error tipado.
- `checkSubmission(context, receipt)` devuelve resultados por actividad.

Context incluye workspaceId, holdingId, modo y referencias de autorización; software registrado, credencial técnica, representación y acceso a explotación son estados separados, no un booleano. Denegar llamada cuando falte autorización incluso con certificado válido.

Idempotencia interna propuesta: scope workspace/holding/revision/operación + payloadHash de bytes canónicos. Mismo key y mismo hash reutiliza receipt; mismo key/hash distinto es conflicto. La fuente no promete header Idempotency-Key: no enviarlo como supuesto contrato IUWS. Timeout tras crear puede significar recepción desconocida; registrar UNKNOWN_DELIVERY y reconciliar antes de repetir, no inventar receipt. Edición oficial preserva id externo estable según operación verificada y crea una nueva revisión audit local.

Audit inmutable propuesto: `cue_authorizations`, `cue_submission_revisions`, `cue_submission_attempts`, `cue_submission_activity_results`, `cue_official_links`, `cue_protocol_bundles`; nombres propuestos, no tablas implementadas. Revisión conserva snapshot exacto, payloadHash, versiones mapper/protocolo/catálogos/reglas, request/response restringidos, ids externos, estado y credential fingerprint. No sobrescribir evidencia aceptada. La respuesta normalizada a Android no contiene secretos, payloads raw ni nombres de métodos IUWS.

Reconciliación: Holding oficial distinto de Farm. DGC oficial y SIGPAC no sustituyen UUID de Parcel ni vínculo Catastro. Descarga crea observaciones/propuestas con fuente/versión; cambios de superficies/cultivo/identidad necesitan reconciliación explícita, no sobrescritura del histórico ni matching solo por alias.

## Gaps pendientes para cerrar #536

- Leer y cruzar Anexo V 3.11, descriptores CUE/REA incrustados y catálogos VII para campos, precisión, obligatoriedad y códigos; no se declara matriz digital exacta cerrada en este slice.
- Cruzar VIII/IX/X, autorización/revocación y alta #555. El flujo descrito en VI no acredita autorización concedida.
- Completar fertilización/riego/suelos/cosecha y modelo #540: los métodos genéricos no acreditan un payload específico.
- En el checkout main inspeccionado hay funciones weather/radar/oil-market y no se localizaron migraciones SQL ni schema canónico cloud; **no se ha auditado Supabase desplegado**. Gaps cloud propuestos no son comparación de tablas live. #335/#330 siguen como dependencias.
- Confirmar Swagger, firma data-signed, Authorization exacta, TLS Andalucía, expiración/revocación, callback, métodos HTTP, ids de edición, límites y paginación. El export CUE se describe completo; no inventar paging/webhooks.
- Ensayo certificado/runtime y sandbox con credenciales autorizadas. Sin secretos reales en esta preparación.
- Adapter vigente `AndaluciaIuws3114Adapter`; futuro adaptador Horizontal 5.9 separado. FEGA anuncia sustitución documental prevista septiembre 2027; no es prueba de runtime andaluz ya migrado.

## QA de este slice

`node --test docs/integrations/cue-andalucia/gateway-prep.test.mjs` ejecuta un fake interno: autorización, idempotencia, estado por actividad y aislamiento de adaptador. No reproduce la firma/TLS ni certifica el protocolo remoto. No hay endpoints desplegables ni cambios Android/Room. Gate productivo sigue pendiente; #536 continúa abierto.
