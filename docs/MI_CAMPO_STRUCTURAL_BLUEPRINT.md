# Mi Campo — plano estructural V20

Estado: **estructura en diseño**. Este documento manda sobre decisiones visuales provisionales.

## 1. Principio de producto

La palabra que entiende el usuario es **Finca**. Una finca existe aunque todavía no tenga Catastro, SIGPAC ni geometría vinculada.

La API existente usa `field` como nombre técnico. Se mantiene temporalmente por compatibilidad, pero en producto, navegación y documentación funcional se usa **Finca**.

Regla: **sencillo por fuera, estructurado por dentro**.

## 2. Jerarquía canónica

```text
Persona / empresa
└── Mi Campo
    ├── Fincas propias / arrendadas / gestionadas
    │   └── Finca
    │       ├── identidad
    │       ├── parcelas / recintos
    │       ├── trabajos
    │       ├── cosechas
    │       ├── costes
    │       ├── documentos
    │       ├── calendario
    │       ├── meteorología / radar / alertas
    │       └── referencias técnicas Catastro / SIGPAC
    └── Trabajos para terceros
        ├── cliente
        ├── finca ajena
        ├── trabajo
        ├── participantes
        ├── maquinaria / materiales
        ├── coste
        └── importe / cobro
```

## 3. Finca

La Finca es la unidad visible principal y debe poder crearse con los mínimos datos:

- nombre tradicional o reconocible;
- pueblo/localidad;
- número aproximado de olivos si se conoce.

Datos ampliables:

- superficie;
- variedad o variedades;
- secano/regadío/mixto;
- propiedad, arrendamiento, gestión o finca de tercero;
- notas y forma de acceso;
- geometría propia;
- relaciones con parcelas/recintos oficiales.

Una Finca **no equivale** obligatoriamente a una parcela catastral ni a un recinto SIGPAC.

## 4. Parcela / recinto

`ParcelaRecord` representa una subdivisión o referencia territorial subordinada a una Finca.

Una Finca puede tener:

- ninguna parcela todavía;
- una sola parcela;
- varias parcelas catastrales;
- varios recintos SIGPAC;
- una geometría propia que no coincida exactamente con ninguna fuente administrativa.

Tipos estructurales:

- `own-boundary`: geometría propia canónica;
- `catastro`: referencia/geometry de Catastro;
- `sigpac`: recinto SIGPAC;
- `manual`: subdivisión interna creada por el usuario.

Catastro y SIGPAC son **fuentes técnicas**, no jerarquía de navegación obligatoria.

## 5. Trabajo como entidad paraguas

`Trabajo` es la unidad operativa que responde a:

- qué se hizo;
- dónde;
- para quién;
- quién participó;
- cuándo;
- cuánto tiempo/jornales;
- maquinaria;
- materiales;
- coste;
- importe a cobrar si procede;
- documentación;
- estado de pago si procede.

Tipos iniciales:

- poda;
- trituración;
- recolección;
- tratamiento;
- abonado;
- riego;
- desbroce;
- laboreo;
- transporte;
- trabajo manual;
- trabajo de maquinaria;
- otro.

Un Trabajo puede afectar a:

- toda la finca (`whole-farm`);
- una selección de parcelas/recintos (`selected-parcels`).

Los registros agronómicos especializados (tratamiento, riego, abonado, etc.) aportan datos propios, pero comparten el contexto común de Trabajo.

## 6. Personas, jornales y recursos

No se modelará `jornal` como sinónimo de trabajador. Un participante puede medirse por:

- horas;
- días;
- jornales;
- unidades;
- importe fijo.

Esto permite representar tanto mano de obra familiar como empleados, autónomos o cuadrillas externas.

Los recursos de un trabajo pueden ser:

- maquinaria;
- materiales;
- servicios externos.

Cada uno puede incorporar cantidad, unidad y coste.

## 7. Propio frente a terceros

Una misma persona puede simultáneamente:

- tener fincas propias;
- gestionar fincas arrendadas;
- llevar fincas de familiares;
- prestar trabajos agrícolas a terceros.

Por eso Trabajo incluye `performedFor` y un `customerId` opcional. La contabilidad doméstica de una finca y el futuro resumen facturable de un profesional pueden compartir la misma base sin mezclarse visualmente.

## 8. Estructura visible prevista de una ficha de Finca

No crear una pestaña por cada tabla interna. La ficha final deberá tender a cinco superficies principales:

```text
Finca
├── Resumen
├── Actividad
├── Cosecha
├── Datos
└── Documentos

+ Registrar
```

Agrupación:

### Resumen
Estado, campaña, pendientes, clima, próximos trabajos y KPIs esenciales.

### Actividad
Línea temporal de riegos, tratamientos, abonos, podas, trabajos, jornales, maquinaria, gastos y observaciones.

### Cosecha
Entregas, reparto entre fincas, tickets/albaranes, kg, resultados posteriores y rendimiento histórico.

### Datos
Identidad de la finca, parcelas/recintos, mapa, geometría, Catastro, SIGPAC, superficie, olivos, variedad y régimen hídrico.

### Documentos
Fotos, albaranes, facturas, fitosanitarios, análisis y cualquier documento relacionado.

## 9. Flujo Registrar

El botón `+ Registrar` es transversal y debe poder abrirse desde Mi Campo, una Finca o el mapa.

Flujo estructural:

```text
+ Registrar
→ qué quieres registrar
→ dónde (finca completa / varias parcelas)
→ cuándo
→ datos específicos
→ personas / maquinaria / materiales si hacen falta
→ coste / cobro si hace falta
→ documento/foto opcional
→ seguimiento/calendario opcional
→ guardar
```

La interfaz debe mostrar solo los pasos relevantes según el tipo de registro.

## 10. Reglas que quedan fijadas

1. Finca es el concepto principal del usuario.
2. Una finca no depende de Catastro ni SIGPAC para existir.
3. Una finca puede agrupar cero, una o muchas parcelas/recintos.
4. La geometría propia de la finca puede ser distinta de una geometría administrativa.
5. Trabajo es entidad paraguas para recursos, personas, costes y trabajos a terceros.
6. Jornal es una unidad de trabajo, no una entidad rígida.
7. Las operaciones especializadas mantienen sus datos agronómicos sin duplicar el contexto común.
8. La ficha visible se mantiene pequeña: Resumen, Actividad, Cosecha, Datos y Documentos.
9. El prototipo visual puede ser provisional mientras este plano no esté cerrado.
10. Ninguna nueva función obtiene una pestaña propia automáticamente.

## 11. Siguientes bloques estructurales

Orden acordado:

1. Finca ✅ base definida
2. Parcela/recinto ✅ base definida
3. Trabajo ✅ base definida
4. Personas / jornales / maquinaria — siguiente
5. Cosecha y entrega/rendimiento
6. Costes e ingresos
7. Documentos
8. Catastro/SIGPAC y geometría
9. Tiempo/radar/alertas por finca
10. navegación global Mi Campo
11. parte pública
12. Admin
13. Mi Olivo

No iniciar pulido visual general hasta cerrar este plano funcional.