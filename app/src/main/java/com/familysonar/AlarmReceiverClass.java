package com.familysonar;

import static android.content.Context.LOCATION_SERVICE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Locale;

public class AlarmReceiverClass extends BroadcastReceiver {
    private static final long LOCATION_TIMEOUT_MILLIS = 30_000L;

    private PowerManager.WakeLock wakeLock;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Handler timeoutHandler;
    private Runnable locationTimeout;
    private String destination;

    private void releaseWakeLock() {
        if (timeoutHandler != null && locationTimeout != null) {
            timeoutHandler.removeCallbacks(locationTimeout);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        destination = intent.getStringExtra("from");
        if (destination == null || destination.trim().isEmpty()) {
            Log.w("FindMe", "Location request has no destination number");
            return;
        }

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FindMe:LocationRequest");
        wakeLock.acquire(LOCATION_TIMEOUT_MILLIS);
        timeoutHandler = new Handler(Looper.getMainLooper());

        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w("FindMe", "Location request cannot run: location permission is missing");
            releaseWakeLock();
            return;
        }

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            sendLocation(location);
                            return;
                        }
                        requestSingleLocation(context);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Log.w("FindMe", "Last location lookup failed", exception);
                        releaseWakeLock();
                    }
                });
    }

    private void requestSingleLocation(Context context) {
        locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
        boolean hasFineLocation = ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean useGps = hasFineLocation && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean useNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!useGps && !useNetwork) {
            Log.w("FindMe", "Location request cannot run: no location provider is enabled");
            releaseWakeLock();
            return;
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                locationManager.removeUpdates(this);
                sendLocation(location);
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
        };
        locationTimeout = () -> {
            if (locationManager != null && locationListener != null) {
                locationManager.removeUpdates(locationListener);
            }
            Log.w("FindMe", "Location request timed out");
            releaseWakeLock();
        };
        timeoutHandler.postDelayed(locationTimeout, LOCATION_TIMEOUT_MILLIS);

        try {
            String provider = useGps ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            locationManager.requestLocationUpdates(provider, 10_000L, 0f, locationListener);
        } catch (SecurityException exception) {
            Log.w("FindMe", "Location request failed because permission was revoked", exception);
            releaseWakeLock();
        }
    }

    private void sendLocation(Location location) {
        locationManagerSafeRemoveUpdates();
        String message = String.format(Locale.US, "[%f,%f]",
                location.getLatitude(), location.getLongitude());
        try {
            SmsManager.getDefault().sendTextMessage(destination, null, message, null, null);
            Log.d("FindMe", "Sent requested location");
        } finally {
            releaseWakeLock();
        }
    }

    private void locationManagerSafeRemoveUpdates() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }
}
