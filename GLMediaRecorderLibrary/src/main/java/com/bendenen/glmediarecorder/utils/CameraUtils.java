package com.bendenen.glmediarecorder.utils;

import android.hardware.Camera;
import android.util.Log;

import java.util.List;

/**
 * Camera-related utility functions.
 */
public class CameraUtils {
    private static final String TAG = CameraUtils.class.getSimpleName();

    private static final double ASPECT_TOLERANCE = 0.05;

    /**
     * Attempts to find a preview size that matches the provided width and height (which
     * specify the dimensions of the encoded video).  If it fails to find a match it just
     * uses the default preview size for video.
     * <p/>
     * TODO: should do a best-fit match, e.g.
     * https://github.com/commonsguy/cwac-camera/blob/master/camera/src/com/commonsware/cwac/camera/CameraUtils.java
     */
    public static void choosePreviewSize(Camera.Parameters parms, int width, int height) {
        // We should make sure that the requested MPEG size is less than the preferred
        // size, and has the same aspect ratio.
        Camera.Size ppsfv = parms.getPreferredPreviewSizeForVideo();
        if (ppsfv != null) {
            Log.d(TAG, "Camera preferred preview size for video is " +
                    ppsfv.width + "x" + ppsfv.height);
        }

        //for (Camera.Size size : parms.getSupportedPreviewSizes()) {
        //    Log.d(TAG, "supported: " + size.width + "x" + size.height);
        //}

        for (Camera.Size size : parms.getSupportedPreviewSizes()) {
            if (size.width == width && size.height == height) {
                parms.setPreviewSize(width, height);
                return;
            }
        }

        Log.w(TAG, "Unable to set preview size to " + width + "x" + height);
        if (ppsfv != null) {
            parms.setPreviewSize(ppsfv.width, ppsfv.height);
        }
        // else use whatever the default size is
    }

    /**
     * Attempts to find a fixed preview frame rate that matches the desired frame rate.
     * <p/>
     * It doesn't seem like there's a great deal of flexibility here.
     * <p/>
     * TODO: follow the recipe from http://stackoverflow.com/questions/22639336/#22645327
     *
     * @return The expected frame rate, in thousands of frames per second.
     */
    public static int chooseFixedPreviewFps(Camera.Parameters parms, int desiredThousandFps) {
        List<int[]> supported = parms.getSupportedPreviewFpsRange();

        for (int[] entry : supported) {
            //Log.d(TAG, "entry: " + entry[0] + " - " + entry[1]);
            if ((entry[0] == entry[1]) && (entry[0] == desiredThousandFps)) {
                parms.setPreviewFpsRange(entry[0], entry[1]);
                return entry[0];
            }
        }

        int[] tmp = new int[2];
        parms.getPreviewFpsRange(tmp);
        int guess;
        if (tmp[0] == tmp[1]) {
            guess = tmp[0];
        } else {
            guess = tmp[1] / 2;     // shrug
        }

        Log.d(TAG, "Couldn't find match for " + desiredThousandFps + ", using " + guess);
        return guess;
    }

    public static Camera.Size getOptimalPreviewSizeByAspectRatio(List<Camera.Size> sizes, double aspectRatio) {
        if (sizes == null) return null;
        Camera.Size optimalSize = null;

        // Find size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - aspectRatio) > ASPECT_TOLERANCE) {
                continue;
            }
            if ((optimalSize == null) || (optimalSize.height < size.height)) {
                optimalSize = size;
            }
        }

        return optimalSize;
    }

    public static Camera.Size getOptimalPreviewSizeByAspectRatioAndVideoHeight(List<Camera.Size> sizes, double aspectRatio, int maxHeight) {
        if (sizes == null) return null;
        Camera.Size optimalSize = null;

        // Find size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - aspectRatio) > ASPECT_TOLERANCE) {
                continue;
            }
            if (((optimalSize == null) && (size.height <= maxHeight)) || ((optimalSize != null) && (optimalSize.height < size.height)
                    && (size.height <= maxHeight))) {
                optimalSize = size;
            }
        }

        return optimalSize;
    }

    public static int getCameraId(int cameraFacing) {
        int count = Camera.getNumberOfCameras();

        if (count > 0) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < count; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == cameraFacing) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static boolean hasFrontCamera() {
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(i, cameraInfo);

            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasFewCamera() {
        return Camera.getNumberOfCameras() > 1 && hasFrontCamera();
    }
}