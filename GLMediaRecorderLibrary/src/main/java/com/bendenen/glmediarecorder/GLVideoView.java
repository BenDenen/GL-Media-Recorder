package com.bendenen.glmediarecorder;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import com.bendenen.glmediarecorder.encoder.MediaVideoEncoder;
import com.bendenen.glmediarecorder.glmodels.GLDrawer2D;
import com.bendenen.glmediarecorder.utils.CameraUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Barys_Dzenisenka on 8/12/15.
 */
public class GLVideoView extends GLSurfaceView {

    private static final boolean DEBUG = true; // TODO set false on release
    private static final String TAG = GLVideoView.class.getSimpleName();

    private static final int CAMERA_ID = 0;

    private final CameraSurfaceRenderer mRenderer;
    private boolean mHasSurface;
    private CameraHandler mCameraHandler = null;
    private int mVideoWidth, mVideoHeight;
    private int mRotation;

    private double mPreferredAspectRatio = (double) 16 / 9;
    private int maxVideoHeight = -1;

    private int currentCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;

    public GLVideoView(final Context context) {
        this(context, null, 0);
    }

    public GLVideoView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GLVideoView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs);
        mRenderer = new CameraSurfaceRenderer(this);
        setEGLContextClientVersion(2);    // GLES 2.0, API >= 8
        setRenderer(mRenderer);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mHasSurface) {
            if (mCameraHandler == null) {
                if (DEBUG) Log.v(TAG, "surface already exist");
                startPreview(getWidth(), getHeight());
            }
        }
    }

    @Override
    public void onPause() {
        if (mCameraHandler != null) {
            // just request stop prviewing
            mCameraHandler.stopPreview(false);
        }
        super.onPause();
    }

    public boolean isFrontFacing() {
        return currentCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT;
    }

    public void switchCamera() {
        if (CameraUtils.hasFewCamera()) {
            if (currentCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                currentCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
            } else {
                currentCameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
            }
            if (mCameraHandler != null) {
                // just request stop prviewing
                mCameraHandler.stopPreview(true);
            }
            if (mHasSurface) {
                if (mCameraHandler == null) {
                    if (DEBUG) Log.v(TAG, "surface already exist");
                    startPreview(getWidth(), getHeight());
                }
            }

        }
    }

    public void setPreferredAspectRatio(double aspectRatio) {
        mPreferredAspectRatio = aspectRatio;
    }

    public void setMaxVideoHeight(int maxVideoHeight) {
        this.maxVideoHeight = maxVideoHeight;
    }

    public void setVideoSize(final int width, final int height) {
        if ((mRotation % 180) == 0) {
            mVideoWidth = width;
            mVideoHeight = height;
        } else {
            mVideoWidth = height;
            mVideoHeight = width;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.updateViewport();
            }
        });
    }

    public int getVideoWidth() {
        return mVideoWidth;
    }

    public int getVideoHeight() {
        return mVideoHeight;
    }

    public SurfaceTexture getSurfaceTexture() {
        if (DEBUG) Log.v(TAG, "getSurfaceTexture:");
        return mRenderer != null ? mRenderer.mSTexture : null;
    }

    @Override
    public void surfaceDestroyed(final SurfaceHolder holder) {
        if (DEBUG) Log.v(TAG, "surfaceDestroyed:");
        if (mCameraHandler != null) {
            // wait for finish previewing here
            // otherwise camera try to display on un-exist Surface and some error will occure
            mCameraHandler.stopPreview(true);
        }
        mCameraHandler = null;
        mHasSurface = false;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.onSurfaceDestroyed();
            }
        });
        super.surfaceDestroyed(holder);
    }

    public void setVideoEncoder(final MediaVideoEncoder encoder) {
        if (DEBUG) Log.v(TAG, "setVideoEncoder:tex_id=" + mRenderer.hTex + ",encoder=" + encoder);
        queueEvent(new Runnable() {
            @Override
            public void run() {
                synchronized (mRenderer) {
                    if (encoder != null) {
                        encoder.setEglContext(EGL14.eglGetCurrentContext(), mRenderer.hTex);
                    }
                    mRenderer.mVideoEncoder = encoder;
                }
            }
        });
    }

    //********************************************************************************
    //********************************************************************************

    private synchronized void startPreview(final int width, final int height) {
        if (mCameraHandler == null) {
            final CameraThread thread = new CameraThread(this);
            thread.start();
            mCameraHandler = thread.getHandler();
        }
        mCameraHandler.startPreview(width, height);
    }

    /**
     * GLSurfaceViewのRenderer
     */
    private static final class CameraSurfaceRenderer
            implements GLSurfaceView.Renderer,
            SurfaceTexture.OnFrameAvailableListener {    // API >= 11

        private final WeakReference<GLVideoView> mWeakParent;
        private SurfaceTexture mSTexture;    // API >= 11
        private int hTex;
        private GLDrawer2D mDrawer;
        private final float[] mStMatrix = new float[16];
        private final float[] mMvpMatrix = new float[16];
        private MediaVideoEncoder mVideoEncoder;

        public CameraSurfaceRenderer(final GLVideoView parent) {
            if (DEBUG) Log.v(TAG, "CameraSurfaceRenderer:");
            mWeakParent = new WeakReference<GLVideoView>(parent);
            Matrix.setIdentityM(mMvpMatrix, 0);
        }

        @Override
        public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
            if (DEBUG) Log.v(TAG, "onSurfaceCreated:");
            // This renderer required OES_EGL_image_external extension
            final String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);    // API >= 8
            if (!extensions.contains("OES_EGL_image_external"))
                throw new RuntimeException("This system does not support OES_EGL_image_external.");
            // create textur ID
            hTex = GLDrawer2D.initTex();
            // create SurfaceTexture with texture ID.
            mSTexture = new SurfaceTexture(hTex);
            mSTexture.setOnFrameAvailableListener(this);
            // clear screen with yellow color so that you can see rendering rectangle
            GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
            final GLVideoView parent = mWeakParent.get();
            if (parent != null) {
                parent.mHasSurface = true;
            }
            // create object for preview display
            mDrawer = new GLDrawer2D();
            mDrawer.setMatrix(mMvpMatrix, 0);
        }

        @Override
        public void onSurfaceChanged(final GL10 unused, final int width, final int height) {
            if (DEBUG) Log.v(TAG, String.format("onSurfaceChanged:(%d,%d)", width, height));
            // if at least with or height is zero, initialization of this view is still progress.
            if ((width == 0) || (height == 0)) return;
            updateViewport();
            final GLVideoView parent = mWeakParent.get();
            if (parent != null) {
                parent.startPreview(width, height);
            }
        }

        /**
         * when GLSurface context is soon destroyed
         */
        public void onSurfaceDestroyed() {
            if (DEBUG) Log.v(TAG, "onSurfaceDestroyed:");
            // TODO: Need to check this moment. Not sure that we clean texture memory
            GLDrawer2D.deleteTex(hTex);
            if (mDrawer != null) {
                mDrawer.release();
                mDrawer = null;
            }
            if (mSTexture != null) {
                mSTexture.release();
                mSTexture = null;
            }
        }

        private final void updateViewport() {
            final GLVideoView parent = mWeakParent.get();
            if (parent != null) {

                final int view_width = parent.getWidth();
                final int view_height = parent.getHeight();

                GLES20.glViewport(0, 0, view_width, view_height);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                final double video_width = parent.mVideoWidth;
                final double video_height = parent.mVideoHeight;

                if (video_width == 0 || video_height == 0) return;

                Matrix.setIdentityM(mMvpMatrix, 0);

                final double view_aspect = view_width / (double) view_height;
                Log.i(TAG, String.format("view(%d,%d)%f,video(%1.0f,%1.0f)", view_width, view_height, view_aspect, video_width, video_height));
                final double scale_x = view_width / video_width;
                final double scale_y = view_height / video_height;
                final double scale = Math.max(scale_x, scale_y);
                final double width = scale * video_width;
                final double height = scale * video_height;
                Log.v(TAG, String.format("size(%1.0f,%1.0f),scale(%f,%f),mat(%f,%f)",
                        width, height, scale_x, scale_y, width / view_width, height / view_height));
                Matrix.scaleM(mMvpMatrix, 0, (float) (width / view_width), (float) (height / view_height), 1.0f);
                if (mDrawer != null) {
                    mDrawer.setMatrix(mMvpMatrix, 0);
                }

            }
        }

        private volatile boolean requesrUpdateTex = false;
        private boolean flip = true;

        /**
         * drawing to GLSurface
         * we set renderMode to GLSurfaceView.RENDERMODE_WHEN_DIRTY,
         * this method is only called when #requestRender is called(= when texture is required to update)
         * if you don't set RENDERMODE_WHEN_DIRTY, this method is called at maximum 60fps
         */
        @Override
        public void onDrawFrame(final GL10 unused) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            if (requesrUpdateTex) {
                requesrUpdateTex = false;
                // update texture(came from camera)
                mSTexture.updateTexImage();
                // get texture matrix
                mSTexture.getTransformMatrix(mStMatrix);
            }
            // draw to preview screen
            mDrawer.draw(hTex, mStMatrix);
            flip = !flip;
            if (flip) {    // ~30fps
                synchronized (this) {
                    if (mVideoEncoder != null) {
                        // notify to capturing thread that the camera frame is available.
                        mVideoEncoder.frameAvailableSoon(mStMatrix);
                    }
                }
            }
        }

        @Override
        public void onFrameAvailable(final SurfaceTexture st) {
            requesrUpdateTex = true;
        }
    }

    /**
     * Handler class for asynchronous camera operation
     */
    private static final class CameraHandler extends Handler {
        private static final int MSG_PREVIEW_START = 1;
        private static final int MSG_PREVIEW_STOP = 2;
        private CameraThread mThread;

        public CameraHandler(final CameraThread thread) {
            mThread = thread;
        }

        public void startPreview(final int width, final int height) {
            sendMessage(obtainMessage(MSG_PREVIEW_START, width, height));
        }

        /**
         * request to stop camera preview
         *
         * @param needWait need to wait for stopping camera preview
         */
        public void stopPreview(final boolean needWait) {
            synchronized (this) {
                sendEmptyMessage(MSG_PREVIEW_STOP);
                if (needWait && mThread.mIsRunning) {
                    try {
                        if (DEBUG) Log.d(TAG, "wait for terminating of camera thread");
                        wait();
                    } catch (final InterruptedException e) {
                    }
                }
            }
        }

        /**
         * message handler for camera thread
         */
        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case MSG_PREVIEW_START:
                    mThread.startPreview(msg.arg1, msg.arg2);
                    break;
                case MSG_PREVIEW_STOP:
                    mThread.stopPreview();
                    synchronized (this) {
                        notifyAll();
                    }
                    Looper.myLooper().quit();
                    mThread = null;
                    break;
                default:
                    throw new RuntimeException("unknown message:what=" + msg.what);
            }
        }
    }

    /**
     * Thread for asynchronous operation of camera preview
     */
    private static final class CameraThread extends Thread {
        private final Object mReadyFence = new Object();
        private final WeakReference<GLVideoView> mWeakParent;
        private CameraHandler mHandler;
        private volatile boolean mIsRunning = false;
        private Camera mCamera;
        private boolean mIsFrontFace;

        public CameraThread(final GLVideoView parent) {
            super("Camera thread");
            mWeakParent = new WeakReference<GLVideoView>(parent);
        }

        public CameraHandler getHandler() {
            synchronized (mReadyFence) {
                try {
                    mReadyFence.wait();
                } catch (final InterruptedException e) {
                }
            }
            return mHandler;
        }

        /**
         * message loop
         * prepare Looper and create Handler for this thread
         */
        @Override
        public void run() {
            if (DEBUG) Log.d(TAG, "Camera thread start");
            Looper.prepare();
            synchronized (mReadyFence) {
                mHandler = new CameraHandler(this);
                mIsRunning = true;
                mReadyFence.notify();
            }
            Looper.loop();
            if (DEBUG) Log.d(TAG, "Camera thread finish");
            synchronized (mReadyFence) {
                mHandler = null;
                mIsRunning = false;
            }
        }

        /**
         * start camera preview
         *
         * @param width
         * @param height
         */
        private final void startPreview(final int width, final int height) {
            if (DEBUG) Log.v(TAG, "startPreview:");
            final GLVideoView parent = mWeakParent.get();
            if ((parent != null) && (mCamera == null)) {
                // This is a sample project so just use 0 as camera ID.
                // it is better to selecting camera is available
                try {
                    // TODO: Check this moment
                    int cameraId = CameraUtils.getCameraId(parent.currentCameraFacing);
                    if (cameraId == -1) {
                        cameraId = CameraUtils.getCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT);
                    }
                    if(cameraId == -1) {
                        cameraId = CameraUtils.getCameraId(Camera.CameraInfo.CAMERA_FACING_BACK);
                    }
                    if (cameraId == -1) {
                        // TODO: Stop recording. Hide Camera frame
                        return;
                    }

                    mCamera = Camera.open(cameraId);
                    final Camera.Parameters params = mCamera.getParameters();
                    final List<String> focusModes = params.getSupportedFocusModes();
                    if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    } else {
                        if (DEBUG) Log.i(TAG, "Camera does not support autofocus");
                    }
                    // let's try fastest frame rate. You will get near 60fps, but your device become hot.
                    final List<int[]> supportedFpsRange = params.getSupportedPreviewFpsRange();
                    final int[] max_fps = supportedFpsRange.get(supportedFpsRange.size() - 1);
                    Log.i(TAG, String.format("fps:%d-%d", max_fps[0], max_fps[1]));
                    params.setPreviewFpsRange(max_fps[0], max_fps[1]);
                    params.setRecordingHint(true);
                    // request preview size
                    // this is a sample project and just use fixed value
                    // if you want to use other size, you also need to change the recording size.

                    Camera.Size size;
                    if (parent.maxVideoHeight != -1) {
                        size = CameraUtils.getOptimalPreviewSizeByAspectRatioAndVideoHeight(params.getSupportedPreviewSizes(), parent.mPreferredAspectRatio,
                                parent.maxVideoHeight);
                    } else {
                        size = CameraUtils.getOptimalPreviewSizeByAspectRatio(params.getSupportedPreviewSizes(), parent.mPreferredAspectRatio);
                    }
                    params.setPreviewSize(size.width, size.height);
                    parent.setVideoSize(size.width, size.height);

                    // rotate camera preview according to the device orientation

                    setRotation(params, cameraId);

                    mCamera.setParameters(params);
                    // get the actual preview size
                    final Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
                    Log.i(TAG, String.format("previewSize(%d, %d)", previewSize.width, previewSize.height));
                    // adjust view size with keeping the aspect ration of camera preview.
                    // here is not a UI thread and we should request parent view to execute.
                    parent.post(new Runnable() {
                        @Override
                        public void run() {
                            parent.setVideoSize(previewSize.width, previewSize.height);
                        }
                    });
                    final SurfaceTexture st = parent.getSurfaceTexture();
                    st.setDefaultBufferSize(previewSize.width, previewSize.height);
                    mCamera.setPreviewTexture(st);
                } catch (final IOException e) {
                    Log.e(TAG, "startPreview:", e);
                    if (mCamera != null) {
                        mCamera.release();
                        mCamera = null;
                    }
                } catch (final RuntimeException e) {
                    Log.e(TAG, "startPreview:", e);
                    if (mCamera != null) {
                        mCamera.release();
                        mCamera = null;
                    }
                }
                if (mCamera != null) {
                    // start camera preview display
                    mCamera.startPreview();
                }
            }
        }

        /**
         * stop camera preview
         */
        private void stopPreview() {
            if (DEBUG) Log.v(TAG, "stopPreview:");
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
            final GLVideoView parent = mWeakParent.get();
            if (parent == null) return;
            parent.mCameraHandler = null;
        }

        /**
         * rotate preview screen according to the device orientation
         *
         * @param params
         */
        private final void setRotation(final Camera.Parameters params, int cameraId) {
            if (DEBUG) Log.v(TAG, "setRotation:");
            final GLVideoView parent = mWeakParent.get();
            if (parent == null) return;

            final Display display = ((WindowManager) parent.getContext()
                    .getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            final int rotation = display.getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }
            // get whether the camera is front camera or back camera
            final Camera.CameraInfo info =
                    new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(cameraId, info);

            mIsFrontFace = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
            if (mIsFrontFace) {    // front camera
                degrees = (info.orientation + degrees) % 360;
                degrees = (360 - degrees) % 360;  // reverse
            } else {  // back camera
                degrees = (info.orientation - degrees + 360) % 360;
            }

            // apply rotation setting
            mCamera.setDisplayOrientation(degrees);
            parent.mRotation = degrees;
            // XXX This method fails to call and camera stops working on some devices.
        }

    }


}
