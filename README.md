# FreezeYou-Fork

> **Unofficial fork** of [FreezeYou](https://github.com/FreezeYou/FreezeYou), not affiliated with the original developer.
> No support or issue tracker here.

---

## About the application

FreezeYou disables applications you do not want running in the background and re-enables them when needed — manually, from the home screen, or automatically by schedule and system events — to reduce battery drain and background noise.

**Core features:**

- Freeze/unfreeze applications in any mode: No-root (DPM / Profile Owner), Root, System app, Shizuku
- One-key freeze and unfreeze lists, freeze on leaving an app, freeze on screen off
- Scheduled tasks by time or by triggers: screen on/off, app opened/left, app frozen/unfrozen
- Home-screen shortcuts and widgets for freeze/unfreeze and launch
- Lists and filters: frozen, unfrozen, system, user, custom groups
- Quick notification after unfreezing, one-tap actions from it
- Backup and restore of settings, lists, shortcuts and scheduled tasks

---

## Differences from the original

### ✦ Added

- **Activity launcher and shortcuts** — pick any activity of any application, launch it directly or create a home-screen shortcut for it; non-exported activities included (elevated launch via Root/Shizuku)
- **"Running" filter** on the main list, available in every mode
- **Offline help page** for the scheduled-task command syntax
- **Second floating button** for multi-selection actions
- **Fast list/grid view toggle**
- **Grey icons** for frozen applications
- **Reorganized settings** — 13 sections in three groups, with a separated danger zone
- **Protection warning** when freezing system-critical packages

### ✦ Improved

- "Unfreeze and launch" reaches non-exported activities as well (elevated launch via Root/Shizuku)
- Theme, language and main-screen layout switches apply instantly, no application restart
- Backup and restore work through files (save to file / restore from file)
- The log can be shared anywhere as a file

### ✦ Fixed
- Batch freeze/unfreeze through Shizuku
- Force stop in Shizuku mode (upstream attempted to run `su`, which does not exist there)
- Screen titles no longer stay in the system language when the in-app language differs

---

## License

This project inherits the license of the original [FreezeYou](https://github.com/FreezeYou/FreezeYou) project.
See [LICENSE](LICENSE) for details.
