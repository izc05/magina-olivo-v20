# Mágina Olivo V20 — arquitectura maestra de producto

Estado: **arquitectura funcional base cerrada; implementación progresiva pendiente**.

Principio rector: **sencillo por fuera, estructurado por dentro**.

## 1. Mapa global

```text
MÁGINA OLIVO
│
├── PÚBLICO
│   ├── Inicio
│   ├── Explorar
│   ├── Tiempo / radar / avisos
│   ├── Aceite y mercado
│   ├── Pueblos
│   ├── Noticias
│   ├── Eventos
│   ├── Almazaras / cooperativas
│   └── Cerca de ti
│
├── PRIVADO · MI CAMPO
│   ├── Fincas
│   │   ├── parcelas / recintos
│   │   ├── actividad
│   │   ├── cosecha
│   │   ├── economía
│   │   ├── documentos
│   │   ├── geometría / Catastro / SIGPAC
│   │   └── clima / radar / alertas
│   ├── Registrar
│   ├── Mapa
│   ├── Personas / cuadrillas
│   ├── Maquinaria / materiales
│   └── Trabajos para terceros
│
├── PERFIL / CUENTA
│   ├── identidad
│   ├── municipio preferido
│   ├── preferencias
│   ├── notificaciones
│   └── memberships/workspaces
│
├── MI OLIVO
│   ├── árbol / nivel
│   ├── misiones
│   ├── logros
│   └── recompensas
│
└── ADMIN
    ├── contenido
    ├── territorio
    ├── almazaras
    ├── negocios
    ├── publicidad
    ├── usuarios
    ├── moderación
    ├── fuentes/datos
    └── configuración
```

## 2. Mi Campo

Documento rector: `MI_CAMPO_STRUCTURAL_BLUEPRINT.md`.

Decisiones cerradas:
- Finca es la entidad visible principal.
- Una finca no equivale obligatoriamente a parcela catastral/SIGPAC.
- Parcela/recinto está subordinada a Finca.
- Trabajo es entidad paraguas.
- Personas, cuadrillas, maquinaria y materiales son reutilizables.
- Jornales son una unidad de trabajo, no una entidad aislada obligatoria.
- Trabajos para terceros comparten motor con trabajos propios.

## 3. Navegación de Mi Campo

Documento: `MI_CAMPO_NAVIGATION_BLUEPRINT.md`.

Superficies primarias:

```text
Mi Campo
├── Resumen
├── Mis fincas
├── Mapa
└── Registrar
```

Ficha de Finca:

```text
Finca
├── Resumen
├── Actividad
├── Cosecha
├── Datos
└── Documentos
```

## 4. Cosecha

Modelo cerrado:
- Campaña.
- Entrega.
- Reparto por una o varias fincas.
- Ticket/albarán como documento relacionado.
- Resultado posterior independiente.
- Rendimiento ponderado por kg.
- Correcciones auditables.

## 5. Economía

Documentos:
- `ECONOMY_DOMAIN.md`
- `ECONOMY_CALCULATION_RULES.md`

Principio:

```text
COSTE ≠ GASTO ≠ PAGO ≠ INGRESO ≠ COBRO
```

Permite:
- coste por trabajo;
- coste por finca;
- coste por campaña;
- €/kg aceituna;
- estimaciones equivalentes de aceite;
- ingresos/margen;
- pendientes de pago/cobro;
- reparto de costes compartidos.

## 6. Documentos

Documento: `DOCUMENTS_DOMAIN.md`.

Separación:

```text
Documento lógico
→ versión
→ archivo
→ OCR
→ extracción propuesta
→ revisión/corrección
→ vínculo de dominio
```

OCR ayuda a capturar datos; no es la fuente definitiva sin validación.

## 7. GIS / Catastro / SIGPAC

Documento: `GIS_DOMAIN.md`.

La geometría canónica pertenece a Mágina/Finca. Catastro y SIGPAC son referencias externas y pueden coexistir múltiples vínculos.

```text
Finca
├── geometría propia
├── Catastro
├── SIGPAC
└── subdivisiones manuales
```

## 8. Tiempo / radar / agronomía

Documento: `WEATHER_RADAR_AGRONOMIC_BLUEPRINT.md`.

Capas separadas:

```text
Previsión
Observación
Radar
Aviso oficial
       ↓
Evidencia normalizada
       ↓
Contexto agronómico
       ↓
Recomendación explicable
       ↓
Regla personal
       ↓
NotificationIntent
```

Sin prometer ETA radar ni convertir reflectividad a lluvia acumulada sin metodología validada.

## 9. Parte pública

Documento: `PUBLIC_PRODUCT_BLUEPRINT.md`.

Debe funcionar sin cuenta y aportar valor propio mediante territorio, clima, noticias, mercado, eventos, almazaras, cooperativas y directorio local.

Separación estricta entre datos públicos y datos privados de Mi Campo.

## 10. Admin

Documento: `ADMIN_BLUEPRINT.md`.

Admin es gestión de plataforma, no una versión privilegiada de Mi Campo.

Roles de plataforma separados de roles agrícolas y permisos por capacidades.

## 11. Mi Olivo

Documento: `MI_OLIVO_BLUEPRINT.md`.

Gamificación opcional basada en ledger, eventos idempotentes y recompensas auditables. Nunca bloquea funciones agrícolas ni incentiva prácticas innecesarias.

## 12. Arquitectura técnica

Base actual:
- Next.js / React / TypeScript;
- Fastify;
- PostgreSQL + PostGIS;
- Kysely;
- contratos Zod;
- worker pg-boss;
- almacenamiento documental por puerto S3;
- adaptadores externos AEMET, radar, Catastro/SIGPAC;
- autenticación y workspaces privados.

Regla técnica: proveedores externos se aíslan mediante adaptadores; la UI consume modelos normalizados del dominio.

## 13. Límites público/privado

```text
Público
├── territorio
├── contenido
├── meteorología general
├── precios
├── directorio
└── datos oficiales reutilizables

Privado
├── fincas
├── geometrías propias
├── trabajos
├── cosecha
├── economía
├── documentos
├── personas/clientes
├── preferencias específicas
└── alertas por finca
```

## 14. Estrategia de implementación desde aquí

La siguiente fase ya no consiste en inventar nuevos módulos. Consiste en **materializar el plano** por verticales completas.

Orden recomendado:

1. Data gateway frontend: API real + fallback de preview.
2. Mi Campo real: listado/resumen de fincas.
3. Ficha de Finca con las cinco superficies definitivas.
4. Registrar unificado y contextual.
5. Personas/recursos dentro de Trabajo.
6. Cosecha completa.
7. Economía y proyecciones.
8. Documentos/OCR integrados en contexto.
9. Datos/Mapa/Catastro/SIGPAC.
10. Tiempo/radar/alertas dentro de Finca e Inicio.
11. Parte pública conectada a datos reales.
12. Admin.
13. Mi Olivo.
14. Revisión visual completa móvil/desktop.
15. Accesibilidad, rendimiento, seguridad y pruebas E2E.

## 15. Criterio de finalización estructural

No se considera una función terminada solo porque exista una pantalla. Debe tener:
- dominio claro;
- contrato de datos;
- persistencia/API;
- permisos;
- estados vacío/error/offline cuando proceda;
- navegación contextual;
- trazabilidad de fuentes si es externa;
- pruebas críticas;
- comportamiento móvil.

## 16. Regla contra crecimiento desordenado

Antes de añadir cualquier nuevo módulo se debe demostrar que no cabe correctamente dentro de las superficies ya existentes.

Mágina Olivo debe crecer **en profundidad de datos y utilidad**, no en número de botones.
