package com.bendenen.glrecordertest.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.bendenen.glrecordertest.GlRecorderTestApplication;
import com.bendenen.glrecordertest.utils.Utils;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Barys_Dzenisenka on 4/12/16.
 */
public class InLineRecordView extends TextView implements View.OnClickListener {

    private static final int SIZE = dpToPx(55);

    private static final int STROKE_SIZE = dpToPx(4);

    private static final int REFRESH_TIME = 70;

    private static final int RADIUS_CHANGE_STEP_PX = dpToPx(1);

    private static final int OUTER_CIRCLE_RADIUS = SIZE / 2 - STROKE_SIZE;

    private static final float SQUARE_BEGINNING = (float) (OUTER_CIRCLE_RADIUS * (1 - 1 / Math.sqrt(2))) + STROKE_SIZE * 2;

    private static final int INNER_CIRCLE_RADIUS = OUTER_CIRCLE_RADIUS - (int) SQUARE_BEGINNING;

    private boolean isActivated;

    private int dynamicRadius = OUTER_CIRCLE_RADIUS - dpToPx(1);

    private RectF rectF;

    private Timer animationTimer;

    private Paint orangePaint;

    private Paint whitePaint;

    private float sweepAngle = 360;

    private int rotateAngle;

    private CompoundButton.OnCheckedChangeListener onCheckedChangeListener;

    public InLineRecordView(Context context) {
        super(context);
        init();
    }

    public InLineRecordView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public InLineRecordView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        setOnClickListener(this);
        setTextColor(Color.WHITE);
        setGravity(Gravity.CENTER);


        rectF = new RectF(STROKE_SIZE / 2 + getPaddingLeft(),
                STROKE_SIZE / 2 + getPaddingTop(),
                SIZE - STROKE_SIZE / 2 - getPaddingRight(),
                SIZE - STROKE_SIZE / 2 - getPaddingBottom());


        orangePaint = new Paint();
        orangePaint.setAntiAlias(true);
        // orange
        orangePaint.setColor(Color.rgb(235, 55, 54));

        whitePaint = new Paint();
        whitePaint.setAntiAlias(true);
        whitePaint.setStyle(Paint.Style.STROKE);
        whitePaint.setColor(Color.WHITE);
        whitePaint.setStrokeWidth(STROKE_SIZE);
    }

    public void setTransparency(float transparency) {
        int transparentCode = (int) (transparency * 255);

        orangePaint.setColor(Color.argb(transparentCode, 235, 55, 54));
        whitePaint.setColor(Color.argb(transparentCode, 255, 255, 255));

        invalidate();
    }

    public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener onCheckedChangeListener) {
        this.onCheckedChangeListener = onCheckedChangeListener;
    }

    public void setRotateAngle(int rotateAngle) {
        this.rotateAngle = rotateAngle;

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(SIZE, SIZE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.rotate(rotateAngle, (float) (getWidth() / 2.), (float) (getHeight() / 2.));

        if (dynamicRadius < OUTER_CIRCLE_RADIUS) {
            RectF rectF = new RectF(SQUARE_BEGINNING, SQUARE_BEGINNING, SIZE - SQUARE_BEGINNING, SIZE - SQUARE_BEGINNING);
            canvas.drawRoundRect(rectF, STROKE_SIZE * 2, STROKE_SIZE * 2, orangePaint);
        }

        canvas.drawCircle(SIZE / 2, SIZE / 2, dynamicRadius, orangePaint);

        canvas.drawArc(rectF, -90, sweepAngle, false, whitePaint);

        super.onDraw(canvas);
    }

    public boolean isActivated() {
        return isActivated;
    }

    public void setProgress(double progress) {
        sweepAngle = (float) progress * 360;
        invalidate();
    }

    public void setIsActivated(boolean isActivated) {

        boolean isActivatedChanged = this.isActivated != isActivated;
        this.isActivated = isActivated;

        if (this.isActivated) {
            sweepAngle = 0;
        }

        if (isActivatedChanged) {
            setEnabled(false);

            stopUpdaterTimer();

            animationTimer = new Timer();
            animationTimer.schedule(new CircleUpdaterTask(), 0, REFRESH_TIME);

            if (onCheckedChangeListener != null) {
                onCheckedChangeListener.onCheckedChanged(null, isActivated);
            }
        }
    }

    public void setText(String text) {
        if (isActivated) {
            super.setText(text);
        } else {
            super.setText("");
        }
    }

    @Override
    public void onClick(View v) {
        setIsActivated(!isActivated);
    }

    private void stopUpdaterTimer() {
        if (animationTimer != null) {
            animationTimer.cancel();
        }
    }

    private static int dpToPx(int dp) {
        return Utils.dpToPx(GlRecorderTestApplication.getInstance().getResources(), dp);
    }

    private class CircleUpdaterTask extends TimerTask {

        @Override
        public void run() {
            dynamicRadius += isActivated ? -RADIUS_CHANGE_STEP_PX : RADIUS_CHANGE_STEP_PX;

            boolean isAnimationInProgress = true;
            if (dynamicRadius <= INNER_CIRCLE_RADIUS || dynamicRadius >= OUTER_CIRCLE_RADIUS) {
                cancel();
                isAnimationInProgress = false;
            }

            updateView(isAnimationInProgress);
        }

        private void updateView(final boolean isAnimationInProgress) {
            post(new Runnable() {
                @Override
                public void run() {
                    setEnabled(!isAnimationInProgress);
                    invalidate();
                }
            });
        }
    }
}
