package cf.playhi.freezeyou.utils

import cf.playhi.freezeyou.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dark and black are two palettes, not one: the owner asked for the difference and it is the point
 * of having both. The mapping from the stored theme name to the pair of colours the settings screens
 * paint with is checked here, because the same pair is what the theme uses for its background and
 * surfaces - if these two drift apart, the cards stop matching the toolbar, the menus and the
 * dialogs of the theme they are drawn in.
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
        val light = R.color.appScreenLight to R.color.appSurfaceLight
        assertEquals(light, ThemePalettes.of("default"))
        assertEquals(light, ThemePalettes.of("white"))
        assertEquals(light, ThemePalettes.of(null))
    }
}
