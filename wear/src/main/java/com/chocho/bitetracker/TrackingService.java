package com.chocho.bitetracker;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.JobIntentService;
import android.util.Log;

public class TrackingService extends JobIntentService implements SensorEventListener {

    private int task;
    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;
    private int mSensorType;

    @Override
    public void onCreate() {
        super.onCreate();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//        gyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
//        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

//        mSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
//        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        task = intent.getIntExtra("task", 0);

        switch(task) {
            case Constants.TASK_START_TRACKING:
                Log.d("BiteTracker", "Service started");
                mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                break;
            case Constants.TASK_STOP_TRACKING:
                break;
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d("BiteTracker", "sensor changed");
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
        {
            Log.d("BiteTracker", "sensor status unreliable");
            return;
        }

        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            Log.d("ACC",
                    "x = " + Float.toString(event.values[0]) + "\n" +
                            "y = " + Float.toString(event.values[1]) + "\n" +
                            "z = " + Float.toString(event.values[2]) + "\n"
            );
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            Log.d("GYRO",
                    "x = " + Float.toString(event.values[0]) + "\n" +
                            "y = " + Float.toString(event.values[1]) + "\n" +
                            "z = " + Float.toString(event.values[2]) + "\n"
            );
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            Log.d("MAG",
                    "x = " + Float.toString(event.values[0]) + "\n" +
                            "y = " + Float.toString(event.values[1]) + "\n" +
                            "z = " + Float.toString(event.values[2]) + "\n"
            );
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d("BiteTracker", "accuracy changed");

    }

}
