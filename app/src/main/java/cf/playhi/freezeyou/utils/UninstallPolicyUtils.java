package cf.playhi.freezeyou.utils;

import java.util.ArrayList;
import java.util.List;

/** Policy shared by the uninstall dialog and the service that enforces the request. */
public final class UninstallPolicyUtils {

    private UninstallPolicyUtils() {}

    /**
     * In Android (DeletePackageHelper.mayDeletePackageLocked), Device Owner
     * PackageInstaller.uninstall() lacks DELETE_SYSTEM_APP and cannot uninstall factory preinstalled
     * system apps without updates, failing with DELETE_FAILED_INTERNAL_ERROR. Updated system apps
     * also require a two-step operation. Thus, full removal of any system app requires a privileged
     * shell (root or Shizuku).
     */
    public static boolean requiresPrivilegedShellForFullUninstall(
            boolean isSystemApp,
            @SuppressWarnings("unused") boolean hasSystemUpdate,
            boolean uninstallUpdatesOnly) {
        return isSystemApp && !uninstallUpdatesOnly;
    }

    public static boolean isValidUpdatesOnlyRequest(
            boolean isSystemApp,
            boolean hasSystemUpdate,
            boolean uninstallUpdatesOnly) {
        return !uninstallUpdatesOnly || (isSystemApp && hasSystemUpdate);
    }

    /**
     * Builds the shell sequence for an uninstall. Ordinary apps and non-updated system apps are
     * removed only for the app's user; removing an updated system app first reverts its shared
     * system update, then removes the factory package for the current user.
     */
    public static List<String> buildUninstallCommands(
            boolean isSystemApp,
            boolean hasSystemUpdate,
            boolean uninstallUpdatesOnly,
            int userId,
            String packageName) {
        if (userId < 0) throw new IllegalArgumentException("User ID must not be negative");
        if (!isValidUpdatesOnlyRequest(isSystemApp, hasSystemUpdate, uninstallUpdatesOnly)) {
            throw new IllegalArgumentException("Updates-only removal requires an updated system app");
        }

        String packageArgument = PrivilegedShellUtils.shellQuote(packageName);
        List<String> commands = new ArrayList<>();
        if (uninstallUpdatesOnly) {
            commands.add("pm uninstall " + packageArgument);
        } else {
            if (isSystemApp && hasSystemUpdate) {
                // The updated APK is shared by users, so reverting it is necessarily global.
                commands.add("pm uninstall " + packageArgument);
            }
            commands.add("pm uninstall --user " + userId + " " + packageArgument);
        }
        return commands;
    }

    /** Avoid probing for root when Device Owner alone can perform the requested operation. */
    public static boolean shouldCheckRootPermission(
            boolean deviceOwnerAvailable,
            boolean operationRequiresPrivilegedShell) {
        return !deviceOwnerAvailable || operationRequiresPrivilegedShell;
    }
}
