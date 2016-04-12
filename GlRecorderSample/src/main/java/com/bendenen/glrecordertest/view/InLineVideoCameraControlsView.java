package com.bendenen.glrecordertest.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bendenen.glrecordertest.R;
import com.bendenen.glrecordertest.utils.Utils;
import com.danikula.aibolit.Aibolit;
import com.danikula.aibolit.annotation.InjectOnClickListener;
import com.danikula.aibolit.annotation.InjectView;

/**
 * Created by Barys_Dzenisenka on 4/12/16.
 */
public class InLineVideoCameraControlsView extends FrameLayout {

    @InjectView(R.id.cameraControlBar)
    private RelativeLayout cameraControlBar;

    @InjectView(R.id.recordButton)
    private InLineRecordView recordButton;

    @InjectView(R.id.cameraSwitcherButton)
    private View cameraSwitcherButton;

    @InjectView(R.id.videoRecordingTimer)
    private TextView videoRecordingTimer;

    private OnClickListener cameraSwitchListener;

    private CompoundButton.OnCheckedChangeListener recordButtonCheckedChangeListener;

    private OnClickListener onAttachButtonClickListener;

    private OnClickListener onCloseVideoButtonClickListener;

    public InLineVideoCameraControlsView(Context context) {
        super(context);
        init(null);
    }

    public InLineVideoCameraControlsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        LayoutInflater.from(getContext()).inflate(R.layout.in_line_camera_controls_view, this);
        Aibolit.doInjections(this);

        //need to avoid of changing button container width, after recording started
    }

    public void setOnAttachButtonClickListener(OnClickListener onAttachButtonClickListener) {
        this.onAttachButtonClickListener = onAttachButtonClickListener;
    }

    public void setOnCloseVideoButtonClickListener(OnClickListener onCloseVideoButtonClickListener) {
        this.onCloseVideoButtonClickListener = onCloseVideoButtonClickListener;
    }

    public void setProgress(double progress) {
        recordButton.setProgress(progress);
    }

    public void updateState(boolean recording, boolean hasFewCamera) {

        Utils.setVisibility(recording, videoRecordingTimer);

        //switch camera button
        Utils.setVisibility(hasFewCamera && !recording, cameraSwitcherButton);

        //record button
        recordButton.setOnCheckedChangeListener(null);
        recordButton.setIsActivated(recording);
        recordButton.setOnCheckedChangeListener(recordButtonCheckedChangeListener);

    }

    public void setCameraSwitchListener(OnClickListener cameraSwitchListener) {
        this.cameraSwitchListener = cameraSwitchListener;
    }

    public void setRecordButtonCheckedChangeListener(CompoundButton.OnCheckedChangeListener recordButtonCheckedChangeListener) {
        this.recordButtonCheckedChangeListener = recordButtonCheckedChangeListener;
    }

    public void updateRecordTime(String recordTime) {
        videoRecordingTimer.setText(recordTime);
    }

    @InjectOnClickListener(R.id.cameraSwitcherButton)
    private void onCameraSwitchClicked(View view) {
        if (cameraSwitchListener != null) {
            cameraSwitchListener.onClick(view);
        }
    }

}
