package cf.playhi.freezeyou.ui;

import android.os.Bundle;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.utils.AlertDialogUtils;
import cf.playhi.freezeyou.utils.ApplicationLabelUtils;
import cf.playhi.freezeyou.utils.AutoAllowListUtils;

import static cf.playhi.freezeyou.utils.ThemeUtils.processActionBar;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;

// Important!
// Also used to deal with ipa_autoAllow
public class UriAutoAllowManageActivity extends FreezeYouBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uaam_main);
        processActionBar(getSupportActionBar());

        init();
    }

    private void init() {
        ListView uaamListView = findViewById(R.id.uaam_listView);
        final boolean ipaMode = getIntent().getBooleanExtra("isIpaMode", false);//Install Package
        if (ipaMode)
            setTitle(R.string.manageIpaAutoAllow);
        final String listKey = ipaMode
                ? AutoAllowListUtils.INSTALL_ALLOW
                : AutoAllowListUtils.URI_ALLOW;
        final List<String> pkgNames = AutoAllowListUtils.readPackageNames(listKey);

        List<HashMap<String, String>> pkgList = new ArrayList<>();
        if (!pkgNames.isEmpty()) {
            for (String pkgName : pkgNames) {
                HashMap<String, String> hashMap = new HashMap<>();
                hashMap.put("Name",
                        ApplicationLabelUtils.getApplicationLabel(
                                this, null, null, pkgName));
                hashMap.put("PkgName", pkgName);
                pkgList.add(hashMap);
            }
            final SimpleAdapter adapter = new SimpleAdapter(this, pkgList,
                    R.layout.uaam_list, new String[]{"Name", "PkgName"}, new int[]{R.id.uaaml_name, R.id.uaaml_pkgName});
            uaamListView.setAdapter(adapter);
            uaamListView.setOnItemClickListener((parent, view, position, id) -> {
                HashMap<String, String> hashMap = ((HashMap<String, String>) adapter.getItem(position));
                final String pkgName = hashMap.get("PkgName");
                AlertDialogUtils.buildAlertDialog(
                                UriAutoAllowManageActivity.this,
                                null,
                                hashMap.get("Name") + System.getProperty("line.separator") + pkgName,
                                getString(R.string.askIfDel))
                        .setNegativeButton(R.string.no, null)
                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                            AutoAllowListUtils.remove(UriAutoAllowManageActivity.this, listKey, pkgName);
                            init();
                        })
                        .create().show();
            });
        } else {
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("Name", getString(R.string.notAvailable));
            hashMap.put("PkgName", getString(R.string.notAvailable));
            pkgList.add(hashMap);
            final SimpleAdapter adapter = new SimpleAdapter(this, pkgList,
                    R.layout.uaam_list, new String[]{"Name", "PkgName"}, new int[]{R.id.uaaml_name, R.id.uaaml_pkgName});
            uaamListView.setAdapter(adapter);
            uaamListView.setOnItemClickListener(null);
        }
    }

}
