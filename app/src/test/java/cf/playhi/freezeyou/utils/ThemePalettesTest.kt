package cf.playhi.freezeyou.utils

import cf.playhi.freezeyou.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dark and black are two palettes, not one: the owner asked for the difference and it is the point
 * of having both. The mapping from the stored theme name to the pair of colours the settings screens
 * paint with is checked here, because for those two it is the same pair the theme uses for its
 * background and surfaces - if they drift apart, the cards stop matching the toolbar, the menus and
 * the dialogs of the theme they are drawn in.
 *
 * Light is the deliberate exception, and the owner is the one who asked for it: he wants light
 * cards on a grey screen in the settings, and grey menus and dialogs on the home screen. One pair
 * cannot be both, so the light card colour is its own resource while the light screen colour is
 * still shared with the theme. That this is a decision and not a drift is what the last test pins.
 */
class ThemePalettesTest {

    @Test
    fun `the black theme is the pure black palette`() {
        assertEquals(
            R.color.appScreenBlack to R.color.appSurfaceBlack,
            ThemePalettes.of("deepBlack")
        )
    }

    @Test
    fun `the dark theme has a palette of its own`() {
        assertEquals(
            R.color.appScreenDark to R.color.appSurfaceDark,
            ThemePalettes.of("black")
        )
    }

    @Test
    fun `every light theme shares the light palette`() {
        val light = R.color.appScreenLight to R.color.appSettingsCardLight
        assertEquals(light, ThemePalettes.of("default"))
        assertEquals(light, ThemePalettes.of("white"))
        assertEquals(light, ThemePalettes.of(null))
    }

    /**
     * The light card colour is not the theme's colorSurface, and that has to stay true for the
     * reason the owner gave: with one pair the settings cards would come out the same grey as the
     * menus and the dialogs, and he asked for light cards on a grey screen there. If the two ever
     * become the same resource again, this is the check that says it was a decision, not a drift.
     */
    @Test
    fun `the light cards are deliberately not the theme's surface colour`() {
        val (screen, card) = ThemePalettes.of("default")
        assertTrue(
            "The light settings cards paint with the same colour as the light colorSurface, so the " +
                "cards and the menus and dialogs of the home screen are one grey again. The owner " +
                "asked for light cards on a grey screen in the settings and grey menus on the " +
                "home screen - one pair cannot be both.",
            card != R.color.appSurfaceLight,
        )
        assertEquals(
            "The light screen colour is the one the theme uses for android:colorBackground, so the " +
                "home screen and the settings screen are the same grey. They were split before and " +
                "the owner did not ask for that.",
            R.color.appScreenLight,
            screen,
        )
    }
}
