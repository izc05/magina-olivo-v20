# Mi Olivo — arquitectura V20

Estado: **estructura definida; experiencia visual pendiente**.

## 1. Papel dentro de Mágina

Mi Olivo es una capa de fidelización y acompañamiento. Nunca bloquea ni condiciona funciones esenciales de Mi Campo, alertas, seguridad, privacidad o datos oficiales.

Objetivos:
- aumentar recurrencia;
- enseñar funciones de Mágina;
- reconocer hábitos útiles;
- conectar territorio, contenido y campo;
- permitir futuras recompensas locales sin convertir la app en un juego invasivo.

## 2. Unidad visible

Cada usuario puede tener un olivo digital con:
- nivel;
- progreso;
- saldo de aceitunas/puntos;
- racha opcional;
- logros;
- misiones;
- recompensas desbloqueadas.

La apariencia del árbol puede evolucionar por nivel, pero la lógica de datos se mantiene independiente de la representación visual.

## 3. Eventos que pueden generar progreso

Solo acciones reales y verificables de la plataforma:
- completar perfil;
- añadir primera finca;
- registrar una actividad;
- añadir un documento;
- consultar una alerta relevante;
- leer contenido público;
- visitar fichas territoriales;
- completar misiones educativas;
- mantener información de finca al día.

No premiar repetición artificial ilimitada de la misma acción.

## 4. Motor de eventos

```text
Evento real de Mágina
→ regla de gamificación
→ validación / antispam
→ recompensa
→ ledger de puntos
→ progreso de misión/logro
→ proyección Mi Olivo
```

Los módulos de negocio no escriben directamente el saldo del usuario.

## 5. Ledger

El saldo no debe ser un contador mutable sin historia.

Cada movimiento conserva:
- id;
- usuario;
- fecha;
- evento origen;
- puntos/aceitunas;
- motivo;
- regla/version;
- idempotency key;
- reversión opcional.

Saldo = suma de movimientos válidos.

## 6. Misiones

Tipos:
- onboarding;
- diarias ligeras;
- semanales;
- estacionales/campaña;
- territorio;
- educativas;
- comunidad futura.

Cada misión define:
- condición;
- periodo;
- recompensa;
- límite;
- elegibilidad;
- estado;
- versión.

## 7. Logros

Reconocen hitos permanentes, por ejemplo:
- primera finca;
- primera cosecha registrada;
- primera campaña cerrada;
- diez actividades;
- documentación organizada;
- varios pueblos explorados.

No crear logros que incentiven registrar datos falsos o realizar tratamientos/riegos innecesarios.

## 8. Racha

La racha es secundaria.

No penalizar de forma agresiva por no abrir la app. Puede basarse en interacción útil y tener mecanismos de gracia. Nunca usar mensajes de culpa.

## 9. Recompensas

Futuro catálogo:
- insignias digitales;
- personalización visual;
- recompensas promocionales ofrecidas por negocios;
- sorteos/campañas legales si se implementan en el futuro;
- ventajas locales claramente descritas.

Una recompensa comercial debe indicar patrocinador y condiciones.

## 10. Relación con el tiempo real

El árbol puede reflejar visualmente el contexto meteorológico actual del municipio/finca seleccionada:
- sol;
- nubes;
- lluvia;
- viento;
- estaciones.

Esto es presentación. No debe alterar puntos ni generar recompensas por fenómenos meteorológicos.

## 11. Relación con Mi Campo

Mi Olivo puede reconocer que el usuario utiliza buenas funciones de organización, pero no debe evaluar agronómicamente si una decisión concreta fue «correcta» salvo que exista una regla validada.

Ejemplo correcto:
- «Has registrado tu tratamiento» → progreso.

Ejemplo a evitar:
- «Has realizado el tratamiento perfecto» sin evidencia técnica.

## 12. Privacidad

La gamificación recibe eventos mínimos, no copias completas de datos privados.

Por ejemplo:
- `activity_registered` + identificadores técnicos necesarios,
no el contenido íntegro de notas, documentos o datos personales.

## 13. Admin

Admin puede gestionar:
- catálogo de misiones;
- reglas/recompensas;
- periodos;
- patrocinadores;
- límites;
- activación/desactivación;
- incidencias/reversiones;
- métricas agregadas.

No editar manualmente saldos sin evento de auditoría.

## 14. Superficies visibles

```text
Inicio
└── tarjeta resumen Mi Olivo

/mi-olivo
├── Árbol / nivel
├── Misiones
├── Logros
└── Recompensas
```

No necesita invadir la navegación de Mi Campo.

## 15. Reglas cerradas

1. Mi Olivo es opcional y no bloquea funciones reales.
2. El saldo se basa en ledger auditable.
3. Las recompensas parten de eventos reales/idempotentes.
4. Hay límites antispam.
5. No se incentivan prácticas agrícolas innecesarias.
6. Meteorología del árbol es presentación, no mecanismo de recompensa.
7. Datos privados enviados al motor se minimizan.
8. Recompensas comerciales se identifican claramente.
9. Rachas no usan patrones de presión o culpa.
10. La lógica de gamificación es independiente del diseño gráfico del árbol.
