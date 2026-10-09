# CR-014 — navegación y acción global de registro

Estado: alcance aprobado por el propietario; implementación y validación en curso.
Fuente: [#705](https://github.com/izc05/magina-olivo-v20/issues/705), decisión del 8 de octubre,
secuencia confirmada por [#711](https://github.com/izc05/magina-olivo-v20/issues/711) / #696.
No requiere una nueva aprobación del mismo alcance.

La barra es `Inicio | Mi Campo | [+] | Cuaderno | Perfil`: cuatro pestañas seleccionables y
un botón central «Añadir registro», diana >=48dp, verde olivo con signo blanco. El + no
pertenece a RootDestination ni usa ordinal como índice. Cuaderno conserva sus vistas y
acciones existentes. Avisos sale de la barra y se abre mediante campana de Inicio, diana
>=48dp y descripción «Avisos y calendario», sobre una superficie legible que no tapa marca
ni pronóstico. No se inventa contador de notificaciones sin fuente verificable.

La hoja del + reutiliza NotebookQuickAction: Trabajo, Riego, Tratamiento, Pesada, Jornal,
Gasto. Usa los formularios y rutas existentes. Resuelve finca/parcela contextual o la última
finca válida, comprobándola contra las fincas activas del workspace actual. Sin contexto
válido, pide selección o ofrece Crear finca. Cambiar finca limpia la parcela previa; una
parcela solo viaja si es activa y pertenece a la finca/workspace validado.

La campaña consultada históricamente no es destino de escritura. Jornal con campaña en
marcha abre la jornada de hoy existente; fuera de campaña, jornal general. La jornada no se
crea al abrir o cancelar +. Pesada conserva su validación de campaña operativa; un error o
carga pendiente de campañas no se interpreta como ausencia. Gasto conserva los contratos
#411/#522, sin transformar el acceso general en contexto explícito de una campaña histórica.
La hoja se cierra antes de navegar y evita abrir dos escritores por doble pulsación.

Se mantienen `avisos`, `calendar`, `plan-work`, enlaces actuales Calendario, recordatorios y
Back hacia Agenda. Recreación y navegación no duplican registros ni mezclan fincas.

Aceptación técnica: pruebas Compose de cuatro raíces/acción/cancelación/seis formularios,
contexto activo/archivado/ajeno, parcela A→finca B, campaña activa/cerrada/cargando/error,
recreación, recordatorios y Back; capturas 360/390/430dp con letra 1,3, controles alcanzables y
teclado donde aplique. Revisión independiente y foundation/gate3-emulator/gate3-evidence
SUCCESS sobre el mismo SHA final antes de integrar. Gate21 físico permanece separado y
pendiente. Web V3, Room y contratos monetarios quedan fuera de este cambio.
