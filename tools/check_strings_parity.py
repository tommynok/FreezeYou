#!/usr/bin/env python3
"""Check that a translation can still be handed to String.format / Resources.getString.

Two kinds of damage live in the locale files. A dropped or retyped `%1$s` throws
MissingFormatArgumentException at runtime, on the user's device, for a string that compiles and
installs happily; and a house typography rule (no `ё`, no machine quotes) that nothing enforces
drifts back in with the next hand edit. Neither shows up in a build, and both are easy to miss in a
diff review, so they are checked here instead.

Key coverage is reported but never fails a run: a locale lagging behind the reference is normal
between releases, while a mismatched placeholder is a crash waiting for its code path. The
reference keys entries by element type as well as name, because Android does — this project has a
`<string name="triggers">` and a `<string-array name="triggers">` side by side, and collapsing them
would invent a conflict aapt never had.

Run from anywhere; exits 1 on a critical finding (format specifier mismatch, or `ё`/`Ё` in the
Russian locale) and 0 otherwise, warnings included. Unlike the other checks in this directory,
this one is allowed to fail a build.
"""

import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
RES_DIR = PROJECT_ROOT / "app" / "src" / "main" / "res"
REFERENCE_DIR = "values"

TEXT_TAGS = ("string", "string-array", "plurals")

# Locale directory -> typography rules that apply to it. Only Russian carries the house style;
# giving another locale rules of its own means adding them here and nowhere else.
TRANSLATIONS = {
    "values-ru-rRU": {"no_yo": True, "no_machine_quotes": True},
    "values-uk-rUA": {},
    "values-zh-rCN": {},
}

GUILLEMETS = "« »"

# A Java/Android format specifier: optional argument index, flags, width, precision, conversion.
# "%%" comes first in the alternation so an escaped literal percent is never read as a placeholder.
FORMAT_SPEC = re.compile(r"%%|%(?:(\d+)\$)?([-+ #0]*\d*(?:\.\d+)?)([a-zA-Z])")

NAME_ON_LINE = re.compile(r'name="([^"]+)"')


class Entry:
    """One translatable resource: the text it holds, and the line it was declared on."""

    def __init__(self, key, tag, segments, line):
        self.key = key
        self.tag = tag
        self.segments = segments
        self.line = line


def line_index(path):
    """Map resource name to its line number.

    ElementTree discards line numbers, and a report that says a string is wrong without saying
    where costs a search through a 500-line file.
    """
    numbers = {}
    for number, text in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        match = NAME_ON_LINE.search(text)
        if match and match.group(1) not in numbers:
            numbers[match.group(1)] = number
    return numbers


def load(path):
    """Read a resource file into `({type/name: Entry}, [duplicate names])`.

    `translatable="false"` entries are dropped on both sides of every comparison: they are meant to
    read identically in every locale, so counting them would turn the reference's own untranslated
    `app_name` into a missing translation everywhere.
    """
    if not path.is_file():
        sys.exit(f"missing resource file: {path}")

    numbers = line_index(path)
    entries = {}
    duplicates = []

    for element in ET.parse(path).getroot():
        if element.tag not in TEXT_TAGS:
            continue
        name = element.get("name")
        if name is None or element.get("translatable") == "false":
            continue
        segments = [text for text in element.itertext() if text.strip()]
        entry_id = f"{element.tag}/{name}"
        if entry_id in entries:
            duplicates.append(name)
        entries[entry_id] = Entry(name, element.tag, segments, numbers.get(name, 0))

    return entries, duplicates


def spec_counters(segments):
    """Return `(conversions, argument indices)` counted over every segment of one entry."""
    conversions = Counter()
    indices = Counter()

    for text in segments:
        for match in FORMAT_SPEC.finditer(text):
            if match.group(0) == "%%":
                continue
            conversions[match.group(3)] += 1
            indices[match.group(1) or ""] += 1

    return conversions, indices


def summarize(conversions, indices):
    """Render specifiers as `%d x1 %s x1 [args 1, 2]`, order-stable so two runs can be compared."""
    parts = [f"%{char} x{count}" for char, count in sorted(conversions.items())]
    named = sorted(index for index in indices if index)
    plain = sum(count for index, count in indices.items() if not index)
    if named:
        parts.append(f"[args {', '.join(named)}]")
    if plain:
        parts.append(f"[{plain} unindexed]")
    return " ".join(parts) if parts else "(none)"


def where(directory, entry):
    return f"{directory}:{entry.line} [{entry.key}]"


def parity_findings(reference, directory, translations):
    """Flag every shared key whose placeholders no longer match the reference."""
    findings = []

    for entry_id, source in sorted(reference.items()):
        target = translations.get(entry_id)
        if target is None:
            continue

        source_types, source_args = spec_counters(source.segments)
        target_types, target_args = spec_counters(target.segments)
        if source_types == target_types and source_args == target_args:
            continue

        if source_types != target_types:
            reason = "conversion types/counts differ"
        elif source_args != target_args and "" in (source_args + target_args):
            reason = "indexed and unindexed specifiers mixed"
        else:
            reason = "argument indices differ"

        findings.append(
            f"{where(directory, target)} {reason}: "
            f"reference '{summarize(source_types, source_args)}' -> "
            f"translation '{summarize(target_types, target_args)}'  ({source.tag})"
        )

    return findings


def typography_findings(directory, rules, translations):
    critical, warnings = [], []
    if not rules:
        return critical, warnings

    for _, entry in sorted(translations.items()):
        if rules.get("no_yo"):
            offenders = sorted({char for text in entry.segments for char in text if char in "ёЁ"})
            if offenders:
                sample = next(t for t in entry.segments if any(c in offenders for c in t))
                critical.append(
                    f"{where(directory, entry)} forbidden letter "
                    f"'{'/'.join(offenders)}': {sample.strip()[:72]}"
                )

        if rules.get("no_machine_quotes"):
            for text in entry.segments:
                if '"' in text:
                    warnings.append(
                        f"{where(directory, entry)} machine quote, use {GUILLEMETS}: "
                        f"{text.strip()[:72]}"
                    )
                    break

    return critical, warnings


def coverage(reference, translations):
    """Return `(not translated, dead keys, shared count, percent)`.

    Compared by type/name, so a translation that moved a name from `<string>` to `<string-array>`
    shows up as both missing and dead rather than quietly matching itself.
    """
    missing = sorted(set(reference) - set(translations))
    orphans = sorted(set(translations) - set(reference))
    shared = len(reference) - len(missing)
    percent = 100.0 * shared / len(reference) if reference else 0.0

    return (
        [reference[entry_id].key for entry_id in missing],
        [translations[entry_id].key for entry_id in orphans],
        shared,
        percent,
    )


def print_key_list(label, keys):
    print(f"      {label} ({len(keys)}):")
    for start in range(0, len(keys), 4):
        print("        " + "".join(f"{key:<36}" for key in keys[start:start + 4]))


def display(path):
    """Shorten a path against the project root, but never insist that it lives there."""
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def main():
    # The report echoes translated text, and the Cyrillic or Han characters in it are not in every
    # Windows console codepage. Without this the tool dies on the very finding it exists to report.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    reference_path = RES_DIR / REFERENCE_DIR / "strings.xml"
    reference, reference_duplicates = load(reference_path)

    print("FreezeYou string resource parity check")
    print(f"  reference  : {display(reference_path)}")
    print(f"  translatable keys: {len(reference)}")
    if reference_duplicates:
        print("  WARNING: duplicate names in the reference: {0}".format(
            ", ".join(sorted(set(reference_duplicates)))))

    critical = []
    warnings = []
    parity_sections = []
    typography_sections = []
    coverage_sections = []

    for directory, rules in sorted(TRANSLATIONS.items()):
        translations, duplicates = load(RES_DIR / directory / "strings.xml")

        if duplicates:
            warnings.append("{0} duplicate names: {1}".format(
                directory, ", ".join(sorted(set(duplicates)))))

        findings = parity_findings(reference, directory, translations)
        shared = [entry_id for entry_id in translations if entry_id in reference]
        parity_sections.append((directory, findings, len(shared)))
        critical.extend(findings)

        hard, soft = typography_findings(directory, rules, translations)
        typography_sections.append((directory, rules, hard, soft))
        critical.extend(hard)
        warnings.extend(soft)

        coverage_sections.append((directory,) + coverage(reference, translations))

    print("\n[1] Format specifier parity")
    for directory, findings, checked in parity_sections:
        status = "ok" if not findings else f"{len(findings)} MISMATCH"
        print(f"  {directory:<16} {checked:>3} shared key(s) checked -> {status}")
        for finding in findings:
            print(f"      {finding}")

    print("\n[2] Typography")
    for directory, rules, hard, soft in typography_sections:
        applied = ", ".join(sorted(rules)) if rules else "(no rules for this locale)"
        print(f"  {directory:<16} {applied}")
        for finding in hard + soft:
            print(f"      {finding}")
        if not hard and not soft:
            print("      clean")

    print("\n[3] Key coverage")
    for directory, missing, orphans, shared, percent in coverage_sections:
        print(f"  {directory:<16} {percent:5.1f}%  "
              f"({shared}/{len(reference)} reference keys translated)")
        if orphans:
            print_key_list(f"dead keys, absent from {REFERENCE_DIR}", orphans)
        if missing:
            print_key_list("not translated", missing)

    print("\nSummary")
    print(f"  critical: {len(critical)}   warnings: {len(warnings)}")
    for line in critical:
        print(f"    CRITICAL  {line}")
    for line in warnings:
        print(f"    warning   {line}")

    if critical:
        print("\nRESULT: FAIL")
        return 1

    print("\nRESULT: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
