package cf.playhi.freezeyou.utils;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cf.playhi.freezeyou.MainApplication;
import cf.playhi.freezeyou.R;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.avoidFreezeForegroundApplications;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.lesserToast;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public final class ForceStopUtils {

    private static final int MAX_FAILED_APPS_SHOWN_IN_TOAST = 5;

    public static final class ForceStopOutcomes {
        public final List<String> succeeded = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();
    }

    public static List<String> buildForceStopCommands(List<String> targetPackages) {
        List<String> commands = new ArrayList<>();
        if (targetPackages == null) return commands;
        for (String pkg : targetPackages) {
            commands.add("am force-stop " + PrivilegedShellUtils.shellQuote(pkg));
        }
        return commands;
    }

    public static PrivilegedShellUtils.Backend resolveBackend(
            boolean shizukuMode, boolean shizukuAvailable, boolean rootAvailable) {
        if (shizukuMode) {
            if (shizukuAvailable) return PrivilegedShellUtils.Backend.SHIZUKU;
            if (rootAvailable) return PrivilegedShellUtils.Backend.ROOT;
        } else {
            if (rootAvailable) return PrivilegedShellUtils.Backend.ROOT;
            if (shizukuAvailable) return PrivilegedShellUtils.Backend.SHIZUKU;
        }
        return null;
    }

    public static ForceStopOutcomes resolveOutcomes(
            List<String> targetPackages, PrivilegedShellUtils.BatchResult result) {
        if (result == null) {
            return resolveOutcomes(targetPackages, false, Collections.emptyList());
        }
        return resolveOutcomes(targetPackages, result.sessionStarted, result.outcomes);
    }

    public static ForceStopOutcomes resolveOutcomes(
            List<String> targetPackages, boolean sessionStarted, List<PrivilegedShellUtils.CommandOutcome> outcomes) {
        ForceStopOutcomes resultOutcomes = new ForceStopOutcomes();
        if (targetPackages == null || targetPackages.isEmpty()) {
            return resultOutcomes;
        }
        if (!sessionStarted) {
            resultOutcomes.failed.addAll(targetPackages);
            return resultOutcomes;
        }
        for (int i = 0; i < targetPackages.size(); i++) {
            String pkg = targetPackages.get(i);
            if (outcomes != null && i < outcomes.size() && outcomes.get(i).isSuccessful()) {
                resultOutcomes.succeeded.add(pkg);
            } else {
                resultOutcomes.failed.add(pkg);
            }
        }
        return resultOutcomes;
    }

    public static void showFailedApplicationsToast(Context context, List<String> failedPkgNames) {
        if (context == null || failedPkgNames == null || failedPkgNames.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(MAX_FAILED_APPS_SHOWN_IN_TOAST, failedPkgNames.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(", ");
            String pkg = failedPkgNames.get(i);
            CharSequence label = ApplicationLabelUtils.getApplicationLabel(context, null, null, pkg);
            sb.append(label != null ? label : pkg);
        }
        int remaining = failedPkgNames.size() - limit;
        String message = context.getString(
                R.string.nApplicationsFailedColonList, failedPkgNames.size(), sb.toString())
                + (remaining > 0 ? context.getString(R.string.andNMoreApplications, remaining) : "");
        showToast(context, message);
    }

    public static void forceStop(Context context, String[] pkgNameList) {
        if (pkgNameList == null || pkgNameList.length == 0 || context == null) {
            return;
        }
        String currentPackage = " ";
        if (avoidFreezeForegroundApplications.getValue(null)) {
            currentPackage = MainApplication.getCurrentPackage();
        }
        if (currentPackage == null) currentPackage = " ";

        int apiMode = -1;
        try {
            String modeValue = selectFUFMode.getValue(context);
            if (modeValue != null) apiMode = Integer.parseInt(modeValue);
        } catch (NumberFormatException ignored) {
        }

        boolean shizukuMode = apiMode >= 8 && apiMode <= 10;
        boolean shizukuAvailable = PrivilegedShellUtils.isShizukuAvailable(context);
        if (shizukuMode && !shizukuAvailable) {
            int waited = 0;
            while (!PrivilegedShellUtils.isShizukuAvailable(context) && waited < 3000) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }
                waited += 50;
            }
            shizukuAvailable = PrivilegedShellUtils.isShizukuAvailable(context);
        }

        boolean rootAvailable = RootUtils.checkRootPermission();
        PrivilegedShellUtils.Backend backend = resolveBackend(shizukuMode, shizukuAvailable, rootAvailable);
        if (backend == null) {
            showToast(context, R.string.mayUnrootedOrOtherEx);
            return;
        }

        List<String> targets = FUFUtils.batchTargetsWithoutSelf(context.getPackageName(), pkgNameList);
        List<String> executableTargets = new ArrayList<>();
        for (String pkg : targets) {
            if (FUFUtils.isAvoidFreezeNotifyingApplicationsEnabledAndAppStillNotifying(pkg)) {
                FUFUtils.checkAndShowAppStillNotifyingToast(context, pkg);
            } else if (currentPackage.equals(pkg)) {
                FUFUtils.checkAndShowAppIsForegroundApplicationToast(context, pkg);
            } else {
                executableTargets.add(pkg);
            }
        }

        if (executableTargets.isEmpty()) {
            return;
        }

        List<String> commands = buildForceStopCommands(executableTargets);
        PrivilegedShellUtils.BatchResult result;
        try {
            result = PrivilegedShellUtils.executeBatchPrivilegedCommand(commands, context, backend);
        } catch (Exception e) {
            e.printStackTrace();
            showToast(context, context.getString(R.string.exception) + e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("permission denied") || msg.contains("not found")) {
                showToast(context, R.string.mayUnrooted);
            }
            return;
        }

        if (!result.sessionStarted) {
            showToast(context, R.string.mayUnrootedOrOtherEx);
            String msg = result.error != null ? result.error.toLowerCase() : "";
            if (msg.contains("permission denied") || msg.contains("not found")) {
                showToast(context, R.string.mayUnrooted);
            }
            return;
        }

        ForceStopOutcomes outcomes = resolveOutcomes(executableTargets, result);
        if (!outcomes.succeeded.isEmpty()) {
            FUFUtils.sendStatusChangedBroadcast(context);
        }
        if (!outcomes.failed.isEmpty()) {
            showFailedApplicationsToast(context, outcomes.failed);
        } else if (!lesserToast.getValue(null)) {
            showToast(context, R.string.executed);
        }
    }
}
