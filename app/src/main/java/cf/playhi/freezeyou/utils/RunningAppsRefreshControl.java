package cf.playhi.freezeyou.utils;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RunningAppsRefreshControl {

    private RunningAppsRefreshControl() {
    }

    /**
     * Drops the rows whose package is no longer running, in place, keeping every other row and
     * its order. A force-stop or freeze on a running tab only ever takes rows away, so this is
     * the whole update the list needs: no spinner, no scroll reset, no second pass over the
     * installed packages and their metrics.
     * <p>
     * An empty running set is a scan that failed (no su, Shizuku binder not up yet), not a device
     * with nothing running — pruning on it would empty the list over a transient fault. The
     * "not available" footer is not a package and is always kept.
     *
     * @return true when at least one row was removed.
     */
    public static boolean pruneStoppedRows(
            List<Map<String, Object>> rows, Set<String> runningPackages, String notAvailableLabel) {
        if (rows == null || runningPackages == null || runningPackages.isEmpty()) {
            return false;
        }
        boolean removed = false;
        Iterator<Map<String, Object>> iterator = rows.iterator();
        while (iterator.hasNext()) {
            Object value = iterator.next().get("PackageName");
            if (!(value instanceof String)) {
                continue;
            }
            String packageName = (String) value;
            if (packageName.equals(notAvailableLabel)) {
                continue;
            }
            if (!runningPackages.contains(packageName)) {
                iterator.remove();
                removed = true;
            }
        }
        return removed;
    }

    public static boolean isRunningFilter(String filter) {
        return "RUN".equals(filter) || "RUN_SYS".equals(filter) || "RUN_USER".equals(filter);
    }

    public static boolean canTriggerRefresh(String filter, boolean generationRunning) {
        return isRunningFilter(filter) && !generationRunning;
    }

    public static boolean isButtonVisible(String filter, boolean generationRunning) {
        return canTriggerRefresh(filter, generationRunning);
    }

    public static boolean isProgressBarVisible(String filter, boolean generationRunning) {
        return isRunningFilter(filter) && generationRunning;
    }

    /**
     * A refresh of a running tab that already has rows keeps them on screen under the touch
     * blocker while the new list is built. Only a re-render of the same running filter qualifies:
     * a tab switch still shows the loading screen, and an empty list has nothing to keep.
     */
    public static boolean canKeepRenderedList(String filter, String renderedFilter, boolean hasRows) {
        return isRunningFilter(filter) && filter.equals(renderedFilter) && hasRows;
    }
}
