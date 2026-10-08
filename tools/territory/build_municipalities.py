#!/usr/bin/env python3
"""Validate and build a deterministic, offline municipality snapshot.

Input: UTF-8 CSV previously downloaded from an official source.
Field mappings are explicit: no assumptions about upstream column names.
This tool does not download data or modify Android/Room.
"""
import argparse
import csv
import hashlib
import json
import re
import sys
import unicodedata
from pathlib import Path


def search_name(value: str) -> str:
    plain = unicodedata.normalize("NFKD", value.strip().casefold())
    plain = "".join(c for c in plain if not unicodedata.combining(c))
    return " ".join(re.sub(r"[^a-z0-9]+", " ", plain).split())


def build_snapshot(rows, code_column, name_column, province="23"):
    if not re.fullmatch(r"\d{2}", province):
        raise ValueError("Province code must be exactly two digits")
    by_code = {}
    errors = []
    for row_number, row in enumerate(rows, start=2):
        code = (row.get(code_column) or "").strip()
        name = (row.get(name_column) or "").strip()
        if not re.fullmatch(r"\d{5}", code):
            errors.append(f"row {row_number}: invalid five-digit municipality code")
            continue
        if code[:2] != province:
            continue
        if not name or not search_name(name):
            errors.append(f"row {row_number}: empty/invalid municipality name for {code}")
            continue
        record = {"code": code, "provinceCode": province, "name": name, "searchName": search_name(name)}
        if code in by_code:
            errors.append(f"row {row_number}: duplicate municipality code {code}")
            continue
        by_code[code] = record
    if errors:
        raise ValueError("\n".join(errors))
    if not by_code:
        raise ValueError(f"No municipalities found for province {province}")
    return [by_code[code] for code in sorted(by_code)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path, help="Official CSV path")
    parser.add_argument("--output", required=True, type=Path, help="Output JSON path")
    parser.add_argument("--code-column", required=True, help="CSV column containing five-digit INE code")
    parser.add_argument("--name-column", required=True, help="CSV column containing municipality name")
    parser.add_argument("--province", default="23", help="Two-digit province code (Jaén=23)")
    parser.add_argument("--expected-count", required=True, type=int, help="Officially verified expected municipality count; prevents publishing partial exports")
    parser.add_argument("--source-url", required=True, help="Original dataset URL for attribution")
    parser.add_argument("--source-sha256", help="Expected SHA256 of original CSV for reproducible imports")
    parser.add_argument("--source-date", required=True, help="Dataset publication/retrieval date YYYY-MM-DD")
    args = parser.parse_args()
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", args.source_date):
        parser.error("--source-date must be YYYY-MM-DD")
    from datetime import date
    try:
        date.fromisoformat(args.source_date)
    except ValueError:
        parser.error("--source-date must be a real calendar date")
    try:
        source_digest = hashlib.sha256(args.input.read_bytes()).hexdigest()
        if args.source_sha256 and source_digest.lower() != args.source_sha256.lower():
            raise ValueError("Source SHA256 mismatch")
        with args.input.open("r", encoding="utf-8-sig", newline="") as source:
            reader = csv.DictReader(source)
            columns = reader.fieldnames or []
            if args.code_column not in columns or args.name_column not in columns:
                raise ValueError(f"Required columns absent. Available: {columns}")
            records = build_snapshot(reader, args.code_column, args.name_column, args.province)
            if args.expected_count < 1 or len(records) != args.expected_count:
                raise ValueError(f"Coverage mismatch: expected {args.expected_count}, found {len(records)}")
        payload = {
            "schemaVersion": 1,
            "provinceCode": args.province,
            "sourceUrl": args.source_url,
            "sourceDate": args.source_date,
            "sourceSha256": source_digest,
            "attribution": "Datos de origen: administración pública indicada en sourceUrl; verificar licencia de cada descarga",
            "municipalities": records,
        }
        canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"Validated {len(records)} municipalities; SHA256 {hashlib.sha256(canonical.encode()).hexdigest()}")
    except (ValueError, OSError, csv.Error) as error:
        print(f"Catalog validation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
