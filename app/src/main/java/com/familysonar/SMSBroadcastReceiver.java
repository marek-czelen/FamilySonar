package com.familysonar;

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.JOB_SCHEDULER_SERVICE;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PersistableBundle;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.Calendar;

public class SMSBroadcastReceiver extends BroadcastReceiver {


    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("FalimySonarApp", "OnBroadcastReceive");
        if (intent.hasExtra("pdus")) {
            Object[] smsArray = (Object[]) intent.getExtras().get("pdus");
            String format = intent.getStringExtra("format");
            ConfigData config = new ConfigData(context);
            try {
                config.Load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            StringBuilder messageBody = new StringBuilder();
            String from = null;
            for (Object pdu : smsArray) {
                SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu, format);
                if (sms == null) {
                    continue;
                }
                if (from == null) {
                    from = sms.getOriginatingAddress();
                }
                if (sms.getMessageBody() != null) {
                    messageBody.append(sms.getMessageBody());
                }
            }
            if (from == null) {
                return;
            }

            String command = messageBody.toString();
            boolean emergencyRequest = command.startsWith("?loc?")
                    && EmergencyPasswordStore.matches(context, command.substring(5));
            boolean trustedRequest = "?loc?".equals(command)
                    && isTrustedContact(from, config);
            if (emergencyRequest || trustedRequest) {
                Intent locationIntent = new Intent(context, LocationService.class);
                locationIntent.setAction(LocationService.ACTION_REQUEST_LOCATION_FOR_SMS);
                locationIntent.putExtra(LocationService.EXTRA_LOCATION_DESTINATION, from);
                ContextCompat.startForegroundService(context, locationIntent);
            }
        }

    }

    private boolean isTrustedContact(String from, ConfigData config) {
        for (Contact contact : config.getContactList()) {
            if (PhoneNumberUtils.compare(from, contact.getPhone())) {
                return true;
            }
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void scheduleJob(Context context, SmsMessage sms) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
        ComponentName componentName = new ComponentName(context, JobService.class);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("message", sms.getMessageBody());
        bundle.putString("from", sms.getOriginatingAddress());


        JobInfo jobInfo = new JobInfo.Builder(1, componentName)
                .setExtras(bundle)
                .setMinimumLatency(0).build();
        jobScheduler.schedule(jobInfo);
    }
}
