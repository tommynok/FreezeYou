package cf.playhi.freezeyou.model;

import android.content.pm.ApplicationInfo;

/**
 * One row of the main application list.
 *
 * <p>It replaces the {@code Map<String, Object>} rows the list used to carry. Those maps held the
 * decoded launcher icon under {@code "Img"}, so a full app list kept several hundred ARGB bitmaps
 * alive for as long as the activity lived, and the freeze indicator was stored twice: as an
 * {@code Integer} drawable resource under {@code "isFrozen"} for the row's dot, and as a
 * {@code Boolean} under {@code "Frozen"} for the greyscale filter. The two had to be written
 * together by hand, and a row that forgot one of them rendered as unfrozen.
 *
 * <p>Instances are immutable so a worker thread can build a new list while the adapter is still
 * reading the old one. Row updates return a copy, which is what lets {@code DiffUtil} find the one
 * changed row instead of re-binding the whole screen.
 */
public final class AppInfoItem {

    private final String packageName;
    private final String label;
    private final boolean frozen;
    private final boolean restorableSystemApp;
    private final long installTime;
    private final long updateTime;
    private final int flags;

    /**
     * Key the icon cache is looked up with. Empty for rows that stand for no package at all, which
     * keeps the loader from asking PackageManager about a display string.
     */
    private final String iconCacheKey;

    public AppInfoItem(
            String packageName,
            String label,
            boolean frozen,
            boolean restorableSystemApp,
            long installTime,
            long updateTime,
            int flags
    ) {
        this(packageName, label, frozen, restorableSystemApp, installTime, updateTime, flags,
                packageName == null ? "" : packageName);
    }

    private AppInfoItem(
            String packageName,
            String label,
            boolean frozen,
            boolean restorableSystemApp,
            long installTime,
            long updateTime,
            int flags,
            String iconCacheKey
    ) {
        this.packageName = packageName == null ? "" : packageName;
        this.label = label;
        this.frozen = frozen;
        this.restorableSystemApp = restorableSystemApp;
        this.installTime = installTime;
        this.updateTime = updateTime;
        this.flags = flags;
        this.iconCacheKey = iconCacheKey == null ? "" : iconCacheKey;
    }

    private AppInfoItem(AppInfoItem source, String label, boolean frozen, boolean restorable) {
        this(source.packageName, label, frozen, restorable, source.installTime, source.updateTime,
                source.flags, source.iconCacheKey);
    }

    /**
     * The row shown when a filter matched nothing. It used to be a map holding
     * {@code android.R.drawable.sym_def_app_icon} as an {@code Integer}; keeping the icon out of
     * the model means the loader's own default is the only place that decides it. The empty cache
     * key is what marks such a row, so nothing here asks PackageManager about a display string.
     */
    public static AppInfoItem placeholder(String label) {
        return new AppInfoItem(label, label, false, false, 0L, 0L, 0, "");
    }

    public String getPackageName() {
        return packageName;
    }

    public String getLabel() {
        return label;
    }

    /** What {@code realGetFrozenStatus} reported, without the uninstalled-system override. */
    public boolean isFrozen() {
        return frozen;
    }

    public boolean isRestorableSystemApp() {
        return restorableSystemApp;
    }

    /**
     * Whether the row should look frozen. An uninstalled system app is listed as frozen whatever
     * the reported status was, which is what the separate {@code "Frozen"} map entry used to
     * encode.
     */
    public boolean isFrozenForDisplay() {
        return frozen || restorableSystemApp;
    }

    public long getInstallTime() {
        return installTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    /** {@code ApplicationInfo.flags} as last read; 0 for a placeholder or a vanished package. */
    public int getFlags() {
        return flags;
    }

    public boolean isSystemApp() {
        return (flags & ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM;
    }

    public String getIconCacheKey() {
        return iconCacheKey;
    }

    /** False only for the placeholder row, whose cache key is empty. */
    public boolean isPackageRow() {
        return !iconCacheKey.isEmpty();
    }

    public AppInfoItem withLabel(String newLabel) {
        return new AppInfoItem(this, newLabel, frozen, restorableSystemApp);
    }

    public AppInfoItem withFrozen(boolean newFrozen) {
        return new AppInfoItem(this, label, newFrozen, restorableSystemApp);
    }

    public AppInfoItem withRestorableSystemApp(boolean newRestorable) {
        return new AppInfoItem(this, label, frozen, newRestorable);
    }

    /**
     * Content comparison for {@code DiffUtil}. The package name is identity, so it is not looked at
     * here; the icon is deliberately absent, because it is loaded per binding and is not data the
     * row owns.
     */
    public boolean sameContentAs(AppInfoItem other) {
        if (other == null) {
            return false;
        }
        return frozen == other.frozen
                && restorableSystemApp == other.restorableSystemApp
                && installTime == other.installTime
                && updateTime == other.updateTime
                && flags == other.flags
                && (label == null ? other.label == null : label.equals(other.label));
    }

    @Override
    public String toString() {
        return packageName + "/" + label;
    }
}
