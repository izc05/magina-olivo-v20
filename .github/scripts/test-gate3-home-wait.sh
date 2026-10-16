#!/usr/bin/env bash
set -euo pipefail
# Exercise the production function, replacing only the emulator boundary.
eval "$(sed -n '/^wait_for_home() {/,/^}/p' .github/scripts/gate3-emulator-evidence.sh)"
tmp="$(mktemp -d)"
trap 'rm -f "$tmp/home.xml"; rmdir "$tmp"' EXIT
sleep() { :; }
mode=home
taps=0
dumps=0
dump_ui() {
  dumps=$((dumps+1))
  if [[ "$mode" == home || ( "$mode" == lost_tap && "$taps" -ge 1 ) ]]; then
    echo '<node text="Inicio" />' > "$1"
  elif [[ "$mode" == unrelated ]]; then
    echo '<node text="Error" />' > "$1"
  else
    echo '<node text="Saltar" />' > "$1"
  fi
}
dismiss_quickstep_anr() { return 1; }
tap_text_from_xml() { [[ "$1" == Saltar ]]; taps=$((taps+1)); }
wait_for_home "$tmp/home.xml"
[[ "$taps" -eq 0 ]] || { echo 'FAIL: Home must not receive onboarding taps'; exit 1; }
mode=lost_tap; taps=0; dumps=0
wait_for_home "$tmp/home.xml" || { echo 'FAIL: dropped first Skip tap must be recovered from fresh UI'; exit 1; }
[[ "$taps" -eq 1 ]] || { echo 'FAIL: recover exactly once'; exit 1; }
mode=stuck; taps=0; dumps=0
if wait_for_home "$tmp/home.xml" >/dev/null 2>&1; then echo 'FAIL: stuck onboarding must fail'; exit 1; fi
[[ "$taps" -eq 3 && "$dumps" -eq 20 ]] || { echo 'FAIL: retries must remain bounded'; exit 1; }
mode=unrelated; taps=0; dumps=0
if wait_for_home "$tmp/home.xml" >/dev/null 2>&1; then echo 'FAIL: unknown screen must fail'; exit 1; fi
[[ "$taps" -eq 0 ]] || { echo 'FAIL: never tap an unrelated screen'; exit 1; }
echo 'PASS: Home, dropped Skip tap, stuck onboarding and unrelated screen'
