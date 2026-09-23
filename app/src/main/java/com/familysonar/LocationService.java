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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private boolean continuousTracking = false;
    private final Runnable fastRequestTimeout = () -> {
        if (locationsCount > 0) {
            if (!sendLocationNumber.isEmpty()) {
                SmsManager.getDefault().sendTextMessage(sendLocationNumber, null,
                        "Nie udało się pobrać lokalizacji w ciągu 30 sekund.", null, null);
            }
            locationsCount = -1;
            LocationRefreshPeridSeconds = LocationSleepRefreshPeridSeconds;
            sendLocationFastEnd = false;
            sendLocationNumber = "";
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
                sendLocationBroadcast();
                sendPendingLocation();
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
    }

    private String buildLocationReport() {
        StringBuilder report = new StringBuilder();
        report.append("Czas=")
                .append(new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                        .format(currentLocationTime))
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
