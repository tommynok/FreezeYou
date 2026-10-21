package cf.playhi.freezeyou.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.utils.ApplicationIconUtils;
import cf.playhi.freezeyou.utils.ApplicationInfoUtils;

import static cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.cacheApplicationsIcons;

/**
 * Loads launcher icons off the UI thread and keeps them in a bounded memory cache.
 *
 * <p>The application list used to decode every icon while it was being built, which meant a few
 * hundred {@code getApplicationIcon} calls plus a PNG encode or decode per app before the first
 * frame, and the bitmaps stayed reachable for the lifetime of the activity because the list data
 * held them. Here only the rows that are actually on screen cost anything, and the cache is capped
 * by bytes rather than by how long the screen has been up.
 */
public final class AppIconLoader {

    /** An icon arriving on the UI thread. */
    public interface IconReceiver {
        void onIcon(@Nullable Drawable icon);
    }

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final int CACHE_BYTES =
            (int) Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 8L);

    private static final LruCache<String, Drawable> ICON_CACHE =
            new LruCache<String, Drawable>(CACHE_BYTES) {
                @Override
                protected int sizeOf(@NonNull String key, @NonNull Drawable value) {
                    return drawableBytes(value);
                }
            };

    /**
     * Two cores, because icon work is mostly binder and file IO, with a couple more allowed once a
     * fast scroll queues up. The queue is unbounded, so rejects never happen and a burst of
     * requests cannot drop an icon the user is looking at; stale requests are cancelled by the
     * adapter when the row is recycled instead.
     */
    private static final ThreadPoolExecutor EXECUTOR = buildExecutor();

    private AppIconLoader() {
    }

    /**
     * @param iconCacheKey the package to load, or the empty string for a row that names no package
     * @return the pending work, which the caller cancels when the row it asked for goes away
     */
    @NonNull
    public static Future<?> load(
            @NonNull Context context,
            @NonNull String iconCacheKey,
            @NonNull IconReceiver receiver
    ) {
        if (iconCacheKey.isEmpty()) {
            // A row that names no package is the "nothing here" placeholder, and it always showed
            // the system's default app icon rather than this application's own.
            Drawable defIcon = context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
            if (Looper.myLooper() == Looper.getMainLooper()) {
                receiver.onIcon(defIcon);
            } else {
                MAIN_HANDLER.post(() -> receiver.onIcon(defIcon));
            }
            return DONE;
        }
        Drawable cached = ICON_CACHE.get(iconCacheKey);
        if (cached != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                receiver.onIcon(cached);
            } else {
                MAIN_HANDLER.post(() -> receiver.onIcon(cached));
            }
            return DONE;
        }
        final Context applicationContext = context.getApplicationContext();
        return EXECUTOR.submit(new Callable<Drawable>() {
            @Override
            public Drawable call() {
                Drawable icon = decode(applicationContext, iconCacheKey);
                ICON_CACHE.put(iconCacheKey, icon);
                MAIN_HANDLER.post(() -> receiver.onIcon(icon));
                return icon;
            }
        });
    }

    /**
     * Drops one icon. An app that was updated or reinstalled keeps its package name, so the cache
     * key alone cannot tell the old icon from the new one.
     */
    public static void evict(@NonNull String iconCacheKey) {
        ICON_CACHE.remove(iconCacheKey);
    }

    public static void evictAll() {
        ICON_CACHE.evictAll();
    }

    @NonNull
    private static Drawable decode(@NonNull Context context, @NonNull String pkgName) {
        Drawable icon = ApplicationIconUtils.getApplicationIcon(
                context,
                pkgName,
                ApplicationInfoUtils.getApplicationInfoFromPkgName(pkgName, context),
                false,
                cacheApplicationsIcons.getValue(context)
        );
        return icon == null ? defaultIcon(context) : icon;
    }

    @NonNull
    private static Drawable defaultIcon(@NonNull Context context) {
        return context.getResources().getDrawable(R.mipmap.ic_launcher_round);
    }

    private static int drawableBytes(@NonNull Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null) {
                return Math.max(1, bitmap.getByteCount());
            }
        }
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            return 1;
        }
        return Math.max(1, width * height * 4);
    }

    @NonNull
    private static ThreadPoolExecutor buildExecutor() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "app-icon-loader");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                4, 4, 15L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), factory
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /** Returned when the answer was already known, so callers always get something to hold. */
    private static final Future<?> DONE = new CompletedFuture();

    private static final class CompletedFuture implements Future<Object> {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Nullable
        @Override
        public Object get() {
            return null;
        }

        @Nullable
        @Override
        public Object get(long timeout, @NonNull TimeUnit unit) {
            return null;
        }
    }
}
