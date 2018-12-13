package com.chocho.bitetracker;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import com.chocho.bitetracker.sensorProc.DataInstance;
import com.chocho.bitetracker.sensorProc.DataInstanceList;
import com.chocho.bitetracker.sensorProc.SlidingWindow;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.Set;

public class SensorDataHandler implements MessageClient.OnMessageReceivedListener{
    public static final String SENSOR_PROCESSING_MESSAGE_PATH = "/sensor_processing";
    private static final String START_DATA_COLLECTION_NAME = "start_data_collection";
    public static final String START_DATA_COLLECTION_PATH = "/start_data_collection";
    private static final String STOP_DATA_COLLECTION_NAME = "stop_data_collection";
    public static final String STOP_DATA_COLLECTION_PATH = "/stop_data_collection";
    final static String TAG = "SensorDataHandler";

    private Context context;
    private SensorManager sensorManager;

    private boolean running;
    private HandlerThread sensorThread;
    private Handler sensorHandler;

    float[] lastAccValues = new float[3];
    float[] lastGyroValues = new float[3];
    float[] currValues = new float[3];

    private String classLabel; // Optional value, only necessary for data collection
    private DataInstanceList dlAcc, dlGyro = new DataInstanceList(); // For raw data save purpose
    private SlidingWindow slidingWindowAcc, slidingWindowGyro; // For extracting samples by window

    String processingNodeId;
    ConnectTask connectTask;

    public SensorDataHandler(Context context) {
        this.context = context;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);


        this.dlAcc = new DataInstanceList();
        this.dlGyro = new DataInstanceList();

        // Sliding window
        slidingWindowAcc = new SlidingWindow(Constants.WINDOW_SIZE, Constants.STEP_SIZE);
        slidingWindowGyro = new SlidingWindow(Constants.WINDOW_SIZE, Constants.STEP_SIZE);

        //Background Thread
        sensorThread = new HandlerThread("Sensor thread", Thread.MAX_PRIORITY);
        sensorThread.start();
        sensorHandler = new Handler(sensorThread.getLooper());

        Log.d(TAG, "SensorDataHandler created");
        connectTask = new ConnectTask();
        connectTask.execute();
    }

    public String getClassLabel() {
        return this.classLabel;
    }
    public void setClassLabel(final String classLabel) {
        sensorHandler.post(new Runnable() {
            @Override
            public void run() {
                SensorDataHandler.this.classLabel = classLabel;
            }
        });
    }

    public void clearData() {
        sensorHandler.post(new Runnable() {
            @Override
            public void run() {
                dlAcc = new DataInstanceList();
                dlGyro = new DataInstanceList();
            }
        });
    }

    public void start() {
        Log.d(TAG, "start called, running: " + Boolean.toString(running));
        if (running) return;
        if (processingNodeId != null) {
            Task<Integer> sendTask = Wearable.getMessageClient(context).sendMessage(
                    processingNodeId, START_DATA_COLLECTION_PATH, "hi".getBytes());
            sendTask.addOnSuccessListener(new OnSuccessListener<Integer>() {
                @Override
                public void onSuccess(Integer integer) {
                    Log.d(TAG, "start success");
                    running = true;
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

    public void stop() {
        if (!running) return;

        if (processingNodeId != null) {
            Task<Integer> sendTask = Wearable.getMessageClient(context).sendMessage(
                    processingNodeId, STOP_DATA_COLLECTION_PATH, "hi".getBytes());
            sendTask.addOnSuccessListener(new OnSuccessListener<Integer>() {
                @Override
                public void onSuccess(Integer integer) {
                    Log.d(TAG, "stop success");
                    running = false;
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

    private class ConnectTask extends AsyncTask<Void, Void, Boolean> {
        boolean connected = false;

        ConnectTask() {
            Log.d(TAG, "Connect Task created");
        }


        @Override
        protected Boolean doInBackground(Void... params) {
            while (!connected) {
                Log.d(TAG, "Do in background");
                connectTask.setupSensorProcessing();
            }
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

        public void setupSensorProcessing() {
            Log.d(TAG, "setupSensorProcessing");

            try {
                CapabilityInfo capabilityInfo = Tasks.await(
                        Wearable.getCapabilityClient(context).getCapability(
                                START_DATA_COLLECTION_NAME, CapabilityClient.FILTER_REACHABLE
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
                        START_DATA_COLLECTION_NAME
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
            connected = true;
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

    public void saveRawDataToCSV() {
        sensorHandler.post(new Runnable() {
            @Override
            public void run() {
                String fileNameAcc = Constants.PREFIX_RAW_DATA + System.currentTimeMillis() + "_" + classLabel + "_acc.txt";
                dlAcc.saveToCsvFile(fileNameAcc);

                String fileNameGyro = Constants.PREFIX_RAW_DATA + System.currentTimeMillis() + "_" + classLabel + "_gyro.txt";
                dlGyro.saveToCsvFile(fileNameGyro);
            }
        });
    }

    public interface DataAdaptor {
        public void slidingWindowData(String classLabel, DataInstanceList dlAcc, DataInstanceList dlGyro);
    }

    public DataAdaptor dataAdaptor;

    private void processWindowBuffer() {
        if (slidingWindowAcc.getHeadTimeId() != slidingWindowGyro.getHeadTimeId()) {
            if (slidingWindowAcc.getHeadTimeId() < slidingWindowGyro.getHeadTimeId()) {
                slidingWindowAcc.removeFirst();
            } else {
                slidingWindowGyro.removeFirst();
            }
        }
        if (!slidingWindowAcc.isBufferReady() || !slidingWindowGyro.isBufferReady()) return;

        // Fetching a slices of sliding window
        DataInstanceList dlAcc = slidingWindowAcc.output();
        DataInstanceList dlGyro = slidingWindowGyro.output();

        if (dlAcc == null || dlGyro == null) return;

        Log.i(TAG, dlAcc.getTimeId() + ", " + dlGyro.getTimeId());

        if (dlAcc.getTimeId() != dlGyro.getTimeId()) {
            Log.e(TAG, "Sample are not synced!"); // Issue : What if not synced (Very rare case) => Ignored
            return;
        }

        if (dataAdaptor == null) return;
        dataAdaptor.slidingWindowData(this.classLabel, dlAcc, dlGyro);
    }


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(SENSOR_PROCESSING_MESSAGE_PATH)) {
            Log.d(TAG, "Sensor processing message received");
            if (messageEvent.getData() != null) {
                String message = new String(messageEvent.getData());
                String[] messageItems = message.split(",");

                if (!running) return;
                if (messageItems[0].equals(Sensor.STRING_TYPE_LINEAR_ACCELERATION)) {
                    currValues[0] = Float.parseFloat(messageItems[1]);
                    currValues[1] = Float.parseFloat(messageItems[2]);
                    currValues[2] = Float.parseFloat(messageItems[3]);

                    DataInstance diAcc = new DataInstance(System.currentTimeMillis(), currValues);
                    diAcc.setLabel(classLabel);
                    dlAcc.add(diAcc);   // Save for raw data backup
                    slidingWindowAcc.input(diAcc);

                    lastAccValues = currValues;
                } else if (messageItems[0].equals(Sensor.STRING_TYPE_GYROSCOPE)) {
                    currValues[0] = Float.parseFloat(messageItems[1]);
                    currValues[1] = Float.parseFloat(messageItems[2]);
                    currValues[2] = Float.parseFloat(messageItems[3]);

                    DataInstance diGyro = new DataInstance(System.currentTimeMillis(), currValues);
                    diGyro.setLabel(classLabel);
                    dlGyro.add(diGyro);
                    slidingWindowGyro.input(diGyro);

                    lastGyroValues = currValues;
                }
                processWindowBuffer();
//                deltaX = Float.parseFloat(deltaDisplacement[0]);
//                deltaY = Float.parseFloat(deltaDisplacement[1]);
//                positionX += deltaX;
//                positionY += deltaY;
//
//                Log.d(TAG, "Delta X: " + deltaX + ", Delta Y: " + deltaY);
//                Log.d(TAG, "Position X: " + positionX + "Position Y: " + positionY);
//                p.lineTo(positionX, positionY);
//                canvas.drawPath(p, paint);
//                float[] ret = new float[input.length / 4];
//                for (int x = 0; x < input.length; x += 4) {
//                    ret[x / 4] = ByteBuffer.wrap(input, x, 4).getFloat();
//                }
//                Log.d(TAG, "x: " + ret[0] + ", y: " + ret[1] + ", z: " + ret[2]);
            }
        }
    }
}
