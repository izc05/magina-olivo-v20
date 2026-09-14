# Mágina Aventura V3 — telemetría en vivo

Esta fase reutiliza exclusivamente el `watchPosition` de `RouteActivityRecorder`. La pantalla `/aventura/en-curso` escucha eventos internos de telemetría y no inicia un segundo seguimiento GPS.

## Validación física pendiente

Probar en Android e iOS, en una ruta real y con consentimiento explícito de ubicación:

- precisión GPS en zona abierta, olivar, barranco y masa arbolada;
- transición `searching → tracking → error/idle`;
- distancia mostrada hasta el siguiente checkpoint frente a una referencia real;
- entrada/salida del radio de desbloqueo configurado;
- pausa y reanudación sin mantener una posición obsoleta;
- pérdida y recuperación de cobertura móvil;
- funcionamiento del GPS cuando hay datos móviles limitados;
- impacto de pantalla apagada/segundo plano según navegador y sistema operativo;
- consumo de batería y temperatura tras 30, 60 y 120 minutos;
- legibilidad de las tarjetas con sol directo y uso con una mano;
- comprobar siempre que el checkpoint no invite a abandonar el sendero seguro ni sustituya señalización, cierres o avisos oficiales.

## Criterio de aceptación

No activar una aventura concreta para uso público hasta validar sobre terreno sus coordenadas, radio de desbloqueo, acceso y seguridad. La telemetría es apoyo lúdico; el track validado y la información oficial prevalecen.
