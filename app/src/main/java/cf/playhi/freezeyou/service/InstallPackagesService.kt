package cf.playhi.freezeyou.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import cf.playhi.freezeyou.MainApplication
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseService
import cf.playhi.freezeyou.receiver.InstallPackagesFinishedReceiver
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.tryDelApkAfterInstalled
import cf.playhi.freezeyou.utils.*
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable
import cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel
import cf.playhi.freezeyou.utils.ProcessUtils.destroyProcess
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import java.io.*
import java.util.*

// Install and uninstall
class InstallPackagesService : FreezeYouBaseService() {
    private val intentArrayList = ArrayList<Intent>()
    private var processing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return super.onStartCommand(null, flags, startId)
        val i = Intent(intent)
        i.putExtra("requestTime", Date().time)
        val packageInfoParcelable = i.getParcelableExtra<Parcelable>("packageInfo")
        val packageInfo = if (packageInfoParcelable is PackageInfo) packageInfoParcelable else null
        if (i.getBooleanExtra("waitForLeaving", false)
            && packageInfo != null && packageInfo.packageName != null && packageInfo.packageName == MainApplication.currentPackage
        ) {
            MainApplication.waitingForLeavingToInstallApplicationIntent = i
            InstallPackagesUtils.postWaitingForLeavingToInstallApplicationNotification(
                this,
                packageInfo
            )
            if (!processing) stopSelf()
        } else {
            if (processing) {
                intentArrayList.add(i)
            } else {
                Thread { installAndUninstall(i) }.start()
            }
        }
        return super.onStartCommand(i, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    "InstallPackages",
                    getString(R.string.installAndUninstall), NotificationManager.IMPORTANCE_NONE
                )
            )
            val mBuilder = Notification.Builder(this, "InstallPackages")
            mBuilder.setSmallIcon(R.drawable.ic_notification)
            mBuilder.setContentText(getString(R.string.installAndUninstall))
            startForeground(5, mBuilder.build())
        } else {
            @Suppress("DEPRECATION")
            val mBuilder = Notification.Builder(this)
            mBuilder.setSmallIcon(R.drawable.ic_notification)
            mBuilder.setContentText(getString(R.string.installAndUninstall))
            startForeground(5, mBuilder.build())
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun installAndUninstall(intent: Intent) {
        processing = true
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(
            this,
            "InstallPackages"
        ) else Notification.Builder(this)
        builder.setSmallIcon(R.drawable.ic_notification)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        if (intent.getBooleanExtra("install", true)) { //Install
            install(intent, builder, notificationManager)
        } else { //Uninstall
            uninstall(intent, builder, notificationManager)
        }

        //移除已完成的
        intentArrayList.remove(intent)
        checkIfAllTaskDoneAndStopSelf()
    }

    private fun uninstall(
        intent: Intent,
        builder: Notification.Builder,
        notificationManager: NotificationManager?
    ) {
        val packageUri = intent.getParcelableExtra<Uri>("packageUri")
        val packageName = packageUri?.encodedSchemeSpecificPart // 应用包名
        val willBeUninstalledName = getApplicationLabel(this, null, null, packageName) // 应用名称
        try {
            if (packageName == null) {
                Handler(Looper.getMainLooper()).post {
                    showToast(
                        applicationContext,
                        getString(R.string.invalidArguments) + " " + packageUri
                    )
                }
                return
            }
            val willBeUninstalledIcon = getApplicationIcon(
                this,
                packageName,
                ApplicationInfoUtils.getApplicationInfoFromPkgName(packageName, this),
                false
            )
            builder.setContentTitle(
                String.format(
                    getString(R.string.uninstalling_app),
                    willBeUninstalledName
                )
            )
            builder.setLargeIcon(getBitmapFromDrawable(willBeUninstalledIcon))
            notificationManager!!.notify(
                ("$packageName@InstallPackagesNotification").hashCode(),
                builder.build()
            )
            if (DevicePolicyManagerUtils.isDeviceOwner(this)) {
                packageManager.packageInstaller.uninstall(
                    packageName,
                    PendingIntent.getBroadcast(
                        this, packageName.hashCode(),
                        Intent(
                            this,
                            InstallPackagesFinishedReceiver::class.java
                        )
                            .putExtra("name", willBeUninstalledName)
                            .putExtra("pkgName", packageName)
                            .putExtra("install", false),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
                    )
                        .intentSender
                )
            } else {
                // Root Mode
                val process = Runtime.getRuntime().exec("su")
                val outputStream = DataOutputStream(process.outputStream)
                outputStream.writeBytes("pm uninstall -k \"$packageName\"\n")
                outputStream.writeBytes("exit\n")
                outputStream.flush()
                process.waitFor()
                destroyProcess(outputStream, process)
                InstallPackagesUtils
                    .notifyFinishNotification(
                        this, notificationManager, builder,
                        false,
                        packageName,
                        String.format(
                            getString(R.string.app_uninstallFinished),
                            willBeUninstalledName
                        ),
                        null,
                        true
                    )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            InstallPackagesUtils
                .notifyFinishNotification(
                    this, notificationManager!!, builder,
                    false,
                    packageName,
                    getString(R.string.uninstallFailed),
                    e.localizedMessage,
                    false
                )
            Handler(Looper.getMainLooper()).post {
                showToast(
                    applicationContext,
                    String.format(
                        getString(R.string.errorUninstallToast),
                        e.localizedMessage
                    )
                )
            }
        }
    }

    private fun install(
        intent: Intent,
        builder: Notification.Builder,
        notificationManager: NotificationManager?
    ) {
        try {
            var apkFilePath = intent.getStringExtra("apkFilePath")
            if (apkFilePath == null || "" == apkFilePath || !File(apkFilePath).exists()) {
                val packageUri = intent.getParcelableExtra<Uri>("packageUri") ?: return
                val `in` = contentResolver.openInputStream(packageUri) ?: return
                val apkFileName = "package" + Date().time + "F.apk"
                apkFilePath = externalCacheDir.toString() + File.separator + "ZDF-" + apkFileName
                FileUtils.copyFile(`in`, apkFilePath)
            }
            val pm = packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkFilePath, 0) ?: return
            val willBeInstalledPackageName = packageInfo.packageName
            val applicationInfo = packageInfo.applicationInfo ?: return
            applicationInfo.sourceDir = apkFilePath
            applicationInfo.publicSourceDir = apkFilePath
            val willBeInstalledName = pm.getApplicationLabel(applicationInfo).toString()
            val willBeInstalledIcon = pm.getApplicationIcon(applicationInfo)
            builder.setContentTitle(
                String.format(
                    getString(R.string.installing_app),
                    willBeInstalledName
                )
            )
            builder.setProgress(100, 0, true)
            builder.setLargeIcon(getBitmapFromDrawable(willBeInstalledIcon))
            notificationManager!!.notify(
                ("$willBeInstalledPackageName@InstallPackagesNotification").hashCode(),
                builder.build()
            )
            if (DevicePolicyManagerUtils.isDeviceOwner(this)) {
                val packageInstaller = pm.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                params.setAppPackageName(willBeInstalledPackageName)
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)
                val outputStream = session.openWrite(
                    apkFilePath.hashCode().toString(), 0, -1
                )
                val in1 = FileInputStream(apkFilePath)
                val buffer = ByteArray(1024 * 1024)
                var bytesRead: Int
                while (in1.read(buffer).also { bytesRead = it } >= 0) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                in1.close()
                session.fsync(outputStream)
                outputStream.close()
                session.commit(
                    PendingIntent.getBroadcast(
                        this, sessionId,
                        Intent(
                            this,
                            InstallPackagesFinishedReceiver::class.java
                        ).apply {
                            putExtra("name", willBeInstalledName)
                            putExtra("pkgName", willBeInstalledPackageName)
                            putExtra("apkFilePath", apkFilePath)
                        },
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
                    ).intentSender
                )
            } else {
                // Root Mode
                var result: String? = null
                try {
                    val process = Runtime.getRuntime().exec("su")
                    val outputStream = DataOutputStream(process.outputStream)
                    outputStream.writeBytes("pm install -r \"$apkFilePath\"\n")
                    outputStream.writeBytes("exit\n")
                    outputStream.flush()
                    process.waitFor()

                    // Delete Temp File
                    InstallPackagesUtils.deleteTempFile(this, apkFilePath, false)
                    val pi = process.inputStream
                    val bufferedReader = BufferedReader(InputStreamReader(pi))
                    result = bufferedReader.readLine()
                    destroyProcess(outputStream, process)
                } finally {
                    if (result != null && result.lowercase(Locale.getDefault()).contains("success")) {
                        InstallPackagesUtils
                            .notifyFinishNotification(
                                this, notificationManager, builder,
                                true,
                                willBeInstalledPackageName,
                                String.format(
                                    getString(R.string.app_installFinished),
                                    willBeInstalledName
                                ),
                                null,
                                true
                            )
                        if (tryDelApkAfterInstalled.getValue(null)) {
                            InstallPackagesUtils.deleteTempFile(this, apkFilePath, true)
                        }
                    } else {
                        InstallPackagesUtils
                            .notifyFinishNotification(
                                this, notificationManager, builder,
                                true,
                                willBeInstalledPackageName,
                                String.format(
                                    getString(R.string.app_installFailed),
                                    willBeInstalledName
                                ),
                                String.format(getString(R.string.reason_colon), result),
                                false
                            )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            InstallPackagesUtils
                .notifyFinishNotification(
                    this, notificationManager!!, builder,
                    true,
                    null,
                    getString(R.string.installFailed),
                    e.localizedMessage,
                    false
                )
            Handler(Looper.getMainLooper()).post {
                showToast(
                    applicationContext,
                    String.format(
                        getString(R.string.errorInstallToast),
                        e.localizedMessage
                    )
                )
            }
        }
    }

    private fun checkIfAllTaskDoneAndStopSelf() {
        if (intentArrayList.isEmpty()) {
            processing = false
            stopSelf()
        } else {
            installAndUninstall(intentArrayList[0])
        }
    }
}
