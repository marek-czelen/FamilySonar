package com.familysonar;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;

public class MmsBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "FamilySonarMms";
    static final String ACTION_MMS_DOWNLOAD_COMPLETE =
            "com.familysonar.action.MMS_DOWNLOAD_COMPLETE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(intent.getAction())) {
            downloadMms(context, intent);
        } else if (ACTION_MMS_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            handleDownloadResult(context);
        }
    }

    private void downloadMms(Context context, Intent intent) {
        Uri contentLocation = intent.getData();
        Log.i(TAG, "Received WAP push: data=" + contentLocation
                + ", type=" + intent.getType()
                + ", extras=" + describeExtras(intent.getExtras()));
        if (contentLocation == null) {
            String extractedLocation = extractContentLocation(intent.getExtras());
            if (extractedLocation != null) {
                contentLocation = Uri.parse(extractedLocation);
            }
        }
        if (contentLocation == null) {
            Log.e(TAG, "MMS delivery did not contain a content location");
            showDownloadFailure(context, -1);
            return;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) System.currentTimeMillis(),
                new Intent(context, MmsBroadcastReceiver.class)
                        .setAction(ACTION_MMS_DOWNLOAD_COMPLETE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            SmsManager.getDefault().downloadMultimediaMessage(
                    context,
                    contentLocation.toString(),
                    Telephony.Mms.Inbox.CONTENT_URI,
                    new Bundle(),
                    pendingIntent);
        } catch (IllegalArgumentException | SecurityException exception) {
            Log.e(TAG, "Unable to start MMS download", exception);
            showDownloadFailure(context, -2);
        }
    }

    private String describeExtras(Bundle extras) {
        if (extras == null) {
            return "none";
        }
        StringBuilder result = new StringBuilder();
        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            result.append(key).append('=');
            if (value instanceof byte[]) {
                result.append("byte[").append(((byte[]) value).length).append(']');
            } else {
                result.append(value == null ? "null" : value.getClass().getSimpleName());
            }
            result.append(' ');
        }
        return result.toString();
    }

    /**
     * Some Android telephony implementations put WAP headers in extras
     * instead of setting Intent.data.
     */
    private String extractContentLocation(Bundle extras) {
        if (extras == null) {
            return null;
        }
        String[] stringKeys = {
                "contentLocation",
                "content-location",
                "content_location",
                "location",
                "mmsLocation"
        };
        for (String key : stringKeys) {
            String value = extras.getString(key);
            if (isHttpUrl(value)) {
                return value;
            }
        }
        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            if (value instanceof byte[]) {
                String candidate = findUrlInBytes((byte[]) value);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private String findUrlInBytes(byte[] bytes) {
        StringBuilder current = new StringBuilder();
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            if (unsigned >= 32 && unsigned <= 126) {
                current.append((char) unsigned);
                continue;
            }
            String candidate = current.toString();
            if (isHttpUrl(candidate)) {
                return candidate;
            }
            current.setLength(0);
        }
        return isHttpUrl(current.toString()) ? current.toString() : null;
    }

    private boolean isHttpUrl(String value) {
        return value != null
                && (value.startsWith("http://") || value.startsWith("https://"))
                && value.length() > 12;
    }

    private void handleDownloadResult(Context context) {
        int resultCode = getResultCode();
        Log.i(TAG, "MMS download completed with result " + resultCode);
        if (resultCode != Activity.RESULT_OK) {
            showDownloadFailure(context, resultCode);
            return;
        }

        java.util.List<ChatMessage> messages = MmsSupport.loadRecentMms(context);
        if (!messages.isEmpty()) {
            ChatMessage message = messages.get(0);
            NotificationHelper.showIncomingMessage(
                    context,
                    message.address,
                    message.body,
                    true);
        }
        context.sendBroadcast(new Intent(SmsRepository.ACTION_STATUS_CHANGED)
                .setPackage(context.getPackageName()));
    }

    private void showDownloadFailure(Context context, int resultCode) {
        String reason;
        switch (resultCode) {
            case SmsManager.RESULT_ERROR_LIMIT_EXCEEDED:
                reason = context.getString(R.string.mms_download_size_limit);
                break;
            case SmsManager.RESULT_NETWORK_ERROR:
            case SmsManager.RESULT_NETWORK_REJECT:
            case SmsManager.RESULT_RADIO_NOT_AVAILABLE:
                reason = context.getString(R.string.mms_download_network_failed);
                break;
            case SmsManager.RESULT_REQUEST_NOT_SUPPORTED:
                reason = context.getString(R.string.mms_download_not_supported);
                break;
            case -1:
                reason = context.getString(R.string.mms_download_missing_location);
                break;
            default:
                reason = context.getString(R.string.mms_download_failed);
                break;
        }
        NotificationHelper.showIncomingMessage(
                context,
                context.getString(R.string.incoming_mms),
                reason + " (kod " + resultCode + ")",
                true);
    }
}
