package com.chocho.bitetracker;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.hardware.Sensor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.chocho.bitetracker.sensorProc.DataInstance;
import com.chocho.bitetracker.sensorProc.DataInstanceList;
import com.chocho.bitetracker.sensorProc.SlidingWindow;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;


public class MainActivity extends AppCompatActivity implements MessageClient.OnMessageReceivedListener {
    private static final String TAG = "BiteTracker";
    public static final String SENSOR_PROCESSING_MESSAGE_PATH = "/sensor_processing";
    private static final String START_DATA_COLLECTION_NAME = "start_data_collection";
    public static final String START_DATA_COLLECTION_PATH = "/start_data_collection";
    private static final String STOP_DATA_COLLECTION_NAME = "stop_data_collection";
    public static final String STOP_DATA_COLLECTION_PATH = "/stop_data_collection";

    static final int REQUEST_GALLERY_PICTURE = 0;
    static final int REQUEST_IMAGE_CAPTURE = 1;


    ImageView drawingImageView;

    float centerX;
    float centerY;
    float positionX;
    float positionY;

    float deltaX;
    float deltaY;

    private SensorDataHandler sensorDataHandler;

    boolean running;
    String processingNodeId;
    ConnectTask connectTask;

//    Old code for drawing path
/*    Canvas canvas;
    Paint paint;
    Path p;

    Button drawButton;
    private int mInterval = 5000; // 5 seconds by default, can be changed later
    private Handler mHandler;
*/

    BiteList biteList;
    FoodList foodList;
    // UI elements
    private Button btnCamera, btnStartCollectingData, btnFinishCollectingData;
    private ImageView ivMeal;
    private TextView tvDataSource, tvLog;
    private ScrollView scrollViewForLog;

    private String classLabel;      // only necessary for data collection
    private DataInstanceList dlAcc, dlGyro = new DataInstanceList(); // For raw data save purpose
    private SlidingWindow slidingWindowAcc, slidingWindowGyro; // For extracting samples by window

    private Context context;
    String encodedImage;
    JSONObject jsonObject;

    String mCurrentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        btnCamera = (Button) findViewById(R.id.btnCamera);
        btnStartCollectingData = (Button) findViewById(R.id.btnStartCollectingData);
        btnFinishCollectingData = (Button) findViewById(R.id.btnFinishCollectingData);
        btnFinishCollectingData.setEnabled(false);

        ivMeal = (ImageView) findViewById(R.id.ivMeal);

        tvLog = (TextView) findViewById(R.id.tvLog);
        scrollViewForLog = (ScrollView) findViewById(R.id.scrollViewForLog);

        // Attaching listeners
        btnCamera.setOnClickListener(btnCameraOnClick);
        btnStartCollectingData.setOnClickListener(btnStartCollectingDataOnClick);
        btnFinishCollectingData.setOnClickListener(btnFinishCollectingDataOnClick);

        running = false;

        connectTask = new ConnectTask();
        connectTask.execute();

        biteList = new BiteList(new Date().getTime());
        foodList = new FoodList(new Date().getTime());

        // Old code for drawing path
        /*
        // For drawing path
        drawingImageView = (ImageView) this.findViewById(R.id.DrawingImageView);
        Bitmap bitmap = Bitmap.createBitmap((int) getWindowManager()
                .getDefaultDisplay().getWidth(), (int) getWindowManager()
                .getDefaultDisplay().getHeight(), Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        drawingImageView.setImageBitmap(bitmap);

        centerX = 500;
        centerY = 500;
//        centerX = getWindowManager().getDefaultDisplay().getWidth();
//        centerY = getWindowManager().getDefaultDisplay().getHeight();


        // Path

        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.GREEN);
        p = new Path();
        p.moveTo(centerX, centerY);
//        p.lineTo(100, 200);
//        p.lineTo(200, 100);
//        p.lineTo(240, 155);
//        p.lineTo(250, 175);
//        p.lineTo(20, 20);
        canvas.drawPath(p, paint);

        positionX = centerX;
        positionY = centerY;

        drawButton = (Button) findViewById(R.id.drawButton);
        drawButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                canvas.restore();
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                positionX = centerX;
                positionY = centerY;
                p.moveTo(positionX, positionY);
                canvas.drawPath(p, paint);
            }
        });
        mHandler = new Handler();
        startRepeatingTask();

        */

    }

    @Override
    protected void onResume() {
        super.onResume();
        Wearable.getMessageClient(context).addListener(this);

    }

    @Override
    protected void onPause() {
        super.onPause();
        Wearable.getMessageClient(context).removeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        stopRepeatingTask();
    }

    //UI Event Handler
    private View.OnClickListener btnCameraOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "camera click");
            AlertDialog.Builder photoAlertDialog = new AlertDialog.Builder(context);
            photoAlertDialog.setTitle("Meal Photo Option");
            photoAlertDialog.setMessage("How do you want to set your picture?");
            photoAlertDialog.setPositiveButton("Gallery",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent pictureActionIntent = null;
                            pictureActionIntent = new Intent(
                                    Intent.ACTION_PICK,
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                            startActivityForResult(pictureActionIntent, REQUEST_GALLERY_PICTURE);
                        }
                    });
            photoAlertDialog.setNegativeButton("Camera",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dispatchTakePictureIntent();
                        }
                    });
            photoAlertDialog.show();
        }
    };
    private View.OnClickListener btnStartCollectingDataOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "startBuildingModel()");
            tvLog.setText("");
            startDataCollection();
        }
    };

    private View.OnClickListener btnFinishCollectingDataOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "finishBuildingModel()");
            finishDataCollection();
            getResults();
            foodList.clear();
            biteList.clear();
        }
    };

    /*
     * Highlight the button specified in argument, if argument is null, the buttons turn to initial condition
     */
    public void highlightButton(Button btn) {

        if(btn == null){ // Initial states
            btnStartCollectingData.setEnabled(true);
            btnFinishCollectingData.setEnabled(false);
        } else { // Highlight
            btnStartCollectingData.setEnabled(false);
            btnFinishCollectingData.setEnabled(false);
            btn.setEnabled(true);
        }
    }

    public void startDataCollection(){
        highlightButton(btnFinishCollectingData);
        Log.d(TAG, "start called, running: " + Boolean.toString(running));
        if (running) return;
        if (processingNodeId != null) {
            Task<Integer> sendTask = Wearable.getMessageClient(context).sendMessage(
                    processingNodeId, START_DATA_COLLECTION_PATH, Integer.toString(foodList.size()).getBytes());
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

    public void finishDataCollection(){
        highlightButton(null);

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

        // Cleaning
        btnStartCollectingData.setEnabled(true);
        btnFinishCollectingData.setEnabled(false);
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                ex.printStackTrace();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                try {
                    File file = new File(mCurrentPhotoPath);
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), Uri.fromFile(file));
                    if (bitmap != null) {
                        ivMeal.setImageBitmap(bitmap);
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                        byte[] byteArrayImage = byteArrayOutputStream.toByteArray();
                        encodedImage = Base64.encodeToString(byteArrayImage, Base64.DEFAULT);

                        new UploadImages().execute();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (requestCode == REQUEST_GALLERY_PICTURE) {
                Uri uri = data.getData();
                ivMeal.setImageURI(uri);
            }
        }
    }

    private class UploadImages extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Void doInBackground(Void... params) {

            try {
                Log.d(TAG, "encodedImage = " + encodedImage);
                jsonObject = new JSONObject();

                jsonObject.put("pic", encodedImage);
                String data = jsonObject.toString();

                String yourURL = "http://143.248.48.96:5050/watchData";
                URL url = new URL(yourURL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setRequestMethod("POST");
                connection.setFixedLengthStreamingMode(data.getBytes().length);
                connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                OutputStream out = new BufferedOutputStream(connection.getOutputStream());
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
                writer.write(data);
                Log.d(TAG, "Data to server = " + data);
                writer.flush();
                writer.close();
                out.close();
                connection.connect();

                InputStream inputStream;

                int status = connection.getResponseCode();

                if (status != HttpURLConnection.HTTP_OK)
                    inputStream = connection.getErrorStream();
                else
                    inputStream = connection.getInputStream();

                InputStream in = new BufferedInputStream(inputStream);
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        in, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line = null;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                in.close();
                String result = sb.toString();
                JSONObject resultObject = new JSONObject(result);
                JSONArray foodsArray = resultObject.getJSONArray("foods");
                for (int i = 0; i < foodsArray.length(); i++) {
                    JSONObject foodObject =  foodsArray.getJSONObject(i);
                    Log.d("FOODO", foodObject.toString());
                    String[] foodInfo = (foodObject.getString(Integer.toString(i))).split(",");
                    Food food = new Food(Integer.toString(i), foodInfo[0],
                            Float.parseFloat(foodInfo[1]), Float.parseFloat(foodInfo[2]),
                            Float.parseFloat(foodInfo[3]), Float.parseFloat(foodInfo[4]));
                    foodList.add(food);
                }

                Log.d(TAG, "Response from server = " + resultObject.toString());
                connection.disconnect();
            } catch (Exception e) {
                Log.d(TAG, "Error Encountered");
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void args) {

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

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(SENSOR_PROCESSING_MESSAGE_PATH)) {
            Log.d(TAG, "Sensor processing message received");
            if (messageEvent.getData() != null) {
                String message = new String(messageEvent.getData());
                String[] messageItems = message.split(",");
                biteList.add(new Bite(messageItems[0], messageItems[1]));
                if (!running) return;
                Log.d(TAG, "Received message: " + message);
                tvLog.append(messageItems[0] + "\n");
            }
        }
    }

    void getResults() {
        tvLog.setText("");
        if (biteList.getFoodCount("LEFT") != 0) {
            tvLog.append("LEFT: " + Integer.toString(biteList.getFoodCount("LEFT")) + "\n");
        }
        if (biteList.getFoodCount("RIGHT") != 0) {
            tvLog.append("RIGHT: " + Integer.toString(biteList.getFoodCount("RIGHT")) + "\n");
        }
        if (biteList.getFoodCount("CENTER") != 0) {
            tvLog.append("CENTER: " + Integer.toString(biteList.getFoodCount("CENTER")) + "\n");
        }
    }



    /*
    public void startModelTest(String[] arffFileNames, String outputFileName) {
        try {
            highlightButton(btnFinishTestingModel);

            sensorDataHandler.setClassLabel(null);
            sensorDataClassifier.setClassifier(arffFileNames);

            sensorDataHandler.start();

            tvLog.setText("");
        } catch (FileNotFoundException e) {
            sensorDataHandler.stop();
            highlightButton(null);
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void saveTestResultToFile(String outputFileName, String content) {
        // Writing summary if output is set
        if(outputFileName != null){
            // Set output file for writing result
            String filePath = Environment.getExternalStorageDirectory() + "/" + Constants.WORKING_DIR_NAME + "/" + Constants.PREFIX_RESULT + System.currentTimeMillis() + "_" + outputFileName + ".txt";
            FileWriter fw = null;
            try {
                fw = new FileWriter(filePath);
                Log.i(TAG, "Output file writer is open!");

                fw.write(content);
                fw.flush();
                fw.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                Log.e(TAG, "setOutputFileName() error : " + e.getMessage());
            }
        }
    }

    public void finishModelTest(String outputFileName){
        highlightButton(null);

        sensorDataHandler.stop();

        // Save raw data
        sensorDataHandler.saveRawDataToCSV();

        // Save calculated feature sets
        String label = sensorDataHandler.getClassLabel();
        sensorDataClassifier.saveInstancesToArff(sensorDataClassifier.getInstances(), Constants.PREFIX_FEATURES + System.currentTimeMillis() + "_" + label + ".txt");


        //String resultCrossValidation = mainRunnable.getResultCrossValidation();
        String resultTest = sensorDataClassifier.getResultTest();
        saveTestResultToFile(outputFileName, "\n" + resultTest);

        // Cleaning
        btnStartCollectingData.setEnabled(true);
        btnFinishCollectingData.setEnabled(false);
    }*/

    // old code for drawing path
    /*
    Runnable mDrawPaint = new Runnable() {
        @Override
        public void run() {
            try {
                canvas.drawPath(p, paint);
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                mHandler.postDelayed(mDrawPaint, mInterval);
            }
        }
    };

    void startRepeatingTask() {
        mDrawPaint.run();
    }

    void stopRepeatingTask() {
        mHandler.removeCallbacks(mDrawPaint);
    }
    */
}
