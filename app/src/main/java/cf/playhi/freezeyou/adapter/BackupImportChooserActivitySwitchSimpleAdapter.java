package cf.playhi.freezeyou.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleAdapter;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import cf.playhi.freezeyou.R;

public class BackupImportChooserActivitySwitchSimpleAdapter extends SimpleAdapter {

    private final ArrayList<HashMap<String, String>> mData;
    private final ArrayList<Integer> isDisabledList = new ArrayList<>();
    private JSONObject mJsonObject = null;

    /**
     * Constructor
     *
     * @param context  The context where the View associated with this SimpleAdapter is running
     * @param data     A List of Maps. Each entry in the List corresponds to one row in the list. The
     *                 Maps contain the data for each row, and should include all the entries specified in
     *                 "from"
     * @param resource Resource identifier of a view layout that defines the views for this list
     *                 item. The layout file should include at least those named views defined in "to"
     * @param from     A list of column names that will be added to the Map associated with each
     *                 item.
     * @param to       The views that should display column in the "from" parameter. These should all be
     *                 TextViews. The first N views in this list are given the values of the first N columns
     */
    public BackupImportChooserActivitySwitchSimpleAdapter(Context context, JSONObject jsonObject, ArrayList<HashMap<String, String>> data, int resource, String[] from, int[] to) {
        super(context, data, resource, from, to);
        mData = data;
        if (jsonObject != null) {
            try {
                mJsonObject = new JSONObject(jsonObject.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        SwitchMaterial s = view.findViewById(R.id.bica_list_item_switch);

        if (s != null) {

            s.setOnCheckedChangeListener(null);

            String categories = mData.get(position).get("categories");
            s.setChecked(!isDisabledList.contains(position));
            if ("Failed!".equals(categories)) {
                s.setChecked(true);
                s.setEnabled(false);
            }
            s.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    isDisabledList.remove((Integer) position);
                } else if (!isDisabledList.contains(position)) {
                    isDisabledList.add(position);
                }
            });
        }

        return view;
    }

    /**
     * Drops every top-level category belonging to a switched-off group. importContents() keys off
     * the presence of those categories, so removing them is all that's needed to skip them.
     */
    public JSONObject getFinalList() {
        if (mJsonObject == null) {
            return new JSONObject();
        }

        for (Integer position : isDisabledList) {
            if (position == null || position < 0 || position >= mData.size()) continue;
            String categories = mData.get(position).get("categories");
            if (categories == null) continue;
            for (String category : categories.split(",")) {
                mJsonObject.remove(category);
            }
        }

        return mJsonObject;

    }
}
