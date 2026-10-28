package cf.playhi.freezeyou.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.SimpleAdapter
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import cf.playhi.freezeyou.viewmodel.AutoDiagnosisViewModel
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.ThemeUtils.getUiTheme
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.databinding.AutodiagnosisBinding
import cf.playhi.freezeyou.utils.AccessibilityUtils.openAccessibilitySettings
import cf.playhi.freezeyou.utils.MoreUtils.requestOpenWebSite
import cf.playhi.freezeyou.utils.NotificationUtils.startAppNotificationSettingsSystemActivity
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

class AutoDiagnosisActivity : FreezeYouBaseActivity() {
    private lateinit var binding: AutodiagnosisBinding
    private val viewModel: AutoDiagnosisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        binding = AutodiagnosisBinding.inflate(layoutInflater)
        setContentView(binding.root)
        processActionBar(supportActionBar)

        // The list, not the progress handshake, owns the adapter. The refresh publishes a
        // snapshot after every phase, and LiveData replays the latest one to a late observer,
        // so rows appear as soon as any snapshot exists - a lost or delayed progress update
        // can no longer leave the screen empty until the refresh button is pressed.
        // The adapter is created once and snapshots are copied into its backing list:
        // reinstalling the adapter on every snapshot would drop scroll and tap state while
        // the checks are still running.
        val diagnosisRows = ArrayList<Map<String, Any>>()
        binding.adgListView.adapter = SimpleAdapter(
            this,
            diagnosisRows,
            R.layout.adg_list_item,
            arrayOf("title", "sTitle", "status", "id"),
            intArrayOf(
                R.id.adgli_title_textView,
                R.id.adgli_subTitle_textView,
                R.id.adgli_status_imageView
            )
        )
        viewModel.getProblemsList().observe(this) { list ->
            diagnosisRows.clear()
            diagnosisRows.addAll(list)
            binding.adgListView.adapter?.notifyDataSetChanged()
        }

        viewModel.getLoadingProgress().observe(this) {
            binding.adgProgressBar.visibility = View.VISIBLE
            if (it == -1) {
                binding.adgProgressBar.isIndeterminate = true
            } else {
                binding.adgProgressBar.isIndeterminate = false

                if (Build.VERSION.SDK_INT >= 24) {
                    binding.adgProgressBar.setProgress(it, true)
                } else {
                    binding.adgProgressBar.progress = it
                }
                if (it == 100) {
                    binding.adgProgressBar.visibility = View.GONE
                }
            }
        }

        binding.adgListView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                val s =
                    (binding.adgListView.adapter?.getItem(position) as Map<*, *>?)?.get("id") as String?
                if (s != null) {
                    when (s) {
                        "1" -> openAccessibilitySettings(this@AutoDiagnosisActivity)
                        "2" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            if (intent.resolveActivity(packageManager) != null) {
                                startActivity(intent)
                            }
                        }
                        "4" -> if (Build.VERSION.SDK_INT >= 23) {
                            val intent =
                                if ((getSystemService(POWER_SERVICE) as PowerManager)
                                        .isIgnoringBatteryOptimizations(packageName)
                                ) {
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                } else {
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:$packageName")
                                    )
                                }

                            if (intent.resolveActivity(packageManager) != null) {
                                startActivity(intent)
                            }
                        }
                        "5" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                            if (intent.resolveActivity(packageManager) != null) {
                                startActivity(intent)
                            }
                        }
                        "6" -> startAppNotificationSettingsSystemActivity(
                            this@AutoDiagnosisActivity,
                            packageName,
                            applicationInfo.uid
                        )
                        "7" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            && ActivityCompat
                                .checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                )
                            != PackageManager.PERMISSION_GRANTED
                        ) {
                            ActivityCompat.requestPermissions(
                                this@AutoDiagnosisActivity,
                                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                                REQUEST_CODE_BLUETOOTH_PERMISSION
                            )
                        }
                        "-8" -> try {
                            if (!Shizuku.pingBinder()) {
                                // The execution path requests the binder itself through
                                // ShizukuProvider; attempt the same repair before reporting failure.
                                ShizukuProvider.requestBinderForNonProviderProcess(applicationContext)
                            }
                            if (!Shizuku.pingBinder()) {
                                showToast(
                                    this@AutoDiagnosisActivity,
                                    R.string.shizukuIsNotInstalledOrOtherExceptions
                                )
                            } else if (Shizuku.isPreV11()) {
                                showToast(
                                    this@AutoDiagnosisActivity,
                                    R.string.shizukuVersionIsTooLow
                                )
                            } else if (Shizuku.checkSelfPermission()
                                != PackageManager.PERMISSION_GRANTED
                            ) {
                                Shizuku.requestPermission(0)
                            }
                        } catch (e: Exception) {
                            showToast(
                                this@AutoDiagnosisActivity,
                                R.string.shizukuIsNotInstalledOrOtherExceptions
                            )
                        }
                    }
                }
            }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDiagnosisData()
    }

    /**
     * The permission dialog does not pause this activity, so a granted Bluetooth permission never
     * reached onResume and the row kept reporting the stale warning. The result callback is the
     * only place that sees the answer.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_BLUETOOTH_PERMISSION) {
            viewModel.refreshDiagnosisData()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.autodiagnosis_menu, menu)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            val cTheme = getUiTheme(this@AutoDiagnosisActivity)
            if ("white" == cTheme || "default" == cTheme) {
                menu.findItem(R.id.menu_autoDiagnosis_refresh)
                    .setIcon(R.drawable.ic_action_refresh_light)
                menu.findItem(R.id.menu_autoDiagnosis_help)
                    .setIcon(R.drawable.ic_action_help_outline_light)
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_autoDiagnosis_refresh -> {
                viewModel.refreshDiagnosisData()
                true
            }
            R.id.menu_autoDiagnosis_help -> {
                requestOpenWebSite(
                    this@AutoDiagnosisActivity,
                    "https://www.zidon.net/${getString(R.string.correspondingAndAvailableWebsiteUrlLanguageCode)}/faq/",
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        /** Matches the Bluetooth diagnosis row id, so the result is traceable to that row. */
        private const val REQUEST_CODE_BLUETOOTH_PERMISSION = 7
    }
}