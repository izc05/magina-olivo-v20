#!/usr/bin/env python3
"""Generate synthetic, non-personal OCR benchmark documents owned by Mágina Olivo."""

from __future__ import annotations

import argparse
import html
import json
from pathlib import Path
from typing import Any


def delivery_svg(case: dict[str, Any], variant: str) -> str:
    expected = case["expected"]
    coop = html.escape(expected["cooperative_or_mill"])
    ticket = html.escape(expected["ticket_number"])
    date = html.escape(expected["date"])
    kg = int(expected["total_kg"])

    transform = ""
    opacity = "1"
    filter_def = ""
    filter_attr = ""
    paper = "#fffdf5"
    text_color = "#17211a"

    if variant == "skew":
        transform = 'transform="rotate(-2 600 500)"'
    elif variant == "low-contrast":
        text_color = "#77776f"
        paper = "#e9e6d9"
    elif variant == "noisy":
        filter_def = '''
        <filter id="paperNoise" x="-10%" y="-10%" width="120%" height="120%">
          <feTurbulence type="fractalNoise" baseFrequency="0.8" numOctaves="2" seed="12" result="noise"/>
          <feBlend in="SourceGraphic" in2="noise" mode="multiply"/>
        </filter>'''
        filter_attr = 'filter="url(#paperNoise)"'
        opacity = "0.93"

    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="1000" viewBox="0 0 1200 1000">
      <defs>{filter_def}</defs>
      <rect width="1200" height="1000" fill="#d7c7a5"/>
      <g {transform}>
        <rect x="105" y="65" width="990" height="870" rx="8" fill="{paper}" stroke="#b5aa92" stroke-width="3" {filter_attr}/>
        <g font-family="Arial, Helvetica, sans-serif" fill="{text_color}" opacity="{opacity}">
          <text x="160" y="150" font-size="42" font-weight="700">ALBARÁN DE ENTREGA</text>
          <text x="160" y="215" font-size="28">{coop}</text>
          <line x1="160" x2="1040" y1="255" y2="255" stroke="{text_color}"/>
          <text x="160" y="330" font-size="27">Fecha</text>
          <text x="600" y="330" font-size="31" font-weight="700">{date}</text>
          <text x="160" y="405" font-size="27">Nº albarán</text>
          <text x="600" y="405" font-size="31" font-weight="700">{ticket}</text>
          <text x="160" y="480" font-size="27">Producto</text>
          <text x="600" y="480" font-size="31">ACEITUNA DE MOLINO</text>
          <text x="160" y="580" font-size="30" font-weight="700">PESO NETO</text>
          <text x="600" y="580" font-size="52" font-weight="700">{kg:,} KG</text>
          <line x1="160" x2="1040" y1="635" y2="635" stroke="{text_color}"/>
          <text x="160" y="710" font-size="22">Documento sintético · Mágina Olivo V20</text>
          <text x="160" y="755" font-size="20">SIN DATOS PERSONALES REALES</text>
        </g>
      </g>
    </svg>'''.replace(f"{kg:,}", f"{kg:,}".replace(",", "."))


def yield_svg(case: dict[str, Any], variant: str) -> str:
    expected = case["expected"]
    ticket = html.escape(expected["ticket_number"])
    date = html.escape(expected["date"])
    yield_percent = str(expected["yield_percent"]).replace(".", ",")
    moisture = str(expected.get("moisture_percent", "")).replace(".", ",")

    rotate = 'transform="rotate(1.6 600 500)"' if variant == "skew" else ""
    text_color = "#68685f" if variant == "low-contrast" else "#17211a"

    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="1000" viewBox="0 0 1200 1000">
      <rect width="1200" height="1000" fill="#d5c19b"/>
      <g {rotate}>
        <rect x="105" y="65" width="990" height="870" rx="8" fill="#fffdf5" stroke="#b5aa92" stroke-width="3"/>
        <g font-family="Arial, Helvetica, sans-serif" fill="{text_color}">
          <text x="160" y="150" font-size="42" font-weight="700">RESULTADO DE MOLTURACIÓN</text>
          <line x1="160" x2="1040" y1="205" y2="205" stroke="{text_color}"/>
          <text x="160" y="300" font-size="27">Fecha resultado</text>
          <text x="620" y="300" font-size="31" font-weight="700">{date}</text>
          <text x="160" y="380" font-size="27">Albarán relacionado</text>
          <text x="620" y="380" font-size="31" font-weight="700">{ticket}</text>
          <text x="160" y="515" font-size="32" font-weight="700">RENDIMIENTO</text>
          <text x="620" y="515" font-size="58" font-weight="700">{yield_percent} %</text>
          <text x="160" y="625" font-size="27">Humedad</text>
          <text x="620" y="625" font-size="34">{moisture} %</text>
          <line x1="160" x2="1040" y1="690" y2="690" stroke="{text_color}"/>
          <text x="160" y="760" font-size="22">Documento sintético · Mágina Olivo V20</text>
          <text x="160" y="805" font-size="20">SIN DATOS PERSONALES REALES</text>
        </g>
      </g>
    </svg>'''


def build_cases() -> list[dict[str, Any]]:
    return [
        {
            "id": "delivery-clean-001",
            "type": "delivery_ticket",
            "variant": "clean",
            "expected": {"cooperative_or_mill": "SCA SAN ISIDRO", "date": "2026-12-12", "ticket_number": "008421", "total_kg": 1842},
            "reference_text": "ALBARÁN DE ENTREGA SCA SAN ISIDRO Fecha 2026-12-12 Nº albarán 008421 Producto ACEITUNA DE MOLINO PESO NETO 1.842 KG",
        },
        {
            "id": "delivery-skew-002",
            "type": "delivery_ticket",
            "variant": "skew",
            "expected": {"cooperative_or_mill": "COOPERATIVA MÁGINA SUR", "date": "2026-12-18", "ticket_number": "A-004781", "total_kg": 975},
            "reference_text": "ALBARÁN DE ENTREGA COOPERATIVA MÁGINA SUR Fecha 2026-12-18 Nº albarán A-004781 Producto ACEITUNA DE MOLINO PESO NETO 975 KG",
        },
        {
            "id": "delivery-low-contrast-003",
            "type": "delivery_ticket",
            "variant": "low-contrast",
            "expected": {"cooperative_or_mill": "ALMAZARA SIERRA VIVA", "date": "2027-01-03", "ticket_number": "310927", "total_kg": 2410},
            "reference_text": "ALBARÁN DE ENTREGA ALMAZARA SIERRA VIVA Fecha 2027-01-03 Nº albarán 310927 Producto ACEITUNA DE MOLINO PESO NETO 2.410 KG",
        },
        {
            "id": "delivery-noisy-004",
            "type": "delivery_ticket",
            "variant": "noisy",
            "expected": {"cooperative_or_mill": "SCA OLIVAR DE MÁGINA", "date": "2027-01-11", "ticket_number": "00011895", "total_kg": 1537},
            "reference_text": "ALBARÁN DE ENTREGA SCA OLIVAR DE MÁGINA Fecha 2027-01-11 Nº albarán 00011895 Producto ACEITUNA DE MOLINO PESO NETO 1.537 KG",
        },
        {
            "id": "yield-clean-001",
            "type": "yield_result",
            "variant": "clean",
            "expected": {"date": "2026-12-15", "ticket_number": "008421", "yield_percent": 21.4, "moisture_percent": 42.8},
            "reference_text": "RESULTADO DE MOLTURACIÓN Fecha resultado 2026-12-15 Albarán relacionado 008421 RENDIMIENTO 21,4 % Humedad 42,8 %",
        },
        {
            "id": "yield-skew-002",
            "type": "yield_result",
            "variant": "skew",
            "expected": {"date": "2026-12-23", "ticket_number": "A-004781", "yield_percent": 18.75, "moisture_percent": 47.2},
            "reference_text": "RESULTADO DE MOLTURACIÓN Fecha resultado 2026-12-23 Albarán relacionado A-004781 RENDIMIENTO 18,75 % Humedad 47,2 %",
        },
        {
            "id": "yield-low-contrast-003",
            "type": "yield_result",
            "variant": "low-contrast",
            "expected": {"date": "2027-01-06", "ticket_number": "310927", "yield_percent": 23.05, "moisture_percent": 39.6},
            "reference_text": "RESULTADO DE MOLTURACIÓN Fecha resultado 2027-01-06 Albarán relacionado 310927 RENDIMIENTO 23,05 % Humedad 39,6 %",
        },
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).parent / "corpus" / "synthetic")
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    cases = build_cases()
    manifest_cases = []

    for case in cases:
        filename = f"{case['id']}.svg"
        svg = delivery_svg(case, case["variant"]) if case["type"] == "delivery_ticket" else yield_svg(case, case["variant"])
        (args.output_dir / filename).write_text(svg, encoding="utf-8")
        manifest_cases.append({
            "id": case["id"],
            "type": case["type"],
            "source": filename,
            "synthetic": True,
            "variant": case["variant"],
            "reference_text": case["reference_text"],
            "expected": case["expected"],
        })

    manifest = {
        "schema_version": 1,
        "description": "Synthetic OCR corpus generated by Mágina Olivo V20. No real personal data.",
        "cases": manifest_cases,
    }
    (args.output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Generated {len(cases)} synthetic OCR cases in {args.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
