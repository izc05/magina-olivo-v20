#!/usr/bin/env python3
import argparse
import hashlib
import io
import json
import math
import pathlib
import sys
import urllib.request
import zipfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]


def read_json(relative):
    return json.loads((ROOT / relative).read_text(encoding='utf-8'))


def local_name(tag):
    return tag.split('}', 1)[-1].split(':')[-1]


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


def line_length(points):
    return sum(haversine(points[i - 1], points[i]) for i in range(1, len(points)))


def parse_kml_lines(text):
    root = ET.fromstring(text)
    lines = []
    for element in root.iter():
        if local_name(element.tag) != 'LineString':
            continue
        for child in element.iter():
            if local_name(child.tag) != 'coordinates' or not child.text:
                continue
            points = []
            for token in child.text.strip().split():
                parts = token.split(',')
                if len(parts) < 2:
                    continue
                try:
                    lon, lat = float(parts[0]), float(parts[1])
                except ValueError:
                    continue
                if -180 <= lon <= 180 and -90 <= lat <= 90:
                    points.append((lon, lat))
            if len(points) >= 2:
                lines.append(points)
    return lines


def download(url, referer=None, timeout=25):
    headers = {
        'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36',
        'Accept': 'application/vnd.google-earth.kmz,application/vnd.google-earth.kml+xml,application/zip,*/*;q=0.8',
        'Accept-Language': 'es-ES,es;q=0.9,en;q=0.7',
    }
    if referer:
        headers['Referer'] = referer
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read(), response.headers.get('Content-Type'), response.geturl(), response.status


def extract_kml(payload):
    bio = io.BytesIO(payload)
    if zipfile.is_zipfile(bio):
        with zipfile.ZipFile(bio) as archive:
            names = [name for name in archive.namelist() if name.lower().endswith('.kml')]
            if not names:
                raise ValueError('KMZ archive contains no KML file')
            name = names[0]
            return archive.read(name).decode('utf-8', errors='replace'), name, True
    text = payload.decode('utf-8', errors='replace')
    if '<kml' not in text.lower():
        raise ValueError('Downloaded payload is neither KMZ nor recognizable KML')
    return text, None, False


def collect_targets():
    intermodal = read_json('data/routes/sierra-magina-intermodal-hiking.json')
    homologated = read_json('data/routes/sierra-magina-homologated-trails.json')
    targets = []
    for route in intermodal.get('routes', []):
        url = route.get('track_url')
        if not url:
            continue
        targets.append({
            'id': route['code'],
            'layer': 'provincial',
            'name': route['name'],
            'url': url,
            'referer': route.get('source_url'),
            'expected_distance_m': route.get('distance_m'),
            'route_shape': route.get('route_shape'),
        })
    for route in homologated.get('routes', []):
        url = route.get('track_kmz_url')
        if not url:
            continue
        expected = route.get('distance_km_one_way')
        targets.append({
            'id': route['code'],
            'layer': 'federative',
            'name': route['name'],
            'url': url,
            'referer': route.get('source_url'),
            'expected_distance_m': round(expected * 1000) if isinstance(expected, (int, float)) else None,
            'route_shape': 'linear',
            'canonical_route_slug': route.get('canonical_route_slug'),
        })
    return targets


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write', default=None)
    parser.add_argument('--timeout', type=int, default=25)
    args = parser.parse_args()

    results = []
    for target in collect_targets():
        result = {
            **target,
            'fetch_status': 'pending',
            'geometry_validated': False,
        }
        try:
            payload, content_type, final_url, status = download(target['url'], target.get('referer'), args.timeout)
            kml, member, was_zip = extract_kml(payload)
            lines = parse_kml_lines(kml)
            points = sum(len(line) for line in lines)
            length = round(sum(line_length(line) for line in lines))
            expected = target.get('expected_distance_m')
            ratio = (length / expected) if expected and length else None
            plausible = None if ratio is None else 0.75 <= ratio <= 2.25
            result.update({
                'fetch_status': 'success',
                'http_status': status,
                'final_url': final_url,
                'content_type': content_type,
                'download_size_bytes': len(payload),
                'download_sha256': hashlib.sha256(payload).hexdigest(),
                'kmz_archive': was_zip,
                'kml_member': member,
                'line_parts': len(lines),
                'point_count': points,
                'calculated_length_m': length,
                'length_ratio_to_sheet': round(ratio, 4) if ratio is not None else None,
                'length_plausible_for_review': plausible,
                'geometry_non_empty': bool(lines and points >= 2 and length > 0),
            })
            result['geometry_validated'] = bool(result['geometry_non_empty'] and plausible is not False)
            result['validation_status'] = 'machine_checks_passed_needs_evidence_review' if result['geometry_validated'] else 'manual_review_required'
        except Exception as exc:
            result['fetch_status'] = 'error'
            result['error'] = f'{type(exc).__name__}: {exc}'[:500]
            result['validation_status'] = 'fetch_failed'

        results.append(result)
        suffix = f" · {result.get('calculated_length_m')} m" if result.get('calculated_length_m') else ''
        print(f"{target['id']}: {result['fetch_status']}{suffix} · {result['validation_status']}")

    report = {
        'version': 1,
        'generated_at': __import__('datetime').datetime.now(__import__('datetime').timezone.utc).isoformat(),
        'scope': 'Official provincial and federation KMZ geometry audit for Sierra Magina',
        'methodology': {
            'download': 'official track URL with browser-like user agent and source-page referer',
            'geometry': 'KML LineString coordinates extracted from KMZ/KML payload',
            'length': 'WGS84 haversine sum in meters',
            'auto_validation_limit': 'machine checks only; catalog track_validated remains unchanged until evidence review',
            'plausibility_ratio_range': [0.75, 2.25],
        },
        'summary': {
            'targets': len(results),
            'fetch_success': sum(row['fetch_status'] == 'success' for row in results),
            'fetch_failed': sum(row['fetch_status'] == 'error' for row in results),
            'machine_checks_passed': sum(row.get('geometry_validated') is True for row in results),
        },
        'routes': results,
    }

    output = json.dumps(report, indent=2, ensure_ascii=False) + '\n'
    if args.write:
        path = ROOT / args.write
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(output, encoding='utf-8')
        print(f'Wrote {args.write}')
    else:
        print(json.dumps(report['summary'], indent=2))

    if report['summary']['fetch_failed']:
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
