package com.chocho.bitetracker;

import android.app.IntentService;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.AsyncQueryHandler;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.security.AccessController.getContext;

//public class MainActivity extends WearableActivity implements SensorEventListener {
public class MainActivity extends WearableActivity {
    private static final String TAG = "BiteTracker";

    private TextView mTextView;

//    private SensorManager mSensorManager;
//    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;
    LinearAccelerometer linearAccelerometer;

    /* Using service */
    private Button mStartTrackingBtn;
    private Button mStopTrackingBtn;

    boolean isTracking = false;

    byte[] sensorData;

    long startTime;

    File folder;

    String filenameAcc;
    String filenameGyro;
    String filenameMag;
    FileWriter fwAcc;
    FileWriter fwGyro;
    FileWriter fwMag;
    long packetNum;
    String row;

    Context context;

    DataClient mDataClient;
    /* For message client */
    private static final String SENSOR_PROCESSING_CAPABILITY_NAME = "sensor_processing";
    public static final String SENSOR_PROCESSING_MESSAGE_PATH = "/sensor_processing";

    String processingNodeId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        mTextView = (TextView) findViewById(R.id.text);

        // Enables Always-on
        setAmbientEnabled();

        folder = new File(this.getExternalFilesDir(null)
                + "/Files" + new Date().getTime());

//        filenameAcc = folder.toString() + "/" + "Acc.csv";
//        final String filenameGyro = folder.toString() + "/" + "Gyro.csv";
//        final String filenameMag = folder.toString() + "/" + "Mag.csv";

        /* Using service */
        mStartTrackingBtn = (Button) findViewById(R.id.start_tracking);
        mStartTrackingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTime = new Date().getTime();
                isTracking = true;
            }
        });

        mStopTrackingBtn = (Button) findViewById(R.id.stop_tracking);
        mStopTrackingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isTracking = false;
            }
        });


        if (!folder.exists())
            if (!folder.mkdirs()) {
                Log.d("BiteTracker","Directory created");
            }

//        try {
//            fwAcc = new FileWriter(filenameAcc);
//            fwGyro = new FileWriter(filenameGyro);
//            fwMag = new FileWriter(filenameMag);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        linearAccelerometer = new LinearAccelerometer();

        ConnectTask connectTask = new ConnectTask();
        connectTask.execute();

        mDataClient = Wearable.getDataClient(context);
    }

    @Override
    protected void onResume() {
        super.onResume();
        linearAccelerometer.registerListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        linearAccelerometer.unregisterListener();
    }

    private class SendSensorValueTask extends AsyncTask<Void, String, Boolean> {

        SendSensorValueTask() {

        }

        @Override
        protected Boolean doInBackground(Void... params) {

            return false;
        }
    }

    private class ConnectTask extends AsyncTask<Void, Void, Boolean> {

        ConnectTask() {
            Log.d(TAG, "Connect Task created");
        }


        @Override
        protected Boolean doInBackground(Void... params) {
            Log.d(TAG, "Do in background");
            setupSensorProcessing();
            return true;
        }


        @Override
        protected void onPostExecute(Boolean isSucess) {

            if ( isSucess ) {
                Log.d(TAG, "isSuccess, processingNodeId: " + processingNodeId);
            }
            else{

            }
        }

        private void setupSensorProcessing() {
            Log.d(TAG, "setupSensorProcessing");

            try {
                CapabilityInfo capabilityInfo = Tasks.await(
                        Wearable.getCapabilityClient(context).getCapability(
                                SENSOR_PROCESSING_CAPABILITY_NAME, CapabilityClient.FILTER_REACHABLE
                        )
                );
                // capabilityInfo has the reachable nodes with the transcription capability
                updateSendCapability(capabilityInfo);

                CapabilityClient.OnCapabilityChangedListener capabilityChangedListener =
                        new CapabilityClient.OnCapabilityChangedListener() {
                            @Override
                            public void onCapabilityChanged(@NonNull CapabilityInfo capabilityInfo) {
                                updateSendCapability(capabilityInfo);
                            }
                        };
                Wearable.getCapabilityClient(context).addListener(
                        capabilityChangedListener,
                        SENSOR_PROCESSING_CAPABILITY_NAME
                );
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
                e.printStackTrace();
            }
        }

        private void updateSendCapability(CapabilityInfo capabilityInfo) {
            Log.d(TAG, "updateSendCapability");

            Set<Node> connectedNodes = capabilityInfo.getNodes();
            processingNodeId = pickBestNodeId(connectedNodes);

        }

        private String pickBestNodeId(Set<Node> nodes) {
            String bestNodeId = null;
            // Find a nearby node or pick one arbitrarily
            Log.d(TAG, "entered pickBestNodeId");
            for (Node node : nodes) {
                Log.d(TAG, "any node");
                if (node.isNearby()) {
                    return node.getId();
                }
                bestNodeId = node.getId();
            }
            Log.d(TAG, "Best node id: " + bestNodeId);
            return bestNodeId;
        }
    }

    void writeToFile(int sensorType, float x, float y, float z) {
        try {
            packetNum = (new Date().getTime() - startTime) / 10;
            row = String.valueOf(packetNum) + "," + x + "," + y + "," + z + "\n";
            switch (sensorType) {
                case Sensor.TYPE_ACCELEROMETER:
                    fwAcc.append(row);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    fwGyro.append(row);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    fwMag.append(row);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void requestSensorProcessing(byte[] sensorData) {
        if (processingNodeId != null) {
            Task<Integer> sendTask = Wearable.getMessageClient(context).sendMessage(
                    processingNodeId, SENSOR_PROCESSING_MESSAGE_PATH, sensorData);
            sendTask.addOnSuccessListener(new OnSuccessListener<Integer>() {
                @Override
                public void onSuccess(Integer integer) {
                    Log.d(TAG, "sendTask success");
                }
            });
            sendTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d(TAG, "sendTask failure");
                }
            });
        } else {
            Log.d(TAG, "Unable to retrieve node with processing capability");
        }
    }

    private class LinearAccelerometer implements SensorEventListener {
        final SensorManager mSensorManager;
        final Sensor mLinearAccelerometer;
        float[] lastEventValues = new float[3];
        float[] deltaValues = new float[3];
        float[] velocity = new float[3];
        float[] displacement = new float[3];
        float[] deltaDisplacement = new float[3];
        float[] oldDisplacement = new float[3];
        float[] eventValuesWithoutNoise = new float[3];
        long last_timestamp = 0;
        long curr_timestamp = 0;
        long time_interval = 0;
        boolean initialized;

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        // byteBuffer reused for every element in floatArray
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);

        LinearAccelerometer() {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mLinearAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            initialized = false;
        }

        void registerListener() {
            mSensorManager.registerListener(this, mLinearAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        }

        void unregisterListener() {
            mSensorManager.unregisterListener(this);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!initialized) {
                last_timestamp = new Date().getTime();
                velocity[0] = 0;
                velocity[1] = 0;
                velocity[2] = 0;
                oldDisplacement[0] = 0;
                oldDisplacement[1] = 0;
                oldDisplacement[2] = 0;
                lastEventValues[0] = 0;
                lastEventValues[1] = 0;
                lastEventValues[2] = 0;
                initialized = true;
            } else {
                curr_timestamp = new Date().getTime();
                time_interval = curr_timestamp - last_timestamp;

                eventValuesWithoutNoise[0] = event.values[0];
                eventValuesWithoutNoise[1] = event.values[1];
                eventValuesWithoutNoise[2] = event.values[2];
                if (Math.abs(event.values[0] - lastEventValues[0]) < 0.15) {
                    eventValuesWithoutNoise[0] = lastEventValues[0];
                }
                if (Math.abs(event.values[1] - lastEventValues[1]) < 0.15) {
                    eventValuesWithoutNoise[1] = lastEventValues[1];
                }
                if (Math.abs(event.values[2] - lastEventValues[2]) < 0.15) {
                    eventValuesWithoutNoise[2] = lastEventValues[2];
                }

                displacement[0] = velocity[0] * time_interval + eventValuesWithoutNoise[0] * time_interval * time_interval / 2;
                displacement[1] = velocity[1] * time_interval + eventValuesWithoutNoise[1] * time_interval * time_interval / 2;
                displacement[2] = velocity[2] * time_interval + eventValuesWithoutNoise[2] * time_interval * time_interval / 2;
                deltaDisplacement[0] = displacement[0] - oldDisplacement[0];
                deltaDisplacement[1] = displacement[1] - oldDisplacement[1];
                deltaDisplacement[2] = displacement[2] - oldDisplacement[2];

                velocity[0] = velocity[0] + eventValuesWithoutNoise[0] * time_interval; //TODO: Check the unit, it is m/s^2!
                velocity[1] = velocity[1] + eventValuesWithoutNoise[1] * time_interval; //TODO: Check the unit, it is m/s^2!
                velocity[2] = velocity[2] + eventValuesWithoutNoise[2] * time_interval; //TODO: Check the unit, it is m/s^2!

                if (Math.abs(deltaDisplacement[0]) > 30)
                    deltaDisplacement[0] = 0;
                if (Math.abs(deltaDisplacement[1]) > 30)
                    deltaDisplacement[1] = 0;
                if (Math.abs(deltaDisplacement[2]) > 30)
                    deltaDisplacement[2] = 0;
                Log.d("BiteTracker", "deltaD: " + deltaDisplacement[0] + "," + deltaDisplacement[1] + "," + deltaDisplacement[2] + "," + curr_timestamp);

                last_timestamp = curr_timestamp;
                oldDisplacement[0] = displacement[0];
                oldDisplacement[1] = displacement[1];
                oldDisplacement[2] = displacement[2];

                lastEventValues[0] = event.values[0];
                lastEventValues[1] = event.values[1];
                lastEventValues[2] = event.values[2];


                // send deltaDisplacement values to mobile
                try {
//                    for (float element : deltaDisplacement) {
//                        byteBuffer.clear();
//                        byteBuffer.putFloat(element);
//                        byteStream.write(byteBuffer.array());
//                    }
//                    requestSensorProcessing("HELLO WORLD".getBytes());
                    requestSensorProcessing((deltaDisplacement[0] + "," + deltaDisplacement[1] + "," + deltaDisplacement[2] + "," + curr_timestamp).getBytes());
//                    byteStream.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }
}
