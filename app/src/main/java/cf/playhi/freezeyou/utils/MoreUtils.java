package cf.playhi.freezeyou.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cf.playhi.freezeyou.R;

import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public final class MoreUtils {

    public static void requestOpenWebSite(Context context, String url) {
        Uri webPage = Uri.parse(url);
        Intent about = new Intent(Intent.ACTION_VIEW, webPage);
        if (about.resolveActivity(context.getPackageManager()) != null) {
            try {
                context.startActivity(about);
            } catch (RuntimeException e) {
                e.printStackTrace();
                about.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(about);
            }
        } else {
            showToast(context, context.getString(R.string.plsVisit) + " " + url);
            ClipboardUtils.copyToClipboard(context, url);
        }
    }

    public static void joinQQGroup(Context context) {
        Intent intent = new Intent();
        intent.setData(Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D92NGzlhmCK_UFrL_oEAV7Fe6QrvFR5y_"));
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            requestOpenWebSite(context, "https://shang.qq.com/wpa/qunwpa?idkey=cbc8ae71402e8a1bc9bb4c39384bcfe5b9f7d18ff1548ea9bdd842f036832f3d");
        }
    }

    public static ArrayList<String> convertToList(String origin, String s) {
        return origin == null ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(origin.split(s)));
    }

    public static ArrayList<String> convertToList(String[] origin) {
        return origin == null ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(origin));
    }

    public static String listToString(List<String> l, String s) {

        if (l == null)
            return "";

        StringBuilder sb = new StringBuilder();
        for (String s1 : l) {
            sb.append(s1);
            sb.append(s);
        }
        return sb.toString();
    }

    public static ArrayList<Map<String, Object>> processListFilter(CharSequence prefix, ArrayList<Map<String, Object>> unfilteredValues) {

        String prefixString = prefix.toString().toLowerCase(Locale.ROOT).trim();

        if (unfilteredValues == null) {
            return new ArrayList<>();
        }

        int count = unfilteredValues.size();

        // Three buckets, in the order they are offered. Searching by the name shown on screen is
        // what a person reaches for first, so those matches come before matches that only occur
        // inside a package name — otherwise typing "droid" buries Droid-ify under every
        // com.android.* package on the device, which is what it looked like when nothing was
        // found at all.
        ArrayList<Map<String, Object>> nameStartsWith = new ArrayList<>(count);
        ArrayList<Map<String, Object>> nameContains = new ArrayList<>(count);
        ArrayList<Map<String, Object>> packageOnly = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            try {
                Map<String, Object> h = unfilteredValues.get(i);
                String name = ((String) h.get("Name"));
                String pkgName = ((String) h.get("PackageName"));
                String lowerName = name == null ? null : name.toLowerCase(Locale.ROOT);

                if (lowerName != null && lowerName.startsWith(prefixString)) {
                    nameStartsWith.add(h);
                } else if (lowerName != null && lowerName.contains(prefixString)) {
                    nameContains.add(h);
                } else if (pkgName != null
                        && pkgName.toLowerCase(Locale.ROOT).contains(prefixString)) {
                    packageOnly.add(h);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        ArrayList<Map<String, Object>> newValues =
                new ArrayList<>(nameStartsWith.size() + nameContains.size() + packageOnly.size());
        newValues.addAll(nameStartsWith);
        newValues.addAll(nameContains);
        newValues.addAll(packageOnly);
        return newValues;
    }

}
