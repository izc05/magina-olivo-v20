# Mágina Olivo — índice vivo de documentación y estado (2026-10-09)

**Tipo:** índice de continuidad y control de evidencias. **Estado:** documentación propuesta para revisión.  
**Propósito:** que propietario, Codex y Claude encuentren la fuente de verdad, el estado demostrable y el siguiente trabajo sin releer conversaciones ni ejecutar tareas duplicadas.

> Este índice no modifica el Product Lock ni declara superado ningún Gate. Las aprobaciones de producción se fundamentan en commits, CI, pruebas y aceptación del propietario; ante discrepancias, consultar el issue/PR más reciente y la norma vigente. No publicar secretos, certificados privados, claves, tokens ni datos personales de agricultores.

## 1. Orden de lectura y jerarquía

1. [AGENTS.md](../../AGENTS.md): disciplina del repositorio, única ejecución productiva, arquitectura y definición de terminado.
2. [RC1.2-PRODUCT-LOCK.md](RC1.2-PRODUCT-LOCK.md): producto y arquitectura Android offline-first; cambios solo mediante Change Request aprobado.
3. [CURRENT-STATE.md](CURRENT-STATE.md): contexto histórico útil, **última revisión 2026-10-03**, por lo que **no usar sus instrucciones antiguas como cola activa**.
4. [#696 — cierre Android P0](https://github.com/izc05/magina-olivo-v20/issues/696), [#711 — cola Android Codex](https://github.com/izc05/magina-olivo-v20/issues/711) y [plan M0–M7](../07-plans/ANDROID-1.0-MACROBLOCK-EXECUTION-2026-10-08.md): ejecución actual y prioridad del propietario.
5. [Matriz de cierre Android](../qa/MAGINA-OLIVO-1.0-CLOSURE-MATRIX.md) y [revisión E2E agrícola](../qa/E2E-AGRICULTURAL-CLOSURE.md): evidencias, incidencias y aceptación; distinguir snapshots históricos de estado vigente.
6. [CUE Andalucía — punto de entrada](../integrations/cue-andalucia/README.md): compatibilidad legal de tratamientos, catálogo MAPA, REAFA, IUWS, condiciones de habilitación y pruebas oficiales.
7. [#534 — plan maestro CUE](https://github.com/izc05/magina-olivo-v20/issues/534) y [matriz CUE](../07-plans/CUE-ANDALUCIA-IMPLEMENTATION-MATRIX.md): desglose funcional y normativo.
8. [#591 — Web V3 pausada](https://github.com/izc05/magina-olivo-v20/issues/591): no desarrollar ni fusionar web sin nueva instrucción expresa.

## 2. Estado verificable al iniciar este índice

| Frente | Evidencia observada | Estado / próximo gate |
| --- | --- | --- |
| Android núcleo agrícola | Finca/Parcela/Campaña, Cuaderno, Pesadas, gastos y pruebas descritos en #696 y matriz de cierre. PR #684/#689 corrigen overflow; #692 nombres de adjuntos; #698 limpieza segura de fixtures | Integrados cambios parciales. **No equivale a APK 1.0 aceptada en teléfono**; Gate21 físico pendiente de evidencia |
| UX agrícola | #709 permisos avisos, #710 encabezado edición Campaña, #712 preservación de campaña al añadir gastos, integradas según PRs consultadas el 09/10 | Repetir E2E de cambios de contexto y comprobar en teléfono |
| Visual Android | #713 contraste de tarjetas detectada **abierta al consultar el 09/10**, #714 auditoría visual y #705/#707 navegación/apariencia como issues | Validar estado actual antes de actuar; no rediseñar sin CR |
| Fitosanitarios locales | #680 Room v25 tratamientos y snapshots; #676 recursos fitosanitarios en interfaz | Base de persistencia integrada; UX/validación legal completa por comprobar |
| CUE oficial Andalucía | #690 IUWS 3.11.4 y matriz; #688 catálogo REGFI: **investigación/documentación, sin despliegue oficial** | Falta alta software #555, catálogos y esquema definitivos, gateway productivo, autorización, sandbox y acuse real |
| Backend, cuenta y nube | Arquitectura y fases descritas en #325/#335/#330 | No acreditar recuperación/sincronización externa sin pruebas específicas; no introducir secretos en Android |
| Web V3 | Ramas y PR conservadas | **Pausada** por #591/#696 |

**Precisión:** esta tabla describe la evidencia examinada al 09/10/2026, no una monitorización continua. La PR y los estados CI pueden cambiar después. Para cualquier decisión de merge, consultar el SHA/CI actual en GitHub.

## 3. Decisiones de producto que no deben perderse

- Mágina Olivo = Android nativo, Kotlin/Compose/Room, datos primero en el móvil y funcionamiento agrícola sin conexión.
- Jerarquía lógica: Finca → Parcela → Campaña y actuaciones → Histórico. No inventar kilos ni costes ni vincular un registro a otra finca/campaña.
- El usuario prefiere **mejoras puntuales** visuales y de navegación, conservando colores crema/verde oliva y la experiencia reconocible. #705 propone campana de avisos arriba, Cuaderno abajo y botón + central; requiere verificar su Change Request/baseline antes de fusionar.
- CR-013: lectura OCR de vales **aplazada**; Pesada manual con foto opcional. No reintroducirla por accidente.
- Web V3 queda congelada hasta cerrar el trabajo Android aprobado.
- La compatibilidad CUE con Junta de Andalucía es **prioridad P0 de arquitectura/negocio**, pero no autoriza saltar Gate21 ni tener dos implementaciones productivas compitiendo.

## 4. Registro mínimo de cada avance

En el issue maestro correspondiente y en la documentación especializada anotar:

1. **Fecha y base exacta:** rama, SHA de main y PR/issue enlazados.
2. **Hechos frente a hipótesis:** IMPLEMENTADO, INTEGRADO, PROBADO (con test/log), VALIDADO EN MÓVIL, VALIDADO CON SISTEMA OFICIAL; nunca tratar sinónimos.
3. **Qué ha cambiado:** alcance de dominio/UX, migración/schema, autorizaciones, seguridad, compatibilidad y documentación afectada.
4. **Pruebas:** comando, dispositivo/Android, entorno, SHA y resultado; adjuntar captura o artefacto solo si existe. Un fake no es IUWS real; un emulador no es dispositivo físico.
5. **Riesgo e incidencias:** severidad, reproducción, mitigación, responsable, enlace y siguiente bloqueo.
6. **Decisión y siguiente acción:** quién ejecuta, quién revisa, dependencia y criterio de aceptación.

Los documentos de investigación deben poner fecha y fuente oficial (URL, versión, sección, fecha de consulta, checksum si corresponde); cualquier contradicción con el código o entre normas queda registrada como **ABIERTA** hasta resolverla.

## 5. Política de conservación y mantenimiento

- Las decisiones del propietario se registran en issue + documento correspondiente. No depender solo de conversaciones de IA.
- Mantener **una sola fuente canónica por contrato**; los índices apuntan al detalle, no duplican el esquema normativo.
- No modificar retrospectivamente un informe de QA histórico para aparentar que siempre estaba al día: añadir encabezado de actualización y conservar el snapshot.
- Cambios documentales: rama docs/ aislada, PR pequeña, enlaces comprobados, ninguna mutación de Android/backend ni fusión automática por una decisión editorial.
- Al concluir cada macrobloque de Codex, actualizar estado y evidencia; cuando cambien norma, API oficial o fechas, revisar el apartado CUE y anotar versión.
- Fuentes externas tienen condiciones de uso y vigencia propias; no confundir compatibilidad de datos con autorización administrativa o homologación de software.

## 6. Próximas actualizaciones sugeridas

- Unificar en un solo registro Gate21 el resultado de la APK probada por el propietario.
- Actualizar el marcador [CURRENT-STATE.md](CURRENT-STATE.md) mediante una PR **separada y coordinada** cuando Codex cierre el macrobloque activo: actualmente arrastra decisiones del 03/10.
- Mantener [README CUE](../integrations/cue-andalucia/README.md) y #534 como referencia para el primer tratamiento oficialmente intercambiable.
