package com.familysonar;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static androidx.core.content.ContextCompat.startForegroundService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

public class BootCompleteReceiverClass extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        boolean hasLocationPermission = context.checkSelfPermission(ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasLocationPermission
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && context.checkSelfPermission(ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            Log.w("FindMe", "Location service not started after boot: background location permission is missing");
            return;
        }
        Intent locationServiceIntent = new Intent(context, LocationService.class);
        startForegroundService(context,locationServiceIntent);
        Log.d("FalimySonarApp", "BootCompleted");
    }
}
