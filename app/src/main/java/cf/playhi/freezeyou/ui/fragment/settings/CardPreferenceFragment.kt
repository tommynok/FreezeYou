package cf.playhi.freezeyou.ui.fragment.settings

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import cf.playhi.freezeyou.utils.PreferenceGroupCardDecoration
import cf.playhi.freezeyou.utils.ThemePalettes
import cf.playhi.freezeyou.utils.ThemeUtils

/**
 * Settings screens dressed as Material 3 card groups: the preference list loses its
 * dividers and every [androidx.preference.PreferenceCategory] plus its children is drawn
 * as one rounded card behind the rows, matching the expressive look of the design
 * reference. Purely visual - no layout or preference changes.
 *
 * The two colours come from values/colors.xml through ThemePalettes. For dark and black they are
 * the pair the theme itself uses, so a card there matches the menus, the dialogs and the toolbar.
 * For light they are not: the cards here are light on a grey screen while the menus and dialogs of
 * the home screen are grey, which is what the owner asked for on 05.10 after seeing grey cards on
 * a near-white screen. That is why the screen colour is still shared with the theme and the card
 * colour is not.
 */
abstract class CardPreferenceFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(null)
        setDividerHeight(0)

        val (screenColorRes, cardColorRes) = ThemePalettes.of(ThemeUtils.getUiTheme(view.context))
        val context = view.context
        view.background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, screenColorRes))
        }
        listView?.addItemDecoration(
            PreferenceGroupCardDecoration(ContextCompat.getColor(context, cardColorRes))
        )
    }
}
