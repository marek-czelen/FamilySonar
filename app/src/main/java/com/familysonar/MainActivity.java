package com.familysonar;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.FOREGROUND_SERVICE;
import static android.Manifest.permission.FOREGROUND_SERVICE_LOCATION;
import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.RECEIVE_SMS;
import static android.Manifest.permission.SEND_SMS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.content.pm.PackageManager;
import android.provider.ContactsContract;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationProvider;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.PhoneNumberUtils;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.w3c.dom.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 200;

    ContactsAdapter contactAdapter = null;
    private ConfigData _configData = null;

        private static final String[] SMS_PERMISSIONS = {
            RECEIVE_SMS,
            SEND_SMS
        };

        private static final String[] PHONE_PERMISSIONS = {
            READ_PHONE_STATE
        };

        private static final String[] LOCATION_PERMISSIONS = {
            ACCESS_COARSE_LOCATION,
            ACCESS_FINE_LOCATION
        };

        private enum PermissionRequest {
            SMS,
            PHONE_STATE,
            LOCATION,
            NOTIFICATIONS,
            BACKGROUND_LOCATION
        }

        private PermissionRequest currentPermissionRequest;

    LocationBroadcastReceiver locationBroadcastReceiver = null;
    String lastLocationAddress = "";
    String lastLocation = "";
    long lastLocationTime = System.currentTimeMillis();
    Timer timerGui = null;

    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null
                        || result.getData().getData() == null) {
                    return;
                }
                addContactFromPicker(result.getData().getData());
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //load config
        this._configData = new ConfigData(this);
        try {
            this._configData.Load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        Button checkPermissionButton = findViewById(R.id.permissionButton);
        checkPermissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!checkPermission()) {
                    PermissionInfo(false);
                    requestPermission();
                } else {
                    PermissionInfo(true);
                    Snackbar snackbar = Snackbar.make(view, "Wszystkie uprawnienia przyznane.", Snackbar.LENGTH_LONG);
                    snackbar.show();
                }

            }
        });

        ImageView logo = findViewById(R.id.logoImage);
        logo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent locationIntent = new Intent(MainActivity.this, LocationService.class)
                        .setAction(LocationService.ACTION_REQUEST_CURRENT_LOCATION);
                ContextCompat.startForegroundService(MainActivity.this, locationIntent);
            }
        });

        findViewById(R.id.settingsButton).setOnClickListener(
                view -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        //check permission
        if (!checkPermission()) {
            PermissionInfo(false);
            requestPermission();
        } else {
            PermissionInfo(true);
            requestPhoneStatePermission();
        }


        //sprawdzenie warunków działania aplikacji
        BatteryOptymalizationOptions();

        //przygotowanie listy numerów
        RecyclerView recyclerView = (RecyclerView) this.findViewById(R.id.contactList);

        contactAdapter = new ContactsAdapter(MainActivity.this, this._configData.getContactList());
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(contactAdapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(0, ItemTouchHelper.RIGHT);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                switch (direction) {
                    case ItemTouchHelper.RIGHT:
                        AlertDialog.Builder confirmationDialog = new AlertDialog.Builder(MainActivity.this);
                        confirmationDialog.setTitle("Usuwanie kontaktu");
                        confirmationDialog.setMessage("Czy chesz usunąć ten element?");
                        confirmationDialog.setPositiveButton("Usuń", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                contactAdapter.RemoveItem(viewHolder.getAdapterPosition());
                                try {
                                    _configData.Save();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                } catch (ClassNotFoundException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                        confirmationDialog.setNegativeButton("Anuluj", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                contactAdapter.notifyDataSetChanged();
                            }
                        });
                        confirmationDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialogInterface) {
                                contactAdapter.notifyDataSetChanged();
                            }
                        });
                        confirmationDialog.show();

                        break;
                }
            }


            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                //background
                Paint paintBackground = new Paint();
                paintBackground.setColor(ContextCompat.getColor(MainActivity.this, R.color.safe_error));
                float backLeft = 0; //viewHolder.itemView.getLeft()+viewHolder.itemView.getWidth()+dX;
                float backTop = viewHolder.itemView.getTop();
                float backRight = dX; //backLeft + Math.abs(dX);
                float backBottom = viewHolder.itemView.getBottom();
                c.drawRect(backLeft, backTop, backRight, backBottom, paintBackground);

                //icon
                Drawable icon = getResources().getDrawable(R.drawable.trash, null);
                int iconMargin = 20;
                int iconLeft = iconMargin;// viewHolder.itemView.getWidth()-viewHolder.itemView.getHeight()+iconMargin;
                int iconTop = (int) backTop + iconMargin;
                int iconRigth = iconLeft + viewHolder.itemView.getHeight() - iconMargin;
                int iconBottom = viewHolder.itemView.getBottom() - iconMargin;
                icon.setBounds(iconLeft, iconTop, iconRigth, iconBottom);
                icon.draw(c);

                //text
                Paint textPaint = new Paint();
                textPaint.setColor(ContextCompat.getColor(MainActivity.this, R.color.safe_on_primary));

                int textMargin = 20;
                Rect textBounds = new Rect();
                textPaint.setTypeface(Typeface.DEFAULT_BOLD);// your preference here
                int spSize = 17;
                float scaledSizeInPixels = spSize * getResources().getDisplayMetrics().scaledDensity;
                textPaint.setTextSize(scaledSizeInPixels);// have this the same as your text size

                String text = "Usuń";
                textPaint.getTextBounds(text, 0, text.length(), textBounds);

                int textLeft = iconRigth + textMargin;
                int textTop = (viewHolder.itemView.getHeight() - textMargin * 2 - textBounds.height()) / 2 + (int) backTop + textMargin;
                int textBottom = textTop + textBounds.height();

                c.drawText(text, textLeft, textBottom, textPaint);
            }
        });
        helper.attachToRecyclerView(recyclerView);

        FloatingActionButton fabContacts = findViewById(R.id.fabContacts);
        fabContacts.setOnClickListener(view -> {
            Intent pickContact = new Intent(Intent.ACTION_PICK,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            try {
                contactPickerLauncher.launch(pickContact);
            } catch (ActivityNotFoundException exception) {
                Toast.makeText(MainActivity.this, "Brak aplikacji do wyboru kontaktu.", Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.sosButton).setOnClickListener(view ->
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Potwierdź SOS")
                        .setMessage("Czy na pewno wysłać prośbę o pomoc do wszystkich zaufanych kontaktów?")
                        .setNegativeButton("Anuluj", null)
                        .setPositiveButton("Wyślij SOS", (dialog, which) -> sendSosMessages())
                        .show());

        StartLocationService();
    }

    private void sendSosMessages() {
        if (_configData.getContactList().isEmpty()) {
            Toast.makeText(this, "Brak zaufanych kontaktów.", Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Brak uprawnienia do lokalizacji.", Toast.LENGTH_LONG).show();
            return;
        }

        FusedLocationProviderClient fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) {
                Toast.makeText(this, "Brak dostępnej lokalizacji.", Toast.LENGTH_LONG).show();
                return;
            }
            String smsMessage = String.format(
                    Locale.US,
                    "!!! S.O.S !!! Potrzebuję twojej pomocy, moja lokalizacja to: [%f;%f]",
                    location.getLatitude(),
                    location.getLongitude())
                    .replace(",", ".")
                    .replace(";", ",");
            for (Contact contact : _configData.getContactList()) {
                SmsManager.getDefault().sendTextMessage(
                        contact.getPhone(), null, smsMessage, null, null);
            }
            Toast.makeText(this, "Wysłano SOS do zaufanych kontaktów.", Toast.LENGTH_LONG).show();
        });
    }


    private boolean BatteryOptymalizationOptions() {
        Button batteryButton = findViewById(R.id.batteryOptimalizationButton);
        batteryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
            }
        });
        return true;
    }

    private void addContactFromPicker(Uri contactUri) {
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                Toast.makeText(this, "Nie udało się odczytać wybranego kontaktu.", Toast.LENGTH_LONG).show();
                return;
            }
            String name = cursor.getString(cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            String phone = cursor.getString(cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER));
            if (phone == null || phone.trim().isEmpty()) {
                Toast.makeText(this, "Wybrany kontakt nie ma numeru telefonu.", Toast.LENGTH_LONG).show();
                return;
            }
            for (Contact contact : _configData.getContactList()) {
                if (contact.getPhone() != null
                        && PhoneNumberUtils.compare(contact.getPhone(), phone)) {
                    Toast.makeText(this, "Ten numer jest już na liście.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            contactAdapter.AddItem(name == null ? "" : name, phone);
            saveContactList();
        }
    }

    private void saveContactList() {
        try {
            _configData.Save();
        } catch (IOException | ClassNotFoundException exception) {
            throw new RuntimeException(exception);
        }
    }

    private boolean checkPermission() {
        return hasPermissions(SMS_PERMISSIONS)
                && hasPermissions(LOCATION_PERMISSIONS)
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(this, ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                switch (currentPermissionRequest) {
                    case SMS:
                        if (hasPermissions(SMS_PERMISSIONS)) {
                            requestPhoneStatePermission();
                        }
                        break;
                    case PHONE_STATE:
                        requestLocationPermissions();
                        break;
                    case LOCATION:
                        if (hasPermissions(LOCATION_PERMISSIONS)) {
                            requestNotificationPermission();
                        }
                        break;
                    case NOTIFICATIONS:
                        requestBackgroundLocationPermission();
                        break;
                    case BACKGROUND_LOCATION:
                        break;
                }
                PermissionInfo(checkPermission());
                if (checkPermission()) {
                    StartLocationService();
                }
            });

    private boolean hasPhoneStatePermission() {
        return !getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                || hasPermissions(PHONE_PERMISSIONS);
    }

    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermission() {
        if (!hasPermissions(SMS_PERMISSIONS)) {
            currentPermissionRequest = PermissionRequest.SMS;
            requestPermissionsLauncher.launch(SMS_PERMISSIONS);
            return;
        }
        requestPhoneStatePermission();
    }

    private void requestPhoneStatePermission() {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                && !hasPermissions(PHONE_PERMISSIONS)) {
            currentPermissionRequest = PermissionRequest.PHONE_STATE;
            requestPermissionsLauncher.launch(PHONE_PERMISSIONS);
            return;
        }
        requestLocationPermissions();
    }

    private void requestLocationPermissions() {
        if (!hasPermissions(LOCATION_PERMISSIONS)) {
            currentPermissionRequest = PermissionRequest.LOCATION;
            requestPermissionsLauncher.launch(LOCATION_PERMISSIONS);
            return;
        }
        requestNotificationPermission();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            currentPermissionRequest = PermissionRequest.NOTIFICATIONS;
            requestPermissionsLauncher.launch(new String[]{POST_NOTIFICATIONS});
            return;
        }
        requestBackgroundLocationPermission();
    }

    private void requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            currentPermissionRequest = PermissionRequest.BACKGROUND_LOCATION;
            requestPermissionsLauncher.launch(new String[]{ACCESS_BACKGROUND_LOCATION});
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionInfo(checkPermission());
    }

    private void PermissionInfo(boolean permissionGranded){
        if (!permissionGranded) {
            TextView view = findViewById(R.id.perissionInfo);
            view.setText("UWAGA !!. Musisz przyznać wszyskie, żądane przez oplikację upawnienia.");
            view.setTextColor(ContextCompat.getColor(this, R.color.safe_error));
        } else {
            TextView view = findViewById(R.id.perissionInfo);
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                    && !hasPhoneStatePermission()) {
                view.setText("Brak dostępu do telefonu: raport BTS i sieci będzie niepełny.");
                view.setTextColor(ContextCompat.getColor(this, R.color.safe_error));
            } else {
                view.setText("Przyznano wymagane uprawnienia.");
                view.setTextColor(ContextCompat.getColor(this, R.color.safe_secondary));
            }
        }

    }
    private void fasterRefreshLocation(){

    }

    private void StartLocationService() {
        if (checkPermission() && !LocationService.isRunning) {
            Intent locationService = new Intent(MainActivity.this, LocationService.class);
            ContextCompat.startForegroundService(this, locationService);
        }
    }

    @Override
    protected void onStart() {
        //Register BroadcastReceiver
        //to receive event from our service

        locationBroadcastReceiver = new LocationBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LocationService.LOCATION_ACTION);
        ContextCompat.registerReceiver(
                this,
                locationBroadcastReceiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);


        //prepare new timertask for gui refreshing
        timerGui = new Timer();
        timerGui.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (LocationService.isRunning){
                            lastLocation=LocationService.currentLocationString;
                            lastLocationAddress=LocationService.currentLocationAddress;
                            lastLocationTime = LocationService.currentLocationTime;
                        }
                        TextView locationTimeoutView = findViewById(R.id.locationTimeout);
                        int elapsedTime = (int) ((System.currentTimeMillis() - lastLocationTime) / 1000);
                        locationTimeoutView.setText(String.format("%d sekund temu", elapsedTime));
                        TextView coordinates = findViewById(R.id.locationInfo);
                        TextView address = findViewById(R.id.loationAddress);
                        //coordinates.setText(lastLocation);
                        //address.setText(lastLocationAddress);

                        //lastLocationTime = LocationService.currentLocationTime;
                        //coordinates.setText(LocationService.currentLocationString);
                        //address.setText(LocationService.currentLocationAddress);

                    }
                });


            }
        }, 0, 30_000);

        super.onStart();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(locationBroadcastReceiver);
        timerGui.cancel();
        //make location slower, for battery saving
        Intent locationIntent = new Intent(this, LocationService.class)
                .setAction(LocationService.ACTION_SET_REFRESH_INTERVAL)
                .putExtra(
                        LocationService.EXTRA_REFRESH_INTERVAL_MILLIS,
                        LocationService.LocationSleepRefreshPeridSeconds * 1000L);
        if (LocationService.isRunning) {
            startService(locationIntent);
        }

        super.onStop();
    }

    class LocationBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case LocationService.LOCATION_ACTION:
                    TextView locView = findViewById(R.id.locationInfo);
                    lastLocation = intent.getStringExtra("location");
                    locView.setText(lastLocation);

                    TextView addressView = findViewById(R.id.loationAddress);
                    lastLocationAddress = intent.getStringExtra("address");
                    addressView.setText(lastLocationAddress);

                    lastLocationTime = intent.getLongExtra("time", System.currentTimeMillis());
                    break;

            }
        }
    }
}