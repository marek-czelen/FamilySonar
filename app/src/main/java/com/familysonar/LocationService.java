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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellInfoWcdma;
import android.telephony.CellIdentityNr;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocationService extends Service {
    public static boolean isRunning=false;

    final static String LOCATION_ACTION = "FAMILIY_SONAR_LOCATION_ACTION";
    final static String LOCATION_RESTART = "FAMILIY_SONAR_LOCATION_RESTART_ACTION";
    final static String LOCATION_CHANGE_REFRESH = "FAMILIY_SONAR_LOCATION_CHANGE_REFRESH_ACTION";
    public static final String ACTION_REQUEST_CURRENT_LOCATION =
            "FAMILIY_SONAR_REQUEST_CURRENT_LOCATION_ACTION";
    public static final String ACTION_SET_REFRESH_INTERVAL =
            "FAMILIY_SONAR_SET_REFRESH_INTERVAL_ACTION";
    public static final String ACTION_REQUEST_LOCATION_FOR_SMS =
            "FAMILIY_SONAR_REQUEST_LOCATION_FOR_SMS_ACTION";
    public static final String EXTRA_LOCATION_DESTINATION = "location_destination";
    public static final String EXTRA_REFRESH_INTERVAL_MILLIS = "refresh_interval_millis";

    public static long LocationSleepRefreshPeridSeconds = 1200L;
    public static long LocationFastRefreshPeridSeconds = 10L;

    public static int locationsCount = -1;

    private static final float SLEEP_LOCATION_MIN_DISTANCE_METERS = 200f;
    private static final float GEOCODE_MIN_DISTANCE_METERS = 200f;
    private static final long FAST_REQUEST_TIMEOUT_MILLIS = 30_000L;
    private static final String LOCATION_CACHE_PREFS = "location_cache";
    private static final String CACHE_LATITUDE = "latitude";
    private static final String CACHE_LONGITUDE = "longitude";
    private static final String CACHE_TIME = "time";
    private static final String CACHE_ACCURACY = "accuracy";
    private static final String CACHE_ADDRESS = "address";
    private static final String CELL_CACHE_PREFS = "cell_cache";
    private static final String CELL_CACHE_SAMPLES = "samples";
    private static final String CELL_CACHE_TIME = "time";
    private static final long CELL_CACHE_MAX_AGE_MILLIS = 5 * 60 * 1000L;

    private long LocationRefreshPeridSeconds = LocationSleepRefreshPeridSeconds;

    String CHANNEL_ID = "FindMe.Location";
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
    private boolean continuousTracking = false;
    private boolean cachedLocationSentForRequest = false;
    private final Runnable fastRequestTimeout = () -> {
        if (locationsCount > 0) {
            if (!cachedLocationSentForRequest && !sendLocationNumber.isEmpty()) {
                SmsManager.getDefault().sendTextMessage(sendLocationNumber, null,
                        "Nie udało się pobrać lokalizacji w ciągu 30 sekund.", null, null);
            }
            locationsCount = -1;
            LocationRefreshPeridSeconds = LocationSleepRefreshPeridSeconds;
            sendLocationFastEnd = false;
            sendLocationNumber = "";
            cachedLocationSentForRequest = false;
            if (stopAfterFastRequest) {
                stopSelf();
            } else {
                GetLocalization();
            }
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
        loadCachedLocation();
        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, getNotification());
        LocationService.isRunning=true;
        if (optionReceiver == null) {
            RegisterOptionReceiver();
        }
        if (intent == null) {
            continuousTracking = true;
            stopAfterFastRequest = false;
            LocationRefreshPeridSeconds = LocationSleepRefreshPeridSeconds;
            GetLocalization();
        } else if (ACTION_SET_REFRESH_INTERVAL.equals(intent.getAction())) {
            continuousTracking = true;
            stopAfterFastRequest = false;
            if (locationsCount <= 0) {
                long refreshMillis = intent.getLongExtra(
                        EXTRA_REFRESH_INTERVAL_MILLIS,
                        LocationSleepRefreshPeridSeconds * 1000L);
                LocationRefreshPeridSeconds = Math.max(1L, refreshMillis / 1000L);
                GetLocalization();
            }
        } else if (ACTION_REQUEST_LOCATION_FOR_SMS.equals(intent.getAction())) {
            continuousTracking = true;
            stopAfterFastRequest = false;
            requestFastLocation(intent.getStringExtra(EXTRA_LOCATION_DESTINATION));
        } else if (ACTION_REQUEST_CURRENT_LOCATION.equals(intent.getAction())) {
            continuousTracking = true;
            stopAfterFastRequest = false;
            requestFastLocation(null);
        } else if (LOCATION_CHANGE_REFRESH.equals(intent.getAction())) {
            stopAfterFastRequest = !continuousTracking
                    && intent.getIntExtra("refreshCounts", -1) > 0;
            applyLocationOptions(intent);
        } else {
            continuousTracking = true;
            stopAfterFastRequest = false;
            GetLocalization();
        }
        return continuousTracking ? START_STICKY : START_NOT_STICKY;
    }

    public static boolean hasLocationPermission(Context context) {
        return ActivityCompat.checkSelfPermission(context, ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
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
        ContextCompat.registerReceiver(
                this,
                optionReceiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        Log.d("RegisterOptionReceiver", "registered");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Service Channel",
                    NotificationManager.IMPORTANCE_LOW
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
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true);
        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }


    private void GetLocalization() {
        boolean hasFineLocation = ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocation = ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!hasFineLocation && !hasCoarseLocation) {
            Log.e("LocationService", "Location updates cannot run: location permission is missing");
            if (!sendLocationNumber.isEmpty()
                    && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED) {
                SmsManager.getDefault().sendTextMessage(sendLocationNumber, null,
                        "Brak uprawnienia do lokalizacji.", null, null);
            }
            stopSelf();
            return;
        }
        if (locationManager == null){
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        }
        boolean isGPSProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        boolean isFastRequest = locationsCount > 0;
        if (isGPSProvider && isFastRequest && hasFineLocation) {
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
        persistLocation(currentLocation);
        if (locationsCount > 0 || sendLocationFastEnd) {
            updateAddressIfNeeded(currentLocation);
        }
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
                    persistLocation(location);
                    addressLookupInProgress = false;
                    sendLocationBroadcast();
                    sendPendingLocation();
                });
            } catch (Exception ex) {
                addressLookupInProgress = false;
                currentLocationAddress = "?????";
                sendLocationBroadcast();
                sendPendingLocation();
            }
            return;
        }

        try {
            List<Address> list = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            currentLocationAddress = list.isEmpty() ? "?????" : list.get(0).getAddressLine(0);
            geocodedLocation = new Location(location);
            persistLocation(location);
        } catch (IOException e) {
            currentLocationAddress = "?????";
        }
        sendLocationBroadcast();
        sendPendingLocation();
    }

    private void sendPendingLocation() {
        if (pendingLocationNumber.isEmpty()) {
            return;
        }
        sendCurrentLocation(pendingLocationNumber);
        pendingLocationNumber = "";
    }

    private void sendCurrentLocation(String destination) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationService", "Cannot send location report: SEND_SMS permission is missing");
            return;
        }
        String message = buildLocationReport();
        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> parts = smsManager.divideMessage(message);
        if (parts.size() == 1) {
            smsManager.sendTextMessage(destination, null, message, null, null);
        } else {
            smsManager.sendMultipartTextMessage(destination, null, parts, null, null);
        }
        if (currentLocation != null) {
            int accuracy = currentLocation.hasAccuracy()
                    ? Math.round(currentLocation.getAccuracy()) : -1;
            sendTechnicalLocation(destination, currentLocation.getLatitude(),
                    currentLocation.getLongitude(), accuracy, currentLocationTime);
        }
    }

    private void requestFastLocation(String destination) {
        sendLocationNumber = destination == null ? "" : destination;
        cachedLocationSentForRequest = false;
        sendCachedLocation(sendLocationNumber);
        LocationRefreshPeridSeconds = LocationFastRefreshPeridSeconds;
        locationsCount = 1;
        sendLocationFastEnd = true;
        heartBeat.removeCallbacks(fastRequestTimeout);
        heartBeat.postDelayed(fastRequestTimeout, FAST_REQUEST_TIMEOUT_MILLIS);
        GetLocalization();
    }

    private void sendCachedLocation(String destination) {
        if (destination == null || destination.trim().isEmpty()
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        SharedPreferences preferences = getSharedPreferences(LOCATION_CACHE_PREFS, MODE_PRIVATE);
        if (!preferences.contains(CACHE_LATITUDE) || !preferences.contains(CACHE_LONGITUDE)) {
            SmsManager.getDefault().sendTextMessage(destination, null,
                    "Brak zapisanej lokalizacji. Pobieram aktualny pomiar.", null, null);
            return;
        }

        long time = preferences.getLong(CACHE_TIME, -1L);
        float accuracy = preferences.getFloat(CACHE_ACCURACY, -1f);
        StringBuilder message = new StringBuilder("Ostatnia lokalizacja: [")
                .append(String.format(Locale.US, "%f,%f",
                        preferences.getFloat(CACHE_LATITUDE, 0f),
                        preferences.getFloat(CACHE_LONGITUDE, 0f)))
                .append("]; czas=")
                .append(new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                        .format(new java.util.Date(time)))
                .append("; wiek=").append(formatLocationAge(time));
        if (accuracy >= 0f) {
            message.append("; dokładność=").append(Math.round(accuracy)).append("m");
        }
        String address = preferences.getString(CACHE_ADDRESS, "");
        if (address != null && !address.isEmpty()) {
            message.append("; adres=").append(address);
        }
        SmsManager.getDefault().sendTextMessage(destination, null, message.toString(), null, null);
        sendTechnicalLocation(destination,
                preferences.getFloat(CACHE_LATITUDE, 0f),
                preferences.getFloat(CACHE_LONGITUDE, 0f),
                accuracy >= 0f ? Math.round(accuracy) : -1,
                time);
        cachedLocationSentForRequest = true;
    }

    private void persistLocation(Location location) {
        if (location == null) {
            return;
        }
        SharedPreferences.Editor editor = getSharedPreferences(LOCATION_CACHE_PREFS, MODE_PRIVATE)
                .edit()
                .putFloat(CACHE_LATITUDE, (float) location.getLatitude())
                .putFloat(CACHE_LONGITUDE, (float) location.getLongitude())
                .putLong(CACHE_TIME, location.getTime());
        if (location.hasAccuracy()) {
            editor.putFloat(CACHE_ACCURACY, location.getAccuracy());
        }
        if (!currentLocationAddress.isEmpty()) {
            editor.putString(CACHE_ADDRESS, currentLocationAddress);
        }
        editor.apply();
    }

    private void loadCachedLocation() {
        SharedPreferences preferences = getSharedPreferences(LOCATION_CACHE_PREFS, MODE_PRIVATE);
        if (!preferences.contains(CACHE_LATITUDE) || !preferences.contains(CACHE_LONGITUDE)) {
            return;
        }
        Location cached = new Location("cached");
        cached.setLatitude(preferences.getFloat(CACHE_LATITUDE, 0f));
        cached.setLongitude(preferences.getFloat(CACHE_LONGITUDE, 0f));
        cached.setTime(preferences.getLong(CACHE_TIME, System.currentTimeMillis()));
        float accuracy = preferences.getFloat(CACHE_ACCURACY, -1f);
        if (accuracy >= 0f) {
            cached.setAccuracy(accuracy);
        }
        currentLocation = cached;
        currentLocationString = String.format(Locale.US, "[%f,%f]",
                cached.getLatitude(), cached.getLongitude());
        currentLocationTime = cached.getTime();
        currentLocationAddress = preferences.getString(CACHE_ADDRESS, "");
    }

    private String buildLocationReport() {
        StringBuilder report = new StringBuilder();
        report.append("Czas=")
                .append(new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                        .format(currentLocationTime))
                .append("; wiek=").append(formatLocationAge(currentLocationTime))
                .append("; lokalizacja=").append(currentLocationString);
        if (currentLocation.hasAccuracy()) {
            report.append("; dokladnosc=").append(Math.round(currentLocation.getAccuracy())).append("m");
        }
        if (!currentLocationAddress.isEmpty()) {
            report.append("; adres=").append(currentLocationAddress);
        }

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        report.append("; oszczedzanie_baterii=")
                .append(powerManager.isPowerSaveMode() ? "ON" : "OFF");
        report.append("; usluga_lokalizacji=")
                .append(isRunning ? "dziala" : "nie_dziala");
        appendBatteryStatus(report);
        appendNetworkAndCells(report);
        return report.toString();
    }

    private String formatLocationAge(long locationTime) {
        long ageMillis = Math.max(0L, System.currentTimeMillis() - locationTime);
        long totalSeconds = ageMillis / 1000L;
        if (totalSeconds < 60L) {
            return totalSeconds + " s";
        }
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes < 60L) {
            return minutes + " min " + seconds + " s";
        }
        long hours = minutes / 60L;
        minutes %= 60L;
        return hours + " h " + minutes + " min";
    }

    private void appendBatteryStatus(StringBuilder report) {
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) {
            report.append("; bateria=brak_danych");
            return;
        }
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) {
            report.append("; bateria=").append(Math.round(level * 100f / scale)).append("%");
        } else {
            report.append("; bateria=brak_danych");
        }
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        report.append("; ladowanie=")
                .append(status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL ? "tak" : "nie");
    }

    private void appendNetworkAndCells(StringBuilder report) {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            report.append("; siec=brak_modemu; BTS=brak_modemu");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            report.append("; siec=brak_uprawnienia; BTS=brak_uprawnienia");
            return;
        }

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            report.append("; siec=brak_danych; BTS=brak_danych");
            return;
        }
        try {
            String operator = telephonyManager.getNetworkOperatorName();
            int networkType = telephonyManager.getDataNetworkType();
            if (networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                networkType = telephonyManager.getVoiceNetworkType();
            }
            report.append("; siec=").append(networkTypeName(networkType));
            if (operator != null && !operator.trim().isEmpty()) {
                report.append(" ").append(operator.trim());
            }
        } catch (SecurityException exception) {
            report.append("; siec=brak_uprawnienia");
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            report.append("; BTS=brak_uprawnienia_lokalizacji");
            return;
        }
        try {
            List<CellInfo> cells = telephonyManager.getAllCellInfo();
            if (cells == null || cells.isEmpty()) {
                report.append("; BTS=brak_danych");
                return;
            }
            report.append("; BTS=");
            boolean first = true;
            for (CellInfo cell : cells) {
                String description = describeCell(cell);
                if (description == null) {
                    continue;
                }
                if (!first) {
                    report.append(" | ");
                }
                report.append(description);
                first = false;
            }
            if (first) {
                report.append("brak_danych");
            }
        } catch (SecurityException exception) {
            report.append("; BTS=brak_uprawnienia");
        }
    }

    private String describeCell(CellInfo cell) {
        if (cell instanceof CellInfoGsm) {
            CellInfoGsm info = (CellInfoGsm) cell;
            return cellDescription(cell, "GSM", info.getCellIdentity().getMccString(),
                    info.getCellIdentity().getMncString(), "LAC",
                    info.getCellIdentity().getLac(), "CID", info.getCellIdentity().getCid(),
                    info.getCellSignalStrength().getDbm());
        }
        if (cell instanceof CellInfoLte) {
            CellInfoLte info = (CellInfoLte) cell;
            return cellDescription(cell, "LTE", info.getCellIdentity().getMccString(),
                    info.getCellIdentity().getMncString(), "TAC",
                    info.getCellIdentity().getTac(), "CID", info.getCellIdentity().getCi(),
                    info.getCellSignalStrength().getDbm());
        }
        if (cell instanceof CellInfoWcdma) {
            CellInfoWcdma info = (CellInfoWcdma) cell;
            return cellDescription(cell, "UMTS", info.getCellIdentity().getMccString(),
                    info.getCellIdentity().getMncString(), "LAC",
                    info.getCellIdentity().getLac(), "CID", info.getCellIdentity().getCid(),
                    info.getCellSignalStrength().getDbm());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell instanceof CellInfoTdscdma) {
            CellInfoTdscdma info = (CellInfoTdscdma) cell;
            return cellDescription(cell, "UMTS", info.getCellIdentity().getMccString(),
                    info.getCellIdentity().getMncString(), "LAC",
                    info.getCellIdentity().getLac(), "CID", info.getCellIdentity().getCid(),
                    info.getCellSignalStrength().getDbm());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell instanceof CellInfoNr) {
            CellInfoNr info = (CellInfoNr) cell;
            CellIdentityNr identity = (CellIdentityNr) info.getCellIdentity();
            return cellDescription(cell, "NR", identity.getMccString(),
                    identity.getMncString(), "TAC",
                    identity.getTac(), "NCI", identity.getNci(),
                    info.getCellSignalStrength().getDbm());
        }
        if (cell instanceof CellInfoCdma) {
            CellInfoCdma info = (CellInfoCdma) cell;
            return (cell.isRegistered() ? "*" : "") + "CDMA SID="
                    + info.getCellIdentity().getSystemId() + " NID="
                    + info.getCellIdentity().getNetworkId() + " BID="
                    + info.getCellIdentity().getBasestationId() + " dBm="
                    + signalValue(info.getCellSignalStrength().getDbm());
        }
        return null;
    }

    private String cellDescription(CellInfo cell, String radio, String mcc, String mnc,
                                   String areaLabel, int areaCode, String idLabel, long cellId,
                                   int dbm) {
        return (cell.isRegistered() ? "*" : "") + radio
                + " MCC=" + textValue(mcc)
                + " MNC=" + textValue(mnc)
                + " " + areaLabel + "=" + numberValue(areaCode)
                + " " + idLabel + "=" + numberValue(cellId)
                + " dBm=" + signalValue(dbm);
    }

    private String networkTypeName(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
            case TelephonyManager.NETWORK_TYPE_GSM:
                return "2G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE:
            case 19:
                return "4G/LTE";
            case TelephonyManager.NETWORK_TYPE_NR:
                return "5G/NR";
            default:
                return "nieznana";
        }
    }

    private String textValue(String value) {
        return value == null || value.isEmpty() ? "?" : value;
    }

    private String numberValue(long value) {
        return value < 0 ? "?" : String.valueOf(value);
    }

    private String signalValue(int dbm) {
        return dbm == Integer.MAX_VALUE ? "?" : String.valueOf(dbm);
    }

    private void sendTechnicalLocation(String destination, double lat, double lon,
                                       int accuracy, long time) {
        if (destination == null || destination.trim().isEmpty()
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        final String target = destination;
        new Thread(() -> {
            List<TechnicalLocationSms.Cell> cells = collectCellSamples();
            String message = TechnicalLocationSms.build(lat, lon, accuracy, time, cells);
            Log.d("FindMeCells", "Technical SMS -> " + target + " (" + cells.size()
                    + " cells): " + message);
            try {
                SmsManager smsManager = SmsManager.getDefault();
                ArrayList<String> parts = smsManager.divideMessage(message);
                if (parts.size() == 1) {
                    smsManager.sendTextMessage(target, null, message, null, null);
                } else {
                    smsManager.sendMultipartTextMessage(target, null, parts, null, null);
                }
            } catch (Exception exception) {
                Log.e("LocationService", "Unable to send technical location SMS", exception);
            }
        }).start();
    }

    private List<TechnicalLocationSms.Cell> collectCellSamples() {
        List<TechnicalLocationSms.Cell> samples = new ArrayList<>();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return samples;
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return samples;
        }
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            return samples;
        }

        List<CellInfo> observed = new ArrayList<>();
        try {
            List<CellInfo> cached = telephonyManager.getAllCellInfo();
            if (cached != null) {
                observed.addAll(cached);
            }
        } catch (SecurityException ignored) {
        }

        // Force a fresh scan so as many visible towers as possible are reported.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                final java.util.concurrent.CountDownLatch latch =
                        new java.util.concurrent.CountDownLatch(1);
                final List<CellInfo> fresh = new ArrayList<>();
                telephonyManager.requestCellInfoUpdate(command -> command.run(),
                        new TelephonyManager.CellInfoCallback() {
                            @Override
                            public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
                                if (cellInfo != null) {
                                    fresh.addAll(cellInfo);
                                }
                                latch.countDown();
                            }

                            @Override
                            public void onError(int errorCode, @Nullable Throwable detail) {
                                latch.countDown();
                            }
                        });
                latch.await(4, java.util.concurrent.TimeUnit.SECONDS);
                observed.addAll(fresh);
            } catch (Throwable ignored) {
            }
        }

        java.util.Set<String> seen = new java.util.HashSet<>();
        Log.d("FindMeCells", "Observed cells (getAllCellInfo + fresh scan) = " + observed.size());
        for (CellInfo cell : observed) {
            TechnicalLocationSms.Cell sample = parseCellSample(cell);
            String described = describeCell(cell);
            if (described == null) {
                described = cell.getClass().getSimpleName();
            }
            if (sample == null) {
                Log.d("FindMeCells", "REJECTED (no usable MCC/MNC/LAC/CID): " + described);
                continue;
            }
            if (seen.add(sample.mcc + "_" + sample.mnc + "_" + sample.cid + "_" + sample.lac)) {
                samples.add(sample);
                Log.d("FindMeCells", "ACCEPTED mcc=" + sample.mcc + " mnc=" + sample.mnc
                        + " cid=" + sample.cid + " lac=" + sample.lac
                        + " dbm=" + sample.dbm + " | " + described);
            } else {
                Log.d("FindMeCells", "DUPLICATE mcc=" + sample.mcc + " mnc=" + sample.mnc
                        + " cid=" + sample.cid + " lac=" + sample.lac);
            }
        }
        if (samples.isEmpty()) {
            samples.addAll(loadRecentCellSamples());
            if (!samples.isEmpty()) {
                Log.w("FindMeCells", "Fresh radio scan had no usable cell; using "
                        + samples.size() + " recently verified cached cell(s)");
            }
        } else {
            persistCellSamples(samples);
        }
        Log.d("FindMeCells", "Usable cells for OpenCellID = " + samples.size());
        return samples;
    }

    private void persistCellSamples(List<TechnicalLocationSms.Cell> samples) {
        StringBuilder encoded = new StringBuilder();
        for (TechnicalLocationSms.Cell sample : samples) {
            if (encoded.length() > 0) {
                encoded.append(';');
            }
            encoded.append(sample.mcc).append(',')
                    .append(sample.mnc).append(',')
                    .append(sample.cid).append(',')
                    .append(sample.lac).append(',')
                    .append(sample.dbm);
        }
        getSharedPreferences(CELL_CACHE_PREFS, MODE_PRIVATE)
                .edit()
                .putString(CELL_CACHE_SAMPLES, encoded.toString())
                .putLong(CELL_CACHE_TIME, System.currentTimeMillis())
                .apply();
    }

    private List<TechnicalLocationSms.Cell> loadRecentCellSamples() {
        SharedPreferences prefs = getSharedPreferences(CELL_CACHE_PREFS, MODE_PRIVATE);
        long cachedAt = prefs.getLong(CELL_CACHE_TIME, 0L);
        if (cachedAt <= 0L || System.currentTimeMillis() - cachedAt > CELL_CACHE_MAX_AGE_MILLIS) {
            return new ArrayList<>();
        }

        List<TechnicalLocationSms.Cell> cached = new ArrayList<>();
        String encoded = prefs.getString(CELL_CACHE_SAMPLES, "");
        if (encoded == null || encoded.isEmpty()) {
            return cached;
        }
        for (String record : encoded.split(";")) {
            String[] fields = record.split(",");
            if (fields.length != 5) {
                continue;
            }
            try {
                int mcc = Integer.parseInt(fields[0]);
                int mnc = Integer.parseInt(fields[1]);
                long cid = Long.parseLong(fields[2]);
                int lac = Integer.parseInt(fields[3]);
                int dbm = Integer.parseInt(fields[4]);
                if (mcc > 0 && mnc >= 0 && isUsableCid(cid) && isUsableLac(lac)) {
                    cached.add(new TechnicalLocationSms.Cell(mcc, mnc, cid, lac, dbm));
                }
            } catch (NumberFormatException ignored) {
                // Ignore a malformed cache entry.
            }
        }
        return cached;
    }

    private TechnicalLocationSms.Cell parseCellSample(CellInfo cell) {
        int mcc = -1;
        int mnc = -1;
        long cid = -1L;
        int lac = -1;
        int dbm = Integer.MAX_VALUE;
        if (cell instanceof CellInfoGsm) {
            CellInfoGsm info = (CellInfoGsm) cell;
            mcc = intOf(info.getCellIdentity().getMccString());
            mnc = intOf(info.getCellIdentity().getMncString());
            lac = info.getCellIdentity().getLac();
            cid = info.getCellIdentity().getCid();
            dbm = info.getCellSignalStrength().getDbm();
        } else if (cell instanceof CellInfoLte) {
            CellInfoLte info = (CellInfoLte) cell;
            mcc = intOf(info.getCellIdentity().getMccString());
            mnc = intOf(info.getCellIdentity().getMncString());
            lac = info.getCellIdentity().getTac();
            cid = info.getCellIdentity().getCi();
            dbm = info.getCellSignalStrength().getDbm();
        } else if (cell instanceof CellInfoWcdma) {
            CellInfoWcdma info = (CellInfoWcdma) cell;
            mcc = intOf(info.getCellIdentity().getMccString());
            mnc = intOf(info.getCellIdentity().getMncString());
            lac = info.getCellIdentity().getLac();
            cid = info.getCellIdentity().getCid();
            dbm = info.getCellSignalStrength().getDbm();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && cell instanceof CellInfoTdscdma) {
            CellInfoTdscdma info = (CellInfoTdscdma) cell;
            mcc = intOf(info.getCellIdentity().getMccString());
            mnc = intOf(info.getCellIdentity().getMncString());
            lac = info.getCellIdentity().getLac();
            cid = info.getCellIdentity().getCid();
            dbm = info.getCellSignalStrength().getDbm();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && cell instanceof CellInfoNr) {
            CellInfoNr info = (CellInfoNr) cell;
            CellIdentityNr identity = (CellIdentityNr) info.getCellIdentity();
            mcc = intOf(identity.getMccString());
            mnc = intOf(identity.getMncString());
            lac = identity.getTac();
            cid = identity.getNci();
            dbm = info.getCellSignalStrength().getDbm();
        }
        if (mcc <= 0 || mnc < 0 || !isUsableCid(cid) || !isUsableLac(lac)) {
            return null;
        }
        int signal = dbm == Integer.MAX_VALUE ? -120 : dbm;
        return new TechnicalLocationSms.Cell(mcc, mnc, cid, lac, signal);
    }

    private int intOf(String value) {
        try {
            return value == null ? -1 : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /** LAC/TAC is valid when it is not the 16-bit (0xFFFF) or int "unavailable" sentinel. */
    static boolean isUsableLac(int lac) {
        return lac > 0 && lac != 0xFFFF && lac != Integer.MAX_VALUE;
    }

    /** CID/CI is valid when it is not the LTE 28-bit (0x0FFFFFFF) or int "unavailable" sentinel. */
    static boolean isUsableCid(long cid) {
        return cid > 0 && cid != 0x0FFFFFFFL && cid != Integer.MAX_VALUE;
    }



    private void sendLocationBroadcast() {
        if (currentLocationString.isEmpty()) return;
        Intent locationIntent = new Intent();
        locationIntent.setAction(LOCATION_ACTION);
        locationIntent.setPackage(getPackageName());
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
        String requestNumber = intent.getStringExtra("from");
        sendLocationNumber = requestNumber == null ? "" : requestNumber;
        sendLocationFastEnd = intent.getBooleanExtra("sendLocationFastEnd", false);
        if (locationsCount > 0) {
            heartBeat.removeCallbacks(fastRequestTimeout);
            heartBeat.postDelayed(fastRequestTimeout, FAST_REQUEST_TIMEOUT_MILLIS);
        }
        GetLocalization();
        Log.d("OptionReceiver refresh_s", String.format("%d",LocationRefreshPeridSeconds));
        Log.d("OptionReceiver locationCount", String.format("%d",locationsCount));
    }

}
