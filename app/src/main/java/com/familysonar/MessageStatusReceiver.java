package com.familysonar;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class MessageStatusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String uri = intent.getStringExtra(SmsRepository.EXTRA_MESSAGE_URI);
        Uri messageUri = uri == null ? null : Uri.parse(uri);
        boolean success = getResultCode() == Activity.RESULT_OK;

        if (SmsRepository.ACTION_MMS_SENT.equals(intent.getAction())) {
            if (!success) {
                Log.w("SafeMessagesStatus", "MMS send failure result=" + getResultCode());
            }
            String pduPath = intent.getStringExtra(SmsRepository.EXTRA_PDU_FILE);
            new SmsRepository(context).completeMmsSend(messageUri, success, pduPath);
            return;
        }

        boolean delivered = SmsRepository.ACTION_SMS_DELIVERED.equals(intent.getAction());
        if (!success) {
            Log.w("SafeMessagesStatus", "SMS status failure result=" + getResultCode()
                    + " action=" + intent.getAction());
        }
        new SmsRepository(context).updateMessageStatus(messageUri, delivered, success);
    }
}
