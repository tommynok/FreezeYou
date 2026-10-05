package cf.playhi.freezeyou.utils

import cf.playhi.freezeyou.R

/**
 * The screen and card colours of each in-app theme, as resource ids.
 *
 * Dark and black paint with the same pair the theme itself uses for `android:colorBackground` and
 * `colorSurface`, so their cards, menus, dialogs and toolbar agree. Light does not, and the split
 * is deliberate: the owner wants the settings cards light on a grey screen - the way the builds
 * before 05.10 had them - while the menus and dialogs of the home screen are grey. One pair cannot
 * do both, so the light card colour is its own resource and the light `colorSurface` stays grey
 * for everything else. The mapping is kept out of the fragment that paints with it so that the
 * decision - dark and black are two palettes, and everything light shares a third - can be checked
 * without a device.
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
        else -> R.color.appScreenLight to R.color.appSettingsCardLight
    }
}
