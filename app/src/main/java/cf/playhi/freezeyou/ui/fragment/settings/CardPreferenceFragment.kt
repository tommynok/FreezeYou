package cf.playhi.freezeyou.ui.fragment.settings

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import cf.playhi.freezeyou.utils.PreferenceGroupCardDecoration
import cf.playhi.freezeyou.utils.ThemeUtils

/**
 * Settings screens dressed as Material 3 card groups: the preference list loses its
 * dividers and every [androidx.preference.PreferenceCategory] plus its children is drawn
 * as one rounded card behind the rows, matching the expressive look of the design
 * reference. Purely visual - no layout or preference changes.
 *
 * Colors are constants, not theme attributes: the AppCompat-based dark and black themes
 * do not carry the Material surface attributes, and reading them through the theme is
 * what crashed these screens before. The theme switch (ThemeUtils.getUiTheme) decides
 * the palette directly.
 */
abstract class CardPreferenceFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(null)
        setDividerHeight(0)

        val theme = ThemeUtils.getUiTheme(view.context)
        val (screenColor, cardColor) = when (theme) {
            "deepBlack" -> 0xFF000000.toInt() to 0xFF2C2E33.toInt()
            "black" -> 0xFF1E1F22.toInt() to 0xFF35383E.toInt()
            else -> 0xFFE2E6EE.toInt() to 0xFFFFFFFF.toInt()
        }
        view.background = GradientDrawable().apply { setColor(screenColor) }
        listView?.addItemDecoration(PreferenceGroupCardDecoration(cardColor))
    }
}
