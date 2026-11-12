#!/usr/bin/env python3
"""Builds tools/demo-route.gpx from the "route" points in app/src/main/assets/demo_trip.json.

The GPX uses the same coordinates as the in-app route panel. Points are interpolated about every
200 m and timestamped at constant speed over the trip's remainingMinutes, so emulator playback at
1x takes the demo trip time (40 min) and 5x takes about 8 minutes.

Usage: python3 tools/make_demo_gpx.py
"""
import json
import math
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TRIP = ROOT / "app/src/main/assets/demo_trip.json"
OUT = ROOT / "tools/demo-route.gpx"
STEP_METERS = 200.0


def distance(a, b):
    lat1, lat2 = math.radians(a[0]), math.radians(b[0])
    d_lat, d_lon = lat2 - lat1, math.radians(b[1] - a[1])
    h = math.sin(d_lat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(d_lon / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(min(1.0, h)))


def main():
    trip = json.loads(TRIP.read_text(encoding="utf-8"))
    route = [(p["latitude"], p["longitude"]) for p in trip["route"]]
    points = [route[0]]
    for a, b in zip(route, route[1:]):
        steps = max(1, round(distance(a, b) / STEP_METERS))
        points += [(a[0] + (b[0] - a[0]) * i / steps, a[1] + (b[1] - a[1]) * i / steps) for i in range(1, steps + 1)]
    total = sum(distance(a, b) for a, b in zip(points, points[1:]))
    seconds_per_meter = trip["remainingMinutes"] * 60 / total
    start = datetime(2026, 11, 14, 9, 0, tzinfo=timezone.utc)
    elapsed = 0.0
    rows = []
    for i, p in enumerate(points):
        if i:
            elapsed += distance(points[i - 1], p) * seconds_per_meter
        t = (start + timedelta(seconds=round(elapsed))).strftime("%Y-%m-%dT%H:%M:%SZ")
        rows.append(f'      <trkpt lat="{p[0]:.6f}" lon="{p[1]:.6f}"><time>{t}</time></trkpt>')
    OUT.write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<gpx version="1.1" creator="Routeonomy tools/make_demo_gpx.py" xmlns="http://www.topografix.com/GPX/1/1">\n'
        f'  <trk>\n    <name>{trip["origin"]["name"]} - {trip["destination"]["name"]} (demo)</name>\n    <trkseg>\n'
        + "\n".join(rows)
        + "\n    </trkseg>\n  </trk>\n</gpx>\n",
        encoding="utf-8",
    )
    print(f"{OUT.relative_to(ROOT)}: {len(points)} points, {total / 1000:.1f} km, {elapsed / 60:.0f} min at 1x")


if __name__ == "__main__":
    main()
