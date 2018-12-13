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
import android.os.Handler;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
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
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
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


public class MainActivity extends WearableActivity implements SensorEventListener,
        MessageClient.OnMessageReceivedListener {

    int numFoods;

    private static final String TAG = "BiteTracker";

    static int ACC_Z_THRESHOLD = -50;
    // LEFT: X ++, Y -, Z --
    static int LEFT_ACC_X_THRESHOLD = 70;      // x > t
    static int LEFT_ACC_Y_THRESHOLD = -10;     // y < t
    static int LEFT_ACC_Z_THRESHOLD = -40;     // z < t

    // CENTER: X ++, Y 0, Z -
    static int CENTER_ACC_X_THRESHOLD = 70;     // x > t
    static int CENTER_ACC_Y_THRESHOLD = 10;     // -t < y < t
    static int CENTER_ACC_Z_THRESHOLD = -10;    // z < t

    // RIGHT: X ++, Y +, Z -
    static int RIGHT_ACC_X_THRESHOLD = 70;       // x > t
    static int RIGHT_ACC_Y_THRESHOLD = 30;       // y > t
    static int RIGHT_ACC_Z_THRESHOLD = -40;      // z < t

    private static final int SENSOR_DELAY = 5000;      // in microseconds; 200 Hz
    int TIME_DELAY = 3000; // ms

    private TextView tvAcc;
    private TextView tvGyro;
    private TextView tvMag;

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
    private static final String START_DATA_COLLECTION_NAME = "start_data_collection";
    public static final String START_DATA_COLLECTION_PATH = "/start_data_collection";
    private static final String STOP_DATA_COLLECTION_NAME = "stop_data_collection";
    public static final String STOP_DATA_COLLECTION_PATH = "/stop_data_collection";

    String processingNodeId = null;

    /* Acc and Gyro sensor */
    SensorManager mSensorManager;
    float[] lastAccValues = new float[3];
    float[] lastGyroValues = new float[3];

    static int SENSOR_WINDOW_SIZE = 100;


    float sumAccX, sumAccY, sumAccZ;
    float sumGyroX, sumGyroY, sumGyroZ;

    long last_timestamp = 0;
    long curr_timestamp = 0;
    long time_interval = 0;
    boolean initialized;
    boolean isCollectingData = false;
    ConnectTask connectTask;

    boolean timeout = true;
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            timeout = true;
            resetSumAcc();
        }
    };

    int RESET_TIME = 500;   // ms

    Handler resetHandler = new Handler();
    Runnable resetRunnable = new Runnable() {
        @Override
        public void run() {
            resetSumAcc();
            resetHandler.postDelayed(this, RESET_TIME);
        }
    };

    Button btReset;

//    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
//    PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
//            "MyApp::MyWakelockTag");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        context = this;
        tvAcc = (TextView) findViewById(R.id.tvAcc);
        tvGyro = (TextView) findViewById(R.id.tvGyro);
        tvMag = (TextView) findViewById(R.id.tvMag);

        // Enables Always-on
        setAmbientEnabled();

        folder = new File(this.getExternalFilesDir(null)
                + "/Files" + new Date().getTime());

        filenameAcc = folder.toString() + "/" + "Acc.csv";
        filenameGyro = folder.toString() + "/" + "Gyro.csv";
        filenameMag = folder.toString() + "/" + "Mag.csv";


        if (!folder.exists())
            if (!folder.mkdirs()) {
                Log.d("BiteTracker","Directory created");
            }

        try {
            fwAcc = new FileWriter(filenameAcc);
            fwGyro = new FileWriter(filenameGyro);
            fwMag = new FileWriter(filenameMag);
        } catch (Exception e) {
            e.printStackTrace();
        }

//        linearAccelerometer = new LinearAccelerometer();

        mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);


        connectTask = new ConnectTask();
        connectTask.execute();

        mDataClient = Wearable.getDataClient(context);

        sumAccX = 0;
        sumAccY = 0;
        sumAccZ = 0;

        btReset = (Button) findViewById(R.id.btReset);
        btReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetSumAcc();
            }
        });

        resetHandler.postDelayed(resetRunnable, RESET_TIME);
        numFoods = 0;
    }

    void resetSumAcc() {
        sumAccX = 0;
        sumAccY = 0;
        sumAccZ = 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
//        wakeLock.acquire();
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SENSOR_DELAY);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SENSOR_DELAY);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SENSOR_DELAY);
        Wearable.getMessageClient(context).addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        Wearable.getMessageClient(context).removeListener(this);
//        wakeLock.release();
    }

    @Override
    protected void onDestroy() {
        try {
            fwAcc.close();
            fwGyro.close();
            fwMag.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
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
                Log.d(TAG, "no success");
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

    private void requestSendResult(byte[] resultData) {
        if (processingNodeId != null) {
            Task<Integer> sendTask = Wearable.getMessageClient(context).sendMessage(
                    processingNodeId, SENSOR_PROCESSING_MESSAGE_PATH, resultData);
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

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        Log.d(TAG, "Message received");
        if (messageEvent.getPath().equals(START_DATA_COLLECTION_PATH)) {
            if (messageEvent.getData() != null) {
                Log.d(TAG, "Start data collection: " + new String(messageEvent.getData()));
                numFoods = Integer.parseInt(new String(messageEvent.getData()));
                startDataCollection();
            }
        } else if (messageEvent.getPath().equals(STOP_DATA_COLLECTION_PATH)) {
            if (messageEvent.getData() != null) {
                Log.d(TAG, "Stop data collection");
                stopDataCollection();
            }
        }
    }

    private void startDataCollection() {
        isCollectingData = true;
    }

    private void stopDataCollection() {
        isCollectingData = false;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        curr_timestamp = new Date().getTime();
//            time_interval = curr_timestamp - last_timestamp;
//
//            writeToFile(event.sensor.getType(), event.values[0], event.values[1], event.values[2]);
        if (isCollectingData) {
            if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                sumAccX += event.values[0];
                sumAccY += event.values[1];
                sumAccZ += event.values[2];
                if (timeout) {
                    // LEFT
//                    if ((sumAccX > LEFT_ACC_X_THRESHOLD)
//                            && (sumAccY < LEFT_ACC_Y_THRESHOLD)
//                            && (sumAccZ < LEFT_ACC_Z_THRESHOLD)) {
                    if (numFoods == 1) {
                        if ((Math.abs(sumAccX) + Math.abs(sumAccY) + Math.abs(sumAccZ)) > 50) {
                            Log.d("CENTER", "\nLIFT\n\n\n SUMACC: \nx: " + sumAccX
                                    + "\ny: " + sumAccY
                                    + "\nz: " + sumAccZ);
                            tvAcc.setText("\nCENTER\n\n\n SUMACC: \nx: " + sumAccX
                                    + "\ny: " + sumAccY
                                    + "\nz: " + sumAccZ);
                            requestSendResult(("CENTER," + curr_timestamp).getBytes());
                            resetSumAcc();
                            timeout = false;
                            timerHandler.postDelayed(timerRunnable, TIME_DELAY);
                        }
                    } else if (numFoods == 2) {
                        if ((sumAccZ <= -10)) {
                            Log.d("LEFT-RIGHT", "\nLEFT\n\n\nACC:\nx: "
                                    + event.values[0] + "\ny: " + event.values[1]
                                    + "\nz: " + event.values[2]
                                    + "\n\n\n SUMACC: \nx: " + sumAccX
                                    + "\ny: " + sumAccY
                                    + "\nz: " + sumAccZ);
                            tvAcc.setText("LEFT\n\n\nACC:\nx: " + event.values[0] + "\ny: " + event.values[1] + "\nz: " + event.values[2]);
                            requestSendResult(("LEFT," + curr_timestamp).getBytes());
                            resetSumAcc();
                            timeout = false;
                            timerHandler.postDelayed(timerRunnable, TIME_DELAY);
                        } else if ((sumAccZ >= 10)) {
                            Log.d("LEFT-RIGHT", "\nRIGHT\n\n\nACC:\nx: "
                                    + event.values[0] + "\ny: " + event.values[1]
                                    + "\nz: " + event.values[2]
                                    + "\n\n\n SUMACC: \nx: " + sumAccX
                                    + "\ny: " + sumAccY
                                    + "\nz: " + sumAccZ);
                            tvAcc.setText("RIGHT\n\n\nACC:\nx: " + event.values[0] + "\ny: " + event.values[1] + "\nz: " + event.values[2]);
                            requestSendResult(("RIGHT," + curr_timestamp).getBytes());
                            resetSumAcc();
                            timeout = false;
                            timerHandler.postDelayed(timerRunnable, TIME_DELAY);
                        }
                    } else if (numFoods == 3) {
                        if ((sumAccY < 20) && (sumAccZ <= -14)) {
                            Log.d("LEFT-C-RIGHT", "\nLEFT\n\n\nACC:\nx: "
                                    + event.values[0] + "\ny: " + event.values[1]
                                    + "\nz: " + event.values[2]
                                    + "\n\n\n SUMACC: \nx: " + sumAccX
                                    + "\ny: " + sumAccY
                                    + "\nz: " + sumAccZ);
                            tvAcc.setText("LEFT\n\n\nACC:\nx: " + event.values[0] + "\ny: " + event.values[1] + "\nz: " + event.values[2]);
                            requestSendResult(("LEFT," + curr_timestamp).getBytes());
                            resetSumAcc();
                            timeout = false;
                            timerHandler.postDelayed(timerRunnable, TIME_DELAY);
                        } // RIGHT
//                    else if ((sumAccX > RIGHT_ACC_X_THRESHOLD)
//                            && (sumAccY > RIGHT_ACC_Y_THRESHOLD)
//                            && (sumAccZ < RIGHT_ACC_Z_THRESHOLD)) {
                        else if (sumAccZ >= 14) {
                            Log.d("LEFT-C-RIGHT", "\nRIGHT\n\n\nACC:\nx: "
                                    + event.values[0] + "\ny: " + event.values[1]
                                    + "\nz: " + event.values[2]
                                    + "\n\n\n SUMACC: \nx: " + sumAccX
                                    + "\ny: " + sumAccY
                                    + "\nz: " + sumAccZ);
                            tvAcc.setText("RIGHT\n\n\nACC:\nx: " + event.values[0] + "\ny: " + event.values[1] + "\nz: " + event.values[2]);
                            requestSendResult(("RIGHT," + curr_timestamp).getBytes());
                            resetSumAcc();
                            timeout = false;
                            timerHandler.postDelayed(timerRunnable, TIME_DELAY);
                        } // CENTER
//                    else if ((sumAccX > CENTER_ACC_X_THRESHOLD)
//                            && ((sumAccY < CENTER_ACC_Y_THRESHOLD) && (sumAccY > -CENTER_ACC_Y_THRESHOLD))
//                            && ((sumAccZ < CENTER_ACC_Z_THRESHOLD))) {
                        else if ((sumAccX < -14)
                                && ((sumAccZ < 14) && (sumAccZ > -14))) {
                            Log.d("LEFT-C-RIGHT", "\nCENTER\n\n\nACC:\nx: "
                                    + event.values[0] + "\ny: " + event.values[1]
                                    + "\nz: " + event.values[2]
                                    + "\n\n\n SUMACC: \nx: " + sumAccX
                                    + "\ny: " + sumAccY
                                    + "\nz: " + sumAccZ);
                            tvAcc.setText("CENTER\n\n\nACC:\nx: " + event.values[0] + "\ny: " + event.values[1] + "\nz: " + event.values[2]);
                            requestSendResult(("CENTER," + curr_timestamp).getBytes());
                            resetSumAcc();
                            timeout = false;
                            timerHandler.postDelayed(timerRunnable, TIME_DELAY);
                        }
                    }
                }

            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
//                tvGyro.setText("GYRO:\nx: " + event.values[0] + "\ny: " + event.values[1] + "\nz: " + event.values[2]);
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
//                tvMag.setText("MAG:\nx: " + event.values[0] + "\ny: " + event.values[1] + "\nz: " + event.values[2]);
            }
        }
//            try {
//                if (isCollectingData) {
//                    requestSensorProcessing((event.sensor.getStringType() + ","
//                            + event.values[0] + "," + event.values[1] + ","
//                            + event.values[2] + "," + curr_timestamp).getBytes());
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
    }

    /*
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
//            mSensorManager.registerListener(this, mLinearAccelerometer, samplingRates);
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
    */
}
