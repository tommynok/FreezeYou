#!/usr/bin/env python3
"""Fail if the same layout exists in several variant folders with different ids.

A layout split across res/layout/ and res/layout-vNN/ has to be edited in every
copy at once. Missing one is silent: the build succeeds, and only a device on
the matching API level inflates the stale copy, where findViewById returns null
and the activity dies. That is exactly what happened to bma_main.xml — the new
backup buttons were added to layout/ only, and every API 23+ device crashed on
startup of that screen.

Variants may legitimately differ when a view depends on an API addition. Those
are listed in ALLOWED_EXTRA_IDS with the reason, and must stay paired with an
SDK_INT guard in code.
"""

import collections
import pathlib
import re
import sys

RES = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res"

# id -> why it may exist in some variants only
ALLOWED_EXTRA_IDS = {
    # ?android:attr/fingerprintAuthDrawable is API 23+; AppLockActivity looks this
    # view up behind `if (SDK_INT >= M)`.
    "app_lock_main_fingerprint_imageButton": "API 23+ fingerprint drawable",
}

ID_PATTERN = re.compile(r'android:id="@\+id/([A-Za-z0-9_]+)"')
COMMENT_PATTERN = re.compile(r"<!--.*?-->", re.DOTALL)


def declared_ids(layout: pathlib.Path) -> set:
    # Commented-out views still contain android:id and would otherwise be reported
    # as a difference between variants.
    text = COMMENT_PATTERN.sub("", layout.read_text(encoding="utf-8"))
    return set(ID_PATTERN.findall(text))


def main() -> int:
    by_name = collections.defaultdict(dict)
    for folder in sorted(RES.glob("layout*")):
        for layout in sorted(folder.glob("*.xml")):
            by_name[layout.name][folder.name] = declared_ids(layout)

    failures = []
    for name, variants in sorted(by_name.items()):
        if len(variants) < 2:
            continue
        union = set().union(*variants.values())
        for folder, ids in sorted(variants.items()):
            missing = {
                i for i in union - ids if i not in ALLOWED_EXTRA_IDS
            }
            if missing:
                failures.append(
                    f"  {folder}/{name} is missing: {', '.join(sorted(missing))}"
                )

    if failures:
        print("Layout variants declare different ids:", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        print(
            "\nAdd the ids to every variant of the file, or collapse the variants "
            "if they no longer differ for an API reason.",
            file=sys.stderr,
        )
        return 1

    checked = sum(1 for v in by_name.values() if len(v) > 1)
    print(f"Layout variants consistent ({checked} file(s) with multiple variants).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
