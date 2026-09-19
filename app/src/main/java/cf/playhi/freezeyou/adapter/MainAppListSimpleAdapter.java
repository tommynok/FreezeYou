package cf.playhi.freezeyou.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.Map;

import cf.playhi.freezeyou.R;

public class MainAppListSimpleAdapter extends SimpleAdapter {

    /**
     * Frozen applications are greyed out by filtering the ImageView rather than by desaturating
     * the icon itself: a filter costs nothing and is shared by every row, while a desaturated
     * copy means one more ARGB_8888 bitmap per frozen application.
     */
    private static final ColorMatrixColorFilter GREYSCALE;

    static {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        GREYSCALE = new ColorMatrixColorFilter(matrix);
    }

    private final ArrayList<Map<String, Object>> mAppList;
    private final ArrayList<String> mIsCheckedPackageList;
    private final int mIconViewId;
    private boolean mGreyFrozenIcons;

    public MainAppListSimpleAdapter(Context context, ArrayList<Map<String, Object>> list, ArrayList<String> isCheckedPackageList, int resource, String[] from, int[] to, int iconViewId, boolean greyFrozenIcons) {
        super(context, list, resource, from, to);
        mAppList = list;
        mIsCheckedPackageList = isCheckedPackageList;
        mIconViewId = iconViewId;
        mGreyFrozenIcons = greyFrozenIcons;

        setViewBinder(new MainAppListSimpleAdapter.ViewBinder() {
            public boolean setViewValue(View view, Object data,
                                        String textRepresentation) {
                if (view instanceof ImageView) {
                    if (data instanceof Drawable) {
                        ((ImageView) view).setImageDrawable((Drawable) data);
                        return true;
                    } else if (data instanceof Bitmap) {
                        ((ImageView) view).setImageBitmap((Bitmap) data);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        });
    }
//
//    public void clearArrayListData() {
//        mAppList.clear();
//        notifyDataSetChanged();
//    }
//
//    public boolean addToArrayList(Map<String, Object> map) {
//        boolean b = mAppList.add(map);
//        notifyDataSetChanged();
//        return b;
//    }
//
//    public void addToArrayList(int index, Map<String, Object> map) {
//        mAppList.add(index, map);
//        notifyDataSetChanged();
//    }
//
//    public boolean removeFromArrayList(Map<String, Object> map) {
//        boolean b = mAppList.remove(map);
//        notifyDataSetChanged();
//        return b;
//    }
//
//    public Map<String, Object> removeFromArrayList(int index) {
//        Map<String, Object> m = mAppList.remove(index);
//        notifyDataSetChanged();
//        return m;
//    }

    public boolean replaceAllInFormerArrayList(ArrayList<Map<String, Object>> list) {
        mAppList.clear();
        boolean b = mAppList.addAll(list);
        notifyDataSetChanged();
        return b;
    }

    /**
     * The adapter outlives a trip to the settings, so the setting is re-read into it on resume
     * rather than only taken at construction. Call from the UI thread.
     */
    public void setGreyFrozenIcons(boolean greyFrozenIcons) {
        if (mGreyFrozenIcons != greyFrozenIcons) {
            mGreyFrozenIcons = greyFrozenIcons;
            notifyDataSetChanged();
        }
    }

    public ArrayList<Map<String, Object>> getStoredArrayList() {
        return mAppList;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);

        View icon = view.findViewById(mIconViewId);
        if (icon instanceof ImageView) {
            // Views are recycled and setImageDrawable leaves the filter in place, so the row
            // has to clear it as explicitly as it sets it.
            boolean grey =
                    mGreyFrozenIcons && Boolean.TRUE.equals(mAppList.get(position).get("Frozen"));
            ((ImageView) icon).setColorFilter(grey ? GREYSCALE : null);
        }

        if (mIsCheckedPackageList.contains((String) mAppList.get(position).get("PackageName"))) {
            view.setBackgroundResource(R.color.translucentGreyBackground);
        } else {
            view.setBackgroundResource(0);
        }

        return view;
    }

}
