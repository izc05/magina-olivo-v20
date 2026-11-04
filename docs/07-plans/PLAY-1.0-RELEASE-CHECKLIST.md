# Publicación 1.0 — parámetros pendientes del titular

La variante `productionRelease` usa el ID `com.isivoltpro.maginaolivo`, API 37 y versión 1.0.0. El build exige parámetros de publicación y falla sin ellos. No se versionan secretos ni se configura Supabase desde este repositorio.

Parámetros privados para `bundleProductionRelease`: `MAGINA_VERSION_CODE` (mayor que el último de Play), `MAGINA_AUTH_PRODUCTION_URL`, `MAGINA_AUTH_PRODUCTION_PUBLIC_KEY`, `MAGINA_UPLOAD_STORE_FILE`, `MAGINA_UPLOAD_STORE_PASSWORD`, `MAGINA_UPLOAD_KEY_ALIAS`, `MAGINA_UPLOAD_KEY_PASSWORD`, `MAGINA_PRIVACY_URL` y `MAGINA_ACCOUNT_DELETION_URL`. Las dos URL web deben ser públicas, HTTPS, accesibles sin instalar la app y mencionar Mágina Olivo o al titular de Play. El flujo de eliminación debe permitir solicitar la eliminación de la cuenta y datos asociados; cualquier retención legal se explica en la política. La clave de Auth es publicable, nunca `service_role`.

Tiempo/mercado en producción permanece sin configurar si faltan `MAGINA_WEATHER_PRODUCTION_URL` y `MAGINA_WEATHER_PRODUCTION_PUBLIC_KEY`; ambos se proporcionan juntos y nunca deben apuntar al proyecto DEV. La app agrícola sigue operativa sin estos servicios.

Antes de subir: comprobar en Play Console el identificador, la firma de subida, la versión monotónica, Data safety, política y enlace de eliminación reales; registrar `com.isivoltpro.maginaolivo://auth/callback` en Auth; probar registro, recuperación, eliminación, datos offline y reapertura con el backend de producción y un dispositivo Android 16/17. La prueba técnica local con valores sintéticos no valida servicios ni páginas reales.
