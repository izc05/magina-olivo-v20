# Mágina Olivo V20 — plan de integración Beta

## Objetivo
Construir una única rama candidata de Beta a partir de `feat/v20-visual-prototype`, integrar de forma controlada los workstreams terminados y mantener `main` intacta hasta que el mismo HEAD pase todos los gates obligatorios.

## Rama de integración
`integrate/v20-beta-closure`

## Regla de integración
1. No duplicar workstreams ya absorbidos por el candidate.
2. Integrar primero superficies aisladas y sin migraciones.
3. Integrar después módulos funcionales con persistencia.
4. Resolver colisiones de migraciones antes de incorporar Admin/Mercado/Planes/Mi Olivo.
5. Integrar Foundation/Runtime/QA al final de cada bloque funcional para validar el conjunto.
6. No fusionar a `main` hasta tener en un mismo HEAD:
   - V20 full candidate check verde;
   - V20 beta browser E2E verde;
   - V20 staging readiness verde;
   - auditoría móvil 360/390/430 sin regresiones críticas.

## Orden de trabajo

### Bloque A — superficies públicas aisladas
- Ayuda / primeros pasos.
- Consejos del campo.
- Pueblos.
- Servicios locales.
- Cooperativas / almazaras.
- Noticias / eventos.

### Bloque B — núcleo de agricultor
- Mi Campo y resiliencia.
- Registro + Campaña.
- Inicio + Hoy.
- Perfil / Ajustes.
- Documentos / OCR.

### Bloque C — actividad profesional
- Profesional: cliente → trabajo → presupuesto → factura → cobro.
- enlaces/documentos públicos asociados.

### Bloque D — producto ampliado
- Mi Olivo.
- Planes.
- Mercado / aceite.
- Admin / CMS / territorio.

### Bloque E — hardening transversal
- Runtime / observabilidad / rate limiting.
- Engineering Foundation.
- QA móvil/browser transversal.
- revisión final de staging y backup/restore.

## Riesgos conocidos a resolver
- colisión histórica de prefijo de migración `0042` entre Admin y Mercado;
- preview integrada anterior desfasada respecto al candidate;
- selector GIS real de alta/edición aún pendiente de cierre;
- UX final de radar/mapa pendiente de cierre;
- validación de servicios reales en staging externo.

## Criterio de cierre
La Beta queda lista para integración final cuando la rama de este plan sea la única referencia integrada, sus gates sean verdes y no queden P0/P1 funcionales abiertos para el candidate.
