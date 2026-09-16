#!/usr/bin/env python3
import argparse
import hashlib
import json
import math
import pathlib
import urllib.request
import xml.etree.ElementTree as ET

REGISTRY = pathlib.Path('data/routes/sierra-magina-municipal-track-sources.json')
USER_AGENT = 'Mozilla/5.0 (compatible; MaginaOlivoRouteAudit/1.0)'


def haversine_m(a, b):
    lon1, lat1 = a
    lon2, lat2 = b
    radius = 6371008.8
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    h = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(h))


def parse_coordinate_text(text):
    points = []
    for token in (text or '').replace('\n', ' ').replace('\t', ' ').split():
        parts = token.split(',')
        if len(parts) < 2:
            continue
        try:
            lon = float(parts[0])
            lat = float(parts[1])
        except ValueError:
            continue
        if -180 <= lon <= 180 and -90 <= lat <= 90:
            points.append((lon, lat))
    return points


def parse_kml(data):
    root = ET.fromstring(data)
    sequences = []
    for element in root.iter():
        if element.tag.endswith('coordinates'):
            points = parse_coordinate_text(element.text)
            if len(points) >= 2:
                sequences.append(points)
    length_m = 0.0
    point_count = 0
    for sequence in sequences:
        point_count += len(sequence)
        length_m += sum(haversine_m(sequence[i - 1], sequence[i]) for i in range(1, len(sequence)))
    return {
        'line_sequence_count': len(sequences),
        'point_count': point_count,
        'calculated_length_m': round(length_m),
        'geometry_valid': bool(sequences and point_count >= 2 and length_m > 0),
    }


def fetch(url):
    request = urllib.request.Request(url, headers={
        'User-Agent': USER_AGENT,
        'Accept': 'application/vnd.google-earth.kml+xml,application/xml,text/xml,*/*',
    })
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.status, response.headers.get('Content-Type'), response.read()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write', required=True)
    args = parser.parse_args()

    registry = json.loads(REGISTRY.read_text(encoding='utf-8'))
    rows = []
    for source in registry.get('sources', []):
        row = {
            'slug': source.get('slug'),
            'municipality': source.get('municipality'),
            'authority': source.get('authority'),
            'source_page_url': source.get('source_page_url'),
            'artifact_url': source.get('artifact_url'),
            'artifact_format': source.get('artifact_format'),
        }
        try:
            status, content_type, data = fetch(source['artifact_url'])
            row['http_status'] = status
            row['content_type'] = content_type
            row['byte_length'] = len(data)
            row['sha256'] = hashlib.sha256(data).hexdigest()
            parsed = parse_kml(data)
            row.update(parsed)
            row['fetch_status'] = 'success'
            row['validation_status'] = 'geometry_validated' if parsed['geometry_valid'] else 'geometry_invalid'
        except Exception as exc:
            row['fetch_status'] = 'failed'
            row['validation_status'] = 'pending_external_fetch'
            row['error'] = f'{type(exc).__name__}: {exc}'
        rows.append(row)

    validated = sum(row.get('validation_status') == 'geometry_validated' for row in rows)
    fetched = sum(row.get('fetch_status') == 'success' for row in rows)
    report = {
        'audit_version': 1,
        'source_registry': str(REGISTRY),
        'summary': {
            'targets': len(rows),
            'fetch_success': fetched,
            'geometry_validated': validated,
            'fetch_failed': len(rows) - fetched,
        },
        'routes': rows,
        'safety_note': 'Geometry validation confirms the downloaded line artifact is parseable and measurable. It does not establish current trail safety or operational opening.',
    }
    output = pathlib.Path(args.write)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report['summary'], ensure_ascii=False))
    for row in rows:
        print(row['slug'], row.get('fetch_status'), row.get('point_count'), row.get('calculated_length_m'), row.get('validation_status'))


if __name__ == '__main__':
    main()
