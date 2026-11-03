#!/usr/bin/env bash
# Local QA only. Runs an existing disposable-Supabase suite runner (supabase/tests/<suite>/run.sh) on its own
# port range, so it never collides with other local Supabase projects (e.g. a leftover one bound to 54321-54324).
#
# The repository runner is NOT modified: a temporary copy is written outside the repository, with exactly two
# textual changes, both checked before running:
#   1. its `cd "$(dirname "$0")/../../.."` points at the repository root;
#   2. after its `major_version = 17` config line, every `= 543NN` port of the generated config.toml becomes
#      `= <prefix>NN` (default prefix 553, i.e. 55320-55329). Fixture ports outside 543NN (e.g. 54443) are untouched.
# Everything else (project id, cleanup with `supabase stop --no-backup`, diagnostics) is the runner's own.
# Never DEV or production: the runners themselves only accept local, disposable projects.
#
# Usage: scripts/local-qa/run-isolated-ports.sh <suite> [port-prefix]
#   e.g. scripts/local-qa/run-isolated-ports.sh g26-harvest-head-v2
#        scripts/local-qa/run-isolated-ports.sh g26-delivery-delete 563
set -euo pipefail
suite="${1:?usage: run-isolated-ports.sh <suite> [port-prefix]}"
prefix="${2:-553}"
[[ "$suite" =~ ^[a-z0-9-]+$ ]] || { echo "Invalid suite name: $suite" >&2; exit 2; }
# Ports <prefix>20..<prefix>29 must stay valid TCP ports (<= 65535): prefixes 100..655, never the default 543.
[[ "$prefix" =~ ^[1-6][0-9]{2}$ && "$prefix" != "543" && "$prefix" -le 655 ]] || { echo "Port prefix must be 100..655 and not 543" >&2; exit 2; }
root="$(cd "$(dirname "$0")/../.." && pwd)"
runner="$root/supabase/tests/$suite/run.sh"
[[ -f "$runner" ]] || { echo "No runner: $runner" >&2; exit 2; }
cd_line='cd "$(dirname "$0")/../../.."'
grep -qxF "$cd_line" "$runner" || { echo "Runner without the expected cd line: $runner" >&2; exit 3; }
grep -q "^config=config.replace(/^major_version = " "$runner" || { echo "Runner without the major_version config step: $runner" >&2; exit 3; }
for port in $(seq "${prefix}20" "${prefix}29"); do
  if (netstat -an 2>/dev/null || ss -ltn 2>/dev/null || true) | grep -qE "[:.]$port[[:space:]].*(LISTEN|LISTENING)"; then
    echo "Port $port is already in use; choose another prefix" >&2; exit 4
  fi
done
copy="$(mktemp "${TMPDIR:-/tmp}/magina-isolated-${suite}-XXXXXX.sh")"
trap 'rm -f -- "$copy"' EXIT
ROOT="$root" PREFIX="$prefix" CD_LINE="$cd_line" node --input-type=module - "$runner" "$copy" <<'JS'
import { readFileSync, writeFileSync } from 'node:fs';
const [source, target] = process.argv.slice(2);
const lines = readFileSync(source, 'utf8').split('\n');
let cd = 0, ports = 0;
const out = [];
for (const line of lines) {
  if (line === process.env.CD_LINE) { out.push(`cd ${JSON.stringify(process.env.ROOT)}`); cd++; continue; }
  out.push(line);
  if (line.startsWith('config=config.replace(/^major_version = ')) {
    out.push(`config=config.replace(/= 543(\\d\\d)$/mg,'= ${process.env.PREFIX}$1');`); ports++;
  }
}
if (cd !== 1 || ports !== 1) throw new Error(`expected one cd and one config step, got cd=${cd} ports=${ports}`);
writeFileSync(target, out.join('\n'));
JS
echo "isolated ports ${prefix}20-${prefix}29 for $suite (repository runner unchanged)"
# Git Bash (MSYS) rewrites arguments that look like paths; keep openssl's -subj '/CN=...' literal.
MSYS2_ARG_CONV_EXCL="${MSYS2_ARG_CONV_EXCL:+$MSYS2_ARG_CONV_EXCL;}/CN=" bash "$copy"
