"""Rasterize the fork's contour snowflake (ic_qs_snowflake geometry) into ic_stat_logo.png.

Notification small icons are rendered from the alpha channel only, so the asset must be
a white contour on a transparent background. The previous attempt copied the alpha of the
filled launcher disc and produced a featureless white circle in the shade.
"""

import math
import struct
import zlib
import os

# 24 line segments from res/drawable/ic_qs_snowflake.xml, viewport 24x24,
# strokeWidth 1.6 with round caps.
SEGMENTS = [
    ((12, 12), (12, 2)),
    ((12, 12), (20.66, 7)),
    ((12, 12), (20.66, 17)),
    ((12, 12), (12, 22)),
    ((12, 12), (3.34, 17)),
    ((12, 12), (3.34, 7)),
    ((12, 5.5), (10, 3.5)),
    ((12, 5.5), (14, 3.5)),
    ((17.63, 8.75), (18.36, 6.02)),
    ((17.63, 8.75), (20.36, 9.48)),
    ((17.63, 15.25), (20.36, 14.52)),
    ((17.63, 15.25), (18.36, 17.98)),
    ((12, 18.5), (14, 20.5)),
    ((12, 18.5), (10, 20.5)),
    ((6.37, 15.25), (5.64, 17.98)),
    ((6.37, 15.25), (3.64, 14.52)),
    ((6.37, 8.75), (3.64, 9.48)),
    ((6.37, 8.75), (5.64, 6.02)),
]

RADIUS = 0.8  # strokeWidth 1.6 / 2, viewport units

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def dist_to_segment(px, py, a, b):
    ax, ay = a
    bx, by = b
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    seg_len_sq = vx * vx + vy * vy
    if seg_len_sq == 0:
        return math.hypot(wx, wy)
    t = max(0.0, min(1.0, (wx * vx + wy * vy) / seg_len_sq))
    return math.hypot(px - (ax + t * vx), py - (ay + t * vy))


def write_png(path, size, alpha_rows):
    def chunk(typ, data):
        c = struct.pack(">I", len(data)) + typ + data
        c += struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)
        return c

    raw = bytearray()
    for row in alpha_rows:
        raw.append(0)  # filter type 0
        raw.extend(row)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def render(size):
    scale = size / 24.0
    ss = 4  # supersampling grid
    rows = []
    for y in range(size):
        row = bytearray()
        for x in range(size):
            hits = 0
            for sy in range(ss):
                for sx in range(ss):
                    vx = (x + (sx + 0.5) / ss) / scale
                    vy = (y + (sy + 0.5) / ss) / scale
                    if any(
                        dist_to_segment(vx, vy, a, b) <= RADIUS
                        for a, b in SEGMENTS
                    ):
                        hits += 1
            a = int(round(255.0 * hits / (ss * ss)))
            row.extend((255, 255, 255, a))
        rows.append(bytes(row))
    return rows


def main():
    res_dir = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
    for density, size in DENSITIES.items():
        out = os.path.join(res_dir, "drawable-%s" % density, "ic_stat_logo.png")
        rows = render(size)
        write_png(out, size, rows)
        print("wrote %s (%dx%d)" % (out, size, size))


if __name__ == "__main__":
    main()
