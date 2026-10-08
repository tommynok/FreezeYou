#!/usr/bin/env python3
"""Keep bundled font assets small enough that a full font cannot replace a subset unnoticed."""

from pathlib import Path
import sys


FONT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/res/font"
FONT_SUFFIXES = {".ttf", ".otf", ".ttc", ".woff", ".woff2"}
REQUIRED_FIRA_CUTS = {
    "fira_condensed_bold.ttf",
    "fira_condensed_medium.ttf",
    "fira_condensed_regular.ttf",
}
MAX_FONT_FILE_BYTES = 200_000
MAX_TOTAL_FONT_BYTES = 500_000


def main() -> int:
    if not FONT_DIR.is_dir():
        print(f"Font resource directory not found: {FONT_DIR}", file=sys.stderr)
        return 1

    font_files = sorted(
        path for path in FONT_DIR.iterdir()
        if path.is_file() and path.suffix.lower() in FONT_SUFFIXES
    )
    present = {path.name for path in font_files}
    missing = sorted(REQUIRED_FIRA_CUTS - present)
    sizes = {path: path.stat().st_size for path in font_files}
    total = sum(sizes.values())

    for path, size in sizes.items():
        print(f"{path.name}: {size:,} / {MAX_FONT_FILE_BYTES:,} bytes")

    errors = []
    if missing:
        errors.append("Required Fira font cuts are missing: " + ", ".join(missing))
    for path, size in sizes.items():
        if size > MAX_FONT_FILE_BYTES:
            errors.append(
                f"{path.name} is {size:,} bytes; per-file budget is {MAX_FONT_FILE_BYTES:,}"
            )
    if total > MAX_TOTAL_FONT_BYTES:
        errors.append(
            f"Total font payload is {total:,} bytes; budget is {MAX_TOTAL_FONT_BYTES:,}"
        )

    if errors:
        print("Font asset budget exceeded; review the subset and APK impact before raising it:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Total font payload: {total:,} / {MAX_TOTAL_FONT_BYTES:,} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
