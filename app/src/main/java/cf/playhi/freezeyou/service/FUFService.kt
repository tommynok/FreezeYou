package cf.playhi.freezeyou.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseService
import cf.playhi.freezeyou.fuf.FUFSinglePackage
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_LEGACY_AUTO
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_MROOT_DPM
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_ROOT_DISABLE_ENABLE
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.lesserToast
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils.isDeviceOwner
import cf.playhi.freezeyou.utils.FUFUtils
import cf.playhi.freezeyou.utils.FUFUtils.checkMRootFrozen
import cf.playhi.freezeyou.utils.FUFUtils.oneKeyAction
import cf.playhi.freezeyou.utils.FUFUtils.processAction
import cf.playhi.freezeyou.utils.FUFUtils.processMRootAction
import cf.playhi.freezeyou.utils.FUFUtils.processRootAction

class FUFService : FreezeYouBaseService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return super.onStartCommand(null, flags, startId)
        val freeze = intent.getBooleanExtra("freeze", false)
        val context = applicationContext
        val apiMode = selectFUFMode.getValue(null)!!.toInt()
        if (intent.getBooleanExtra("single", false)) {
            val pkgName = intent.getStringExtra("pkgName") ?: ""
            val target = intent.getStringExtra("target")
            val tasks = intent.getStringExtra("tasks")
            val askRun = intent.getBooleanExtra("askRun", false)
            val runImmediately = intent.getBooleanExtra("runImmediately", false)
            @Suppress("DEPRECATION")
            if (apiMode == API_FREEZEYOU_LEGACY_AUTO) {
                if (freeze) {
                    if (isDeviceOwner(context)) {
                        processMRootAction(
                            context, pkgName, target, tasks, true, askRun,
                            false, null, false,
                            !lesserToast.getValue(null)
                        )
                    } else {
                        processRootAction(
                            pkgName, target, tasks, context, false, askRun,
                            false, null, false,
                            !lesserToast.getValue(null)
                        )
                    }
                } else {
                    if (checkMRootFrozen(context, pkgName)) {
                        processMRootAction(
                            context, pkgName, target, tasks, false,
                            askRun, runImmediately, null, false,
                            !lesserToast.getValue(null)
                        )
                    } else {
                        processRootAction(
                            pkgName, target, tasks, context, true, askRun,
                            runImmediately, null, false,
                            !lesserToast.getValue(null)
                        )
                    }
                }
            } else {
                processAction(
                    context, pkgName, apiMode, !freeze,
                    !lesserToast.getValue(null),
                    askRun, target, tasks,
                    runImmediately, null, false
                )
            }
        } else {
            val packages = intent.getStringArrayExtra("packages")
            val decidedApiMode: Int = @Suppress("DEPRECATION")
            if (apiMode == API_FREEZEYOU_LEGACY_AUTO) {
                if (freeze) {
                    if (isDeviceOwner(context)) {
                        API_FREEZEYOU_MROOT_DPM
                    } else {
                        API_FREEZEYOU_ROOT_DISABLE_ENABLE
                    }
                } else {
                    if (FUFUtils.checkRootPermission()) {
                        API_FREEZEYOU_ROOT_DISABLE_ENABLE
                    } else {
                        API_FREEZEYOU_MROOT_DPM
                    }
                }
            } else {
                apiMode
            }
            oneKeyAction(context, freeze, packages, decidedApiMode)
        }
        stopSelf()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    "FreezeAndUnfreeze", getString(R.string.freezeAUF),
                    NotificationManager.IMPORTANCE_NONE
                )
            )
            val mBuilder = Notification.Builder(this, "FreezeAndUnfreeze")
            mBuilder.setSmallIcon(R.drawable.ic_notification)
            mBuilder.setContentText(getString(R.string.freezeAUF))
            startForeground(4, mBuilder.build())
        } else {
            @Suppress("DEPRECATION")
            startForeground(4, Notification())
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
