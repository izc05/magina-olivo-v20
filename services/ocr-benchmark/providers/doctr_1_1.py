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


def word_box(geometry: Any) -> tuple[float, float, float, float] | None:
    """Normalize docTR word geometry to x_min, y_min, x_max, y_max."""
    try:
        points = list(geometry)
        if len(points) == 2 and all(len(point) == 2 for point in points):
            (x0, y0), (x1, y1) = points
            return float(x0), float(y0), float(x1), float(y1)
        if len(points) >= 4 and all(len(point) == 2 for point in points):
            xs = [float(point[0]) for point in points]
            ys = [float(point[1]) for point in points]
            return min(xs), min(ys), max(xs), max(ys)
    except (TypeError, ValueError):
        return None
    return None


def spatial_text_and_confidences(exported: dict[str, Any], row_tolerance: float = 0.025) -> tuple[str, list[float]]:
    page_texts: list[str] = []
    scores: list[float] = []

    for page in exported.get("pages", []):
        items: list[tuple[float, float, str]] = []
        fallback_words: list[str] = []
        for block in page.get("blocks", []):
            for line in block.get("lines", []):
                for word in line.get("words", []):
                    value = str(word.get("value", "")).strip()
                    if not value:
                        continue
                    fallback_words.append(value)
                    confidence = word.get("confidence")
                    if isinstance(confidence, (int, float)):
                        scores.append(float(confidence))
                    box = word_box(word.get("geometry"))
                    if box is None:
                        continue
                    x_min, y_min, x_max, y_max = box
                    items.append(((y_min + y_max) / 2.0, x_min, value))

        if not items:
            page_texts.append(" ".join(fallback_words))
            continue

        items.sort(key=lambda item: (item[0], item[1]))
        rows: list[list[tuple[float, float, str]]] = []
        row_centres: list[float] = []

        for item in items:
            y_center = item[0]
            target_index: int | None = None
            best_distance = float("inf")
            for index, centre in enumerate(row_centres):
                distance = abs(y_center - centre)
                if distance <= row_tolerance and distance < best_distance:
                    target_index = index
                    best_distance = distance

            if target_index is None:
                rows.append([item])
                row_centres.append(y_center)
            else:
                rows[target_index].append(item)
                row_centres[target_index] = sum(row_item[0] for row_item in rows[target_index]) / len(rows[target_index])

        ordered_rows = sorted(zip(row_centres, rows, strict=False), key=lambda pair: pair[0])
        page_texts.append("\n".join(
            " ".join(item[2] for item in sorted(row, key=lambda item: item[1]))
            for _, row in ordered_rows
        ))

    return "\n".join(page_texts).strip(), scores


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

            exported = prediction.export()
            raw_text, scores = spatial_text_and_confidences(exported)

            row["raw_text"] = raw_text
            row["fields"] = extract_fields(raw_text, case["type"])
            if scores:
                row["confidence"] = round(sum(scores) / len(scores), 6)
        except Exception as exc:
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
