#!/usr/bin/env bash
# Review tooling: renders whole screens with sample data and copies them to ./screens.
set -euo pipefail
PKG=com.isivoltpro.maginaolivo.dev
adb install -r -t "$(find app/build/outputs/apk/dev/debug -name '*.apk' | head -n 1)"
adb install -r -t "$(find app/build/outputs/apk/androidTest -name '*.apk' | head -n 1)"
adb shell run-as "$PKG" rm -rf files/gate3 >/dev/null 2>&1 || true
mkdir -p screens
set +e
adb shell am instrument -w \
  -e class com.isivoltpro.maginaolivo.Gate3EvidenceScreenshotTest,com.isivoltpro.maginaolivo.WalkthroughScreenshotTest \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > screens/instrumentation.txt 2>&1
set -e
cat screens/instrumentation.txt
for name in $(adb shell run-as "$PKG" ls files/gate3 | tr -d '\r'); do
  adb exec-out run-as "$PKG" cat "files/gate3/$name" > "screens/$name"
done
ls -la screens
