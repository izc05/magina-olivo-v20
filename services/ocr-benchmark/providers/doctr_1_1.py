#!/usr/bin/env python3
"""Run docTR 1.1 against the Mágina OCR benchmark corpus."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from extract_fields import extract_fields  # noqa: E402


def collect_confidences(exported: dict[str, Any]) -> list[float]:
    scores: list[float] = []
    for page in exported.get("pages", []):
        for block in page.get("blocks", []):
            for line in block.get("lines", []):
                for word in line.get("words", []):
                    confidence = word.get("confidence")
                    if isinstance(confidence, (int, float)):
                        scores.append(float(confidence))
    return scores


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--images-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source-ext", default=".png")
    args = parser.parse_args()

    try:
        import doctr
        import torch
        from doctr.io import DocumentFile
        from doctr.models import ocr_predictor
    except ImportError as exc:
        print(f"docTR dependencies are missing: {exc}", file=sys.stderr)
        return 2

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    args.output.parent.mkdir(parents=True, exist_ok=True)

    # Keep a single warmed model for all cases. The corpus includes slight skew,
    # therefore we do not force the fast straight-page assumption here.
    predictor = ocr_predictor(pretrained=True, assume_straight_pages=False)
    predictor = predictor.to(torch.device("cpu"))

    provider_version = f"python-doctr {getattr(doctr, '__version__', 'unknown')} / torch {getattr(torch, '__version__', 'unknown')}"
    rows: list[dict[str, Any]] = []

    for case in manifest["cases"]:
        case_id = case["id"]
        image = args.images_dir / f"{case_id}{args.source_ext}"
        row: dict[str, Any] = {
            "case_id": case_id,
            "provider": "doctr-1.1",
            "provider_version": provider_version,
            "duration_ms": None,
            "raw_text": "",
            "fields": {},
            "confidence": None,
            "error": None,
        }

        try:
            document = DocumentFile.from_images(str(image))
            started = time.perf_counter()
            prediction = predictor(document)
            row["duration_ms"] = round((time.perf_counter() - started) * 1000, 2)

            # page.render() follows docTR's resolved line order and is preferable
            # to flattening individual words ourselves.
            raw_text = "\n".join(page.render() for page in prediction.pages).strip()
            exported = prediction.export()
            scores = collect_confidences(exported)

            row["raw_text"] = raw_text
            row["fields"] = extract_fields(raw_text, case["type"])
            if scores:
                row["confidence"] = round(sum(scores) / len(scores), 6)
        except Exception as exc:  # benchmark runner records failures per case
            row["error"] = f"{type(exc).__name__}: {exc}"

        rows.append(row)
        print(json.dumps(row, ensure_ascii=False))

    args.output.write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows),
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
