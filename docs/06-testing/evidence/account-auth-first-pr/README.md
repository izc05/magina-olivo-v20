# Evidencia local — primera Cuenta Android (14/10/2026)

Base main `193923a851629dcfb5b1dc54dfc9969fbc19c63d`; CI de integración #751, run `38599039231`, SUCCESS para ese SHA. Verificado de nuevo al entregar este bloque.

## Ejecución

- JVM completa: 663 tests, sin fallos; respuestas Auth sintéticas, incluidas validación, identidad, expiración, logout offline, protocolo y PKCE.
- Lint DEV y compilación app/test: correctos. La ruta SDK local ignorada se corrigió; overlays DEV/STAGING declaran explícitamente exported para el callback.
- Instrumentadas API35 en `emulator-5614`: 8/8; 3 custodia Keystore real, 3 pantalla/conservación de datos sintéticos, 2 MainActivity/navegación y callback durante recreación.
- Matriz 360 y 390 dp, fuente 1.3: 5/5 por tamaño (3 Cuenta y 2 Perfil claro/oscuro). Cuenta cubre sesión offline en oscuro y registro/recuperación en claro. Total 18 ejecuciones instrumentadas; son 10 métodos distintos, con repeticiones de matriz.
- Ajustes restaurados: densidad física 420, fuente 1.0, modo nocturno desactivado. No limpieza de datos reales ni de dispositivos ajenos.

`account-callback-red2.log` reproduce el fallo antes de corregirlo: al recrear Activity, el intercambio iniciado en lifecycleScope se cancelaba después de consumir el código/verifier. `account-final-native.log` verifica la corrección: el repositorio de proceso continúa el intercambio y la pantalla muestra RECOVERY; el Intent no conserva el código. La copia del Intent actual preserva también los extras del launcher.

Las imágenes contienen exclusivamente datos ficticios. Son capturas de una pantalla desplazable: un texto fuera del viewport puede alcanzarse mediante scroll. La matriz prueba navegación y controles; no equivale a una prueba con un usuario real o Supabase remoto.

## Límite de estas pruebas

Auth remoto se sustituye por proveedor simulado; no se han registrado usuarios ni enviado correos. Keystore, cifrado/atomicidad, Activity, Compose, Room y comparación de bytes de una fotografía sintética sí se ejecutan en Android real del emulador. Ninguna finca se vincula a una identidad Auth. No se aplican políticas, migraciones ni servicios de #743.

La prueba real contra `magina-olivo-dev` sigue pendiente: proyecto independiente Free/0 €, creación previamente autorizada, URL y clave pública, buzón sintético autorizado. La PR permanece DRAFT. La evidencia de GitHub debe leerse por SHA en la PR; estos logs locales se generaron antes del commit final para evitar alterar el SHA al guardar resultados posteriores.

Configuración y gate remoto: [ACCOUNT-AUTH-DEV-SETUP.md](../../../07-plans/ACCOUNT-AUTH-DEV-SETUP.md).
La repetición final fija conectividad sintética en el caso de registro, exige la llamada al proveedor y espera renderizado antes de capturar; la prueba de offline conserva su transición true→false. Esto evita depender de la conectividad externa del emulador para validar respuestas simuladas.
