#!/usr/bin/env python3
"""
Phase 17 — Spain Catastro technical spike (research only, never shipped in the app).

Talks only to the official DG Catastro INSPIRE WFS (docs/03-maps/CADASTRE-CONTRACT-RC1.md §2).
No scraping, no invented references: parcels are discovered with a small BBOX query around
real olive-growing points, then re-read by their own cadastral reference.

Checks, per parcel:
  - GetParcel by reference returns exactly that reference;
  - geometry arrives as GML 3.2 polygons (exterior + interior rings, multipart);
  - EPSG:4326 output axis order (lat,lon vs lon,lat);
  - EPSG:25830 output converted to WGS84 matches the EPSG:4326 output (max deviation, m);
  - shoelace area (UTM) vs the provider's areaValue;
  - error modes: malformed reference, unknown reference, oversize BBOX.

Writes raw GML and a JSON summary to spikes/catastro/out/ and prints the summary.
"""
import json
import math
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

from pyproj import Transformer

WFS = "https://ovc.catastro.meh.es/INSPIRE/wfsCP.aspx"
OUT = os.path.join(os.path.dirname(__file__), "out")
NS = {
    "gml": "http://www.opengis.net/gml/3.2",
    "cp": "http://inspire.ec.europa.eu/schemas/cp/4.0",
    "wfs": "http://www.opengis.net/wfs/2.0",
}
# Olive-growing points in Sierra Mágina (Jaén), WGS84 lat/lon. Only used to discover
# real parcels nearby; no reference is typed by hand.
POINTS = {
    "huelma-olivar": (37.6360, -3.4800),
    "bedmar-olivar": (37.8300, -3.4200),
    "jimena-olivar": (37.8250, -3.4900),
}
HALF_SIDE_M = 120  # 240 m square: far below the documented 1 km² limit
TO_UTM = Transformer.from_crs("EPSG:4326", "EPSG:25830", always_xy=True)
TO_WGS = Transformer.from_crs("EPSG:25830", "EPSG:4326", always_xy=True)


def get(params, name):
    url = WFS + "?" + urllib.parse.urlencode(params)
    started = time.time()
    try:
        with urllib.request.urlopen(url, timeout=60) as response:
            body = response.read()
            status = response.status
    except urllib.error.HTTPError as error:
        body, status = error.read(), error.code
    except Exception as error:  # network/timeout: recorded, never hidden
        return {"url": url, "status": None, "error": repr(error), "ms": int((time.time() - started) * 1000)}, b""
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name), "wb") as handle:
        handle.write(body)
    return {"url": url, "status": status, "bytes": len(body), "ms": int((time.time() - started) * 1000)}, body


def parcels(body):
    """(reference, areaValue, [polygons]) where a polygon is [exterior, *interiors] of (a, b) pairs."""
    root = ET.fromstring(body)
    found = []
    for parcel in root.iter("{%s}CadastralParcel" % NS["cp"]):
        ref = parcel.findtext("cp:nationalCadastralReference", default=None, namespaces=NS)
        area = parcel.findtext("cp:areaValue", default=None, namespaces=NS)
        polygons = []
        # The official WFS currently returns MultiSurface/Surface/PolygonPatch.
        # Keep Polygon as a fallback for other valid GML 3.2 responses.
        for polygon in parcel.iter():
            if polygon.tag not in ("{%s}PolygonPatch" % NS["gml"], "{%s}Polygon" % NS["gml"]):
                continue
            rings = []
            for boundary in polygon:
                if boundary.tag not in ("{%s}exterior" % NS["gml"], "{%s}interior" % NS["gml"]):
                    continue
                positions = boundary.find(".//gml:posList", NS)
                if positions is None or not positions.text:
                    continue
                values = [float(v) for v in positions.text.split()]
                if len(values) < 8 or len(values) % 2:
                    continue
                rings.append(list(zip(values[0::2], values[1::2])))
            if rings:
                polygons.append(rings)
        found.append((ref, float(area) if area else None, polygons))
    return found


def exception_text(body):
    try:
        root = ET.fromstring(body)
        return " ".join(t.strip() for t in root.itertext() if t.strip())[:300]
    except Exception:
        return body[:300].decode("utf-8", "replace")


def shoelace(ring):
    return abs(sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in zip(ring, ring[1:] + ring[:1]))) / 2


def utm_area(polygons):
    return sum(shoelace(rings[0]) - sum(shoelace(hole) for hole in rings[1:]) for rings in polygons)


def metres(lat1, lon1, lat2, lon2):
    k = 111_320.0
    return math.hypot((lat1 - lat2) * k, (lon1 - lon2) * k * math.cos(math.radians(lat1)))


def main():
    summary = {"service": WFS, "points": {}, "errors": {}}
    checked = 0
    for name, (lat, lon) in POINTS.items():
        x, y = TO_UTM.transform(lon, lat)
        bbox = f"{x - HALF_SIDE_M:.0f},{y - HALF_SIDE_M:.0f},{x + HALF_SIDE_M:.0f},{y + HALF_SIDE_M:.0f}"
        meta, body = get({"service": "wfs", "version": "2.0.0", "request": "GetFeature",
                          "typenames": "CP:CadastralParcel", "srsname": "EPSG::25830", "bbox": bbox},
                         f"{name}-bbox.gml")
        entry = {"bbox_request": meta}
        try:
            found = parcels(body) if body else []
        except ET.ParseError as error:
            entry["parse_error"] = repr(error)
            entry["body"] = exception_text(body)
            summary["points"][name] = entry
            continue
        entry["parcels_in_bbox"] = len(found)
        entry["samples"] = []
        for ref, _, _ in found[:2]:
            sample = {"reference": ref}
            m25830, b25830 = get({"service": "wfs", "version": "2.0.0", "request": "GetFeature",
                                  "STOREDQUERIE_ID": "GetParcel", "refcat": ref, "srsname": "EPSG::25830"},
                                 f"{ref}-25830.gml")
            m4326, b4326 = get({"service": "wfs", "version": "2.0.0", "request": "GetFeature",
                                "STOREDQUERIE_ID": "GetParcel", "refcat": ref, "srsname": "EPSG::4326"},
                               f"{ref}-4326.gml")
            sample["ms"] = {"25830": m25830.get("ms"), "4326": m4326.get("ms")}
            try:
                p_utm = parcels(b25830)
                p_geo = parcels(b4326)
            except ET.ParseError as error:
                sample["parse_error"] = repr(error)
                entry["samples"].append(sample)
                continue
            sample["returned_refs"] = [p[0] for p in p_utm]
            if not p_utm or not p_geo or not p_utm[0][2] or not p_geo[0][2]:
                sample["geometry_error"] = "No polygon geometry in one of the WFS responses"
                entry["samples"].append(sample)
                continue
            ref_utm, area_value, polys_utm = p_utm[0]
            _, _, polys_geo = p_geo[0]
            sample["polygons"] = len(polys_utm)
            sample["rings"] = [len(rings) for rings in polys_utm]
            sample["vertices"] = sum(len(r) for rings in polys_utm for r in rings)
            sample["area_value_m2"] = area_value
            sample["utm_shoelace_m2"] = round(utm_area(polys_utm), 1)
            # Axis order of the 4326 output: Spain is lat≈36-44, lon≈-10..5.
            a, b = polys_geo[0][0][0]
            order = "lat,lon" if 35 < a < 45 and -11 < b < 6 else ("lon,lat" if 35 < b < 45 else "unknown")
            sample["epsg4326_axis_order"] = order
            worst = 0.0
            for rings_u, rings_g in zip(polys_utm, polys_geo):
                for ring_u, ring_g in zip(rings_u, rings_g):
                    for (ux, uy), (ga, gb) in zip(ring_u, ring_g):
                        glat, glon = (ga, gb) if order == "lat,lon" else (gb, ga)
                        tlon, tlat = TO_WGS.transform(ux, uy)
                        worst = max(worst, metres(tlat, tlon, glat, glon))
            sample["max_deviation_utm_vs_4326_m"] = round(worst, 3)
            entry["samples"].append(sample)
            checked += 1
        summary["points"][name] = entry

    for label, refcat in {"malformed": "ABC", "unknown": "0000000XX0000X"}.items():
        meta, body = get({"service": "wfs", "version": "2.0.0", "request": "GetFeature",
                          "STOREDQUERIE_ID": "GetParcel", "refcat": refcat, "srsname": "EPSG::25830"},
                         f"error-{label}.gml")
        try:
            meta["parcels"] = len(parcels(body)) if body else 0
        except ET.ParseError:
            meta["parcels"] = 0
        meta["message"] = exception_text(body) if body else None
        summary["errors"][label] = meta
    lat, lon = POINTS["huelma-olivar"]
    x, y = TO_UTM.transform(lon, lat)
    meta, body = get({"service": "wfs", "version": "2.0.0", "request": "GetFeature",
                      "typenames": "CP:CadastralParcel", "srsname": "EPSG::25830",
                      "bbox": f"{x - 1500:.0f},{y - 1500:.0f},{x + 1500:.0f},{y + 1500:.0f}"},
                     "error-oversize-bbox.gml")
    meta["message"] = exception_text(body) if body else None
    summary["errors"]["oversize_bbox_9km2"] = meta
    summary["parcels_checked"] = checked

    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "summary.json"), "w") as handle:
        json.dump(summary, handle, indent=2, ensure_ascii=False)
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    return 0 if checked > 0 else 1


if __name__ == "__main__":
    sys.exit(main())
