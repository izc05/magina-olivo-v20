# Finca Demo Mágina — escenario DEV/QA (#399, ampliado en #696)

**Solo DEV.** El código vive en el *flavor* `dev` (`app/src/dev/.../DemoFarmSeeder.kt`); `staging` y
`production` no lo compilan (`DevTools.demoFarm` devuelve `null`). Nunca se crea sola: Perfil →
*Herramientas de desarrollo* → **Cargar Finca Demo** / **Restablecer Finca Demo** / **Retirar datos de
demostración**.

Todo se escribe con los repositorios canónicos (los mismos que usa la app), así que las cifras de
pantalla salen de los agregados reales. Los datos son ficticios y no son una recomendación agronómica.

## Finca y parcelas
| | Municipio | Variedad | Riego | Superficie | Olivos | Referencia |
|---|---|---|---|---|---|---|
| Los Llanos | Bedmar | Picual | Goteo · Sector 1 (mar/vie) | 1,25 ha | 120 | DEMO-LLANOS-001 |
| La Loma | Bedmar | Picual | Secano | 0,95 ha | 90 | DEMO-LOMA-002 |
| El Barranco | Garcíez | Hojiblanca | Goteo · Sector 2 (mié) | 1,00 ha | 110 | DEMO-BARRANCO-003 |
| **Finca Demo Mágina** (Bedmar, Jaén, EUR) | | | | **3,20 ha** | **320** | |

Las parcelas son **MANUAL**: la referencia es inequívocamente ficticia (`DEMO-…`), no tiene forma de
referencia catastral, no hay geometría y no se afirma cumplimiento CUE ni conexión con Catastro.

## Campaña activa 2026/27 (inicio 28-09-2026)
| Pesada | Fecha | Kg | Rend. | Origen | Parcelas | Vale |
|---|---|---|---|---|---|---|
| 1 | 28-09-2026 | 1.850 | 20,80 % | Árbol | Los Llanos | DEMO-001 |
| 2 | 01-10-2026 | 2.120 | 21,60 % | Árbol | La Loma + Los Llanos (reparto desconocido) | DEMO-002 |
| 3 | 01-10-2026 | 980 | 19,10 % | **Suelo** | El Barranco | DEMO-004 |
| 4 | 03-10-2026 | 1.730 | 19,90 % | Árbol | El Barranco | DEMO-003 |
| **Total** | **3 días** | **6.680 kg** | **≈ 20,57 %** ponderado | | | |

Las dos pesadas del 01-10 comparten la **misma Jornada automática**: los kg del día son su suma exacta
(#696, CR-010), con vale y origen propios cada una.

- **Jornales** — jornada completa 65 €, media jornada y horas en la misma campaña:
  | Día | Líneas | Importe |
  |---|---|---|
  | 28-09 | Ana, Miguel, José (jornada) + María 5 h a 10 €/h | 245,00 € |
  | 01-10 | los cuatro (jornada) | 260,00 € |
  | 03-10 | Ana, Miguel, María (jornada) + José **media jornada** | 227,50 € |
  | **Total** | **10 jornadas · 1 media · 5 h** | **732,50 €** |
  Cada día tiene un gasto de jornales **publicado** igual al subtotal exacto de sus líneas.
- **Pagos y saldos**: Ana 195,00 € (completo) · Miguel 100,00 € de 195,00 € (**parcial**) ·
  María 130,00 € + 50,00 € = 180,00 € (completo, en dos pagos) · José **162,50 € pendientes**.
- **Maquinaria** por día: tractor 85 €, vibradora 60 €, remolque 35 € → **540 €**.
- **Otros gastos de recogida**: gasóleo 140 €, aceite/mantenimiento 35 €, transporte 75 € → **250 €**.
- **Coste de recogida 1.522,50 €** (732,50 + 540 + 250) · **coste recogida/kg = 0,228 €/kg**
  (#486: milésimas, no céntimos).

## Gastos generales fuera de campaña (temporada 2026/27)
Poda 520 € · producto 180 € · abonado 320 € · gasóleo 90 € · riego/energía 75 € → **1.185 €**, sin campaña,
nunca dentro del coste de recogida. El gasóleo general (02-10-2026) cae **dentro de las fechas de la campaña**
2026/27 a propósito: es QA de #417 — pertenecer a la campaña solo por relación explícita, nunca por fecha.
**Coste total 2.707,50 €** · **coste total/kg = 0,405 €/kg**.

## Campaña histórica 2025/26 (cerrada 15-12-2025)
3 pesadas (1.600 + 1.900 + 1.600 = **5.100 kg**; 19,50 / 20,40 / 19,60 %), 3 días, jornales 3 × 3 × 60 € = 540 €,
maquinaria 3 × 170 € = 510 €, transporte 70 €; todo pagado. Separada de 2026/27 en Histórico y gráficas.

## Trabajos
Tratamiento de otoño (20-09-2025), poda, labores de suelo, abonado, tratamiento de primavera, mantenimiento,
observación y cuatro riegos (15-06, 10-07, 22-07, 18-08-2026) en distintas parcelas; sin importe (el dinero
está solo en el libro de gastos). Durante la campaña activa hay además un **tratamiento general** (30-09-2026,
La Loma) y una **reparación de valla** (02-10-2026, El Barranco), sin campaña: deben verse en el Cuaderno
general y no en el ledger, los totales ni el coste/kg de la campaña.

## Acciones y seguridad
- **Cargar Finca Demo** es idempotente: cargar dos veces no duplica nada (se reconoce por el nombre
  `Finca Demo Mágina`).
- **Restablecer** retira la demo actual y la crea de nuevo.
- **Retirar datos de demostración** (#696) retira la demo y **no crea nada**. Solo toca fincas llamadas
  `Finca Demo Mágina`; las fincas reales, sus parcelas, pesadas, costes y fotos quedan intactas
  (verificado en `DemoFarmSeederTest.removeRetiresOnlyTheDemoAndKeepsRealData`).

## Límites conocidos (GAP, no inventados)
- **Retirar/Restablecer cierran la campaña en curso y archivan la finca** («Finca Demo Mágina (retirada)»
  en *Fincas archivadas*): **no hay borrado físico**, porque ningún repositorio ofrece borrado en cascada
  de una finca y sus agregados. Implementarlo es un *slice* propio, con su migración y sus pruebas.
- Las pesadas se fechan hasta el 03-10-2026: la carga falla en un dispositivo con fecha anterior.
- **Sin documentos ni fotos de demostración**: los adjuntos necesitan ficheros reales en el dispositivo;
  no se simulan. Queda como GAP de este escenario.
- Sin geometría ni referencia catastral real: el mapa de las parcelas demo queda sin recinto.
- Datos de un *build* DEV: si algún día se sincroniza, solo con el backend DEV.
