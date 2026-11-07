package cf.playhi.freezeyou.utils;

public final class RunningAppsRefreshControl {

    private RunningAppsRefreshControl() {
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
}
