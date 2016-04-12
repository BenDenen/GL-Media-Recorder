package com.bendenen.glrecordertest.utils;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by Barys_Dzenisenka on 4/12/16.
 */
public class Utils {

    private static final SimpleDateFormat VIDEO_FILE_NAME_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

    private static final File VIDEO_DIRECTORY = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
    private static final String FILE_NAME_PREFIX = "Video_";
    private static final String FILE_NAME_EXTENSION = ".mp4";

    public static int dpToPx(Resources resources, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.getDisplayMetrics());
    }

    /**
     * Sets visibility of views.
     *
     * @param visible boolean <code>true</code> if views should be visible
     * @param views   View[] views
     */
    public static void setVisibility(boolean visible, View... views) {
        for (View view : views) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public static int getScreenWidth(Activity activity) {
        DisplayMetrics displaymetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        return displaymetrics.widthPixels;
    }

    public static int getScreenHeightByAspectRatio(int width, double aspectRatio) {
        if (aspectRatio == 0) {
            return 0;
        }
        return (int) (width / aspectRatio);
    }

    public static File getNewVideoFile() {
        VIDEO_DIRECTORY.mkdirs();
        return new File(VIDEO_DIRECTORY, getVideoFilename());
    }

    public static String getVideoFilename() {
        String fileName = VIDEO_FILE_NAME_DATE_FORMAT.format(new Date());
        fileName = FILE_NAME_PREFIX + fileName + FILE_NAME_EXTENSION;
        return fileName;
    }
}
