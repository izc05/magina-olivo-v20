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

`ParcelRecord` representa una subdivisión o referencia territorial subordinada a una Finca.

Una Finca puede tener:

- ninguna parcela todavía;
- una sola parcela;
- varias parcelas catastrales;
- varios recintos SIGPAC;
- una geometría propia que no coincida exactamente con ninguna fuente administrativa.

Tipos estructurales:

- `own-boundary`: geometría propia canónica;
- `catastro`: referencia/geometría de Catastro;
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

## 6. Personas, empresas, cuadrillas y jornales

Se usa una entidad genérica `PartyRecord` para representar tanto personas como organizaciones. Sus roles son contextuales y no se mezclan con los permisos de acceso a la aplicación.

Una parte puede actuar como:

- propietario;
- familiar;
- trabajador;
- autónomo/contratista;
- cliente;
- proveedor;
- cooperativa;
- almazara;
- otro.

Una misma persona puede tener varios roles simultáneos. Ejemplo: propietario de sus propias fincas y trabajador para terceros.

`Jornal` no es una entidad rígida ni sinónimo de trabajador. Cada participante de un trabajo puede valorarse por:

- horas;
- días;
- jornales;
- unidades;
- importe fijo.

Cada participación puede guardar cantidad, tarifa, coste calculado, importe pagado y estado de pago.

### Cuadrillas

`CrewRecord` permite guardar grupos reutilizables de trabajadores.

Una cuadrilla puede tener:

- nombre;
- miembros;
- responsable;
- tarifa orientativa;
- unidad de tarifa;
- notas.

El trabajo conserva los participantes reales de ese día aunque posteriormente cambie la composición de la cuadrilla.

## 7. Maquinaria, materiales y servicios

La maquinaria se modela como catálogo reutilizable (`MachineryRecord`) y puede ser:

- propia;
- alquilada;
- servicio de tercero.

Puede guardar categoría, propietario/proveedor, matrícula o número de serie, tarifa habitual y unidad de cobro.

Los materiales (`MaterialRecord`) pueden guardar nombre, categoría, unidad habitual, coste unitario habitual y proveedor.

Dentro de cada Trabajo se guarda una instantánea del recurso realmente usado: cantidad, unidad y coste. Esto evita que un cambio posterior en la tarifa del catálogo altere el histórico.

## 8. Propio frente a terceros

Una misma persona puede simultáneamente:

- tener fincas propias;
- gestionar fincas arrendadas;
- llevar fincas de familiares;
- prestar trabajos agrícolas a terceros.

Por eso Trabajo incluye `performedFor` y, cuando procede, un contexto comercial separado con:

- cliente;
- presupuesto/importe previsto;
- importe final a cobrar;
- cantidad cobrada;
- estado pendiente/parcial/pagado;
- futura referencia de factura.

Esto permite compartir la misma base de Trabajo sin convertir Mi Campo en un programa de contabilidad.

## 9. Cosecha: estructura canónica

La cosecha no se modela como un único número de kilos.

```text
CAMPAÑA
└── Entrega
    ├── fecha/hora
    ├── cooperativa / almazara
    ├── ticket / albarán
    ├── kg totales
    ├── una o varias fincas de origen
    └── reparto de kg
        ├── Finca A
        └── Finca B

        más tarde
            ↓
Resultado de entrega
├── fecha del resultado
├── rendimiento %
├── humedad % opcional
├── acidez % opcional
└── otros parámetros futuros
```

Reglas:

1. `Entrega` y `Resultado` son registros distintos.
2. El rendimiento puede llegar días después.
3. Una entrega puede mezclar aceituna de varias fincas.
4. El total repartido debe poder ser exacto o provisional.
5. Las correcciones posteriores no deben destruir el dato original; deben quedar auditables.
6. El rendimiento de una finca/campaña se calcula ponderado por kg, nunca como media simple de porcentajes.
7. Una foto/OCR de un ticket es una fuente documental, no sustituye al registro estructurado.
8. La campaña debe poder existir aunque aún no tenga entregas.

## 10. Estructura visible prevista de una ficha de Finca

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

## 11. Flujo Registrar

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

## 12. Reglas que quedan fijadas

1. Finca es el concepto principal del usuario.
2. Una finca no depende de Catastro ni SIGPAC para existir.
3. Una finca puede agrupar cero, una o muchas parcelas/recintos.
4. La geometría propia de la finca puede ser distinta de una geometría administrativa.
5. Trabajo es entidad paraguas para recursos, personas, costes y trabajos a terceros.
6. Jornal es una unidad de trabajo, no una entidad rígida.
7. Personas/empresas son entidades reutilizables con roles contextuales múltiples.
8. Cuadrillas son plantillas reutilizables, pero el Trabajo conserva su foto histórica de participantes.
9. Maquinaria/materiales tienen catálogo, pero cada Trabajo conserva cantidades y costes históricos.
10. Las operaciones especializadas mantienen sus datos agronómicos sin duplicar el contexto común.
11. Entrega y resultado de cosecha son registros distintos.
12. El rendimiento agregado se pondera por kg.
13. La ficha visible se mantiene pequeña: Resumen, Actividad, Cosecha, Datos y Documentos.
14. El prototipo visual puede ser provisional mientras este plano no esté cerrado.
15. Ninguna nueva función obtiene una pestaña propia automáticamente.

## 13. Siguientes bloques estructurales

Orden acordado:

1. Finca ✅ base definida
2. Parcela/recinto ✅ base definida
3. Trabajo ✅ base definida
4. Personas / jornales / maquinaria ✅ base definida
5. Cosecha y entrega/rendimiento ✅ estructura definida; siguiente implementación de dominio
6. Costes e ingresos
7. Documentos
8. Catastro/SIGPAC y geometría
9. Tiempo/radar/alertas por finca
10. navegación global Mi Campo
11. parte pública
12. Admin
13. Mi Olivo

No iniciar pulido visual general hasta cerrar este plano funcional.
