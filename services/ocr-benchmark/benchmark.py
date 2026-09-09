#!/usr/bin/env python3
"""Provider-neutral OCR benchmark scoring for Mágina Olivo V20."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import unicodedata
from pathlib import Path
from typing import Any


def normalize_text(value: str) -> str:
    value = unicodedata.normalize("NFKC", value or "")
    value = value.upper().replace("\r", "\n")
    return " ".join(value.split())


def normalize_token(value: Any) -> str:
    text = unicodedata.normalize("NFKD", str(value or "")).upper()
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return re.sub(r"[^A-Z0-9]", "", text)


def levenshtein(a: str, b: str) -> int:
    if len(a) < len(b):
        a, b = b, a
    if not b:
        return len(a)

    previous = list(range(len(b) + 1))
    for i, char_a in enumerate(a, start=1):
        current = [i]
        for j, char_b in enumerate(b, start=1):
            insert = current[j - 1] + 1
            delete = previous[j] + 1
            replace = previous[j - 1] + (char_a != char_b)
            current.append(min(insert, delete, replace))
        previous = current
    return previous[-1]


def character_error_rate(reference: str, hypothesis: str) -> float:
    ref = normalize_text(reference)
    hyp = normalize_text(hypothesis)
    if not ref:
        return 0.0 if not hyp else 1.0
    return min(1.0, levenshtein(ref, hyp) / len(ref))


def as_number(value: Any) -> float | None:
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value) if math.isfinite(float(value)) else None
    text = str(value).strip().replace(" ", "")
    if not text:
        return None
    # Spanish OCR commonly yields 1.842 for thousands and 21,4 for decimals.
    if re.fullmatch(r"\d{1,3}(?:\.\d{3})+", text):
        text = text.replace(".", "")
    elif "," in text and "." not in text:
        text = text.replace(",", ".")
    elif "," in text and "." in text:
        text = text.replace(".", "").replace(",", ".")
    try:
        number = float(text)
    except ValueError:
        return None
    return number if math.isfinite(number) else None


def field_matches(name: str, expected: Any, actual: Any) -> bool:
    if expected is None:
        return actual in (None, "")

    if name in {"total_kg", "kilograms"}:
        left, right = as_number(expected), as_number(actual)
        return left is not None and right is not None and abs(left - right) < 0.0001

    if name in {"yield_percent", "moisture_percent", "acidity_percent"}:
        left, right = as_number(expected), as_number(actual)
        return left is not None and right is not None and abs(left - right) <= 0.05

    if name in {"date", "delivery_date", "result_date"}:
        return str(expected) == str(actual)

    return normalize_token(expected) == normalize_token(actual)


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = line.strip()
        if not line:
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"Invalid JSONL at line {line_no}: {exc}") from exc
        if not isinstance(row, dict):
            raise ValueError(f"Result line {line_no} must be a JSON object")
        rows.append(row)
    return rows


def score(manifest: dict[str, Any], results: list[dict[str, Any]]) -> dict[str, Any]:
    cases = manifest.get("cases")
    if not isinstance(cases, list) or not cases:
        raise ValueError("Manifest must contain a non-empty 'cases' array")

    by_id = {row.get("case_id"): row for row in results if row.get("case_id")}
    field_totals: dict[str, int] = {}
    field_correct: dict[str, int] = {}
    case_details: list[dict[str, Any]] = []
    cer_values: list[float] = []
    durations: list[float] = []
    complete_correct = 0
    completed = 0

    for case in cases:
        case_id = case.get("id")
        if not case_id:
            raise ValueError("Every manifest case requires 'id'")
        result = by_id.get(case_id)
        expected_fields = case.get("expected", {})
        reference_text = case.get("reference_text", "")

        if not result:
            case_details.append({"case_id": case_id, "status": "missing", "complete": False})
            for field in expected_fields:
                field_totals[field] = field_totals.get(field, 0) + 1
            continue

        if result.get("error"):
            case_details.append({
                "case_id": case_id,
                "status": "error",
                "error": result.get("error"),
                "complete": False,
            })
            for field in expected_fields:
                field_totals[field] = field_totals.get(field, 0) + 1
            continue

        completed += 1
        raw_text = str(result.get("raw_text", ""))
        cer = character_error_rate(reference_text, raw_text)
        cer_values.append(cer)

        duration = result.get("duration_ms")
        if isinstance(duration, (int, float)) and math.isfinite(float(duration)):
            durations.append(float(duration))

        actual_fields = result.get("fields") if isinstance(result.get("fields"), dict) else {}
        matches: dict[str, bool] = {}
        for field, expected in expected_fields.items():
            field_totals[field] = field_totals.get(field, 0) + 1
            ok = field_matches(field, expected, actual_fields.get(field))
            matches[field] = ok
            if ok:
                field_correct[field] = field_correct.get(field, 0) + 1

        complete = all(matches.values()) if matches else True
        if complete:
            complete_correct += 1

        case_details.append({
            "case_id": case_id,
            "status": "ok",
            "cer": round(cer, 6),
            "fields": matches,
            "complete": complete,
        })

    total_cases = len(cases)
    field_accuracy = {
        field: round(field_correct.get(field, 0) / total, 6) if total else 0.0
        for field, total in sorted(field_totals.items())
    }

    provider = next((row.get("provider") for row in results if row.get("provider")), None)
    provider_version = next((row.get("provider_version") for row in results if row.get("provider_version")), None)

    return {
        "provider": provider,
        "provider_version": provider_version,
        "cases_total": total_cases,
        "cases_completed": completed,
        "cases_complete_correct": complete_correct,
        "complete_accuracy": round(complete_correct / total_cases, 6),
        "mean_cer": round(sum(cer_values) / len(cer_values), 6) if cer_values else None,
        "mean_duration_ms": round(sum(durations) / len(durations), 2) if durations else None,
        "field_accuracy": field_accuracy,
        "cases": case_details,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--results", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    try:
        report = score(load_json(args.manifest), load_jsonl(args.results))
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"benchmark error: {exc}", file=sys.stderr)
        return 2

    encoded = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
