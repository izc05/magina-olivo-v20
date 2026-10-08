"""Offline contract tests: run python3 -m unittest discover -s tools/territory -p 'test_*.py'."""
import unittest
import csv
import json
import subprocess
import sys
import tempfile
from pathlib import Path

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


    def test_preserves_leading_zeroes_in_municipality_codes(self):
        rows = [{"code": "01001", "name": "Municipio de prueba"}]
        snapshot = build_snapshot(rows, "code", "name", province="01")
        self.assertEqual(snapshot[0]["code"], "01001")

    def test_empty_name_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "empty/invalid"):
            build_snapshot([{"code": "23009", "name": " "}], "code", "name")

    def test_invalid_province_rejected(self):
        with self.assertRaisesRegex(ValueError, "Province code"):
            build_snapshot([{"code": "23009", "name": "Baeza"}], "code", "name", province="230")

    def test_duplicate_normalized_names_can_have_distinct_official_codes(self):
        rows = [
            {"code": "23001", "name": "Ejemplo"},
            {"code": "23002", "name": "EJEMPLO"},
        ]
        self.assertEqual(len(build_snapshot(rows, "code", "name")), 2)


    def test_cli_generates_offline_snapshot_and_rejects_partial_export(self):
        script = Path(__file__).with_name("build_municipalities.py")
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "source.csv"
            output = Path(tmp) / "jaen.json"
            with source.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=["code", "name"])
                writer.writeheader()
                writer.writerow({"code": "23092", "name": "Úbeda"})
                writer.writerow({"code": "23009", "name": "Baeza"})
            cmd = [sys.executable, str(script), "--input", str(source),
                   "--output", str(output), "--code-column", "code",
                   "--name-column", "name", "--province", "23",
                   "--source-url", "https://example.org/test-only",
                   "--source-date", "2026-10-08", "--expected-count", "2"]
            passed = subprocess.run(cmd, capture_output=True, text=True)
            self.assertEqual(passed.returncode, 0, passed.stderr)
            data = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual([m["code"] for m in data["municipalities"]], ["23009", "23092"])
            self.assertEqual(len(data["sourceSha256"]), 64)
            output.unlink()
            mismatch = subprocess.run(cmd + ["--source-sha256", "0" * 64], capture_output=True, text=True)
            self.assertNotEqual(mismatch.returncode, 0)
            self.assertIn("SHA256 mismatch", mismatch.stderr)
            self.assertFalse(output.exists())
            cmd[-1] = "3"
            rejected = subprocess.run(cmd, capture_output=True, text=True)
            self.assertNotEqual(rejected.returncode, 0)
            self.assertIn("Coverage mismatch", rejected.stderr)
            self.assertFalse(output.exists())

    def test_cli_rejects_impossible_source_date(self):
        script = Path(__file__).with_name("build_municipalities.py")
        result = subprocess.run([sys.executable, str(script), "--input", "unused.csv",
            "--output", "unused.json", "--code-column", "code",
            "--name-column", "name", "--source-url", "https://example.org/test-only",
            "--source-date", "2026-02-30", "--expected-count", "1"],
            capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("real calendar date", result.stderr)


if __name__ == "__main__":
    unittest.main()
