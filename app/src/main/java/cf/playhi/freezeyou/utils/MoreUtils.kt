package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import java.util.*

object MoreUtils {
    @JvmStatic
    fun requestOpenWebSite(context: Context, url: String) {
        val webPage = Uri.parse(url)
        val about = Intent(Intent.ACTION_VIEW, webPage)
        if (about.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(about)
            } catch (e: RuntimeException) {
                e.printStackTrace()
                about.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(about)
            }
        } else {
            showToast(context, context.getString(R.string.plsVisit) + " " + url)
            ClipboardUtils.copyToClipboard(context, url)
        }
    }

    @JvmStatic
    fun joinQQGroup(context: Context) {
        val intent = Intent()
        intent.data =
            Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D92NGzlhmCK_UFrL_oEAV7Fe6QrvFR5y_")
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            requestOpenWebSite(
                context,
                "https://shang.qq.com/wpa/qunwpa?idkey=cbc8ae71402e8a1bc9bb4c39384bcfe5b9f7d18ff1548ea9bdd842f036832f3d"
            )
        }
    }

    @JvmStatic
    fun convertToList(origin: String?, s: String?): ArrayList<String> {
        return if (origin == null) ArrayList() else ArrayList(listOf(*origin.split(s!!).toTypedArray()))
    }

    @JvmStatic
    fun convertToList(origin: Array<String>?): ArrayList<String> {
        return if (origin == null) ArrayList() else ArrayList(listOf(*origin))
    }

    @JvmStatic
    fun listToString(l: List<String>?, s: String): String {
        if (l == null) return ""
        val sb = StringBuilder()
        for (s1 in l) {
            sb.append(s1)
            sb.append(s)
        }
        return sb.toString()
    }

    @JvmStatic
    fun processListFilter(
        prefix: CharSequence,
        unfilteredValues: ArrayList<Map<String, Any?>>?
    ): ArrayList<Map<String, Any?>> {
        val prefixString = prefix.toString().lowercase(Locale.getDefault())
        if (unfilteredValues != null) {
            val count = unfilteredValues.size
            val newValues = ArrayList<Map<String, Any?>>(count)
            for (i in 0 until count) {
                try {
                    val h = unfilteredValues[i]
                    val name = h["Name"] as String?
                    val pkgName = h["PackageName"] as String?
                    if (name != null && name.lowercase(Locale.ROOT).contains(prefixString)
                        || pkgName != null && pkgName.lowercase(Locale.ROOT).contains(prefixString)
                    ) {
                        newValues.add(h)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return newValues
        }
        return ArrayList()
    }
}
