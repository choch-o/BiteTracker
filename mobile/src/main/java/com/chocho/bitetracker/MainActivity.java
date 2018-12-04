package com.chocho.bitetracker;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements
        DataClient.OnDataChangedListener,
        MessageClient.OnMessageReceivedListener {
    public static final String SENSOR_PROCESSING_MESSAGE_PATH = "/sensor_processing";
    private static final String TAG = "BiteTracker";

    ImageView drawingImageView;

    float centerX;
    float centerY;
    float positionX;
    float positionY;

    float deltaX;
    float deltaY;

    Canvas canvas;
    Paint paint;
    Path p;

    Button drawButton;
    private int mInterval = 5000; // 5 seconds by default, can be changed later
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* For drawing path */
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        Wearable.getDataClient(this).addListener(this);
        Wearable.getMessageClient(this).addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Wearable.getDataClient(this).removeListener(this);
        Wearable.getMessageClient(this).removeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRepeatingTask();
    }

    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            Log.d(TAG, "some data event occurred");
        }

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "Message received");
        if (messageEvent.getPath().equals(SENSOR_PROCESSING_MESSAGE_PATH)) {
            if (messageEvent.getData() != null) {
//                byte[] input = messageEvent.getData();
                String message = new String(messageEvent.getData());
                String[] deltaDisplacement = message.split(",");
                deltaX = Float.parseFloat(deltaDisplacement[0]);
                deltaY = Float.parseFloat(deltaDisplacement[1]);
                positionX += deltaX;
                positionY += deltaY;

                Log.d(TAG, "Delta X: " + deltaX + ", Delta Y: " + deltaY);
                Log.d(TAG, "Position X: " + positionX + "Position Y: " + positionY);
                p.lineTo(positionX, positionY);
//                canvas.drawPath(p, paint);
//                float[] ret = new float[input.length / 4];
//                for (int x = 0; x < input.length; x += 4) {
//                    ret[x / 4] = ByteBuffer.wrap(input, x, 4).getFloat();
//                }
//                Log.d(TAG, "x: " + ret[0] + ", y: " + ret[1] + ", z: " + ret[2]);
            }
        }
    }

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
}
