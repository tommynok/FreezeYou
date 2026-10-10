package cf.playhi.freezeyou.ui

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilder
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.MoreUtils.requestOpenWebSite
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import cf.playhi.freezeyou.utils.VersionUtils.getVersionCode
import cf.playhi.freezeyou.utils.VersionUtils.getVersionName
import java.util.ArrayList

private class AboutMenuItem(val title: String, val action: () -> Unit)

class AboutActivity : FreezeYouBaseActivity() {

    private var countdownToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about)
        processActionBar(supportActionBar)

        val aboutSlogan = findViewById<TextView>(R.id.about_slogan)
        val aboutListView = findViewById<ListView>(R.id.about_listView)
        val aboutAppName = findViewById<TextView>(R.id.about_appName)

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        var isUpdateUnlocked = sp.getBoolean("unlockedUpdateCheck", false)
        var tapCount = 0

        val checkUpdateItem = AboutMenuItem(resources.getString(R.string.checkUpdate)) {
            requestOpenWebSite(this@AboutActivity, "https://github.com/tommynok/FreezeYou/releases")
        }

        val aboutMenuItems = mutableListOf<AboutMenuItem>()

        fun buildMenu() {
            aboutMenuItems.clear()
            aboutMenuItems.addAll(
                listOf(
                    AboutMenuItem(resources.getString(R.string.hToUse)) {
                        requestOpenWebSite(
                            this@AboutActivity,
                            "https://www.zidon.net/${getString(R.string.correspondingAndAvailableWebsiteUrlLanguageCode)}/guide/how-to-use.html"
                        )
                    },
                    AboutMenuItem(resources.getString(R.string.faq)) {
                        requestOpenWebSite(
                            this@AboutActivity, String.format(
                                "https://www.zidon.net/%1\$s/faq/",
                                getString(R.string.correspondingAndAvailableWebsiteUrlLanguageCode)
                            )
                        )
                    },
                    AboutMenuItem(resources.getString(R.string.thanksList)) {
                        requestOpenWebSite(
                            this@AboutActivity, String.format(
                                "https://www.zidon.net/%1\$s/thanks/",
                                getString(R.string.correspondingAndAvailableWebsiteUrlLanguageCode)
                            )
                        )
                    },
                    AboutMenuItem(resources.getString(R.string.visitWebsite)) {
                        requestOpenWebSite(this@AboutActivity, "https://www.zidon.net")
                    },
                    AboutMenuItem(
                        "V${getVersionName(applicationContext)}(${getVersionCode(applicationContext)})"
                    ) {
                        if (!isUpdateUnlocked) {
                            tapCount++
                            val remaining = 10 - tapCount
                            if (remaining in 1..5) {
                                countdownToast?.cancel()
                                countdownToast = Toast.makeText(
                                    this@AboutActivity,
                                    getString(R.string.stepsToUnlock_d, remaining),
                                    Toast.LENGTH_SHORT
                                )
                                countdownToast?.show()
                            } else if (remaining <= 0) {
                                isUpdateUnlocked = true
                                sp.edit().putBoolean("unlockedUpdateCheck", true).apply()
                                countdownToast?.cancel()
                                countdownToast = null
                                Toast.makeText(
                                    this@AboutActivity,
                                    R.string.updateCheckUnlocked,
                                    Toast.LENGTH_SHORT
                                ).show()
                                buildMenu()
                                val adapter = aboutListView.adapter as? ArrayAdapter<String>
                                if (adapter != null) {
                                    adapter.clear()
                                    adapter.addAll(aboutMenuItems.map { it.title })
                                    adapter.notifyDataSetChanged()
                                }
                            }
                        } else {
                            showToast(
                                this@AboutActivity,
                                "V" + getVersionName(this@AboutActivity) + "(" + getVersionCode(
                                    this@AboutActivity
                                ) + ")"
                            )
                        }
                    }
                )
            )
            if (isUpdateUnlocked) {
                aboutMenuItems.add(checkUpdateItem)
            }
        }

        buildMenu()

        val adapter = ArrayAdapter(
            this@AboutActivity,
            android.R.layout.simple_list_item_1,
            ArrayList(aboutMenuItems.map { it.title })
        )
        aboutListView.adapter = adapter

        aboutListView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                if (position in 0 until aboutMenuItems.size) {
                    aboutMenuItems[position].action()
                }
            }

        aboutSlogan.text = String.format("V %s", getVersionCode(this@AboutActivity))

        aboutAppName.setOnClickListener {
            FreezeYouAlertDialogBuilder(this@AboutActivity)
                .setTitle(
                    String.format(
                        getString(R.string.welcomeToUseAppName),
                        getString(R.string.app_name)
                    )
                )
                .setIcon(R.mipmap.ic_launcher_new_round)
                .setMessage(
                    String.format(
                        getString(R.string.welcomeToUseAppName),
                        getString(R.string.app_name)
                    )
                )
                .setNegativeButton(
                    R.string.importConfig
                ) { _: DialogInterface?, _: Int ->
                    startActivity(
                        Intent(applicationContext, BackupMainActivity::class.java)
                    )
                }
                .setPositiveButton(
                    R.string.quickSetup
                ) { _: DialogInterface?, _: Int ->
                    startActivity(
                        Intent(applicationContext, FirstTimeSetupActivity::class.java)
                    )
                }
                .setNeutralButton(R.string.okay, null)
                .show()
        }
    }

    override fun onDestroy() {
        countdownToast?.cancel()
        super.onDestroy()
    }
}
