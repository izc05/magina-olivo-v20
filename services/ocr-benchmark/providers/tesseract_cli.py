#!/usr/bin/env python3
"""Run Tesseract CLI against the Mágina OCR benchmark corpus."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from extract_fields import extract_fields  # noqa: E402


def tesseract_version(binary: str) -> str:
    completed = subprocess.run([binary, "--version"], check=True, capture_output=True, text=True)
    return completed.stdout.splitlines()[0].strip()


def run_case(binary: str, image: Path, language: str, psm: int) -> tuple[str, float]:
    started = time.perf_counter()
    completed = subprocess.run(
        [binary, str(image), "stdout", "-l", language, "--psm", str(psm)],
        check=False,
        capture_output=True,
        text=True,
    )
    duration_ms = (time.perf_counter() - started) * 1000
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or f"tesseract returned {completed.returncode}")
    return completed.stdout, duration_ms


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--images-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--binary", default="tesseract")
    parser.add_argument("--language", default="spa+eng")
    parser.add_argument("--psm", type=int, default=6)
    parser.add_argument("--source-ext", default=".png")
    args = parser.parse_args()

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    version = tesseract_version(args.binary)
    args.output.parent.mkdir(parents=True, exist_ok=True)

    rows = []
    for case in manifest["cases"]:
        case_id = case["id"]
        image = args.images_dir / f"{case_id}{args.source_ext}"
        row = {
            "case_id": case_id,
            "provider": "tesseract-cli",
            "provider_version": version,
            "duration_ms": None,
            "raw_text": "",
            "fields": {},
            "error": None,
        }
        try:
            raw_text, duration_ms = run_case(args.binary, image, args.language, args.psm)
            row["duration_ms"] = round(duration_ms, 2)
            row["raw_text"] = raw_text
            row["fields"] = extract_fields(raw_text, case["type"])
        except (OSError, subprocess.SubprocessError, RuntimeError) as exc:
            row["error"] = str(exc)
        rows.append(row)
        print(json.dumps(row, ensure_ascii=False))

    args.output.write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows),
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
