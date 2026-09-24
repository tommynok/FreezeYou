#!/usr/bin/env python3
"""Check that every settings row is wired to a storage key that exists.

A preference writes SharedPreferences under its `app:key`. SettingsUtils then looks that key up
by name among the storage enums and, on a match, copies the value into MMKV — which is where the
rest of the application reads it from. A key that matches no enum constant fails that lookup
silently: the switch moves, the setting never takes effect, and nothing is logged.

That is not hypothetical. `DebugModeEnabled` sat in spr_advance.xml against an enum constant named
`debugModeEnabled`, so debug mode could not be turned on at all, and the only way it surfaced was
an owner reporting that he had enabled it while the log said otherwise.

Run from the repository root; exits non-zero and names the offending rows.
"""

import glob
import re
import sys
import xml.etree.ElementTree as ET

APP_NS = "{http://schemas.android.com/apk/res-auto}"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

# Rows that hold a value. A plain <Preference> is a link to another screen or an action, and has
# no value to store, so its key is free-form.
VALUE_TAGS = {
    "CheckBoxPreference",
    "SwitchPreference",
    "SwitchPreferenceCompat",
    "ListPreference",
    "EditTextPreference",
    "MultiSelectListPreference",
    "SeekBarPreference",
}

ENUM_GLOB = "app/src/main/java/cf/playhi/freezeyou/storage/key/Default*Keys.kt"
XML_GLOB = "app/src/main/res/xml/spr*.xml"


def storage_key_names():
    names = set()
    for path in glob.glob(ENUM_GLOB):
        with open(path, encoding="utf-8") as f:
            names |= set(re.findall(r"^\s{4}(\w+)\s*\{", f.read(), re.M))
    return names


def main():
    known = storage_key_names()
    if not known:
        print("check_settings_keys: found no storage keys — has the storage package moved?")
        return 1

    checked = 0
    bad = []
    for path in sorted(glob.glob(XML_GLOB)):
        for element in ET.parse(path).iter():
            if element.tag.split("}")[-1] not in VALUE_TAGS:
                continue
            checked += 1
            key = element.get(APP_NS + "key") or element.get(ANDROID_NS + "key")
            if key not in known:
                bad.append((path, key))

    if bad:
        print("check_settings_keys: these rows store a value under a key no storage enum defines,")
        print("so the setting will appear to work and do nothing:")
        for path, key in bad:
            print(f"  {path}: app:key=\"{key}\"")
        return 1

    print(f"Settings keys consistent ({checked} rows, {len(known)} storage keys).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
