# V20 — trabajo en paralelo y ramas

Objetivo: permitir que varios frentes avancen a la vez sin que un cambio de mapa, UX, backend o limpieza estructural bloquee o contamine a los demás.

## Regla base

Cada rama debe tener **un propósito principal revisable**. Si un cambio no pertenece a ese propósito, va a otra rama.

## Tipos de rama

- `feat/*` — funcionalidad de producto.
- `fix/*` — corrección aislada.
- `chore/*` — limpieza, estructura, tooling o mantenimiento.
- `docs/*` — documentación sin cambio funcional.
- `agent/*` — workstream temporal y acotado para trabajo paralelo.

## Frentes que deben permanecer separados

En V20 conviene separar, como mínimo:

1. GIS / mapa / Catastro / SIGPAC.
2. Mi Campo y ficha de finca.
3. Registro / campaña / cosecha / rendimiento.
4. Profesional / clientes / presupuestos / facturas.
5. Documentos / OCR.
6. Tiempo / radar / alertas.
7. UX móvil / accesibilidad / visual.
8. Limpieza estructural / deuda / documentación / tooling.

Una rama puede tocar contratos compartidos si son necesarios para su objetivo, pero debe evitar refactors laterales que generen conflictos artificiales.

## Flujo recomendado

1. Partir del HEAD candidato que se quiera limpiar o ampliar.
2. Crear una rama con nombre explícito.
3. Mantener commits pequeños y con intención clara.
4. Ejecutar `pnpm check` antes de considerar el workstream listo.
5. Ejecutar los checks específicos afectados por el cambio.
6. Abrir PR preferentemente como draft mientras haya trabajo pendiente.
7. No fusionar un workstream solo porque compile: revisar impacto funcional y conflictos con ramas paralelas.

## Criterio de cierre de una rama de limpieza

Una rama `chore/*cleanup*` se considera terminada cuando:

- no cambia funcionalidad de usuario sin documentarlo;
- elimina o corrige documentación obsoleta detectada en su alcance;
- deja comandos reproducibles para validar el repositorio;
- mejora la higiene de artefactos y entorno local;
- registra qué deuda queda fuera de alcance;
- los checks correspondientes están verdes o el bloqueo externo está documentado.

## Conflictos

Si dos ramas necesitan modificar el mismo archivo estructural:

- gana la rama cuyo objetivo principal sea ese archivo/área;
- la otra rama debe reducir su cambio al mínimo;
- no se hace merge cruzado entre ramas de trabajo salvo necesidad clara;
- la reconciliación se hace al integrar sobre el candidato, no copiando cambios a ciegas.

## `main`

`main` representa la línea estable. Los workstreams V20 no deben usar `main` como zona de trabajo ni recibir commits directos durante el cierre del candidate.
