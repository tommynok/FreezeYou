package cf.playhi.freezeyou.utils;

import android.os.Process;

/** Converts Android app UIDs to their user IDs. */
public final class AndroidUserUtils {

    // Android reserves one contiguous range of 100,000 UIDs for each user.
    private static final int PER_USER_RANGE = 100000;

    private AndroidUserUtils() {}

    public static int currentUserId() {
        return userIdFromUid(Process.myUid());
    }

    public static int userIdFromUid(int uid) {
        if (uid < 0) throw new IllegalArgumentException("UID must not be negative");
        return uid / PER_USER_RANGE;
    }
}
