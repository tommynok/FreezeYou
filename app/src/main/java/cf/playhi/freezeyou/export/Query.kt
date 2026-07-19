package cf.playhi.freezeyou.export

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import cf.playhi.freezeyou.export.FUFMode.MODE_DPM
import cf.playhi.freezeyou.export.FUFMode.MODE_LEGACY_AUTO
import cf.playhi.freezeyou.export.FUFMode.MODE_PROFILE_OWNER
import cf.playhi.freezeyou.export.FUFMode.MODE_ROOT_DISABLE_ENABLE
import cf.playhi.freezeyou.export.FUFMode.MODE_ROOT_HIDE_UNHIDE
import cf.playhi.freezeyou.export.FUFMode.MODE_SYSTEM_APP_ENABLE_DISABLE
import cf.playhi.freezeyou.export.FUFMode.MODE_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED
import cf.playhi.freezeyou.export.FUFMode.MODE_SYSTEM_APP_ENABLE_DISABLE_USER
import cf.playhi.freezeyou.export.FUFMode.MODE_UNKNOWN
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_LEGACY_AUTO
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_MROOT_DPM
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_MROOT_PROFILE_OWNER
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_ROOT_DISABLE_ENABLE
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_ROOT_UNHIDE_HIDE
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_USER
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode
import cf.playhi.freezeyou.utils.ApplicationInfoUtils
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils
import cf.playhi.freezeyou.utils.FUFUtils

class Query : ContentProvider() {
    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return 0
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = context
        val bundle = Bundle()
        if (extras == null) {
            return bundle
        }
        val queryPkg = extras.getString("packageName")
        when (method) {
            QUERY_MODE -> {
                if (context != null && DevicePolicyManagerUtils.isDeviceOwner(context)) {
                    bundle.putString("currentMode", "dpm")
                } else if (FUFUtils.checkRootPermission()) {
                    bundle.putString("currentMode", "root")
                } else {
                    bundle.putString("currentMode", "unavailable")
                }
                return bundle
            }
            QUERY_MODE_V2 -> {
                if (context == null) return bundle
                val mode = selectFUFMode.getValue(null) ?: "0"
                when (mode.toInt()) {
                    API_FREEZEYOU_MROOT_DPM -> bundle.putString("currentMode", MODE_DPM)
                    API_FREEZEYOU_ROOT_DISABLE_ENABLE -> bundle.putString(
                        "currentMode",
                        MODE_ROOT_DISABLE_ENABLE
                    )
                    API_FREEZEYOU_ROOT_UNHIDE_HIDE -> bundle.putString(
                        "currentMode",
                        MODE_ROOT_HIDE_UNHIDE
                    )
                    API_FREEZEYOU_LEGACY_AUTO -> bundle.putString("currentMode", MODE_LEGACY_AUTO)
                    API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED -> bundle.putString(
                        "currentMode",
                        MODE_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED
                    )
                    API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_USER -> bundle.putString(
                        "currentMode",
                        MODE_SYSTEM_APP_ENABLE_DISABLE_USER
                    )
                    API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE -> bundle.putString(
                        "currentMode",
                        MODE_SYSTEM_APP_ENABLE_DISABLE
                    )
                    API_FREEZEYOU_MROOT_PROFILE_OWNER -> bundle.putString(
                        "currentMode",
                        MODE_PROFILE_OWNER
                    )
                    else -> bundle.putString("currentMode", MODE_UNKNOWN)
                }
                return bundle
            }
            QUERY_FREEZE_STATUS -> {
                if (context == null) {
                    bundle.putInt("status", -1)
                } else if (queryPkg == null) {
                    bundle.putInt("status", -2)
                } else {
                    if (ApplicationInfoUtils.getApplicationInfoFromPkgName(queryPkg, context) == null) {
                        bundle.putInt("status", 998)
                    } else {
                        val dpmFrozen = FUFUtils.checkMRootFrozen(context, queryPkg)
                        val rootFrozen = FUFUtils.checkRootFrozen(context, queryPkg, null)
                        if (dpmFrozen && rootFrozen) {
                            bundle.putInt("status", 3)
                        } else if (dpmFrozen) {
                            bundle.putInt("status", 2)
                        } else if (rootFrozen) {
                            bundle.putInt("status", 1)
                        } else {
                            bundle.putInt("status", 0)
                        }
                    }
                }
                return bundle
            }
            QUERY_IF_CAN_INSTALL_APPLICATIONS_STATUS -> {
                if (context == null) {
                    bundle.putBooleanArray("status", booleanArrayOf(false, false, false, false)) // 可用状态、installActivityEnabled、hasRootPerm、hasDpmPerm
                } else {
                    val installActivityEnabled: Boolean =
                        when (context.packageManager.getComponentEnabledSetting(
                            ComponentName(context, "cf.playhi.freezeyou.InstallPackagesActivity")
                        )) {
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> false
                            else -> false
                        }
                    val hasDpmPerm = DevicePolicyManagerUtils.isDeviceOwner(context)
                    val hasRootPerm = FUFUtils.checkRootPermission()
                    bundle.putBooleanArray(
                        "status",
                        booleanArrayOf(
                            installActivityEnabled && (hasDpmPerm || hasRootPerm),
                            installActivityEnabled,
                            hasRootPerm,
                            hasDpmPerm
                        )
                    )
                }
                return bundle
            }
            else -> {}
        }
        return bundle
    }

    companion object {
        private const val QUERY_MODE = "QUERY_MODE"
        private const val QUERY_FREEZE_STATUS = "QUERY_FREEZE_STATUS"
        private const val QUERY_IF_CAN_INSTALL_APPLICATIONS_STATUS =
            "QUERY_IF_CAN_INSTALL_APPLICATIONS_STATUS"
        private const val QUERY_MODE_V2 = "QUERY_MODE_V2"
    }
}
