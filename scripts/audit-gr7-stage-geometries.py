#!/usr/bin/env python3
import argparse
import hashlib
import json
import math
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'data/routes/sierra-magina-gr7-stage-track-sources.json'


def haversine(a, b):
    lon1, lat1 = a
    lon2, lat2 = b
    r = 6371008.8
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    h = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return 2 * r * math.asin(min(1.0, math.sqrt(h)))


def length(points):
    return sum(haversine(points[i - 1], points[i]) for i in range(1, len(points)))


def lname(tag):
    return tag.split('}', 1)[-1].split(':')[-1]


def parse_gpx(path):
    root = ET.parse(path).getroot()
    lines = []
    current = []
    for elem in root.iter():
        name = lname(elem.tag)
        if name in ('trkseg', 'rte'):
            pts = []
            for child in elem.iter():
                child_name = lname(child.tag)
                if child_name in ('trkpt', 'rtept'):
                    try:
                        lat = float(child.attrib['lat']); lon = float(child.attrib['lon'])
                    except Exception:
                        continue
                    if -90 <= lat <= 90 and -180 <= lon <= 180:
                        pts.append((lon, lat))
            if len(pts) >= 2:
                lines.append(pts)
    return lines


def parse_kml(path):
    text = path.read_text(encoding='utf-8', errors='replace')
    lines = []
    for match in re.finditer(r'<(?:[A-Za-z0-9_-]+:)?coordinates(?:\s[^>]*)?>([\s\S]*?)</(?:[A-Za-z0-9_-]+:)?coordinates>', text, re.I):
        pts = []
        for token in match.group(1).strip().split():
            parts = token.split(',')
            if len(parts) < 2:
                continue
            try:
                lon, lat = float(parts[0]), float(parts[1])
            except ValueError:
                continue
            if -180 <= lon <= 180 and -90 <= lat <= 90:
                pts.append((lon, lat))
        if len(pts) >= 2:
            lines.append(pts)
    return lines


def parse_plt(path):
    lines = []
    pts = []
    for raw in path.read_text(encoding='latin-1', errors='replace').splitlines():
        parts = [part.strip() for part in raw.split(',')]
        if len(parts) < 2:
            continue
        try:
            lat = float(parts[0]); lon = float(parts[1])
        except ValueError:
            continue
        if -90 <= lat <= 90 and -180 <= lon <= 180:
            pts.append((lon, lat))
    if len(pts) >= 2:
        lines.append(pts)
    return lines


def parse_track(path):
    suffix = path.suffix.lower()
    if suffix == '.gpx':
        return parse_gpx(path), 'gpx'
    if suffix in ('.kml', '.xml'):
        return parse_kml(path), 'kml'
    if suffix == '.plt':
        return parse_plt(path), 'ozi_plt'
    return [], None


def download(url, referer, timeout):
    req = urllib.request.Request(url, headers={
        'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36',
        'Referer': referer,
        'Accept': 'application/octet-stream,application/x-rar-compressed,*/*;q=0.8',
        'Accept-Language': 'es-ES,es;q=0.9,en;q=0.7',
    })
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return response.read(), response.status, response.headers.get('Content-Type'), response.geturl()


def ensure_7z():
    exe = shutil.which('7z') or shutil.which('7zz')
    if not exe:
        raise RuntimeError('7z/7zz not found; install p7zip-full or 7zip in the workflow')
    return exe


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write', default='data/routes/sierra-magina-gr7-stage-geometry-audit.json')
    parser.add_argument('--timeout', type=int, default=30)
    args = parser.parse_args()

    source = json.loads(SOURCE.read_text(encoding='utf-8'))
    sevenzip = ensure_7z()
    results = []

    with tempfile.TemporaryDirectory(prefix='gr7-audit-') as tmp:
        tmpdir = pathlib.Path(tmp)
        for stage in source.get('stages', []):
            row = {
                'stage': stage['stage'], 'from': stage['from'], 'to': stage['to'],
                'archive_url': stage['archive_url'], 'official_distance_m': round(stage['official_distance_km'] * 1000),
                'fetch_status': 'pending', 'geometry_validated': False,
            }
            try:
                payload, status, content_type, final_url = download(stage['archive_url'], source['source_page'], args.timeout)
                archive = tmpdir / f"stage-{stage['stage']}.rar"
                archive.write_bytes(payload)
                out = tmpdir / f"stage-{stage['stage']}-out"
                out.mkdir(parents=True, exist_ok=True)
                proc = subprocess.run([sevenzip, 'x', '-y', f'-o{out}', str(archive)], capture_output=True, text=True, timeout=60)
                if proc.returncode != 0:
                    raise RuntimeError(f'7z failed rc={proc.returncode}: {(proc.stderr or proc.stdout)[-500:]}')

                files = [p for p in out.rglob('*') if p.is_file()]
                parsed = []
                for file in files:
                    try:
                        geometries, fmt = parse_track(file)
                    except Exception:
                        geometries, fmt = [], None
                    if not geometries:
                        continue
                    point_count = sum(len(line) for line in geometries)
                    calc = round(sum(length(line) for line in geometries))
                    parsed.append({
                        'file_name': str(file.relative_to(out)), 'format': fmt,
                        'line_parts': len(geometries), 'point_count': point_count,
                        'calculated_length_m': calc,
                        'file_sha256': hashlib.sha256(file.read_bytes()).hexdigest(),
                    })

                if not parsed:
                    raise RuntimeError(f'Archive extracted {len(files)} files but no supported non-empty track geometry was parsed')

                # Prefer the parsed candidate whose length is closest to the published stage distance.
                expected = row['official_distance_m']
                best = min(parsed, key=lambda item: abs(item['calculated_length_m'] - expected))
                ratio = best['calculated_length_m'] / expected if expected else None
                plausible = ratio is not None and 0.75 <= ratio <= 1.25
                row.update({
                    'fetch_status': 'success', 'http_status': status, 'content_type': content_type,
                    'final_url': final_url, 'archive_size_bytes': len(payload),
                    'archive_sha256': hashlib.sha256(payload).hexdigest(),
                    'extracted_files': [str(p.relative_to(out)) for p in files],
                    'parsed_candidates': parsed, 'selected_track': best,
                    'length_ratio_to_official': round(ratio, 4) if ratio is not None else None,
                    'length_plausible_for_review': plausible,
                    'geometry_validated': bool(best['point_count'] >= 2 and best['calculated_length_m'] > 0 and plausible),
                })
                row['validation_status'] = 'machine_checks_passed_needs_evidence_review' if row['geometry_validated'] else 'manual_review_required'
            except Exception as exc:
                row['fetch_status'] = 'error'; row['validation_status'] = 'fetch_failed'
                row['error'] = f'{type(exc).__name__}: {exc}'[:700]
            results.append(row)
            selected = row.get('selected_track') or {}
            print(f"Stage {row['stage']}: {row['fetch_status']} · {selected.get('calculated_length_m')} m · {row['validation_status']}")

    report = {
        'version': 1,
        'generated_at': datetime.now(timezone.utc).isoformat(),
        'authority': source['authority'], 'source_page': source['source_page'],
        'scope': source['scope'],
        'methodology': {
            'source_urls': 'published hrefs extracted from official FAM GR-7 Jaen page',
            'archive': 'RAR extracted with 7z; archive and selected track SHA-256 recorded',
            'formats': ['GPX', 'KML/XML coordinates', 'OziExplorer PLT'],
            'length': 'WGS84 haversine segment sum in metres',
            'selection': 'supported non-empty track candidate closest to official stage distance',
            'automatic_ratio_window': [0.75, 1.25],
            'safety': 'geometry validation never clears FAM maintenance/signage warnings',
        },
        'summary': {
            'stages_expected': len(source.get('stages', [])),
            'fetch_success': sum(r['fetch_status'] == 'success' for r in results),
            'fetch_failed': sum(r['fetch_status'] == 'error' for r in results),
            'machine_checks_passed': sum(r.get('geometry_validated') is True for r in results),
        },
        'stages': results,
    }
    output = ROOT / args.write
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps(report['summary'], indent=2))
    print(f'Wrote {args.write}')
    return 2 if report['summary']['fetch_failed'] else 0


if __name__ == '__main__':
    sys.exit(main())
