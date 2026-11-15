# Mágina Olivo · Rain Nowcasting (spike, no despliegue)

**Estado: experimento ejecutable con datos sintéticos. No activo en producción.**

Módulo TypeScript independiente del proveedor que:
- compara dos rejillas georreferenciadas de lluvia en **mm/h**;
- estima un desplazamiento global con puntuación de solapamiento y rechaza ambigüedades;
- proyecta orientativamente la lluvia hasta 60 minutos sobre el punto de cada finca;
- aplica consentimiento, antelación 15/30/60 min, frescura máxima 20 min y antirrepetición 90 min;
- devuelve `null` si falta cobertura, hay datos obsoletos, el movimiento es confuso o el raster no sirve.

Desde la raíz del repositorio (Node.js 22.16+):

```bash
node --experimental-strip-types --test spikes/rain-alerts/engine.test.ts
```

**Importante:** la entrada debe contener precipitación georreferenciada y validada en mm/h; **NO** se deben pasar colores PNG ni dBZ sin calibración. Todavía no se ha implementado el conector AEMET GeoTIFF, estimador de movimiento local, worker periódico, RLS/Auth, FCM, permisos Android ni conexión a la interfaz.

La prueba está separada del Android principal para respetar AGENTS.md y los gates. Véase `docs/technical/RAIN-NOWCAST-ALERTS-2026-11-15.md`.
