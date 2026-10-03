package cf.playhi.freezeyou.utils

import cf.playhi.freezeyou.R

/**
 * The screen and container colours of each in-app theme, as resource ids.
 *
 * The same two resources are what the theme itself uses for `android:colorBackground` and
 * `colorSurface` (see `values/colors.xml`), which is what keeps the settings cards, the menus,
 * the dialogs and the toolbar of one theme in agreement. The mapping is kept out of the fragment
 * that paints with it so that the decision - dark and black are two palettes, and everything
 * light shares a third - can be checked without a device.
 */
object ThemePalettes {

    /**
     * @param uiTheme a value of [ThemeUtils.getUiTheme]
     * @return the screen colour and the container colour of that theme
     */
    @JvmStatic
    fun of(uiTheme: String?): Pair<Int, Int> = when (uiTheme) {
        "deepBlack" -> R.color.appScreenBlack to R.color.appSurfaceBlack
        "black" -> R.color.appScreenDark to R.color.appSurfaceDark
        else -> R.color.appScreenLight to R.color.appSurfaceLight
    }
}
