#!/usr/bin/env bash
set -euo pipefail
bash .github/scripts/assert-gate3-emulator-safe.sh
export ANDROID_SERIAL="$(adb get-serialno | tr -d '\r')"
PKG=com.isivoltpro.maginaolivo.dev
RUNNER="$PKG.test/androidx.test.runner.AndroidJUnitRunner"
CLASS=com.isivoltpro.maginaolivo.G1cJournalProcessTest
TOKEN="${G1C_QA_TOKEN:-$(cat /proc/sys/kernel/random/uuid)}"
[[ "$TOKEN" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]] || { echo 'Canonical UUID required'; exit 1; }
OUT=evidence/g1c-journal-process
mkdir -p "$OUT"
git rev-parse HEAD > "$OUT/checkout-sha.txt"
printf '%s\n' "$TOKEN" > "$OUT/token.txt"
printf 'checkpoint\twriter_adb_exit\trecovery_adb_exit\tresult\n' > "$OUT/results.tsv"
writer_job=''
cleanup() {
  if [[ -n "$writer_job" ]]; then
    timeout 3s adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
    kill "$writer_job" >/dev/null 2>&1 || true
    local deadline=$((SECONDS+10))
    while kill -0 "$writer_job" 2>/dev/null && ((SECONDS<deadline)); do sleep 0.1; done
    if kill -0 "$writer_job" 2>/dev/null; then kill -KILL "$writer_job" 2>/dev/null || true; fi
    wait "$writer_job" 2>/dev/null || true
  fi
}
trap cleanup EXIT
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk > "$OUT/install-app.txt"
adb install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk > "$OUT/install-test.txt"

for checkpoint in prepared receipt-before-rename receipt-committed; do
  directory="$OUT/$checkpoint"
  mkdir -p "$directory"
  private="files/g1c-journal-process/$TOKEN/$checkpoint/marker.txt"
  args=(-e qaG1cJournalProcess true -e qaG1cJournalToken "$TOKEN" -e qaG1cJournalCheckpoint "$checkpoint")
  adb logcat -c
  adb shell am instrument -w -r -e class "$CLASS#holdAtCheckpoint" "${args[@]}" "$RUNNER" > "$directory/writer.txt" 2>&1 &
  writer_job=$!
  marker_ready=0
  deadline=$((SECONDS+30))
  while ((SECONDS<deadline)); do
    if ! kill -0 "$writer_job" 2>/dev/null; then break; fi
    if timeout 3s adb exec-out run-as "$PKG" cat "$private" > "$directory/marker-read.txt" 2> "$directory/marker-read-error.txt"; then
      if [[ "$(grep -c '^checkpoint=' "$directory/marker-read.txt")" == 1 ]] &&
         [[ "$(grep -c '^pid=' "$directory/marker-read.txt")" == 1 ]] &&
         [[ "$(grep -c '^journal_sha256=' "$directory/marker-read.txt")" == 1 ]] &&
         [[ "$(wc -l < "$directory/marker-read.txt")" == 3 ]]; then marker_ready=1; break; fi
    fi
    sleep 0.1
  done
  if [[ "$marker_ready" != 1 ]]; then
    echo "Writer exited or missed checkpoint deadline: $checkpoint" >&2
    cat "$directory/writer.txt"
    writer_rc=running
    if ! kill -0 "$writer_job" 2>/dev/null; then
      set +e
      wait "$writer_job"
      writer_rc=$?
      set -e
      writer_job=''
      printf '%s\n' "$writer_rc" > "$directory/writer-adb-exit.txt"
    fi
    printf '%s\t%s\tnot-run\tFAIL_MARKER\n' "$checkpoint" "$writer_rc" >> "$OUT/results.tsv"
    exit 1
  fi
  marker_checkpoint="$(sed -n 's/^checkpoint=//p' "$directory/marker-read.txt")"
  writer_pid="$(sed -n 's/^pid=//p' "$directory/marker-read.txt")"
  marker_hash="$(sed -n 's/^journal_sha256=//p' "$directory/marker-read.txt")"
  [[ "$marker_checkpoint" == "$checkpoint" && "$writer_pid" =~ ^[1-9][0-9]*$ && "$marker_hash" =~ ^[0-9a-f]{64}$ ]]
  actual_pid="$(timeout 3s adb shell pidof -s "$PKG" | tr -d '\r')"
  [[ "$actual_pid" == "$writer_pid" ]]
  timeout 3s adb shell am force-stop "$PKG"
  deadline=$((SECONDS+10))
  while kill -0 "$writer_job" 2>/dev/null && ((SECONDS<deadline)); do sleep 0.1; done
  if kill -0 "$writer_job" 2>/dev/null; then echo 'Writer adb child did not stop' >&2; exit 1; fi
  set +e
  wait "$writer_job"
  writer_rc=$?
  set -e
  writer_job=''
  printf '%s\n' "$writer_rc" > "$directory/writer-adb-exit.txt"
  # adb may return zero when instrumentation was killed: never treat this as a writer PASS.
  set +e
  timeout 3s adb shell pidof -s "$PKG" > "$directory/pid-after-stop.txt" 2> "$directory/pid-after-stop-error.txt"
  pid_after_stop_rc=$?
  set -e
  printf '%s\n' "$pid_after_stop_rc" > "$directory/pid-after-stop-exit.txt"
  if [[ "$pid_after_stop_rc" != 1 || -s "$directory/pid-after-stop.txt" ]]; then
    echo "Cannot prove DEV process disappeared: pidof rc=$pid_after_stop_rc" >&2
    printf '%s\t%s\tnot-run\tFAIL_PID_STOP\n' "$checkpoint" "$writer_rc" >> "$OUT/results.tsv"
    exit 1
  fi
  set +e
  adb shell am instrument -w -r -e class "$CLASS#recoverAfterForceStop" "${args[@]}" "$RUNNER" > "$directory/recovery.txt" 2>&1
  recovery_rc=$?
  set -e
  capture_rc=0
  timeout 10s adb logcat -d > "$directory/logcat.txt" 2>&1 || capture_rc=1
  timeout 15s adb pull "/sdcard/Android/data/$PKG/files/g1c-journal-process-evidence/$TOKEN/$checkpoint" "$directory/device" > "$directory/pull.txt" 2>&1 || capture_rc=1
  : > "$directory/capture-manifest.txt"
  for name in marker.txt journal-before.bin journal-after-b.bin journal-after-a.bin result.txt; do
    if [[ -s "$directory/device/$name" ]]; then printf 'PRESENT %s\n' "$name" >> "$directory/capture-manifest.txt"
    else printf 'MISSING %s\n' "$name" >> "$directory/capture-manifest.txt"; capture_rc=1; fi
  done
  result=FAIL
  if [[ "$capture_rc" == 0 && "$recovery_rc" == 0 ]] &&
     grep -qE '^OK \(1 test\)' "$directory/recovery.txt" &&
     ! grep -qE 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed' "$directory/recovery.txt" &&
     cmp -s "$directory/marker-read.txt" "$directory/device/marker.txt" &&
     [[ "$(sha256sum "$directory/device/journal-before.bin" | cut -d ' ' -f1)" == "$marker_hash" ]] &&
     cmp -s "$directory/device/journal-before.bin" "$directory/device/journal-after-b.bin" &&
     grep -qx "PASS checkpoint=$checkpoint" "$directory/device/result.txt"; then result=PASS; fi
  printf '%s\t%s\t%s\t%s\n' "$checkpoint" "$writer_rc" "$recovery_rc" "$result" | tee -a "$OUT/results.tsv"
  cat "$directory/recovery.txt"
  [[ "$result" == PASS ]] || exit 1
done
[[ "$(grep -c $'\tPASS$' "$OUT/results.tsv")" == 3 ]]
echo 'PASS: three Android force-stop journal boundaries; no app-data clear or production transport'
