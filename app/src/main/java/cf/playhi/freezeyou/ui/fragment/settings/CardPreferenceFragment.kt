package cf.playhi.freezeyou.ui.fragment.settings

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.PreferenceGroupCardDecoration
import cf.playhi.freezeyou.utils.ThemeUtils

/**
 * Settings screens dressed as Material 3 card groups: the preference list loses its
 * dividers and every [androidx.preference.PreferenceCategory] plus its children is drawn
 * as one rounded card behind the rows, matching the expressive look of the design
 * reference. Purely visual - no layout or preference changes.
 *
 * The two colours come from values/colors.xml and are the same pair the theme itself uses for
 * android:colorBackground and colorSurface, so a card here matches the menus, the dialogs and
 * the toolbar of the theme it is painted in. Dark and black therefore differ on this screen
 * exactly as much as they do everywhere else, and the difference is defined in one place.
 */
abstract class CardPreferenceFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(null)
        setDividerHeight(0)

        val (screenColorRes, cardColorRes) = when (ThemeUtils.getUiTheme(view.context)) {
            "deepBlack" -> R.color.appScreenBlack to R.color.appSurfaceBlack
            "black" -> R.color.appScreenDark to R.color.appSurfaceDark
            else -> R.color.appScreenLight to R.color.appSurfaceLight
        }
        val context = view.context
        view.background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, screenColorRes))
        }
        listView?.addItemDecoration(
            PreferenceGroupCardDecoration(ContextCompat.getColor(context, cardColorRes))
        )
    }
}
