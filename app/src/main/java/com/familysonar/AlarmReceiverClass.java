package com.familysonar;

import static android.content.Context.LOCATION_SERVICE;
import static android.content.Context.TELEPHONY_SERVICE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.telephony.CellInfo;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.List;

public class AlarmReceiverClass extends BroadcastReceiver {
    PowerManager.WakeLock TempWakeLock = null;
    LocationManager locationManager = null;
    LocationListener locationListener = null;

    private void releaseWakeLock() {

    }


    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager TempPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        TempWakeLock = TempPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "famillySonal:TempWakeLock");
        TempWakeLock.acquire();
        Bundle extras = intent.getExtras();
        String from = extras.getString("from");
        SmsManager.getDefault().sendTextMessage(from, null, "START", null, null);

        FusedLocationProviderClient mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        Log.d("FalimySonarApp", "onAlamSuccess");
                        if (location != null) {
                            String message = String.format("[%f;%f]", location.getLatitude(), location.getLongitude());
                            message = message.replace(",", ".");
                            message = message.replace(";", ",");
                            SmsManager.getDefault().sendTextMessage(from, null, message, null, null);
                            Log.d("FalimySonarApp", message);
                            TempWakeLock.release();
                        } else {
                            Log.d("FalimySonarApp", "location null");
                            locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
                            boolean isGPSProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                            boolean isNetworkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                            locationListener = new LocationListener() {
                                @Override
                                public void onLocationChanged(@NonNull Location location) {
                                    ProcessLocationUpdates(location, from);
                                }
                            };
                            if (isGPSProvider) {
                                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                                        && ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                                }

                                locationManager.requestLocationUpdates(
                                        LocationManager.GPS_PROVIDER,
                                        500,
                                        0f,
                                        locationListener

                                );
                            } else if (isNetworkProvider) {
                                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                                        500,
                                        0f,
                                        locationListener
                                );
                            }

                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (TempWakeLock.isHeld()) TempWakeLock.release();
                    }
                });
    }

    private void ProcessLocationUpdates(Location location, String from) {
        if (location != null) {
            String message = String.format("[%f;%f]", location.getLatitude(), location.getLongitude());
            message = message.replace(",", ".");
            message = message.replace(";", ",");
            SmsManager.getDefault().sendTextMessage(from, null, message, null, null);
            Log.d("FalimySonarApp", message);
            if (locationListener != null) locationManager.removeUpdates(locationListener);
            if (TempWakeLock.isHeld()) TempWakeLock.release();
        }
    }
}
