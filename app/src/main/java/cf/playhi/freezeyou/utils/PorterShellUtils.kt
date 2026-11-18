package cf.playhi.freezeyou.utils

import android.content.Context
import android.os.Build
import android.os.IBinder
import android.util.Log
import eu.darken.porter.sdk.PermissionState
import eu.darken.porter.sdk.Porter
import eu.darken.porter.sdk.PorterApiProvider
import eu.darken.porter.sdk.PorterAvailability
import eu.darken.porter.sdk.PorterConnection
import eu.darken.porter.sdk.PorterConnectionLostException
import eu.darken.porter.sdk.extras.PorterSystemServices
import eu.darken.porter.sdk.extras.exec
import eu.darken.porter.sdk.extras.startProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Java-friendly bridge to the Porter SDK, which is Kotlin/coroutines-only.
 *
 * Porter is Shizuku's independent continuation: a server running as ADB shell (UID 2000) or
 * root (UID 0) hands this app a Binder after the user grants permission once. The blocking
 * helpers here follow the same contract as the Shizuku/root paths in [PrivilegedShellUtils]:
 * call them from background threads only.
 *
 * The SDK requires API 24; the app installs on older devices, so every entry point is guarded
 * by [isSupported] and degrades to "unavailable" there.
 */
object PorterShellUtils {

    const val PERMISSION_RESULT_GRANTED = 0
    const val PERMISSION_RESULT_DENIED = 1
    const val PERMISSION_RESULT_PERMANENTLY_DENIED = 2
    const val PERMISSION_RESULT_NOT_CONNECTED = 3
    const val PERMISSION_RESULT_NOT_INSTALLED = 4
    const val PERMISSION_RESULT_INCOMPATIBLE = 5
    const val PERMISSION_RESULT_UNRECOGNIZED = 6
    const val PERMISSION_RESULT_UNSUPPORTED_SDK = 7
    const val PERMISSION_RESULT_OTHER_EXCEPTION = 8

    private const val BINDER_WAIT_MS = 3000L
    private const val BINDER_POLL_MS = 50L

    private val permissionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @JvmStatic
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 24

    /**
     * Obtains the connection in this process. Safe to call in every process, including the one
     * hosting the SDK provider, and safe to call again: it only re-asks the provider process.
     */
    @JvmStatic
    fun init(context: Context) {
        if (!isSupported()) return
        try {
            PorterApiProvider.requestBinderForNonProviderProcess(context.applicationContext)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    @JvmStatic
    fun currentConnection(): PorterConnection? =
        if (isSupported()) Porter.connection.value else null

    /** Cheap check of the held connection and its last known grant; no IPC. */
    @JvmStatic
    fun isPorterAvailable(): Boolean = isPorterAvailable(null)

    /**
     * Like [PrivilegedShellUtils.isShizukuAvailable]: when no connection is held yet, ask the
     * provider process for one (one shot, no waiting) and answer from what is held now.
     */
    @JvmStatic
    fun isPorterAvailable(context: Context?): Boolean {
        if (!isSupported()) return false
        return try {
            if (Porter.connection.value == null && context != null) {
                init(context)
            }
            Porter.connection.value?.permission?.value == PermissionState.Granted
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Waits briefly for a connection, repairing a missing one through the provider the way
     * FUFSinglePackage.ensureBinderAlive does for Shizuku. Background threads only.
     */
    @JvmStatic
    @JvmOverloads
    fun awaitConnectionBlocking(context: Context?, timeoutMs: Long = BINDER_WAIT_MS): PorterConnection? {
        if (!isSupported()) return null
        currentConnection()?.let { return it }
        if (context != null) init(context)
        var waited = 0L
        while (waited < timeoutMs) {
            try {
                Thread.sleep(BINDER_POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
            waited += BINDER_POLL_MS
            currentConnection()?.let { return it }
        }
        return null
    }

    @JvmStatic
    @JvmOverloads
    fun runPorterCommand(
        command: String,
        timeoutMs: Long = PrivilegedShellUtils.DEFAULT_COMMAND_TIMEOUT_MS
    ): PrivilegedShellUtils.CommandResult {
        val start = System.currentTimeMillis()
        val debug = DebugModeUtils.isDebugModeEnabled()
        if (debug) Log.d("FreezeYouDebug", "runPorterCommand starting: $command")
        val connection = currentConnection()
            ?: throw IllegalStateException("Porter is not connected")
        val result = runBlocking {
            withTimeout(timeoutMs) {
                connection.exec("sh", "-c", "($command) 2>&1")
            }
        }
        val output = when {
            result.errors.isEmpty() -> result.output
            result.output.isEmpty() -> result.errors
            else -> result.output + "\n" + result.errors
        }
        if (debug) {
            Log.d(
                "FreezeYouDebug",
                "runPorterCommand finished in " + (System.currentTimeMillis() - start) +
                        " ms, exitCode=" + result.exitCode + ", output=" + output
            )
        }
        return PrivilegedShellUtils.CommandResult(result.exitCode, output)
    }

    /** One `sh -s` session for a batch, the Porter counterpart of Shizuku.newProcess. */
    @JvmStatic
    fun startShellSession(): Process {
        val connection = currentConnection()
            ?: throw IllegalStateException("Porter is not connected")
        return runBlocking { connection.startProcess("sh", "-s") }
    }

    /** One `sh -c` process whose streams the caller reads, as with Shizuku.newProcess. */
    @JvmStatic
    fun startCommandProcess(command: String): Process {
        val connection = currentConnection()
            ?: throw IllegalStateException("Porter is not connected")
        return runBlocking { connection.startProcess("sh", "-c", command) }
    }

    /** A system service Binder re-issued at Porter's identity, for IPackageManager wrapping. */
    @JvmStatic
    fun wrapSystemService(name: String): IBinder? {
        if (!isSupported()) return null
        val connection = currentConnection() ?: return null
        return try {
            PorterSystemServices.getSystemService(name)?.let { connection.wrap(it) }
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    /** 2000 for ADB-backed Porter, 0 for root-backed; null when not connected. */
    @JvmStatic
    fun serverUid(): Int? = currentConnection()?.uid

    /** Why no connection is held, for settings and diagnosis messages. Background threads only. */
    @JvmStatic
    fun availabilityBlocking(context: Context): PorterAvailability? {
        if (!isSupported()) return null
        return try {
            runBlocking { Porter.availability(context) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Checks the grant and, when missing, asks Porter to prompt the user. Asynchronous on
     * purpose: requestPermission suspends until the user answers the dialog, and blocking the
     * main thread that long would ANR. The callback runs on the main thread.
     */
    @JvmStatic
    fun requestPermissionAsync(context: Context, callback: (Int) -> Unit) {
        if (!isSupported()) {
            callback(PERMISSION_RESULT_UNSUPPORTED_SDK)
            return
        }
        val appContext = context.applicationContext
        permissionScope.launch {
            val result = try {
                resolvePermission(appContext)
            } catch (e: Exception) {
                e.printStackTrace()
                PERMISSION_RESULT_OTHER_EXCEPTION
            }
            if (result == PERMISSION_RESULT_GRANTED) init(appContext)
            callback(result)
        }
    }

    private suspend fun resolvePermission(context: Context): Int {
        init(context)
        val connection = awaitConnectionSuspend(context, 5000)
            ?: return when (Porter.availability(context)) {
                PorterAvailability.NotInstalled -> PERMISSION_RESULT_NOT_INSTALLED
                is PorterAvailability.Incompatible -> PERMISSION_RESULT_INCOMPATIBLE
                is PorterAvailability.InstalledUnrecognized -> PERMISSION_RESULT_UNRECOGNIZED
                else -> PERMISSION_RESULT_NOT_CONNECTED
            }
        var state = connection.checkPermission()
        if (state is PermissionState.Denied && !state.permanentlyDenied) {
            state = try {
                connection.requestPermission()
            } catch (e: PorterConnectionLostException) {
                // Porter restarted while the dialog was up; the next connection arrives on its own.
                return PERMISSION_RESULT_NOT_CONNECTED
            }
        }
        return when {
            state == PermissionState.Granted -> PERMISSION_RESULT_GRANTED
            state is PermissionState.Denied && state.permanentlyDenied ->
                PERMISSION_RESULT_PERMANENTLY_DENIED
            else -> PERMISSION_RESULT_DENIED
        }
    }

    private suspend fun awaitConnectionSuspend(context: Context, timeoutMs: Long): PorterConnection? {
        currentConnection()?.let { return it }
        init(context)
        var waited = 0L
        while (waited < timeoutMs) {
            delay(BINDER_POLL_MS)
            waited += BINDER_POLL_MS
            currentConnection()?.let { return it }
        }
        return null
    }
}