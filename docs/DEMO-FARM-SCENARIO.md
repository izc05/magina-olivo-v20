# Finca Demo Mágina — escenario DEV/QA (#399)

**Solo DEV.** El código vive en el *flavor* `dev` (`app/src/dev/.../DemoFarmSeeder.kt`); `staging` y
`production` no lo compilan (`DevTools.demoFarm` devuelve `null`). Nunca se crea sola: Perfil →
*Herramientas de desarrollo* → **Cargar Finca Demo** / **Restablecer Finca Demo**.

Todo se escribe con los repositorios canónicos (los mismos que usa la app), así que las cifras de
pantalla salen de los agregados reales. Los datos son ficticios y no son una recomendación agronómica.

## Finca y parcelas
| | Superficie | Olivos |
|---|---|---|
| Los Llanos | 1,25 ha | 120 |
| La Loma | 0,95 ha | 90 |
| El Barranco | 1,00 ha | 110 |
| **Finca Demo Mágina** (Bedmar, Jaén, EUR) | **3,20 ha** | **320** |

Sin referencia catastral ni geometría: no se finge Catastro.

## Campaña activa 2026/27 (inicio 28-09-2026)
| Pesada | Fecha | Kg | Rend. | Parcelas | Vale |
|---|---|---|---|---|---|
| 1 | 28-09-2026 | 1.850 | 20,80 % | Los Llanos | DEMO-001 |
| 2 | 01-10-2026 | 2.120 | 21,60 % | La Loma + Los Llanos (reparto desconocido) | DEMO-002 |
| 3 | 03-10-2026 | 1.730 | 19,90 % | El Barranco | DEMO-003 |
| **Total** | 3 días | **5.700 kg** | **≈ 20,82 %** ponderado | | |

- **Jornales** (65 €/jornada): 28-09 Ana, Miguel, José · 01-10 los cuatro · 03-10 Ana, Miguel, María →
  **10 jornadas, 650 €**. Pagos: Ana 195 € (completo), Miguel 100 € (parcial), José pendiente, María 130 € (completo).
- **Maquinaria** por día: tractor 85 €, vibradora 60 €, remolque 35 € → **540 €**.
- **Otros gastos de recogida**: gasóleo 140 €, aceite/mantenimiento 35 €, transporte 75 € → **250 €**.
- **Coste de recogida 1.440 €** · **coste recogida/kg ≈ 0,253 €/kg** (#486: milésimas, no céntimos).

## Gastos generales fuera de campaña (temporada 2026/27)
Poda 520 € · producto 180 € · abonado 320 € · gasóleo 90 € · riego/energía 75 € → **1.185 €**, sin campaña,
nunca dentro del coste de recogida. El gasóleo general (02-10-2026) cae **dentro de las fechas de la campaña**
2026/27 a propósito: es QA de #417 — pertenecer a la campaña solo por relación explícita, nunca por fecha. **Coste total 2.625 €** · **coste total/kg ≈ 0,461 €/kg**.

## Campaña histórica 2025/26 (cerrada 15-12-2025)
3 pesadas (1.600 + 1.900 + 1.600 = **5.100 kg**; 19,50 / 20,40 / 19,60 %), 3 días, jornales 3 × 3 × 60 € = 540 €,
maquinaria 3 × 170 € = 510 €, transporte 70 €; todo pagado. Separada de 2026/27 en Histórico y gráficas.

## Trabajos
Tratamiento de otoño (20-09-2025), poda, labores de suelo, abonado, tratamiento de primavera, mantenimiento,
observación y cuatro riegos (15-06, 10-07, 22-07, 18-08-2026) en distintas parcelas; sin importe (el dinero
está solo en el libro de gastos). Durante la campaña activa hay además un **tratamiento general** (30-09-2026,
La Loma) y una **reparación de valla** (02-10-2026, El Barranco), sin campaña: deben verse en el Cuaderno
general y no en el ledger, los totales ni el coste/kg de la campaña.

## Límites conocidos
- **Restablecer** cierra la campaña en curso de la demo y **archiva** la finca anterior (queda en «Fincas
  archivadas» como «Finca Demo Mágina (retirada)») antes de crearla de nuevo; no hay borrado físico.
- Las pesadas se fechan hasta el 03-10-2026: la carga falla en un dispositivo con fecha anterior.
- Sin documentos/adjuntos en esta primera versión.
- Datos de un *build* DEV: si algún día se sincroniza, solo con el backend DEV.
