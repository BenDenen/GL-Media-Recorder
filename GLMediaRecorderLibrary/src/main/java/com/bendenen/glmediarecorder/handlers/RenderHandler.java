package com.bendenen.glmediarecorder.handlers;

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.bendenen.glmediarecorder.glmodels.EGLBase;
import com.bendenen.glmediarecorder.glmodels.GLDrawer2D;

/**
 * Created by Barys_Dzenisenka on 8/12/15.
 */
public class RenderHandler implements Runnable {
    private static final boolean DEBUG = true;    // TODO set false on release
    private static final String TAG = "RenderHandler";

    private final Object mSync = new Object();
    private EGLContext mShard_context;
    private boolean mIsRecordable;
    private Object mSurface;
    private int mTexId = -1;
    private float[] mTexMatrix;
    private final float[] mMvpMatrix = new float[16];

    private boolean mRequestSetEglContext;
    private boolean mRequestRelease;
    private int mRequestDraw;

    // Video Source size for scaling
    private int mVideoSourceHeight;
    private int mVideoSourceWidth;

    private int mFrameWidth;
    private int mFrameHeight;

    private boolean isFrontFacing;

    public static final RenderHandler createHandler(final String name, int frameWidth, int frameHeight, int videoSourceWidth, int videoSourceHeight,
                                                    boolean isFrontFacing) {
        if (DEBUG) Log.v(TAG, "createHandler:");
        final RenderHandler handler = new RenderHandler(frameWidth, frameHeight, videoSourceWidth, videoSourceHeight, isFrontFacing);
        synchronized (handler.mSync) {
            new Thread(handler, !TextUtils.isEmpty(name) ? name : TAG).start();
            try {
                handler.mSync.wait();
            } catch (final InterruptedException e) {
            }
        }
        return handler;
    }

    private RenderHandler(int frameWidth, int frameHeight, int videoSourceWidth, int videoSourceHeight, boolean isFrontFacing) {
        mVideoSourceWidth = videoSourceWidth;
        mVideoSourceHeight = videoSourceHeight;
        mFrameWidth = frameWidth;
        mFrameHeight = frameHeight;
        this.isFrontFacing = isFrontFacing;
    }

    public final void setEglContext(final EGLContext shared_context, final int tex_id, final Object surface, final boolean isRecordable) {
        if (DEBUG) Log.i(TAG, "setEglContext:");
        if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture) && !(surface instanceof SurfaceHolder))
            throw new RuntimeException("unsupported window type:" + surface);
        Matrix.setIdentityM(mMvpMatrix, 0);
        synchronized (mSync) {
            if (mRequestRelease) return;
            mShard_context = shared_context;
            mTexId = tex_id;
            mSurface = surface;
            mIsRecordable = isRecordable;
            mRequestSetEglContext = true;
            mSync.notifyAll();
            try {
                mSync.wait();
            } catch (final InterruptedException e) {
            }
        }
    }

    private final void updateViewport() {

        final int view_width = mFrameWidth;
        final int view_height = mFrameHeight;

        GLES20.glViewport(0, 0, view_width, view_height);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        final double video_width = mVideoSourceWidth;
        final double video_height = mVideoSourceHeight;

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
        Matrix.scaleM(mMvpMatrix, 0, isFrontFacing ? (float) (0 - (width / view_width)) : (float) (width / view_width),
                (float) (height / view_height), 1.0f);
        if (mDrawer != null) {
            mDrawer.setMatrix(mMvpMatrix, 0);
        }

    }

    public final void draw() {
        draw(mTexId, mTexMatrix);
    }

    public final void draw(final int tex_id) {
        draw(tex_id, mTexMatrix);
    }

    public final void draw(final float[] tex_matrix) {
        draw(mTexId, tex_matrix);
    }

    public final void draw(final int tex_id, final float[] tex_matrix) {
        synchronized (mSync) {
            if (mRequestRelease) return;
            mTexId = tex_id;
            mTexMatrix = tex_matrix;
            mRequestDraw++;
            mSync.notifyAll();
        }
    }

    public boolean isValid() {
        synchronized (mSync) {
            return !(mSurface instanceof Surface) || ((Surface) mSurface).isValid();
        }
    }

    public final void release() {
        if (DEBUG) Log.i(TAG, "release:");
        synchronized (mSync) {
            if (mRequestRelease) return;
            mRequestRelease = true;
            mSync.notifyAll();
            try {
                mSync.wait();
            } catch (final InterruptedException e) {
            }
        }
    }

    //********************************************************************************
//********************************************************************************
    private EGLBase mEgl;
    private EGLBase.EglSurface mInputSurface;
    private GLDrawer2D mDrawer;

    @Override
    public final void run() {
        if (DEBUG) Log.i(TAG, "RenderHandler thread started:");
        synchronized (mSync) {
            mRequestSetEglContext = mRequestRelease = false;
            mRequestDraw = 0;
            mSync.notifyAll();
        }
        boolean localRequestDraw;
        for (; ; ) {
            synchronized (mSync) {
                if (mRequestRelease) break;
                if (mRequestSetEglContext) {
                    mRequestSetEglContext = false;
                    internalPrepare();
                }
                localRequestDraw = mRequestDraw > 0;
                if (localRequestDraw) {
                    mRequestDraw--;
                }
            }
            if (localRequestDraw) {
                if ((mEgl != null) && mTexId >= 0) {
                    mInputSurface.makeCurrent();
                    mDrawer.draw(mTexId, mTexMatrix);
                    mInputSurface.swap();
                }
            } else {
                synchronized (mSync) {
                    try {
                        mSync.wait();
                    } catch (final InterruptedException e) {
                        break;
                    }
                }
            }
        }
        synchronized (mSync) {
            mRequestRelease = true;
            internalRelease();
            mSync.notifyAll();
        }
        if (DEBUG) Log.i(TAG, "RenderHandler thread finished:");
    }

    private final void internalPrepare() {
        if (DEBUG) Log.i(TAG, "internalPrepare:");
        internalRelease();
        mEgl = new EGLBase(mShard_context, false, mIsRecordable);

        mInputSurface = mEgl.createFromSurface(mSurface);

        mInputSurface.makeCurrent();
        mDrawer = new GLDrawer2D();
        mSurface = null;

        // TODO: Need to check for recreation
        if ((mVideoSourceHeight != -1) && (mVideoSourceWidth != -1)) {
            updateViewport();
        }
        mSync.notifyAll();
    }

    private final void internalRelease() {
        if (DEBUG) Log.i(TAG, "internalRelease:");
        if (mDrawer != null) {
            mDrawer.release();
            mDrawer = null;
        }
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        if (mEgl != null) {
            mEgl.release();
            mEgl = null;
        }
    }

}
