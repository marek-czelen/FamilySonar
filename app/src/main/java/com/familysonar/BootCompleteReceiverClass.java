package com.familysonar;

import static androidx.core.content.ContextCompat.startForegroundService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootCompleteReceiverClass extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent locationServiceIntent = new Intent(context, LocationService.class);
        startForegroundService(context,locationServiceIntent);
        Log.d("FalimySonarApp", "BootCompleted");
    }
}
