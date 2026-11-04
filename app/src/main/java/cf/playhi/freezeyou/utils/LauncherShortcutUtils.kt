package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.allowEditWhenCreateShortcut
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import cf.playhi.freezeyou.ui.LauncherShortcutConfirmAndGenerateActivity
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import org.json.JSONArray
import org.json.JSONObject

object LauncherShortcutUtils {

    const val KEY_SAVED_SHORTCUTS = "savedLauncherShortcuts"

    val ALLOWED_SHORTCUT_CLASSES: Set<String> = setOf(
        "cf.playhi.freezeyou.Freeze",
        "cf.playhi.freezeyou.ForceStop",
        "cf.playhi.freezeyou.OneKeyFreeze",
        "cf.playhi.freezeyou.OneKeyUF",
        "cf.playhi.freezeyou.ui.OneKeyScreenLockImmediatelyActivity",
        "cf.playhi.freezeyou.OneKeyScreenLockImmediatelyActivity",
        "cf.playhi.freezeyou.Main"
    )

    @JvmStatic
    fun isAllowedShortcutClass(cls: String?): Boolean {
        return cls != null && ALLOWED_SHORTCUT_CLASSES.contains(cls)
    }

    data class ShortcutRecord(
        val id: String,
        val name: String,
        val pkgName: String,
        val cls: String,
        val target: String? = null,
        val tasks: String? = null,
        val justLaunch: Boolean = false
    )

    /**
     * @param title Shortcut label
     * @param pkgName Package name
     * @param icon Shortcut icon, drawable
     * @param cls Dest class
     * @param id Unique string
     * @param context Context
     */
    @JvmStatic
    fun checkSettingsAndRequestCreateShortcut(
        title: String,
        pkgName: String,
        icon: Drawable,
        cls: Class<*>,
        id: String,
        context: Context
    ) {
        if (allowEditWhenCreateShortcut.getValue(context)) {
            context.startActivity(
                Intent(
                    context, LauncherShortcutConfirmAndGenerateActivity::class.java
                )
                    .putExtra("pkgName", pkgName)
                    .putExtra("name", title)
                    .putExtra("id", id)
                    .putExtra("class", cls)
            )
        } else {
            createShortCut(title, pkgName, icon, cls, id, context)
        }
    }

    /**
     * @param title Shortcut label
     * @param pkgName Package name
     * @param icon Shortcut icon, drawable
     * @param cls Dest class
     * @param id Unique string
     * @param context Context
     * @param target Target activity. If null, launch the application directly.
     * @param tasks Attached task(s)
     * @param justLaunch Start the target and nothing else — no freeze, no unfreeze, no dialog.
     */
    @JvmStatic
    @JvmOverloads
    fun createShortCut(
        title: String,
        pkgName: String,
        icon: Drawable,
        cls: Class<*>,
        id: String,
        context: Context,
        target: String? = null,
        tasks: String? = null,
        justLaunch: Boolean = false
    ): Boolean {
        val intent = Intent(context, cls)
            .setAction(Intent.ACTION_MAIN)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("pkgName", pkgName)
            .putExtra("target", target)
            .putExtra("tasks", tasks)
            .putExtra("justLaunch", justLaunch)
        val action = when (cls.name) {
            "cf.playhi.freezeyou.Freeze" -> ShortcutTokenUtils.ACTION_FREEZE
            "cf.playhi.freezeyou.ForceStop" -> ShortcutTokenUtils.ACTION_FORCE_STOP
            "cf.playhi.freezeyou.OneKeyFreeze" -> ShortcutTokenUtils.ACTION_ONE_KEY_FREEZE
            "cf.playhi.freezeyou.OneKeyUF" -> ShortcutTokenUtils.ACTION_ONE_KEY_UF
            "cf.playhi.freezeyou.ui.OneKeyScreenLockImmediatelyActivity",
            "cf.playhi.freezeyou.OneKeyScreenLockImmediatelyActivity" -> ShortcutTokenUtils.ACTION_LOCK_SCREEN
            else -> ShortcutTokenUtils.ACTION_FREEZE
        }
        ShortcutTokenUtils.attachToken(intent, action)
        val shortcutId = id.ifBlank { "FreezeYou! ${pkgName}_${System.currentTimeMillis()}" }
        val requested = requestCreateShortCut(
            title,
            intent,
            icon, shortcutId, context, null
        )
        if (requested) {
            saveShortcutRecord(
                context,
                ShortcutRecord(
                    id = shortcutId,
                    name = title,
                    pkgName = pkgName,
                    cls = cls.name,
                    target = target,
                    tasks = tasks,
                    justLaunch = justLaunch
                )
            )
        }
        return requested
    }

    /**
     * One of `icon` and `bm` cannot be null,
     * if both `icon` and `bm` is not null,
     * `bm` will be used as the shortcut icon.
     *
     * @param title Shortcut label
     * @param intent Intent which contains `pkgName`, `target` and `tasks`.
     * @param icon Shortcut icon, drawable
     * @param id Unique string
     * @param context Context
     * @param bm Shortcut icon, bitmap
     */
    @JvmStatic
    fun requestCreateShortCut(
        title: String,
        intent: Intent,
        icon: Drawable?,
        id: String,
        context: Context,
        bm: Bitmap?
    ): Boolean {
        if (!intent.hasExtra(ShortcutTokenUtils.EXTRA_AUTH_TOKEN)) {
            val component = intent.component
            val action = when (component?.className) {
                "cf.playhi.freezeyou.Freeze" -> ShortcutTokenUtils.ACTION_FREEZE
                "cf.playhi.freezeyou.ForceStop" -> ShortcutTokenUtils.ACTION_FORCE_STOP
                "cf.playhi.freezeyou.OneKeyFreeze" -> ShortcutTokenUtils.ACTION_ONE_KEY_FREEZE
                "cf.playhi.freezeyou.OneKeyUF" -> ShortcutTokenUtils.ACTION_ONE_KEY_UF
                "cf.playhi.freezeyou.ui.OneKeyScreenLockImmediatelyActivity",
                "cf.playhi.freezeyou.OneKeyScreenLockImmediatelyActivity" -> ShortcutTokenUtils.ACTION_LOCK_SCREEN
                else -> ShortcutTokenUtils.ACTION_FREEZE
            }
            ShortcutTokenUtils.attachToken(intent, action)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return requestCreateShortCutOldApi(title, intent, icon, context, bm)
        } else {
            val mShortcutManager: ShortcutManager? = context.getSystemService(
                ShortcutManager::class.java
            )
            if (mShortcutManager != null) {
                if (mShortcutManager.isRequestPinShortcutSupported) {
                    val shortcutId = id.ifBlank { java.util.UUID.randomUUID().toString() }
                    val shortcutInfoBuilder = ShortcutInfo.Builder(context, shortcutId)
                        .setIcon(
                            Icon.createWithBitmap(
                                bm ?: getBitmapFromDrawable(icon)
                            )
                        )
                        .setIntent(intent)
                        .setShortLabel(title.ifEmpty { " " })
                        .setLongLabel(title.ifEmpty { " " })

                    val pinShortcutInfo = shortcutInfoBuilder.build()
                    return try {
                        mShortcutManager.requestPinShortcut(pinShortcutInfo, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast(context, context.getString(R.string.requestFailed) + e.message)
                        false
                    }
                } else {
                    return requestCreateShortCutOldApi(title, intent, icon, context, bm)
                }
            } else {
                return requestCreateShortCutOldApi(title, intent, icon, context, bm)
            }
        }
    }

    /**
     * At least one of bm or icon must be non-null; if both are present, bm is used.
     *
     * @param title   Title
     * @param intent  Intent
     * @param icon    Icon Drawable
     * @param context Context
     * @param bm      Bitmap icon
     */
    private fun requestCreateShortCutOldApi(
        title: String,
        intent: Intent,
        icon: Drawable?,
        context: Context,
        bm: Bitmap?
    ): Boolean {
        val addShortCut = Intent("com.android.launcher.action.INSTALL_SHORTCUT")

        @Suppress("DEPRECATION")
        addShortCut.putExtra(Intent.EXTRA_SHORTCUT_NAME, title)

        @Suppress("DEPRECATION")
        addShortCut.putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent)

        return try {
            val bitmap: Bitmap = bm ?: getBitmapFromDrawable(icon)
            var size = context.resources.getDimension(android.R.dimen.app_icon_size)
            if (size < 1) {
                size = 72f
            }
            if (bitmap.height > size) {
                val matrix = Matrix()
                val scaleWidth = size / bitmap.width
                val scaleHeight = size / bitmap.height
                matrix.postScale(scaleWidth, scaleHeight)
                addShortCut.putExtra(
                    @Suppress("DEPRECATION")
                    Intent.EXTRA_SHORTCUT_ICON,
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                )
            } else {
                @Suppress("DEPRECATION")
                addShortCut.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)
            }
            context.sendBroadcast(addShortCut)
            showToast(context, R.string.requested)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, context.getString(R.string.requestFailed) + e.message)
            false
        }
    }

    @JvmStatic
    fun getSavedShortcutRecords(context: Context): List<ShortcutRecord> {
        val storage = DefaultMultiProcessMMKVStorage()
        val jsonStr = storage.getString(KEY_SAVED_SHORTCUTS, null) ?: return emptyList()
        return parseShortcutRecords(jsonStr)
    }

    @JvmStatic
    fun parseShortcutRecords(jsonStr: String): List<ShortcutRecord> {
        val list = ArrayList<ShortcutRecord>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                val pkgName = obj.optString("pkgName", "")
                val cls = obj.optString("cls", "")
                if (name.isNotEmpty() && pkgName.isNotEmpty() && isAllowedShortcutClass(cls)) {
                    list.add(
                        ShortcutRecord(
                            id = if (id.isNotEmpty()) id else "FreezeYou! ${pkgName}_$i",
                            name = name,
                            pkgName = pkgName,
                            cls = cls,
                            target = if (obj.has("target") && !obj.isNull("target")) obj.optString("target") else null,
                            tasks = if (obj.has("tasks") && !obj.isNull("tasks")) obj.optString("tasks") else null,
                            justLaunch = obj.optBoolean("justLaunch", false)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @JvmStatic
    fun saveShortcutRecord(context: Context, record: ShortcutRecord) {
        val storage = DefaultMultiProcessMMKVStorage()
        try {
            storage.lock()
            val existing = getSavedShortcutRecords(context).toMutableList()
            val index = existing.indexOfFirst {
                it.id == record.id || (it.pkgName == record.pkgName && it.cls == record.cls && it.target == record.target)
            }
            if (index >= 0) {
                existing[index] = record
            } else {
                existing.add(record)
            }
            saveShortcutRecords(context, existing)
        } finally {
            storage.unlock()
        }
    }

    @JvmStatic
    fun saveShortcutRecords(context: Context, records: List<ShortcutRecord>) {
        val array = JSONArray()
        for (rec in records) {
            val obj = JSONObject()
            obj.put("id", rec.id)
            obj.put("name", rec.name)
            obj.put("pkgName", rec.pkgName)
            obj.put("cls", rec.cls)
            obj.put("target", rec.target)
            obj.put("tasks", rec.tasks)
            obj.put("justLaunch", rec.justLaunch)
            array.put(obj)
        }
        val storage = DefaultMultiProcessMMKVStorage()
        storage.putString(KEY_SAVED_SHORTCUTS, array.toString()).sync()
    }

    @JvmStatic
    fun removeShortcutsForPackage(context: Context, pkgName: String) {
        val storage = DefaultMultiProcessMMKVStorage()
        try {
            storage.lock()
            val existing = getSavedShortcutRecords(context).filter { it.pkgName != pkgName }
            saveShortcutRecords(context, existing)
        } finally {
            storage.unlock()
        }
    }

    @JvmStatic
    fun cleanUninstalledShortcuts(context: Context) {
        val storage = DefaultMultiProcessMMKVStorage()
        try {
            storage.lock()
            val existing = getSavedShortcutRecords(context).filter { rec ->
                if (isSpecialOrBatchShortcut(rec.pkgName)) {
                    true
                } else {
                    ApplicationInfoUtils.isAppInstalled(context, rec.pkgName)
                }
            }
            saveShortcutRecords(context, existing)
        } finally {
            storage.unlock()
        }
    }

    @JvmStatic
    fun isSpecialOrBatchShortcut(pkgName: String?): Boolean {
        if (pkgName == null) return false
        return when (pkgName) {
            "cf.playhi.freezeyou.extra.fuf",
            "cf.playhi.freezeyou.extra.oklock",
            "OF", "UF", "OO", "OOU", "FOQ", "OS", "OU", "UFU" -> true
            else -> pkgName.startsWith("CATEGORY") || pkgName.startsWith("FORCESTOPCATEGORY")
        }
    }

    @JvmStatic
    fun exportShortcutsToJson(context: Context): JSONArray {
        val records = getSavedShortcutRecords(context)
        val array = JSONArray()
        for (rec in records) {
            val obj = JSONObject()
            obj.put("id", rec.id)
            obj.put("name", rec.name)
            obj.put("pkgName", rec.pkgName)
            obj.put("cls", rec.cls)
            obj.put("target", rec.target)
            obj.put("tasks", rec.tasks)
            obj.put("justLaunch", rec.justLaunch)
            array.put(obj)
        }
        return array
    }

    @JvmStatic
    fun importShortcutsFromJson(context: Context, jsonArray: JSONArray): Int {
        val storage = DefaultMultiProcessMMKVStorage()
        try {
            storage.lock()
            val existing = getSavedShortcutRecords(context).toMutableList()
            var importedCount = 0
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val name = obj.optString("name", "")
                val pkgName = obj.optString("pkgName", "")
                val cls = obj.optString("cls", "")
                if (name.isNotEmpty() && pkgName.isNotEmpty() && isAllowedShortcutClass(cls)) {
                    val id = obj.optString("id", "FreezeYou! ${pkgName}_${System.currentTimeMillis()}_$i")
                    val rec = ShortcutRecord(
                        id = id,
                        name = name,
                        pkgName = pkgName,
                        cls = cls,
                        target = if (obj.has("target") && !obj.isNull("target")) obj.optString("target") else null,
                        tasks = if (obj.has("tasks") && !obj.isNull("tasks")) obj.optString("tasks") else null,
                        justLaunch = obj.optBoolean("justLaunch", false)
                    )
                    val idx = existing.indexOfFirst {
                        it.id == rec.id || (it.pkgName == rec.pkgName && it.cls == rec.cls && it.target == rec.target)
                    }
                    if (idx >= 0) {
                        existing[idx] = rec
                    } else {
                        existing.add(rec)
                    }
                    importedCount++
                }
            }
            if (importedCount > 0) {
                saveShortcutRecords(context, existing)
            }
            return importedCount
        } finally {
            storage.unlock()
        }
    }

    @JvmStatic
    fun resolveShortcutIcon(context: Context, pkgName: String): Drawable {
        return when (pkgName) {
            "cf.playhi.freezeyou.extra.oklock" -> {
                context.resources.getDrawable(R.drawable.screenlock)
            }
            "cf.playhi.freezeyou.extra.fuf", "OF", "UF", "OO", "OOU", "FOQ", "OS", "OU", "UFU" -> {
                context.resources.getDrawable(R.mipmap.ic_launcher_round)
            }
            else -> {
                if (pkgName.startsWith("CATEGORY") || pkgName.startsWith("FORCESTOPCATEGORY")) {
                    context.resources.getDrawable(R.mipmap.ic_launcher_round)
                } else {
                    ApplicationIconUtils.getApplicationIcon(
                        context,
                        pkgName,
                        ApplicationInfoUtils.getApplicationInfoFromPkgName(pkgName, context),
                        false
                    )
                }
            }
        }
    }

    @JvmStatic
    fun restoreShortcut(context: Context, record: ShortcutRecord): Boolean {
        if (!isAllowedShortcutClass(record.cls)) {
            return false
        }
        return try {
            val cls = Class.forName(record.cls)
            val icon = resolveShortcutIcon(context, record.pkgName)
            createShortCut(
                title = record.name,
                pkgName = record.pkgName,
                icon = icon,
                cls = cls,
                id = record.id,
                context = context,
                target = record.target,
                tasks = record.tasks,
                justLaunch = record.justLaunch
            )
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
