package com.familysonar;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.telephony.TelephonyManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Builds a human readable device status report for the "?status?" SMS command. */
class DeviceStatusHelper {

    private DeviceStatusHelper() {
    }

    static String buildStatusMessage(Context context) {
        StringBuilder builder = new StringBuilder();
        builder.append("FindMe\n");
        builder.append("Czas: ")
                .append(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                        .format(new Date()))
                .append('\n');
        builder.append("Operator: ").append(operatorName(context)).append('\n');
        builder.append("Bateria: ").append(batteryStatus(context));
        return builder.toString();
    }

    private static String operatorName(Context context) {
        try {
            TelephonyManager telephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                String operator = telephonyManager.getNetworkOperatorName();
                if (operator != null && !operator.trim().isEmpty()) {
                    return operator.trim();
                }
                String simOperator = telephonyManager.getSimOperatorName();
                if (simOperator != null && !simOperator.trim().isEmpty()) {
                    return simOperator.trim();
                }
            }
        } catch (SecurityException exception) {
            return "brak danych";
        }
        return "nieznany";
    }

    private static String batteryStatus(Context context) {
        BatteryManager batteryManager =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int level = -1;
        if (batteryManager != null) {
            level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }

        boolean charging = false;
        Intent batteryIntent = context.registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            if (level < 0) {
                int rawLevel = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (rawLevel >= 0 && scale > 0) {
                    level = Math.round(rawLevel * 100f / scale);
                }
            }
        }

        String levelText = level >= 0 ? level + "%" : "brak danych";
        return levelText + (charging ? " (ładowanie)" : "");
    }
}
