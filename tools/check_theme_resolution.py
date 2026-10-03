#!/usr/bin/env python3
"""Checks the resource side of the themes: what a style resolves to on a given API level.

Two classes of bug live here, and both of them cost this fork real builds:

1. A style with the same name in a qualifier directory (values-v21, values-v23, ...) replaces
   the base one completely. The M3 parents of Base.AppTheme.Dark / Light were dropped that way
   and every Material3 attribute became inert on every device from API 21 up, which no
   compiler error and no unit test could see.

2. A theme attribute that resolves to a library default instead of ours - colorPrimary still
   near black on a dark theme, a popup overlay whose surface is the stock #1C1B1F. That is what
   made checkboxes invisible and two popups different colours.

Run without arguments for a report; --strict makes findings fail. Nothing here needs Android:
it parses res/values* and resolves style parents and @color references the same way the resource
compiler does for a given API level.
"""

import argparse
import glob
import os
import re
import sys

BASE_DIR = 0


def parse_resources(res_dir):
    """dir -> {style_name: {"parent": str|None, "items": {attr: value}}}"""
    styles = {}
    colors = {}
    for d in sorted(glob.glob(os.path.join(res_dir, "values*"))):
        if not os.path.isdir(d):
            continue
        qualifier = os.path.basename(d)
        for f in sorted(glob.glob(os.path.join(d, "*.xml"))):
            text = open(f, encoding="utf-8").read()
            text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
            for m in re.finditer(r'<style\b([^>]*)>', text):
                attrs = m.group(1)
                nm = re.search(r'name="([^"]+)"', attrs)
                if not nm:
                    continue
                name = nm.group(1)
                pm = re.search(r'parent="([^"]+)"', attrs)
                parent = pm.group(1) if pm else None
                items = {}
                if not attrs.rstrip().endswith('/'):
                    end = text.find('</style>', m.end())
                    body = text[m.end():end] if end != -1 else ''
                    for im in re.finditer(r'<item\s+name="([^"]+)"\s*>(.*?)</item>', body, re.S):
                        items[im.group(1)] = im.group(2).strip()
                styles.setdefault(qualifier, {})[name] = {
                    "parent": parent,
                    "items": items,
                    "file": os.path.relpath(f, os.path.dirname(res_dir)),
                }
            for m in re.finditer(r'<color\s+name="([^"]+)"\s*>(.*?)</color>', text, re.S):
                colors.setdefault(qualifier, {})[m.group(1)] = m.group(2).strip()
    return styles, colors


def qualifier_version(qualifier):
    if qualifier == "values":
        return BASE_DIR
    m = re.search(r"-v(\d+)", qualifier)
    return int(m.group(1)) if m else BASE_DIR


def effective(styles, name, api):
    """The definition that wins for `name` on a device of API level `api`."""
    best = None
    best_ver = -1
    for qualifier, table in styles.items():
        ver = qualifier_version(qualifier)
        if ver > api or name not in table:
            continue
        if ver >= best_ver:
            best_ver = ver
            best = (qualifier, table[name])
    return best


def resolve_style(styles, name, api, seen=None):
    """Flatten a style chain: returns (items, first_unknown_parent, chain)."""
    seen = seen or set()
    if name in seen:
        return {}, name, []
    seen.add(name)
    found = effective(styles, name, api)
    if not found:
        return {}, name, []
    qualifier, body = found
    parent = body["parent"] or (name.rsplit(".", 1)[0] if "." in name else None)
    items = dict(body["items"])
    chain = [f"{name} ({qualifier})"]
    if parent:
        parent_items, unknown, parent_chain = resolve_style(styles, parent, api, seen)
        for k, v in parent_items.items():
            items.setdefault(k, v)
        chain += parent_chain
        return items, unknown, chain
    return items, None, chain


def resolve_color(colors, value, api, depth=0):
    """@color/x -> #RRGGBB, following the same qualifier rules."""
    if depth > 8 or not value or not value.startswith("@color/"):
        return value
    key = value.split("/", 1)[1]
    best, best_ver = None, -1
    for qualifier, table in colors.items():
        ver = qualifier_version(qualifier)
        if ver > api or key not in table:
            continue
        if ver >= best_ver:
            best, best_ver = table[key], ver
    if best is None:
        return value
    return resolve_color(colors, best, api, depth + 1)


def find_overridden_styles(styles):
    """Styles defined in more than one qualifier set: the trap that cost six builds."""
    by_name = {}
    for qualifier, table in styles.items():
        for name, body in table.items():
            by_name.setdefault(name, []).append((qualifier, body.get("parent")))
    return {
        name: places
        for name, places in by_name.items()
        if len({q for q, _ in places}) > 1
    }


def find_recreated_themes(styles):
    """A theme overridden in a qualifier dir whose parent there is not Material3."""
    out = []
    for name, places in find_overridden_styles(styles).items():
        if not name.startswith(("AppTheme.", "Base.AppTheme")):
            continue
        for qualifier, parent in places:
            if parent and parent.startswith("Theme.AppCompat"):
                out.append((name, qualifier, parent))
    return out


# Theme -> the attributes that must resolve to this fork's values, and what they must be.
# "apis" narrows an expectation to a range, for attributes that legitimately differ by platform
# version: AppTheme.Default is the old white palette below API 31 and a Material3 light theme
# from 31 on, and those two carry different accents.
EXPECTATIONS = {
    "AppTheme.Dark.Default": {
        "colorPrimary": "#FFFFFF",
        "colorOnPrimary": "#1E1F22",
        "colorSurface": "#35383E",
        "colorAccent": "#1976D2",
        "actionBarPopupTheme": "ThemeOverlay.App.ActionBarPopup",
        "textAppearanceLargePopupMenu": "TextAppearance.App.PopupMenu.Dark",
    },
    "AppTheme.Dark.Black": {
        "colorPrimary": "#FFFFFF",
        "colorOnPrimary": "#000000",
        "colorSurface": "#22252A",
        "colorAccent": "#1976D2",
        "actionBarPopupTheme": "ThemeOverlay.App.ActionBarPopup",
    },
    "AppTheme.Dark.Dialog.Default": {
        "colorPrimary": "#FFFFFF",
        "colorOnPrimary": "#1E1F22",
        "colorSurface": "#35383E",
    },
    "AppTheme.Dark.Dialog.Black": {
        "colorPrimary": "#FFFFFF",
        "colorOnPrimary": "#000000",
        "colorSurface": "#22252A",
    },
    "AppTheme.Default": {
        "colorAccent": "#1976D2",  # values-v31 and up
    },
}

INTENTIONAL_OVERRIDES = {
    "Base.AppTheme.Translucent.NoTitleBar",
    "AppTheme.Default",
    "AppTheme.Default.Dialog",
    "Base.AppTheme.SplashScreen",
}

M3_PARENTS = ("Theme.Material3.", "ThemeOverlay.Material3.", "Widget.Material3.")


def check(res_dir, apis, strict):
    styles, colors = parse_resources(res_dir)
    findings = []

    for name, places in sorted(find_overridden_styles(styles).items()):
        dirs = sorted({q for q, _ in places})
        # Overrides that are deliberate, with the reason each one is safe:
        #   - the translucent chain: each step keeps the previous one as its parent;
        #   - AppTheme.Default and AppTheme.Default.Dialog: the v31 copy adds the splash and
        #     light-status items and keeps the Material3 light parent from the base file;
        #   - Base.AppTheme.SplashScreen: same, one more parent in the chain.
        # Anything else that shows up here is the trap that cost six builds.
        if name in INTENTIONAL_OVERRIDES:
            continue
        findings.append(
            ("override", f"{name}: defined in {', '.join(dirs)} - "
                         f"the most specific one replaces the rest entirely")
        )

    for name, qualifier, parent in find_recreated_themes(styles):
        findings.append(
            ("appcompat-theme", f"{name} in {qualifier} has parent {parent}")
        )

    for api in apis:
        for theme, expected in EXPECTATIONS.items():
            found = effective(styles, theme, api)
            if not found:
                findings.append(("missing-theme", f"{theme}: not defined for API {api}"))
                continue
            items, unknown, chain = resolve_style(styles, theme, api)
            base = chain[-1].split(" (")[0]
            for attr, want in expected.items():
                if attr == "colorAccent" and theme == "AppTheme.Default" and api < 31:
                    want = "#424242"  # the white palette below API 31, unchanged from stock
                value = items.get(attr)
                if value is None:
                    findings.append(
                        ("missing-attr", f"{theme} (API {api}): {attr} is not set anywhere in "
                                         f"the chain {base}")
                    )
                    continue
                actual = resolve_color(colors, value, api)
                if actual.startswith("@style/"):
                    actual = actual.split("/", 1)[1]
                if actual != want:
                    findings.append(
                        ("wrong-attr",
                         f"{theme} (API {api}): {attr} = {actual} (expected {want}) "
                         f"via {value} [{base}]")
                    )
            # The dark themes must not silently fall back to the AppCompat side. The chain of
            # styles defined in this project ends at the first parent that is not, and that
            # parent is the library theme everything inherits from.
            if "Dark" in theme:
                top = unknown or chain[-1].split(" (")[0]
                if unknown and not any(top.startswith(p) for p in M3_PARENTS):
                    findings.append(
                        ("non-m3-base", f"{theme} (API {api}): library base theme is {top}")
                    )
    return findings


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    here = os.path.dirname(os.path.abspath(__file__))
    parser.add_argument(
        "--res",
        default=os.path.join(here, "..", "app", "src", "main", "res"),
    )
    parser.add_argument(
        "--apis",
        default="19,21,29,31,34",
        help="API levels to resolve for, comma separated",
    )
    parser.add_argument("--strict", action="store_true", help="exit 1 on findings")
    args = parser.parse_args()

    apis = [int(x) for x in args.apis.split(",") if x.strip()]
    findings = check(args.res, apis, args.strict)

    if not findings:
        print(f"Theme resolution consistent (APIs {', '.join(map(str, apis))}).")
        return 0

    groups = {}
    for kind, text in findings:
        groups.setdefault(kind, []).append(text)
    for kind in sorted(groups):
        print(f"== {kind} ==")
        for text in groups[kind]:
            print("  " + text)
    print(f"\n{len(findings)} finding(s).")
    return 1 if args.strict else 0


if __name__ == "__main__":
    sys.exit(main())
