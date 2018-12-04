package com.chocho.bitetracker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Set;

public class DataLayerListenerService extends WearableListenerService {
    private static final String TAG = "DataLayerSample";
    private static final String START_ACTIVITY_PATH = "/start-activity";
    private static final String DATA_ITEM_RECEIVED_PATH = "/data-item-received";

    /* For message client */
    private static final String SEND_LINEAR_ACC_CAPABILITY_NAME = "send_linear_acc";
    public static final String SEND_LINEAR_ACC_MESSAGE_PATH = "/send_linear_acc";

    Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ListernerService created");
        context = this;
    }

//    private void requestSendSensorData(byte[] sensorData) {
//        NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes( mApiClient ).await();
//        for(Node node : nodes.getNodes()) {
//            MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
//                    mApiClient, node.getId(), SEND_LINEAR_ACC_MESSAGE_PATH, sensorData ).await();
//        }
//        if (sendNodeId != null) {
//            Task<Integer> sendTask =
//                    Wearable.getMessageClient(context).sendMessage(
//                            sendNodeId, SEND_LINEAR_ACC_MESSAGE_PATH, sensorData);
//            sendTask.addOnSuccessListener(
//                    new OnSuccessListener<Integer>() {
//                        @Override
//                        public void onSuccess(Integer integer) {
//                            Log.d("BiteTracker", "Send Success");
//                        }
//                    }
//            );
//            sendTask.addOnFailureListener(
//                    new OnFailureListener() {
//                        @Override
//                        public void onFailure(@NonNull Exception e) {
//                            Log.d("BiteTracker", "Send Failure");
//                        }
//                    }
//            );
//        } else {
////            Log.d("BiteTracker", "Unable to retrieve node with send capability");
//        }
//    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onDataChanged: " + dataEvents);
        }

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent event : dataEvents) {
            Uri uri = event.getDataItem().getUri();

            // Get the node id from the host value of the URI
            String nodeId = uri.getHost();
            // Set the data of the message to be the bytes of the URI
            byte[] payload = uri.toString().getBytes();

            // Send the RPC
            Wearable.getMessageClient(this).sendMessage(
                    nodeId,  DATA_ITEM_RECEIVED_PATH, payload);
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        Log.d(TAG, "onMessageReceived");
        if (messageEvent.getPath().equals(SEND_LINEAR_ACC_MESSAGE_PATH)) {
            Intent startIntent = new Intent(this, MainActivity.class);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startIntent.putExtra("VOICE_DATA", messageEvent.getData());
            startActivity(startIntent);
        }
    }
}
