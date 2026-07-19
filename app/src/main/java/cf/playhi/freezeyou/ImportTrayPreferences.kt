package cf.playhi.freezeyou

import android.content.Context
import net.grandcentrix.tray.TrayPreferences
import net.grandcentrix.tray.core.SharedPreferencesImport

internal class ImportTrayPreferences(context: Context) : TrayPreferences(context, context.packageName, 1) {
    override fun onCreate(initialVersion: Int) {
        super.onCreate(initialVersion)
        importSharedPreferences()
    }

    private fun importSharedPreferences() {

        // migrate sharedPreferences in here.
        val newFreezeOnceQuit = SharedPreferencesImport(
            context,
            "New_FreezeOnceQuit", "pkgName", context.getString(R.string.sFreezeOnceQuit)
        )
        migrate(newFreezeOnceQuit)
        val newAutoFreezeApplicationList = SharedPreferencesImport(
            context,
            "New_AutoFreezeApplicationList",
            "pkgName",
            context.getString(R.string.sAutoFreezeApplicationList)
        )
        migrate(newAutoFreezeApplicationList)
        val newOneKeyUFApplicationList = SharedPreferencesImport(
            context,
            "New_OneKeyUFApplicationList",
            "pkgName",
            context.getString(R.string.sOneKeyUFApplicationList)
        )
        migrate(newOneKeyUFApplicationList)
    }
}
