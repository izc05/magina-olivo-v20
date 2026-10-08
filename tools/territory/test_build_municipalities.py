"""Offline contract tests: run python3 -m unittest discover -s tools/territory -p 'test_*.py'."""
import unittest

from build_municipalities import build_snapshot, search_name


class MunicipalitySnapshotTests(unittest.TestCase):
    def test_accent_insensitive_search(self):
        self.assertEqual(search_name("  Úbeda  "), "ubeda")
        self.assertEqual(search_name("Alcalá la Real"), "alcala la real")

    def test_province_filter_and_stable_order(self):
        rows = [
            {"code": "23092", "name": "Úbeda"},
            {"code": "18087", "name": "Granada"},
            {"code": "23009", "name": "Baeza"},
        ]
        snapshot = build_snapshot(rows, "code", "name")
        self.assertEqual([r["code"] for r in snapshot], ["23009", "23092"])
        self.assertEqual(snapshot[1]["searchName"], "ubeda")

    def test_duplicate_codes_rejected(self):
        with self.assertRaisesRegex(ValueError, "duplicate"):
            build_snapshot([{"code": "23009", "name": "Baeza"}] * 2, "code", "name")

    def test_invalid_code_rejected(self):
        with self.assertRaisesRegex(ValueError, "five-digit"):
            build_snapshot([{"code": "239", "name": "Ejemplo"}], "code", "name")

    def test_empty_province_rejected(self):
        with self.assertRaisesRegex(ValueError, "No municipalities"):
            build_snapshot([{"code": "18087", "name": "Granada"}], "code", "name")

    def test_does_not_assume_population_place_is_municipality(self):
        # A locality/nucleus must not be assigned a fabricated municipality code.
        with self.assertRaisesRegex(ValueError, "five-digit"):
            build_snapshot([{"code": "", "name": "Garcíez"}], "code", "name")


if __name__ == "__main__":
    unittest.main()
