package com.bendenen.glrecordertest;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.bendenen.glmediarecorder.GLVideoView;
import com.bendenen.glmediarecorder.encoder.MediaAudioEncoder;
import com.bendenen.glmediarecorder.encoder.MediaEncoder;
import com.bendenen.glmediarecorder.encoder.MediaVideoEncoder;
import com.bendenen.glmediarecorder.mediamuxer.MediaMuxerWrapper;
import com.bendenen.glrecordertest.utils.Utils;
import com.bendenen.glrecordertest.view.InLineVideoCameraControlsView;
import com.danikula.aibolit.Aibolit;
import com.danikula.aibolit.annotation.InjectView;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    public static final double ASPECT_RATIO = (double) 4 / 3;

    private static final long FILE_SIZE_UPDATE_INTERVAL = 100;

    private static final long RECORD_TIME_UPDATE_INTERVAL = 1000;

    private static final long RECORD_TIME_DELAY = 200;

    private static final long CONTROLS_BAR_ANIMATION_TIME = 300;

    private static final long AVARAGE_INDEX = 400000;

    public interface StartOrResultListener {
        void onRecordFinished(Uri fileUri);

        void onStartRecording();
    }

    @InjectView(R.id.rootContainer)
    private RelativeLayout rootContainer;

    @InjectView(R.id.buttonsHolder)
    private InLineVideoCameraControlsView buttonsHolder;

    @InjectView(R.id.glVideoView)
    private GLVideoView glVideoView;

    private StartOrResultListener startOrResultListener;

    private Timer progressUpdaterTimer = new Timer();

    private long recordStartTime;

    private Uri videoUri;

    // Variables for OpenGL implementation
    private MediaMuxerWrapper muxer;

    private boolean isRecording = false;

    private int mainColorResourceId = -1;

    private long maxRecordingSize = 40000;


    /**
     * callback methods from encoder
     */
    private final MediaEncoder.MediaEncoderListener mediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
        @Override
        public void onPrepared(final MediaEncoder encoder) {
            if (encoder instanceof MediaVideoEncoder)
                glVideoView.setVideoEncoder((MediaVideoEncoder) encoder);
        }

        @Override
        public void onStopped(final MediaEncoder encoder) {
            if (encoder instanceof MediaVideoEncoder)
                glVideoView.setVideoEncoder(null);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Aibolit.setInjectedContentView(this, R.layout.activity_main);

        ViewGroup.LayoutParams layoutParams = rootContainer.getLayoutParams();
        layoutParams.width = Utils.getScreenWidth(this);
        layoutParams.height = Utils.getScreenHeightByAspectRatio(layoutParams.width, ASPECT_RATIO);
        rootContainer.requestLayout();


        glVideoView.setPreferredAspectRatio(ASPECT_RATIO);
        glVideoView.setMaxVideoHeight(800);

        updateButtonsState();

        buttonsHolder.setCameraSwitchListener(new CameraSwitchClickListener());
        buttonsHolder.setRecordButtonCheckedChangeListener(new RecordClickHandler());
    }

    private void updateButtonsState() {
        boolean recording = isRecording();

        buttonsHolder.updateState(recording, true);
    }

    private void startControlBarAnimation() {

        int colorFrom = ((ColorDrawable) buttonsHolder.getBackground()).getColor();
        int colorTo = getResources().getColor(mainColorResourceId == -1 ? R.color.navigation_bg : mainColorResourceId);
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                buttonsHolder.setBackgroundColor((int) animator.getAnimatedValue());
            }

        });
        colorAnimation.setDuration(150);
        colorAnimation.start();


        final int rootContainerHeight = rootContainer.getHeight();

        final int height = (int) getResources().getDimension(R.dimen.in_line_video_camera_controls_container_height);
        Animation transformAnimation = new Animation() {

            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                ViewGroup.LayoutParams params = rootContainer.getLayoutParams();
                params.height = (int) (rootContainerHeight + (height * interpolatedTime));
                rootContainer.requestLayout();
            }
        };
        transformAnimation.setDuration(CONTROLS_BAR_ANIMATION_TIME); // need to check
        rootContainer.startAnimation(transformAnimation);

    }

    /**
     * start resorcing
     * This is a sample project and call this on UI thread to avoid being complicated
     * but basically this should be called on private thread because prepareing
     * of encoder is heavy work
     */
    private void startRecording() {
        try {
            muxer = new MediaMuxerWrapper(Utils.getNewVideoFile());
            // for video capturing
            new MediaVideoEncoder(muxer, mediaEncoderListener, 800, 600,
                    glVideoView.getVideoWidth(), glVideoView.getVideoHeight(), glVideoView.isFrontFacing());
            // for audio capturing
            new MediaAudioEncoder(muxer, mediaEncoderListener);
            muxer.prepare();
            muxer.startRecording();

            isRecording = true;
            recordStartTime = System.currentTimeMillis();
            startUpdatingRecordProgress();
            if (startOrResultListener != null) {
                startOrResultListener.onStartRecording();
            }
            startControlBarAnimation();

        } catch (final IOException e) {
            Log.e(TAG, "Start Capture Error: ", e);
        }
    }

    /**
     * request stop recording
     */
    private void stopRecording(final boolean isMoveToPreview) {
        if (muxer != null) {
            muxer.setRecordStateChangeListener(new MediaMuxerWrapper.RecordStateChangeListener() {
                @Override
                public void onRecordStateChanged(boolean isStarted) {
                    if (!isStarted) {
                        isRecording = false;
                        stopUpdatingSizeProgress();
                        if ((startOrResultListener != null) && isMoveToPreview) {
                            MainActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Log.e("MyTag", muxer.getOutputFile().toString());
                                    startOrResultListener.onRecordFinished(Uri.fromFile(muxer.getOutputFile()));
                                    muxer = null;
                                }
                            });
                        } else {
                            // TODO: Need to check this case
                            muxer.getOutputFile().delete();
                            muxer = null;
                        }
                    }
                }
            });
            muxer.stopRecording();

        }
    }

    public boolean isRecording() {
        return isRecording;
    }


    private void handleStartStop(boolean isStart) {

        try {
            if (isStart) {
                startRecording();


            } else {
                stopRecording(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error using camera: ", e);
            updateButtonsState();
            if (e instanceof RuntimeException && e.getMessage() != null && e.getMessage().contains("stop failed")) {
                // if press stop right after start we get this native RuntimeException.
                // We can't fix it. Simple ignore. Native camera app behaves the same
            } else {
                Toast.makeText(this, R.string.video_recording_error, Toast.LENGTH_LONG).show();
            }
        }

        updateButtonsState();
    }


    private void startUpdatingRecordProgress() {
        progressUpdaterTimer.cancel();
        progressUpdaterTimer = new Timer();

        // actual recording does not start when this method invoked. We don't know when it starts...
        progressUpdaterTimer.schedule(new RecordTimeMonitor(System.currentTimeMillis() + RECORD_TIME_DELAY), RECORD_TIME_DELAY, RECORD_TIME_UPDATE_INTERVAL);

        progressUpdaterTimer.schedule(new FileSizeMonitor(muxer.getOutputFile()), FILE_SIZE_UPDATE_INTERVAL, FILE_SIZE_UPDATE_INTERVAL);
    }

    private void stopUpdatingSizeProgress() {
        progressUpdaterTimer.cancel();
    }

    private class RecordTimeMonitor extends TimerTask {
        private long recordStartTime;

        private RecordTimeMonitor(long recordStartTime) {
            this.recordStartTime = recordStartTime;
        }

        @Override
        public void run() {
            buttonsHolder.post(new Runnable() {
                @Override
                public void run() {
                    long elapsedTime = System.currentTimeMillis() - recordStartTime;
                    int elapsedMinutes = (int) (elapsedTime / 1000 / 60);
                    if (elapsedMinutes > 0) {
                        elapsedTime = elapsedTime - (elapsedMinutes * 1000 * 60);
                    }
                    int elapsedSeconds = (int) (elapsedTime / 1000);

                    buttonsHolder.updateRecordTime(String.format("%d.%02d", elapsedMinutes, elapsedSeconds));
                }
            });
        }
    }

    private class FileSizeMonitor extends TimerTask {
        private File file;

        private FileSizeMonitor(File file) {
            this.file = file;
        }

        @Override
        public void run() {
            // TODO: It is silly method. We need to change it.
            if (file.length() < maxRecordingSize) {
                final double progress = (double) file.length() / maxRecordingSize;
                buttonsHolder.post(new Runnable() {
                    @Override
                    public void run() {
                        buttonsHolder.setProgress(progress);
                    }
                });
            } else {
                buttonsHolder.post(new Runnable() {
                    @Override
                    public void run() {
                        handleStartStop(false);
                    }
                });
            }


        }
    }


    private class RecordClickHandler implements CompoundButton.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            handleStartStop(isChecked);
        }
    }

    private class CameraSwitchClickListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            if (glVideoView == null) {
                return;
            }
            if (com.bendenen.glmediarecorder.utils.CameraUtils.hasFewCamera()) {
                glVideoView.switchCamera();
            }
        }
    }
}
