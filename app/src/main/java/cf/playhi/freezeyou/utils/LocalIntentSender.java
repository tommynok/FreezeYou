package cf.playhi.freezeyou.utils;

import android.content.Intent;
import android.content.IntentSender;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An {@link IntentSender} that delivers its intent in-process, for receiving
 * {@link android.content.pm.PackageInstaller} results without a BroadcastReceiver.
 *
 * Implements the IIntentSender protocol as a direct Binder, identical to Canta's architecture.
 */
public class LocalIntentSender extends Binder {

    private static final String DESCRIPTOR = "android.content.IIntentSender";

    private static final String[] HIDDEN_API_PREFIXES = new String[] {
            "Landroid/content/pm/",
            "Landroid/content/IIntentSender",
            "Landroid/content/IntentSender;"
    };

    private static volatile boolean sHiddenApisAllowed = false;

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final AtomicReference<Intent> mResult = new AtomicReference<>(null);

    private IntentSender mIntentSender;

    public static synchronized void allowHiddenApis() {
        if (sHiddenApisAllowed) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions(HIDDEN_API_PREFIXES);
            } catch (Throwable t) {
                Log.w("FreezeYouDebug", "HiddenApiBypass exemptions failed: " + t.getMessage());
            }
        }
        sHiddenApisAllowed = true;
    }

    public synchronized IntentSender getIntentSender() throws Exception {
        if (mIntentSender != null) {
            return mIntentSender;
        }
        allowHiddenApis();
        Class<?> stubClass = Class.forName(DESCRIPTOR + "$Stub");
        Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
        Object target = asInterface.invoke(null, this);
        Constructor<IntentSender> ctor = IntentSender.class.getConstructor(Class.forName(DESCRIPTOR));
        mIntentSender = ctor.newInstance(target);
        return mIntentSender;
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code != FIRST_CALL_TRANSACTION) {
            return super.onTransact(code, data, reply, flags);
        }
        data.enforceInterface(DESCRIPTOR);
        data.readInt(); // result code, unused by PackageInstaller
        if (data.readInt() != 0) {
            Intent intent = Intent.CREATOR.createFromParcel(data);
            mResult.set(intent);
            mLatch.countDown();
        }
        return true;
    }

    /**
     * Awaits delivery of the Intent.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @return the delivered Intent, or null if timed out or interrupted
     */
    public Intent await(long timeoutMs) {
        try {
            boolean done = mLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) {
                return null;
            }
            return mResult.get();
        } catch (InterruptedException e) {
            return null;
        }
    }
}
