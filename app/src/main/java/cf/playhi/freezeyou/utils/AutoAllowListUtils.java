package cf.playhi.freezeyou.utils;

import android.content.Context;
import android.util.Base64;

import java.util.ArrayList;
import java.util.List;

import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage;

/**
 * The two whitelists that let a request skip the confirmation dialog: the applications allowed to
 * send freeze/unfreeze URIs, and the applications whose install request is accepted on their own.
 *
 * Both are one string in the multi-process store, so they read as a list here rather than as three
 * hand-rolled copies of the same split-compare-join. Entries are kept exactly as the app has always
 * written them: {@code Base64.encodeToString(pkg.getBytes(), Base64.DEFAULT)} gives a name with a
 * trailing newline, and {@code Base64.decode} tolerates it, so tidying it would stop old entries
 * from matching. Blank entries are dropped - a list that ever held one showed an unnamed row in the
 * manage screen and asked the package manager for a package called "".
 */
public final class AutoAllowListUtils {

    /** Packages allowed to fire a freeze/unfreeze URI without asking. */
    public static final String URI_ALLOW = "uriAutoAllowPkgs_allows";

    /** Packages whose install request is accepted without asking. */
    public static final String INSTALL_ALLOW = "installPkgs_autoAllowPkgs_allows";

    private AutoAllowListUtils() {
    }

    /**
     * @param key one of the two constants above
     * @return the stored list, or an empty string when nothing is allowed yet
     */
    public static String readRaw(String key) {
        String stored = new DefaultMultiProcessMMKVStorage().getString(key, "");
        return stored == null ? "" : stored;
    }

    public static boolean allows(String key, String pkgName) {
        return pkgName != null && splitEntries(readRaw(key)).contains(encode(pkgName));
    }

    /**
     * The whitelisted package names, decoded.
     *
     * Decoding belongs here for the same reason encoding does: an entry is only a package name once
     * it has been run through the same Base64 flags it was written with.
     */
    public static List<String> readPackageNames(String key) {
        List<String> packageNames = new ArrayList<>();
        for (String entry : splitEntries(readRaw(key))) {
            try {
                packageNames.add(new String(Base64.decode(entry, Base64.DEFAULT)));
            } catch (IllegalArgumentException e) {
                // A whitelist is editable text in a backup file. An entry that is not Base64 names
                // no application, and dropping it only means the app asks once more - which is the
                // safe direction for an allow list.
                e.printStackTrace();
            }
        }
        return packageNames;
    }

    /** Add {@code pkgName} to the whitelist unless it is already on it. */
    public static void add(Context context, String key, String pkgName) {
        if (pkgName == null) {
            return;
        }
        final DefaultMultiProcessMMKVStorage storage = new DefaultMultiProcessMMKVStorage();
        storage.lock();
        try {
            List<String> entries = splitEntries(readRaw(key));
            String encoded = encode(pkgName);
            if (!entries.contains(encoded)) {
                entries.add(encoded);
                write(context, key, joinEntries(entries));
            }
        } finally {
            storage.unlock();
        }
    }

    public static void remove(Context context, String key, String pkgName) {
        if (pkgName == null) {
            return;
        }
        final DefaultMultiProcessMMKVStorage storage = new DefaultMultiProcessMMKVStorage();
        storage.lock();
        try {
            List<String> entries = splitEntries(readRaw(key));
            if (entries.remove(encode(pkgName))) {
                write(context, key, joinEntries(entries));
            }
        } finally {
            storage.unlock();
        }
    }

    /**
     * Replace a whole whitelist, which is what a restore does: the file being imported is the list,
     * not an edit to the one on the device.
     */
    public static void write(Context context, String key, String entries) {
        new DefaultMultiProcessMMKVStorage()
                .putString(key, entries == null ? "" : entries)
                .sync();
    }

    private static String encode(String pkgName) {
        return Base64.encodeToString(pkgName.getBytes(), Base64.DEFAULT);
    }

    private static List<String> splitEntries(String stored) {
        List<String> entries = new ArrayList<>();
        for (String entry : stored.split(",")) {
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static String joinEntries(List<String> entries) {
        StringBuilder builder = new StringBuilder();
        for (String entry : entries) {
            builder.append(entry).append(',');
        }
        return builder.toString();
    }
}
