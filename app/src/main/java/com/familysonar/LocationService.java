package com.familysonar;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class LocationService extends Service {
    public static boolean isRunning=false;

    final static String LOCATION_ACTION = "FAMILIY_SONAR_LOCATION_ACTION";
    final static String LOCATION_RESTART = "FAMILIY_SONAR_LOCATION_RESTART_ACTION";
    final static String LOCATION_CHANGE_REFRESH = "FAMILIY_SONAR_LOCATION_CHANGE_REFRESH_ACTION";

    public static long LocationSleepRefreshPeridSeconds = 900L;
    public static long LocationFastRefreshPeridSeconds = 10L;

    public static int locationsCount = -1;

    private static final float SLEEP_LOCATION_MIN_DISTANCE_METERS = 100f;
    private static final float GEOCODE_MIN_DISTANCE_METERS = 200f;
    private static final long FAST_REQUEST_TIMEOUT_MILLIS = 30_000L;

    private long LocationRefreshPeridSeconds = LocationSleepRefreshPeridSeconds;

    String CHANNEL_ID = "FamilySonar.Location";
    Location currentLocationGPS = null;
    Location currentLocationNetwork = null;
    LocationListener gpsLocationListener = null;
    LocationListener networkLocationListener = null;

    Location currentLocation = null;
    Location geocodedLocation = null;
    public static String currentLocationString = "";
    public static Long currentLocationTime = -1L;
    public static String currentLocationAddress = "";

    private OptionReceiver optionReceiver =null;
    LocationManager locationManager =null;
    private boolean sendLocationFastEnd = false;
    private String sendLocationNumber = "";
    private boolean addressLookupInProgress = false;
    private String pendingLocationNumber = "";
    private boolean stopAfterFastRequest = false;
    private final Runnable fastRequestTimeout = () -> {
        if (stopAfterFastRequest && locationsCount > 0) {
            if (!sendLocationNumber.isEmpty()) {
                SmsManager.getDefault().sendTextMessage(sendLocationNumber, null,
                        "Nie udało się pobrać lokalizacji w ciągu 30 sekund.", null, null);
            }
            stopSelf();
        }
    };





    Handler heartBeat = null;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        createNotificationChannel();
        heartBeat = new Handler(getMainLooper());
        networkLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                currentLocationNetwork = location;
                ProcessLocationUpdate();
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
        gpsLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                currentLocationGPS = location;
                ProcessLocationUpdate();
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
        };
        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, getNotification());
        LocationService.isRunning=true;
        if (optionReceiver == null) {
            RegisterOptionReceiver();
        }
        if (intent != null && LOCATION_CHANGE_REFRESH.equals(intent.getAction())) {
            stopAfterFastRequest = true;
            applyLocationOptions(intent);
        } else {
            GetLocalization();
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public ComponentName startService(Intent service) {
        return super.startService(service);
    }

    @Override
    public void onDestroy() {
        LocationService.isRunning=false;
        if (locationManager != null) {
            locationManager.removeUpdates(gpsLocationListener);
            locationManager.removeUpdates(networkLocationListener);
        }
        if (optionReceiver != null) {
            unregisterReceiver(optionReceiver);
        }
        if (heartBeat != null) {
            heartBeat.removeCallbacks(fastRequestTimeout);
        }
        super.onDestroy();
    }

    private void RegisterOptionReceiver(){
        //Register BroadcastReceiver
        //to receive event from our service
        optionReceiver = new LocationService.OptionReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LocationService.LOCATION_RESTART);
        intentFilter.addAction(LocationService.LOCATION_CHANGE_REFRESH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(optionReceiver, intentFilter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(optionReceiver, intentFilter);
        }
        Log.d("RegisterOptionReceiver", "registered");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager =
                    getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification getNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new
                NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Service")
                .setContentText("Getting location updates")
                .setContentIntent(pendingIntent)
                .setOngoing(true);
        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }


    private void GetLocalization() {
        if (locationManager == null){
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        }
        boolean isGPSProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        boolean isFastRequest = locationsCount > 0;
        if (isGPSProvider && isFastRequest) {
            if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            }
            locationManager.removeUpdates(gpsLocationListener);
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LocationRefreshPeridSeconds * 1000,
                    0f,
                    gpsLocationListener
            );
                } else {
                    locationManager.removeUpdates(gpsLocationListener);
        }
        if (isNetworkProvider) {
            locationManager.removeUpdates(networkLocationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    LocationRefreshPeridSeconds * 1000,
                        isFastRequest ? 0f : SLEEP_LOCATION_MIN_DISTANCE_METERS,
                    networkLocationListener
            );
        }


    }

    private void ProcessLocationUpdate() {

        if (currentLocationGPS == null) currentLocation = currentLocationNetwork;
        else if (currentLocationNetwork == null) {
            currentLocation = currentLocationGPS;
        } else if (System.currentTimeMillis() - currentLocationGPS.getTime() <= 30000) {
            currentLocation = currentLocationGPS;
        } else {
            currentLocation = currentLocationGPS.getTime() < currentLocationNetwork.getTime() ? currentLocationNetwork : currentLocationGPS;
        }

        if (currentLocation == null) return;
        currentLocationString = String.format("[%f;%f]", currentLocation.getLatitude(), currentLocation.getLongitude());
        currentLocationString = currentLocationString.replace(",", ".");
        currentLocationString = currentLocationString.replace(";", ",");
        //TextView locView = findViewById(R.id.locationInfo);
        //locView.setText(locationInfo);
        Log.d("GetLocalization", currentLocationString);
        Log.d("GetLocalization", String.format("refresh time: %d",LocationRefreshPeridSeconds));
        Log.d("GetLocalization", String.format("refresh count: %d",locationsCount));
        updateAddressIfNeeded(currentLocation);
        currentLocationTime=currentLocation.getTime();
        sendLocationBroadcast();

        if (locationsCount > 0) locationsCount--;
        if (locationsCount == 0){
            locationsCount = -1;
            heartBeat.removeCallbacks(fastRequestTimeout);
            LocationRefreshPeridSeconds = LocationSleepRefreshPeridSeconds;
            if (sendLocationFastEnd){
                sendLocationFastEnd = false;
                if (!sendLocationNumber.isEmpty()){
                    if (addressLookupInProgress) {
                        pendingLocationNumber = sendLocationNumber;
                    } else {
                        sendCurrentLocation(sendLocationNumber);
                    }
                    sendLocationNumber = "";
                }

            }
            if (stopAfterFastRequest) {
                stopSelf();
            } else {
                GetLocalization();
            }
        }


    }

    private void updateAddressIfNeeded(Location location) {
        if (geocodedLocation != null
                && !currentLocationAddress.isEmpty()
                && geocodedLocation.distanceTo(location) < GEOCODE_MIN_DISTANCE_METERS) {
            return;
        }

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                addressLookupInProgress = true;
                geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1, list -> {
                    currentLocationAddress = list.isEmpty() ? "?????" : list.get(0).getAddressLine(0);
                    geocodedLocation = new Location(location);
                    addressLookupInProgress = false;
                    sendLocationBroadcast();
                    sendPendingLocation();
                });
            } catch (Exception ex) {
                addressLookupInProgress = false;
                currentLocationAddress = "?????";
            }
            return;
        }

        try {
            List<Address> list = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            currentLocationAddress = list.isEmpty() ? "?????" : list.get(0).getAddressLine(0);
            geocodedLocation = new Location(location);
        } catch (IOException e) {
            currentLocationAddress = "?????";
        }
    }

    private void sendPendingLocation() {
        if (pendingLocationNumber.isEmpty()) {
            return;
        }
        sendCurrentLocation(pendingLocationNumber);
        pendingLocationNumber = "";
    }

    private void sendCurrentLocation(String destination) {
        SmsManager.getDefault().sendTextMessage(destination, null,
                new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(currentLocationTime),
                null, null);
        SmsManager.getDefault().sendTextMessage(destination, null, currentLocationString, null, null);
        SmsManager.getDefault().sendTextMessage(destination, null, currentLocationAddress, null, null);
    }



    private void sendLocationBroadcast() {
        if (currentLocationString.isEmpty()) return;
        if (currentLocationAddress.isEmpty()) return;
        Intent locationIntent = new Intent();
        locationIntent.setAction(LOCATION_ACTION);
        locationIntent.putExtra("location", currentLocationString);
        locationIntent.putExtra("address", currentLocationAddress);
        locationIntent.putExtra("time",currentLocation.getTime());
        sendBroadcast(locationIntent);
    }





    class OptionReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case LocationService.LOCATION_RESTART:
                    GetLocalization();
                    break;
                case LocationService.LOCATION_CHANGE_REFRESH:
                    applyLocationOptions(intent);
                    break;
            }
        }
    }

    private void applyLocationOptions(Intent intent) {
        LocationRefreshPeridSeconds=intent.getLongExtra("refresh_s",LocationService.LocationSleepRefreshPeridSeconds);
        locationsCount=intent.getIntExtra("refreshCounts",-1);
        sendLocationNumber = intent.getStringExtra("from");
        sendLocationFastEnd = intent.getBooleanExtra("sendLocationFastEnd", false);
        if (stopAfterFastRequest && locationsCount > 0) {
            heartBeat.removeCallbacks(fastRequestTimeout);
            heartBeat.postDelayed(fastRequestTimeout, FAST_REQUEST_TIMEOUT_MILLIS);
        }
        GetLocalization();
        Log.d("OptionReceiver refresh_s", String.format("%d",LocationRefreshPeridSeconds));
        Log.d("OptionReceiver locationCount", String.format("%d",locationsCount));
    }

}
