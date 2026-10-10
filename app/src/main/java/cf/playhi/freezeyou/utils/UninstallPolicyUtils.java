package cf.playhi.freezeyou.utils;

import java.util.ArrayList;
import java.util.List;

/** Policy shared by the uninstall dialog and the service that enforces the request. */
public final class UninstallPolicyUtils {

    private UninstallPolicyUtils() {}

    /**
     * Removing an updated system app in one step cannot be guaranteed by the Device Owner
     * PackageInstaller route; full removal needs the root/Shizuku two-step shell operation.
     */
    public static boolean requiresPrivilegedShellForFullUninstall(
            boolean isSystemApp,
            boolean hasSystemUpdate,
            boolean uninstallUpdatesOnly) {
        return isSystemApp && hasSystemUpdate && !uninstallUpdatesOnly;
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
