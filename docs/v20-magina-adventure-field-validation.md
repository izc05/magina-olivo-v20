# Mágina Aventura — protocolo de validación física en campo

Estado: pendiente de pruebas físicas antes de considerar validado el comportamiento GPS real en Sierra Mágina.

Este documento separa el cierre software (tests, TypeScript, build, E2E y CI) de la validación que solo puede hacerse caminando con móviles reales. Mágina Aventura es una capa lúdica sobre rutas publicadas con track validado; nunca sustituye señalización, restricciones, avisos oficiales, cartografía de seguridad ni criterio del usuario.

## 1. Matriz mínima de dispositivos

Probar, como mínimo, un Android reciente con Chrome y un iPhone reciente con Safari. Repetir al menos una ruta con ahorro de batería activado/desactivado y con pantalla encendida/bloqueada. Anotar modelo, versión de SO, navegador y permisos concedidos.

## 2. Checkpoints y geofencing

Para varios checkpoints situados en entorno abierto, olivar, bosque y zona encajonada:

- comprobar que no se desbloquean claramente fuera del radio configurado;
- comprobar desbloqueo al entrar realmente en el radio;
- registrar precisión horizontal reportada y distancia calculada;
- repetir aproximándose desde distintas direcciones;
- comprobar deriva GPS en parada de 5–10 minutos;
- comprobar que un reto lineal mantiene bloqueados los obligatorios posteriores;
- comprobar que un reto ya desbloqueado no duplica XP al reintentarlo;
- comprobar que los marcadores del mapa cambian de pendiente a completado y que el foco del cuaderno abre el checkpoint correcto.

Criterio: cualquier falso positivo sistemático obliga a revisar el radio de desbloqueo o a incorporar una política de precisión mínima antes de publicar esa aventura.

## 3. Grabación de actividad

Realizar una actividad de al menos 60–90 minutos y otra larga de varias horas:

- iniciar, pausar, reanudar y completar;
- bloquear/desbloquear pantalla;
- cambiar temporalmente a otra app;
- provocar pérdida de cobertura de datos sin desactivar GPS;
- comprobar reconexión y envío de puntos pendientes;
- cerrar/reabrir el navegador para medir qué estado puede recuperarse;
- verificar distancia, duración, desnivel y número de puntos contra una referencia GPS conocida;
- exportar GPX y revisarlo en un visor externo;
- borrar la actividad y confirmar que desaparecen también sus puntos privados.

Nota: un navegador móvil puede limitar geolocalización en segundo plano o con la pantalla bloqueada. La web no debe prometer grabación continua en esas condiciones hasta validarlo físicamente por dispositivo/SO.

## 4. Batería, temperatura y consumo

En una salida de varias horas registrar batería inicial/final, calentamiento del dispositivo y consumo aproximado con GPS de alta precisión. Repetir con pantalla apagada cuando el SO lo permita. Si el consumo es excesivo, revisar frecuencia de puntos, precisión y estrategia de envío por lotes.

## 5. Cobertura y degradación segura

Probar con cobertura buena, débil y sin datos:

- la pérdida de mapa base no debe ocultar los datos de seguridad y ficha de la ruta ya cargados;
- no debe inventarse un checkpoint como desbloqueado por pérdida de red;
- los errores deben ser comprensibles y permitir reintento;
- verificar qué información queda disponible tras recargar sin red; el modo offline completo no se considerará disponible hasta que exista y se pruebe una estrategia explícita de cacheado.

## 6. Seguridad y avisos

En cada ruta piloto revisar en móvil:

- acceso, dificultad, distancia, desnivel, agua y avisos editoriales/oficiales;
- avisos comunitarios claramente diferenciados y nunca presentados como oficiales;
- textos de Aventura indicando que el juego no sustituye navegación ni seguridad;
- legibilidad a pleno sol y uso con una mano;
- ausencia de incentivos para abandonar el sendero seguro con el fin de alcanzar un checkpoint.

No publicar un checkpoint si su posición empuja al usuario a una zona peligrosa, privada, ambientalmente sensible o fuera de un acceso permitido.

## 7. Ruta piloto recomendada

Antes de escalar el catálogo, validar una sola aventura completa con 5–8 checkpoints variados: un lugar/patrimonio, una observación, un elemento de olivar/tradición, un paisaje, un reto y al menos un checkpoint opcional. Tras esa prueba ajustar radios, textos, densidad de checkpoints y ritmo de XP y repetir la ruta antes de duplicar el patrón.

## 8. Evidencia de cierre de campo

Para cada sesión guardar fecha, ruta, dispositivo/SO/navegador, duración, km de referencia, km registrados, batería consumida, checkpoints intentados/desbloqueados, incidencias y capturas. Una aventura queda `field_validated` solo cuando no hay fallos críticos de geofencing, seguridad o pérdida de actividad y los desvíos de distancia están dentro del margen que se acuerde para producción.
