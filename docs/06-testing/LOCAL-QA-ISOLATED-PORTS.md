# Validación local antes de cada push (Windows + Git Bash)

Autorizada por el propietario el 03/11/2026 (#831).

**Objetivo:** detectar fallos antes de subir.

**Lo que no cambia:**
- el CI de GitHub sigue siendo la única evidencia para un GO;
- no se toca DEV/PROD, los emuladores históricos A/B, #796 ni ningún contenedor o proyecto local ajeno.

## 1. Herramientas aisladas

Están en `%USERPROFILE%\magina-qa-tools` y no tocan el PATH global:

| Herramienta | Versión (igual que el CI) |
| --- | --- |
| JDK | Temurin 17.0.20.1+1 |
| Gradle | 9.4.1 |
| Supabase CLI | 2.120.0 |
| Android SDK | El de Android Studio (`%LOCALAPPDATA%\Android\Sdk`): `adb`, `emulator` e imagen `android-35` `google_apis` x86_64 |
| AVD | `MaginaOlivo_LocalQA_Disposable_API35`, propio y desechable |

**Cómo se instalaron:** con checksums verificados (Adoptium, `.sha256` de Gradle y `checksums.txt` de la release de Supabase).

**Cómo se cargan**, en cada terminal de Git Bash:

```bash
source "$USERPROFILE/magina-qa-tools/env.sh"   # JAVA_HOME, PATH, ANDROID_HOME, MAGINA_QA_AVD, ANDROID_SERIAL=emulator-5580
java -version; gradle --version; supabase --version
```

Además, Docker Desktop tiene que estar arrancado (backend WSL2).

## 2. SQL en Supabase desechable con puertos aislados

Los runners del repositorio (`supabase/tests/<suite>/run.sh`) usan los puertos por defecto de Supabase (54321–54329). Si otro proyecto local ya los ocupa, el arranque falla con `port is already allocated`.

**Esos proyectos no se paran.** En su lugar:

```bash
scripts/local-qa/run-isolated-ports.sh <suite> [prefijo]   # prefijo por defecto 553 -> puertos 55320-55329
# ejemplos
scripts/local-qa/run-isolated-ports.sh g26-harvest-head-v2
scripts/local-qa/run-isolated-ports.sh g26-delivery-delete 563
```

**Qué hace el script:**
1. Escribe una **copia temporal** del runner fuera del repositorio. **El runner del repositorio no se modifica.** La copia lleva solo dos cambios, comprobados antes de ejecutar:
   - el `cd` apunta a la raíz del repositorio;
   - tras la línea `major_version = 17` del runner, todos los puertos `= 543NN` del `config.toml` generado pasan a `= <prefijo>NN`.
2. Comprueba que el rango `<prefijo>20–29` está libre.
3. Ejecuta la copia y la borra al terminar.

**Lo que no cambia:** el `project_id` único, la limpieza con `supabase stop --no-backup`, los diagnósticos y la aceptación de solo proyectos locales son los del runner.

**Límites del prefijo:** de 100 a 655 (nunca 543), para que los puertos no pasen de 65535.

**Git Bash:** el script mantiene literal el `-subj '/CN=…'` de `openssl` (`MSYS2_ARG_CONV_EXCL`), que los runners HTTPS necesitan.

**Alcance:**
- sirve para los 24 runners que arrancan Supabase;
- `g26-parcel-mutation-android` (solo Room) no lo necesita;
- los puertos de fixture que no son `543NN` (por ejemplo el puente TLS 54443 de los runners HTTPS) no se tocan, así que un runner HTTPS sigue necesitando ese puerto libre.

## 3. Android

**Orden recomendado:**

1. **JVM:**

   ```bash
   gradle --build-cache :app:testDevDebugUnitTest --tests "*Clase*"
   ```

   Necesita `local.properties` con `sdk.dir`; está ignorado por git.

2. **Room en el emulador de QA, solo ese y siempre limpio:**

   ```bash
   emulator -avd "$MAGINA_QA_AVD" -port 5580 -wipe-data -no-snapshot-load -no-snapshot-save -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
   adb devices                                                  # debe listar solo emulator-5580
   GATE3_ALLOW_APP_DATA_RESET=1 bash .github/scripts/assert-gate3-emulator-safe.sh
   gradle --build-cache assembleDevDebug assembleDevDebugAndroidTest
   adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
   adb install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
   adb shell pm clear com.isivoltpro.maginaolivo.dev
   adb shell am instrument -w -e class com.isivoltpro.maginaolivo.data.local.<Clase> \
     com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner
   adb -s emulator-5580 emu kill
   ```

3. **Batería completa (Room 184 y HTTPS 5):** solo antes del push final del gate, o directamente en CI.

## 4. Qué se reutiliza y qué no

| Se reutiliza | Nunca se reutiliza |
| --- | --- |
| Caché de Gradle (`~/.gradle`) y build cache | Contenedores y volúmenes de un Supabase de prueba (cada ejecución crea los suyos y los borra) |
| Imágenes Docker de Supabase | Datos de la app en el emulador (`-wipe-data` y `pm clear`) |
| APKs del mismo SHA | Certificados TLS o tokens de una ejecución anterior |
