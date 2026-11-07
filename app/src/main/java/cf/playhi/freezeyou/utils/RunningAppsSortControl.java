package cf.playhi.freezeyou.utils;

import java.text.Collator;
import java.util.Comparator;
import java.util.Map;

public final class RunningAppsSortControl {

    public static final int SORT_BY_DEFAULT = 0;
    public static final int SORT_BY_ALPHABETICAL = 8;
    public static final int SORT_BY_RUNNING_RAM = 11;
    public static final int SORT_BY_RUNNING_CPU = 12;

    private RunningAppsSortControl() {
    }

    public static boolean isRunningFilter(String filter) {
        return "RUN".equals(filter) || "RUN_SYS".equals(filter) || "RUN_USER".equals(filter);
    }

    public static boolean isRunningSortRule(int sortRule) {
        return sortRule == SORT_BY_RUNNING_RAM || sortRule == SORT_BY_RUNNING_CPU;
    }

    public static boolean isSortButtonVisible(String filter) {
        return true;
    }

    public static int resolveSortRuleForFilter(String filter, int generalSortRule, int runningSortRule) {
        return isRunningFilter(filter) ? runningSortRule : generalSortRule;
    }

    public static Comparator<Map<String, Object>> createRamComparator(final Collator collator) {
        return new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> m0, Map<String, Object> m1) {
                RunningAppsUtils.ResourceUsage u0 = (RunningAppsUtils.ResourceUsage) m0.get("RunningUsage");
                RunningAppsUtils.ResourceUsage u1 = (RunningAppsUtils.ResourceUsage) m1.get("RunningUsage");
                Long pss0 = u0 != null ? u0.getPssBytes() : null;
                Long pss1 = u1 != null ? u1.getPssBytes() : null;
                if (pss0 != null && pss1 != null) {
                    int cmp = Long.compare(pss1, pss0);
                    if (cmp != 0) return cmp;
                } else if (pss0 != null) {
                    return -1;
                } else if (pss1 != null) {
                    return 1;
                }

                Double cpu0 = u0 != null ? u0.getCpuPercent() : null;
                Double cpu1 = u1 != null ? u1.getCpuPercent() : null;
                if (cpu0 != null && cpu1 != null) {
                    int cmp = Double.compare(cpu1, cpu0);
                    if (cmp != 0) return cmp;
                } else if (cpu0 != null) {
                    return -1;
                } else if (cpu1 != null) {
                    return 1;
                }

                String name0 = (String) m0.get("Name");
                String name1 = (String) m1.get("Name");
                if (name0 != null && name1 != null) {
                    int cmp = collator.compare(name0, name1);
                    if (cmp != 0) return cmp;
                }

                String pkg0 = (String) m0.get("PackageName");
                String pkg1 = (String) m1.get("PackageName");
                if (pkg0 != null && pkg1 != null) {
                    return pkg0.compareTo(pkg1);
                }
                return 0;
            }
        };
    }

    public static Comparator<Map<String, Object>> createCpuComparator(final Collator collator) {
        return new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> m0, Map<String, Object> m1) {
                RunningAppsUtils.ResourceUsage u0 = (RunningAppsUtils.ResourceUsage) m0.get("RunningUsage");
                RunningAppsUtils.ResourceUsage u1 = (RunningAppsUtils.ResourceUsage) m1.get("RunningUsage");
                Double cpu0 = u0 != null ? u0.getCpuPercent() : null;
                Double cpu1 = u1 != null ? u1.getCpuPercent() : null;
                if (cpu0 != null && cpu1 != null) {
                    int cmp = Double.compare(cpu1, cpu0);
                    if (cmp != 0) return cmp;
                } else if (cpu0 != null) {
                    return -1;
                } else if (cpu1 != null) {
                    return 1;
                }

                Long pss0 = u0 != null ? u0.getPssBytes() : null;
                Long pss1 = u1 != null ? u1.getPssBytes() : null;
                if (pss0 != null && pss1 != null) {
                    int cmp = Long.compare(pss1, pss0);
                    if (cmp != 0) return cmp;
                } else if (pss0 != null) {
                    return -1;
                } else if (pss1 != null) {
                    return 1;
                }

                String name0 = (String) m0.get("Name");
                String name1 = (String) m1.get("Name");
                if (name0 != null && name1 != null) {
                    int cmp = collator.compare(name0, name1);
                    if (cmp != 0) return cmp;
                }

                String pkg0 = (String) m0.get("PackageName");
                String pkg1 = (String) m1.get("PackageName");
                if (pkg0 != null && pkg1 != null) {
                    return pkg0.compareTo(pkg1);
                }
                return 0;
            }
        };
    }
}
