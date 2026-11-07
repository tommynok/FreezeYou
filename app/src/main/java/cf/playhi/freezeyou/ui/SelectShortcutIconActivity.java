package cf.playhi.freezeyou.ui;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.SimpleAdapter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.utils.ThemeUtils;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;

import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public class SelectShortcutIconActivity extends FreezeYouBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.processSetTheme(this);
        super.onCreate(savedInstanceState);
        ThemeUtils.processActionBar(getSupportActionBar());
        setContentView(R.layout.ssia_main);
        init();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 21 && data != null) {
            Uri fullPhotoUri = data.getData();
            if (fullPhotoUri != null) {
                ContentResolver contentResolver = getContentResolver();
                Bitmap bitmap = null;
                try {
                    BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
                    boundsOptions.inJustDecodeBounds = true;
                    try (InputStream boundsStream = contentResolver.openInputStream(fullPhotoUri)) {
                        BitmapFactory.decodeStream(boundsStream, null, boundsOptions);
                    }

                    BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
                    decodeOptions.inSampleSize = calculateInSampleSize(
                            boundsOptions.outWidth, boundsOptions.outHeight, 512, 512);
                    try (InputStream decodeStream = contentResolver.openInputStream(fullPhotoUri)) {
                        bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions);
                    }
                    if (bitmap == null) {
                        showToast(this, R.string.failed);
                        return;
                    }
                    if (bitmap.getByteCount() > getBitmapFromDrawable(
                            getResources().getDrawable(R.mipmap.ic_launcher_new_round)).getByteCount() * 5) {
                        int width = bitmap.getWidth();
                        int height = bitmap.getHeight();
                        Matrix matrix = new Matrix();
                        float scaleWidth = ((float) 192) / width;
                        float scaleHeight = ((float) 192) / height;
                        matrix.postScale(scaleWidth, scaleHeight);
                        Bitmap scaledBitmap =
                                Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                        bitmap.recycle();
                        bitmap = scaledBitmap;
                    }
                    setResult(
                            RESULT_OK,
                            new Intent().putExtra("Icon", bitmap)
                    );
                    finish();
                } catch (Throwable e) {
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                    showToast(this, R.string.failed);
                }
            }
        }
    }

    private static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void init() {
        final ArrayList<HashMap<String, Drawable>> icons = new ArrayList<>();

        // "Add more" icon (placed first for visibility)
        addToIconsArrayList(icons, getResources().getDrawable(R.drawable.grid_add));
        // Built-in
        addToIconsArrayList(icons, getResources().getDrawable(R.mipmap.ic_launcher_new_round));
        // Built-in
        addToIconsArrayList(icons, getResources().getDrawable(R.mipmap.ic_launcher_round));
        // Built-in
        addToIconsArrayList(icons, getResources().getDrawable(R.mipmap.ic_launcher));
        // Built-in
        addToIconsArrayList(icons, getResources().getDrawable(R.drawable.screenlock));
        // Built-in
        addToIconsArrayList(icons, getResources().getDrawable(R.drawable.ic_notification));

        List<ApplicationInfo> applicationInfoS = getPackageManager().getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES);
        if (applicationInfoS != null) {
            for (ApplicationInfo applicationInfo : applicationInfoS) {
                if (applicationInfo != null) {
                    addToIconsArrayList(
                            icons,
                            getApplicationIcon(
                                    SelectShortcutIconActivity.this,
                                    applicationInfo.packageName,
                                    applicationInfo,
                                    false));
                }
            }
        }

        SimpleAdapter simpleAdapter = new SimpleAdapter(this, icons,
                R.layout.ssia_main_grid_item, new String[]{"Icon"},
                new int[]{R.id.ssia_mgi_imageView});

        simpleAdapter.setViewBinder((view, data, textRepresentation) -> {
            if (view instanceof ImageView && data instanceof Drawable) {
                ((ImageView) view).setImageDrawable((Drawable) data);
                return true;
            } else
                return false;
        });

        GridView gridView = findViewById(R.id.ssia_main_gridView);

        gridView.setAdapter(simpleAdapter);

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(intent, 21);
                }
            } else {
                setResult(
                        RESULT_OK,
                        new Intent()
                                .putExtra(
                                        "Icon",
                                        getBitmapFromDrawable(icons.get(position).get("Icon"))));
                finish();
            }
        });

    }

    private void addToIconsArrayList(ArrayList<HashMap<String, Drawable>> icons, Drawable drawable) {
        HashMap<String, Drawable> map = new HashMap<>();
        map.put("Icon", drawable);
        icons.add(map);
    }
}
