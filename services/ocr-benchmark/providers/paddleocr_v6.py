#!/usr/bin/env python3
"""Run PaddleOCR 3.7 / PP-OCRv6 against the Mágina OCR benchmark corpus."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from extract_fields import extract_fields  # noqa: E402


def as_result_dict(result: Any) -> dict[str, Any]:
    payload = getattr(result, "json", None)
    if callable(payload):
        payload = payload()
    if not isinstance(payload, dict):
        raise RuntimeError("PaddleOCR result does not expose a dict-like json payload")
    nested = payload.get("res")
    return nested if isinstance(nested, dict) else payload


def reading_order(texts: list[str], boxes: list[Any], row_tolerance: float = 34.0) -> list[str]:
    """Rebuild human reading order using rectangular OCR boxes.

    PaddleOCR exposes rec_texts and rec_boxes but the returned text list is not
    guaranteed to be a semantic left-to-right line order for label/value forms.
    We cluster detections into visual rows by vertical centre and then sort each
    row from left to right. If boxes are unavailable, preserve provider order.
    """
    if len(texts) != len(boxes) or not boxes:
        return texts

    items: list[tuple[float, float, str]] = []
    for text, box in zip(texts, boxes, strict=False):
        try:
            x_min, y_min, x_max, y_max = [float(value) for value in box]
        except (TypeError, ValueError):
            return texts
        items.append(((y_min + y_max) / 2.0, x_min, text))

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
    ordered: list[str] = []
    for _, row in ordered_rows:
        ordered.extend(item[2] for item in sorted(row, key=lambda item: item[1]))
    return ordered


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--images-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source-ext", default=".png")
    parser.add_argument("--device", default="cpu")
    args = parser.parse_args()

    try:
        import paddle
        import paddleocr
        from paddleocr import PaddleOCR
    except ImportError as exc:
        print(f"PaddleOCR dependencies are missing: {exc}", file=sys.stderr)
        return 2

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    args.output.parent.mkdir(parents=True, exist_ok=True)

    # Keep benchmark preprocessing minimal so the same source image is compared
    # against the Tesseract baseline. PP-OCRv6 is the default OCR generation.
    ocr = PaddleOCR(
        use_doc_orientation_classify=False,
        use_doc_unwarping=False,
        use_textline_orientation=False,
        device=args.device,
    )

    provider_version = f"paddleocr {getattr(paddleocr, '__version__', 'unknown')} / paddle {getattr(paddle, '__version__', 'unknown')} / PP-OCRv6"
    rows: list[dict[str, Any]] = []

    for case in manifest["cases"]:
        case_id = case["id"]
        image = args.images_dir / f"{case_id}{args.source_ext}"
        row: dict[str, Any] = {
            "case_id": case_id,
            "provider": "paddleocr-v6",
            "provider_version": provider_version,
            "duration_ms": None,
            "raw_text": "",
            "fields": {},
            "confidence": None,
            "error": None,
        }

        try:
            started = time.perf_counter()
            prediction = list(ocr.predict(str(image)))
            row["duration_ms"] = round((time.perf_counter() - started) * 1000, 2)
            if not prediction:
                raise RuntimeError("PaddleOCR returned no prediction result")

            payload = as_result_dict(prediction[0])
            texts = [str(text).strip() for text in payload.get("rec_texts", []) if str(text).strip()]
            scores = [float(score) for score in payload.get("rec_scores", [])]
            boxes = payload.get("rec_boxes", [])
            ordered_texts = reading_order(texts, list(boxes) if boxes is not None else [])
            raw_text = "\n".join(ordered_texts)

            row["raw_text"] = raw_text
            row["fields"] = extract_fields(raw_text, case["type"])
            if scores:
                row["confidence"] = round(sum(scores) / len(scores), 6)
        except Exception as exc:  # benchmark runner must report provider failures per case
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
