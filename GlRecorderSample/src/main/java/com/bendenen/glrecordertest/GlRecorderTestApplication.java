package com.bendenen.glrecordertest;

import android.app.Application;

/**
 * Created by Barys_Dzenisenka on 4/12/16.
 */
public class GlRecorderTestApplication extends Application {

    private static GlRecorderTestApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static GlRecorderTestApplication getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Application is not initialized yet");
        }
        return instance;
    }
}
