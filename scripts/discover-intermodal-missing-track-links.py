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
ROUTES = {
    'R4': 'https://www.jaenparaisointerior.es/es/w/sm/senderismo/r4',
    'R5': 'https://www.jaenparaisointerior.es/es/w/sm/senderismo/r5',
    'R8': 'https://www.jaenparaisointerior.es/es/w/sm/senderismo/r8',
}

class Parser(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self.current = None
        self.text = []

    def handle_starttag(self, tag, attrs):
        if tag.lower() == 'a':
            self.current = dict(attrs).get('href')
            self.text = []

    def handle_data(self, data):
        if self.current is not None:
            self.text.append(data)

    def handle_endtag(self, tag):
        if tag.lower() == 'a' and self.current is not None:
            self.links.append({'href': self.current, 'text': ' '.join(''.join(self.text).split())})
            self.current = None
            self.text = []


def fetch(url, timeout=25):
    req = urllib.request.Request(url, headers={
        'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'es-ES,es;q=0.9,en;q=0.7',
    })
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return response.read(), response.status, response.headers.get('Content-Type'), response.geturl()


def score_link(href, text):
    haystack = f'{href} {text}'.lower()
    score = 0
    reasons = []
    for token, weight in [
        ('kmz', 10), ('gpx', 10), ('kml', 10), ('track', 8), ('trazado', 8),
        ('descarga', 5), ('download', 5), ('document', 3), ('mapa', 3), ('ruta', 1),
    ]:
        if token in haystack:
            score += weight
            reasons.append(token)
    if re.search(r'\.(kmz|gpx|kml|zip|rar)(?:$|[?#])', href, re.I):
        score += 20
        reasons.append('track_extension')
    return score, sorted(set(reasons))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write', default='data/routes/sierra-magina-r4-r5-r8-link-discovery.json')
    parser.add_argument('--timeout', type=int, default=25)
    args = parser.parse_args()
    rows = []

    for code, url in ROUTES.items():
        row = {'code': code, 'source_url': url, 'fetch_status': 'pending'}
        try:
            payload, status, content_type, final_url = fetch(url, args.timeout)
            p = Parser(); p.feed(payload.decode('utf-8', errors='replace'))
            links = []
            for index, link in enumerate(p.links):
                href = link.get('href') or ''
                absolute = urllib.parse.urljoin(final_url, href)
                score, reasons = score_link(href, link.get('text') or '')
                if score > 0:
                    links.append({
                        'index': index,
                        'text': link.get('text') or '',
                        'href': href,
                        'absolute_url': absolute,
                        'score': score,
                        'reasons': reasons,
                    })
            links.sort(key=lambda item: (-item['score'], item['index']))
            row.update({
                'fetch_status': 'success',
                'http_status': status,
                'content_type': content_type,
                'final_url': final_url,
                'page_size_bytes': len(payload),
                'links': links,
                'explicit_track_links': [item for item in links if item['score'] >= 8],
            })
        except Exception as exc:
            row['fetch_status'] = 'error'
            row['error'] = f'{type(exc).__name__}: {exc}'[:600]
        rows.append(row)
        print(code, row['fetch_status'], 'candidates', len(row.get('links', [])), 'explicit', len(row.get('explicit_track_links', [])))
        for item in row.get('explicit_track_links', [])[:20]:
            print(' ', item['score'], item['text'], '->', item['absolute_url'])

    report = {
        'version': 1,
        'generated_at': datetime.now(timezone.utc).isoformat(),
        'scope': 'Published-link discovery for provincial hiking routes R4/R5/R8',
        'policy': 'No URL pattern inference. Only href values actually present in the official page HTML are retained.',
        'routes': rows,
    }
    output = ROOT / args.write
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print('Wrote', args.write)

if __name__ == '__main__':
    main()
