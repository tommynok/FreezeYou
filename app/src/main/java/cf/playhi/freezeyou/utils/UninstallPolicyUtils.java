package cf.playhi.freezeyou.utils;

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
}
