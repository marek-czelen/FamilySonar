package com.familysonar;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.FOREGROUND_SERVICE;
import static android.Manifest.permission.FOREGROUND_SERVICE_LOCATION;
import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.RECEIVE_SMS;
import static android.Manifest.permission.SEND_SMS;
import static android.Manifest.permission.WAKE_LOCK;

import android.app.Instrumentation;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.SmsManager;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
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


public class SettingsActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 200;

    ContactsAdapter contactAdapter = null;
    private ConfigData _configData = null;

        private static final String[] SMS_PERMISSIONS = {
            RECEIVE_SMS,
            SEND_SMS
        };

        private static final String[] LOCATION_PERMISSIONS = {
            ACCESS_COARSE_LOCATION,
            ACCESS_FINE_LOCATION
        };

        private enum PermissionRequest {
            SMS,
            LOCATION,
            NOTIFICATIONS,
            BACKGROUND_LOCATION
        }

        private PermissionRequest currentPermissionRequest;
        private boolean backgroundLocationSettingsOpened = false;
        private boolean smsRoleRequestInProgress = false;

    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null
                                || result.getData().getData() == null) {
                            return;
                        }
                        addContactFromUri(result.getData().getData());
                    });

    private final ActivityResultLauncher<Intent> smsRoleLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        smsRoleRequestInProgress = false;
                        if (isDefaultSmsApp()) {
                            requestPermission();
                        } else {
                            Toast.makeText(
                                    this,
                                    R.string.default_sms_required,
                                    Toast.LENGTH_LONG).show();
                            PermissionInfo(false);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        View settingsRoot = findViewById(R.id.mainLayout);
        final int rootTop = settingsRoot.getPaddingTop();
        final int rootBottom = settingsRoot.getPaddingBottom();
        final int rootLeft = settingsRoot.getPaddingLeft();
        final int rootRight = settingsRoot.getPaddingRight();
        ViewCompat.setOnApplyWindowInsetsListener(settingsRoot, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(
                    rootLeft + bars.left,
                    rootTop + bars.top,
                    rootRight + bars.right,
                    rootBottom + Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(settingsRoot);

        findViewById(R.id.backButton).setOnClickListener(view -> finish());

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

        //check permission
        if (!checkPermission()) {
            PermissionInfo(false);
            requestPermission();
        } else {
            PermissionInfo(true);
        }


        //sprawdzenie warunków działania aplikacji
        BatteryOptymalizationOptions();
        setupEmergencyPassword();

    }

    private void showContactOptions() {
        String[] options = {
                getString(R.string.choose_contact_from_phonebook),
                getString(R.string.enter_phone_number)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_contact)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent pickerIntent = new Intent(
                                Intent.ACTION_PICK,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                        contactPickerLauncher.launch(pickerIntent);
                    } else {
                        showManualContactDialog();
                    }
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private void showManualContactDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_number, null);
        EditText editedField = dialogView.findViewById(R.id.textviewPhoneNumber);
        editedField.setRawInputType(InputType.TYPE_CLASS_PHONE);
        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle(R.string.enter_phone_number)
                .setPositiveButton("Dodaj", (dialog, which) ->
                        addContact("", editedField.getText().toString()))
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private void addContactFromUri(android.net.Uri contactUri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    contactUri,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null,
                    null,
                    null);
            if (cursor == null || !cursor.moveToFirst()) {
                Toast.makeText(this, R.string.contact_not_selected, Toast.LENGTH_LONG).show();
                return;
            }
            int nameIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int phoneIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER);
            if (phoneIndex < 0) {
                Toast.makeText(this, R.string.contact_not_selected, Toast.LENGTH_LONG).show();
                return;
            }
            String name = nameIndex >= 0 ? cursor.getString(nameIndex) : "";
            addContact(name, cursor.getString(phoneIndex));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void addContact(String name, String phoneNumber) {
        String phone = phoneNumber == null ? "" : phoneNumber.trim();
        if (phone.isEmpty()) {
            Toast.makeText(this, R.string.invalid_phone_number, Toast.LENGTH_LONG).show();
            return;
        }
        for (Contact contact : _configData.getContactList()) {
            if (contact.matchesPhone(phone)) {
                Toast.makeText(this, "Ten numer jest już na liście.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        contactAdapter.AddItem(name == null ? "" : name.trim(), phone);
        saveContacts();
    }

    private void saveContacts() {
        try {
            _configData.Save();
            Toast.makeText(this, R.string.contact_saved, Toast.LENGTH_SHORT).show();
        } catch (IOException | ClassNotFoundException exception) {
            Log.e("FamilySonar", "Unable to save contacts", exception);
            Toast.makeText(this, R.string.contact_save_failed, Toast.LENGTH_LONG).show();
        }
    }


    private boolean BatteryOptymalizationOptions() {
        Button batteryButton = findViewById(R.id.batteryOptimalizationButton);
        batteryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_BATTERY_SAVER_SETTINGS);
                startActivity(intent);
            }
        });
        return true;
    }

    private void setupEmergencyPassword() {
        EditText passwordInput = findViewById(R.id.emergencyPasswordInput);
        TextView passwordStatus = findViewById(R.id.emergencyPasswordStatus);
        updateEmergencyPasswordStatus(passwordStatus);

        findViewById(R.id.saveEmergencyPasswordButton).setOnClickListener(view -> {
            String password = passwordInput.getText() == null
                    ? ""
                    : passwordInput.getText().toString();
            if (!EmergencyPasswordStore.isValid(password)) {
                passwordInput.setError("Hasło musi mieć 6-64 znaków i nie może zawierać spacji.");
                return;
            }
            EmergencyPasswordStore.save(this, password);
            passwordInput.setText("");
            passwordInput.setError(null);
            updateEmergencyPasswordStatus(passwordStatus);
            Toast.makeText(this, "Hasło awaryjne zostało zapisane.", Toast.LENGTH_LONG).show();
        });

        findViewById(R.id.clearEmergencyPasswordButton).setOnClickListener(view -> {
            EmergencyPasswordStore.clear(this);
            passwordInput.setText("");
            passwordInput.setError(null);
            updateEmergencyPasswordStatus(passwordStatus);
            Toast.makeText(this, "Hasło awaryjne zostało usunięte.", Toast.LENGTH_LONG).show();
        });
    }

    private void updateEmergencyPasswordStatus(TextView statusView) {
        if (EmergencyPasswordStore.isConfigured(this)) {
            statusView.setText("Hasło jest aktywne");
            statusView.setTextColor(ContextCompat.getColor(this, R.color.safe_secondary));
        } else {
            statusView.setText("Hasło nie jest ustawione");
            statusView.setTextColor(ContextCompat.getColor(this, R.color.safe_muted));
        }
    }

    private boolean checkPermission() {
        return isDefaultSmsApp()
                && hasPermissions(SMS_PERMISSIONS)
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
                        requestLocationPermissions();
                        break;
                    case LOCATION:
                        requestNotificationPermission();
                        break;
                    case NOTIFICATIONS:
                        requestBackgroundLocationPermission();
                        break;
                    case BACKGROUND_LOCATION:
                        break;
                }
                PermissionInfo(checkPermission());
            });

    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermission() {
        if (!isDefaultSmsApp()) {
            requestSmsRole();
            return;
        }
        if (!hasPermissions(SMS_PERMISSIONS)) {
            currentPermissionRequest = PermissionRequest.SMS;
            requestPermissionsLauncher.launch(SMS_PERMISSIONS);
            return;
        }
        requestLocationPermissions();
    }

    private boolean isDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            return roleManager != null
                    && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)
                    && roleManager.isRoleHeld(RoleManager.ROLE_SMS);
        }
        return getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(this));
    }

    private void requestSmsRole() {
        if (smsRoleRequestInProgress) {
            return;
        }

        Intent roleIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                showDefaultSmsSettings();
                return;
            }
            roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
        } else {
            roleIntent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    .putExtra(
                            Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                            getPackageName());
        }

        smsRoleRequestInProgress = true;
        try {
            smsRoleLauncher.launch(roleIntent);
        } catch (android.content.ActivityNotFoundException exception) {
            smsRoleRequestInProgress = false;
            showDefaultSmsSettings();
        }
    }

    private void showDefaultSmsSettings() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.default_sms_title)
                .setMessage(R.string.default_sms_required)
                .setPositiveButton(R.string.open_default_sms_settings, (dialog, which) -> {
                    Intent settingsIntent = new Intent(
                            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
                    startActivity(settingsIntent);
                })
                .setNegativeButton("Anuluj", null)
                .show();
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
            new AlertDialog.Builder(this)
                    .setTitle("Lokalizacja w tle")
                    .setMessage("Aby odpowiadać na SMS-y po zablokowaniu ekranu, wybierz w ustawieniach aplikacji lokalizację „Zawsze zezwalaj”.")
                    .setPositiveButton("Otwórz ustawienia", (dialog, which) -> {
                        backgroundLocationSettingsOpened = true;
                        Intent settingsIntent = new Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(settingsIntent);
                    })
                    .setNegativeButton("Anuluj", null)
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PermissionInfo(checkPermission());
        if (backgroundLocationSettingsOpened) {
            backgroundLocationSettingsOpened = false;
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
            if (!isDefaultSmsApp()) {
                view.setText(R.string.default_sms_required);
            } else {
                view.setText("UWAGA !!. Musisz przyznać wszyskie, żądane przez oplikację upawnienia.");
            }
            view.setTextColor(ContextCompat.getColor(this, R.color.safe_error));
        } else {
            TextView view = findViewById(R.id.perissionInfo);
            view.setText("Przyznano wszystkie żądane uprawnienia.");
            view.setTextColor(ContextCompat.getColor(this, R.color.safe_secondary));
        }

    }
}