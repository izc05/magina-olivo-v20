#!/usr/bin/env bash
# Historical contract classes of Jornadas, Pesadas and hand-recorded Jornadas (JornadaPesadasContractTest,
# DeliveryContractTest, HarvestContractTest) on the attached disposable API 35 emulator.
#
# Counted PER TEST from `am instrument -r` (raw status codes), never from the human summary: that summary was
# truncated in an earlier baseline and under-counted the failures (#841). The run passes only if exactly
# EXPECTED tests started, each ended with code 0 (no failure, error, ignore or assumption) and the
# instrumentation itself completed.
set -euo pipefail
cd "$(dirname "$0")/../../.."
command -v adb >/dev/null
EXPECTED=125   # 113 historical + 1 (#845) + 11 added by #796 to DeliveryContractTest
work="$(mktemp -d)"
cleanup() { rm -rf "$work"; }
trap cleanup EXIT
gradle --no-daemon --max-workers=1 --no-parallel assembleDevDebug assembleDevDebugAndroidTest >/dev/null
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk >/dev/null
adb install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk >/dev/null
adb shell pm clear com.isivoltpro.maginaolivo.dev >/dev/null
base=com.isivoltpro.maginaolivo.data.local
adb shell am instrument -r -w -e class \
  $base.JornadaPesadasContractTest,$base.DeliveryContractTest,$base.HarvestContractTest \
  com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner \
  | tr -d '\r' > "$work/raw.out"
awk -v expected="$EXPECTED" '
  /^INSTRUMENTATION_STATUS: class=/ { cls = substr($0, index($0, "=") + 1) }
  /^INSTRUMENTATION_STATUS: test=/  { tst = substr($0, index($0, "=") + 1) }
  /^INSTRUMENTATION_STATUS_CODE: /  {
    key = tst "(" cls ")"; code = $2
    if (code == 1) { if (!(key in started)) { started[key] = 1; nstarted++ } }
    else { result[key] = code }
  }
  /^INSTRUMENTATION_CODE: / { finished = $2 }
  END {
    for (k in started) {
      split(k, parts, "("); c = parts[2]; sub(/\)$/, "", c); n = split(c, seg, "."); c = seg[n]
      total[c]++
      if (!(k in result)) { bad++; print "NO_RESULT " k }
      else if (result[k] != 0) { bad++; failed[c]++; print "FAILED(" result[k] ") " k }
      else passed[c]++
    }
    for (c in total) printf "CONTRACT %s passed=%d failed=%d total=%d\n", c, passed[c], failed[c] + 0, total[c]
    printf "CONTRACT TOTAL started=%d expected=%d bad=%d instrumentation=%s\n", nstarted, expected, bad + 0, finished
    exit !(nstarted == expected && bad == 0 && finished == "-1")
  }' "$work/raw.out"
