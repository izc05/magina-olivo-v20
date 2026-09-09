#!/usr/bin/env python3
"""Simple provider-neutral field extraction used only by OCR benchmarking.

Production extraction will be versioned separately after OCR benchmark evidence.
"""

from __future__ import annotations

import re
import unicodedata
from typing import Any


def ascii_upper(text: str) -> str:
    value = unicodedata.normalize("NFKD", text or "")
    value = "".join(ch for ch in value if not unicodedata.combining(ch))
    return value.upper()


def parse_spanish_number(text: str) -> float | None:
    text = text.strip().replace(" ", "")
    if re.fullmatch(r"\d{1,3}(?:\.\d{3})+", text):
        text = text.replace(".", "")
    elif "," in text and "." not in text:
        text = text.replace(",", ".")
    elif "," in text and "." in text:
        text = text.replace(".", "").replace(",", ".")
    try:
        return float(text)
    except ValueError:
        return None


def first(patterns: list[str], text: str) -> str | None:
    for pattern in patterns:
        match = re.search(pattern, text, flags=re.IGNORECASE | re.DOTALL)
        if match:
            return match.group(1).strip()
    return None


def clean_line(line: str) -> str:
    line = re.sub(r"^[^A-Z0-9]+|[^A-Z0-9]+$", "", line.strip())
    return " ".join(line.split())


def line_after_heading(raw_text: str, heading: str) -> str | None:
    lines = [clean_line(ascii_upper(line)) for line in raw_text.splitlines()]
    lines = [line for line in lines if line]
    for index, line in enumerate(lines):
        if heading in line:
            for candidate in lines[index + 1:]:
                if candidate and not re.fullmatch(r"[_\-=|. ]+", candidate):
                    return candidate
    return None


def extract_fields(raw_text: str, document_type: str) -> dict[str, Any]:
    normalized = ascii_upper(raw_text)
    one_line = " ".join(normalized.split())
    fields: dict[str, Any] = {}

    date = first([
        r"(?:FECHA(?:\s+RESULTADO)?)[\s:.-]*([0-9]{4}[-/.][0-9]{2}[-/.][0-9]{2})",
        r"(?:FECHA(?:\s+RESULTADO)?)[\s:.-]*([0-9]{2}[-/.][0-9]{2}[-/.][0-9]{4})",
    ], one_line)
    if date:
        date = date.replace("/", "-").replace(".", "-")
        if re.fullmatch(r"\d{2}-\d{2}-\d{4}", date):
            day, month, year = date.split("-")
            date = f"{year}-{month}-{day}"
        fields["date"] = date

    # OCR commonly confuses Nº/N° with NO, NE or N0.
    ticket = first([
        r"(?:N(?:O|E|0)?\s*ALBARAN|ALBARAN\s+RELACIONADO)[\s:.-]*([A-Z0-9][A-Z0-9\-]{2,})",
    ], one_line)
    if ticket:
        fields["ticket_number"] = ticket

    if document_type == "delivery_ticket":
        kg_text = first([
            r"PESO\s+NETO[^0-9]{0,12}([0-9][0-9.,\s]*)\s*KG",
        ], one_line)
        if kg_text:
            kg = parse_spanish_number(kg_text)
            if kg is not None:
                fields["total_kg"] = int(round(kg))

        cooperative = line_after_heading(raw_text, "ALBARAN DE ENTREGA")
        if cooperative and not cooperative.startswith("FECHA"):
            cooperative = clean_line(cooperative)
            if cooperative:
                fields["cooperative_or_mill"] = cooperative

    elif document_type == "yield_result":
        yield_text = first([
            r"RENDIMIENTO[^0-9]{0,12}([0-9]+(?:[.,][0-9]+)?)\s*%",
        ], one_line)
        if yield_text:
            value = parse_spanish_number(yield_text)
            if value is not None:
                fields["yield_percent"] = value

        moisture_text = first([
            r"HUMEDAD[^0-9]{0,12}([0-9]+(?:[.,][0-9]+)?)\s*%",
        ], one_line)
        if moisture_text:
            value = parse_spanish_number(moisture_text)
            if value is not None:
                fields["moisture_percent"] = value

    return fields
