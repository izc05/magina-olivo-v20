#!/usr/bin/env python3
from extract_fields import extract_fields


def assert_ticket(label: str) -> None:
    text = f"ALBARÁN DE ENTREGA\nSCA TEST\nFecha 2026-12-12\n{label} 008421\nPESO NETO 1.842 KG"
    fields = extract_fields(text, "delivery_ticket")
    assert fields.get("ticket_number") == "008421", (label, fields)


def main() -> int:
    for label in ["Nº albarán", "N° albarán", "No albarán", "Ne albarán", "N0 albarán", "N albarán"]:
        assert_ticket(label)

    noisy = "| ALBARÁN DE ENTREGA |\n| SCA OLIVAR DE MÁGINA |\n| Fecha 2027-01-11 |\n| Ne albarán 00011895\n| PESO NETO 1.537 KG |"
    fields = extract_fields(noisy, "delivery_ticket")
    assert fields["cooperative_or_mill"] == "SCA OLIVAR DE MAGINA", fields
    assert fields["date"] == "2027-01-11", fields
    assert fields["ticket_number"] == "00011895", fields
    assert fields["total_kg"] == 1537, fields

    print("OCR benchmark field parser regression checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
