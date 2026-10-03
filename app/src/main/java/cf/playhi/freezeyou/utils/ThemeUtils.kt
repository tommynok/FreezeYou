package cf.playhi.freezeyou.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.NonNull
import androidx.appcompat.app.ActionBar
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.allowFollowSystemAutoSwitchDarkMode
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.themeOfAutoSwitchDarkMode
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.uiStyleSelection

internal object ThemeUtils {

    @JvmStatic
    fun getThemeDot(@NonNull context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return R.drawable.shapedot_coloraccent
        }

        val string = getUiTheme(context)
        return if (string != null) {
            // Only the three themes remain, and below API 21 the dot of the two dark ones is the
            // light shape while everything else - the light theme, and any palette value stored by
            // an older version of the application - gets the dark one.
            when (string) {
                "black", "deepBlack" -> R.drawable.shapedotwhite
                else -> R.drawable.shapedotblack
            }
        } else {
            R.drawable.shapedotblack
        }
    }

    /**
     * 主要用于各点的 getThemeDot 的另一（相对/相反）状态
     *
     * The dot for "not frozen" is meant to be seen by nobody: it exists so that the row keeps its
     * shape whether or not the application is frozen. It used to be painted in the theme's
     * background colour, which works only as long as it sits on that background — on a selected
     * row, drawn on a grey highlight, it turned into a visible light circle and read as a marker
     * of its own. Nothing to see is the same on every background.
     *
     * @param context Context
     * @return 资源 Id
     */
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun getThemeSecondDot(@NonNull context: Context): Int {
        return R.drawable.shapedot_transparent
    }

    /**
     * The round background behind the floating button, below API 21 where there is no ripple
     * drawable to use instead. Every remaining theme wants the same dark circle: the coloured
     * palettes are gone, and a value they left in storage gets it too.
     */
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun getThemeFabDotBackground(@NonNull context: Context): Int {
        return R.drawable.shapedotblack
    }

    @JvmStatic
    fun getUiTheme(@NonNull context: Context): String? {
        return if (allowFollowSystemAutoSwitchDarkMode.getValue()) {
            if (isSystemDarkModeEnabled(context))
                when (themeOfAutoSwitchDarkMode.getValue()) {
                    "dark" -> "black"
                    "black" -> "deepBlack"
                    else -> {
                        if (isMaterial3Theme()) {
                            uiStyleSelection.defaultValue()
                        } else {
                            "black"
                        }
                    }
                }
            else uiStyleSelection.getValue()
        } else {
            uiStyleSelection.getValue()
        }
    }

    private fun isSystemDarkModeEnabled(@NonNull context: Context): Boolean {
        return context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    @JvmStatic
    fun processAddTranslucent(@NonNull activity: Activity) {
        val window = activity.window
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE)
            window.setBackgroundDrawableResource(R.color.realTranslucent)
            when {
                Build.VERSION.SDK_INT >= 21 -> {
                    window.navigationBarColor = Color.TRANSPARENT
                    window.statusBarColor = Color.TRANSPARENT
                }
                Build.VERSION.SDK_INT >= 19 -> {
                    @Suppress("DEPRECATION")
                    window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                    @Suppress("DEPRECATION")
                    window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                }
            }
        }
    }

    @JvmStatic
    fun processActionBar(actionBar: ActionBar?) {
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(false)
            actionBar.setDisplayShowTitleEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun processSetTheme(@NonNull context: Context, isDialog: Boolean = false) {
        try {
            val string = getUiTheme(context)
            if (string != null) {
                when (string) {
                    "black" -> context.setTheme(if (isDialog) R.style.AppTheme_Dark_Dialog_Default else R.style.AppTheme_Dark_Default)
                    "deepBlack" -> context.setTheme(if (isDialog) R.style.AppTheme_Dark_Dialog_Black else R.style.AppTheme_Dark_Black)
                    // "default", and anything a palette left in storage: the coloured themes are
                    // gone from the code, and an installation that once stored "blue" simply comes
                    // up as the light theme rather than as a screen with no theme at all.
                    else -> context.setTheme(if (isDialog) R.style.AppTheme_Default_Dialog else R.style.AppTheme_Default)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun isMaterial3Theme(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && uiStyleSelection.getValue() == uiStyleSelection.defaultValue()
    }

    /**
     * Recreates every live activity, so a theme, language or main-screen-pattern switch
     * applies immediately instead of waiting for a process restart. Each activity picks
     * the new values up in its own onCreate via processSetTheme and attachBaseContext.
     */
    @JvmStatic
    fun recreateAllActivities(context: Context) {
        val application = context.applicationContext
        if (application is cf.playhi.freezeyou.MainApplication) {
            for (activity in application.liveActivities()) {
                if (!activity.isFinishing) {
                    activity.recreate()
                }
            }
        }
    }
}