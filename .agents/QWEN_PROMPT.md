Review commits `aeb1e133..4bf984ce` (PR #4).
COMMUNICATION CHANNEL: Write your review ONLY to `.agents/REVIEW_QWEN.md` (append only, do not overwrite or delete previous rounds).

Focus areas of the 5 implemented chunks:
1. NotificationUtils.java: MMKV cross-process locking and PendingIntent.getForegroundService.
2. BootCompletedReceiver.java: goAsync() offload to BootCompletedWorker background thread.
3. ElevatedLaunchUtils.kt, ProcessUtils.java, ForceStopUtils.java: shell injection prevention via shellQuote.
4. InstallPackagesService.java: safe root install via PrivilegedShellUtils.runRootCommand.
5. FUFUtils.kt, Main.java, RestoreUtils.java: batch unfreeze card optimization, background root check, and Activity lifecycle safety.

Block format:
## ROUND N — aeb1e133..4bf984ce
VERDICT: can merge / has issues / blocker
F1 [critical|important|minor] <file>, <function>: what is wrong -> consequence/risk -> how to fix. STATUS: OPEN
F2 ...
NOT CHECKED: ...

Rules:
- Focus on real regressions, thread safety, and edge-case correctness. Reject academic overengineering.
- Be precise and self-contained: specify file and function name, not only line numbers.
- In chat, reply with only ONE line: "Round N recorded, issues: X (critical Y)".
- On re-review after fixes: check only items marked FIXED, set to STATUS: CLOSED or return to STATUS: OPEN with reason. If no open items remain, append STATUS: READY.
- Maximum 2 rounds; if critical issues remain after round 2, append STATUS: HUMAN REQUIRED.
