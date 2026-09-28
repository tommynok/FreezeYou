# Parked translations

**2026-09-28: zh-rCN is live again** — `app/src/main/res/values-zh-rCN/` was rebuilt from
these snapshots plus agent translations for everything the fork added (native-speaker review
still pending). This folder stays for the record and for zh-rTW, which remains parked.

Upstream's Chinese strings, snapshotted from `upstream/master` on 2026-09-27 so the original
author's translation work survives even as the upstream repository is rewritten and the fork
does not ship Chinese.

These files are **reference material, not build inputs**: nothing under `translations/` is
compiled into the APK. Reviving Chinese means, in order:

1. Diff each key set against `app/src/main/res/values/strings.xml` — the fork has added keys
   (activity shortcuts, log sharing, the reworked settings sections and their summaries,
   the danger zone, the built-in micro-help) that have no Chinese text at all.
2. Translate the missing keys and review the ones whose English source has been rewritten —
   a stale translation of a replaced string is worse than none.
3. Only then copy the result into `app/src/main/res/values-zh-rCN/`, add `zh-rCN` to
   `resConfigs`, and restore the Chinese entry in `displayLanguageOptionsSelection`.

zh-rTW is kept for the same reason; deciding whether to maintain two variants can wait until
zh-rCN is alive again.

A native speaker's pass is the honest way to do step 2 — machine translation of a frozen
Chinese baseline risks exactly the kind of drift the fork just cleaned out of English.
