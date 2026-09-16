#!/usr/bin/env python3
import argparse
import html.parser
import json
import pathlib
import re
import urllib.parse
import urllib.request
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE_URL = 'https://www.fedamon.com/index.php/home-6/2013-04-16-17-25-48/11-federacion/186-gr7-jaen'


class AnchorParser(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self._href = None
        self._text = []

    def handle_starttag(self, tag, attrs):
        if tag.lower() == 'a':
            self._href = dict(attrs).get('href')
            self._text = []

    def handle_data(self, data):
        if self._href is not None:
            self._text.append(data)

    def handle_endtag(self, tag):
        if tag.lower() == 'a' and self._href is not None:
            self.links.append({'href': self._href, 'text': ' '.join(''.join(self._text).split())})
            self._href = None
            self._text = []


def fetch(url, timeout=25):
    request = urllib.request.Request(url, headers={
        'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'es-ES,es;q=0.9,en;q=0.7',
    })
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read(), response.headers.get('Content-Type'), response.geturl(), response.status


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write', default='data/routes/sierra-magina-gr7-stage-track-discovery.json')
    parser.add_argument('--timeout', type=int, default=25)
    args = parser.parse_args()

    payload, content_type, final_url, status = fetch(SOURCE_URL, args.timeout)
    text = payload.decode('utf-8', errors='replace')
    anchor_parser = AnchorParser()
    anchor_parser.feed(text)

    candidates = []
    for index, link in enumerate(anchor_parser.links):
        href = link.get('href') or ''
        anchor_text = link.get('text') or ''
        absolute = urllib.parse.urljoin(final_url, href)
        haystack = f'{href} {anchor_text}'.lower()
        if any(token in haystack for token in ['descargar', 'download', 'track', 'gpx', 'kmz', 'kml', 'gr7', 'gr-7', 'etapa']):
            candidates.append({
                'index': index,
                'text': anchor_text,
                'href': href,
                'absolute_url': absolute,
            })

    stage_names = {
        '08': ['Jódar', 'Bédmar'],
        '09': ['Bédmar', 'Albánchez de Mágina'],
        '10': ['Albánchez de Mágina', 'Torres'],
        '11': ['Torres', 'Cambil'],
        '12': ['Cambil', 'Carchalejo'],
    }

    report = {
        'version': 1,
        'generated_at': datetime.now(timezone.utc).isoformat(),
        'source_url': SOURCE_URL,
        'http_status': status,
        'content_type': content_type,
        'final_url': final_url,
        'source_size_bytes': len(payload),
        'target_stages': stage_names,
        'candidate_links': candidates,
        'notes': 'Candidate links are extracted from the official FAM GR-7 Jaen page. No filename pattern is inferred; only published href values are retained.'
    }

    output = ROOT / args.write
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(f'Found {len(candidates)} candidate links')
    for row in candidates:
        print(f"{row['index']}: {row['text']} -> {row['absolute_url']}")
    print(f'Wrote {args.write}')


if __name__ == '__main__':
    main()
