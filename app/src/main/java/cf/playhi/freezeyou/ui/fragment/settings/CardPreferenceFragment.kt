package cf.playhi.freezeyou.ui.fragment.settings

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import cf.playhi.freezeyou.utils.PreferenceGroupCardDecoration

/**
 * Settings screens dressed as Material 3 card groups: the preference list loses its
 * dividers and every [androidx.preference.PreferenceCategory] plus its children is drawn
 * as one rounded card behind the rows, matching the expressive look of the design
 * reference. Purely visual - no layout or preference changes.
 */
abstract class CardPreferenceFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(null)
        setDividerHeight(0)
        listView?.addItemDecoration(PreferenceGroupCardDecoration())
    }
}
