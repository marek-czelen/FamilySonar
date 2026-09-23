package com.familysonar;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

public class RespondViaMessageService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String body = intent.getStringExtra(Intent.EXTRA_TEXT);
            String address = extractAddress(intent.getData());
            if (!TextUtils.isEmpty(address) && !TextUtils.isEmpty(body)) {
                try {
                    new SmsRepository(this).sendTextMessage(address, body);
                } catch (RuntimeException exception) {
                    Log.e("SafeMessagesReply", "Unable to respond via SMS", exception);
                }
            }
        }
        stopSelfResult(startId);
        return START_NOT_STICKY;
    }

    private String extractAddress(Uri data) {
        if (data == null) {
            return "";
        }
        String value = data.getSchemeSpecificPart();
        if (value != null && value.startsWith("//")) {
            value = value.substring(2);
        }
        return value == null ? "" : Uri.decode(value).trim();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
