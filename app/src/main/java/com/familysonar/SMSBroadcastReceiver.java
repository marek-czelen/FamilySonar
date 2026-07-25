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
            ConfigData config = new ConfigData(context);
            try {
                config.Load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            for (int i = 0; i < smsArray.length; i++) {
                SmsMessage sms = SmsMessage.createFromPdu((byte[]) smsArray[i]);
                if (sms.getMessageBody().compareTo("?loc?")!=0) continue;
                for (Contact c : config.getContactList()) {
                    String from = sms.getOriginatingAddress();
                    if (sms.getOriginatingAddress().compareTo(c.getPhone())==0) {
                        //scheduleJob(context, sms);
                        //AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
                        //Intent alarmIntent = new Intent(context,AlarmReceiverClass.class);
                        //alarmIntent.putExtra("from",sms.getOriginatingAddress());
                        //PendingIntent pendingIntent = PendingIntent.getBroadcast(context,0,alarmIntent,PendingIntent.FLAG_IMMUTABLE);
                        //long timeInMilis = Calendar.getInstance().getTimeInMillis()+5000;
                        //alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,timeInMilis,0,pendingIntent);
                        //Log.d("FalimySonarApp", "onAlamschedule");
                        Intent locationIntent = new Intent(context, LocationService.class);
                        locationIntent.setAction(LocationService.LOCATION_CHANGE_REFRESH);
                        locationIntent.putExtra("refresh_s", LocationService.LocationFastRefreshPeridSeconds);
                        locationIntent.putExtra("refreshCounts", 1);
                        locationIntent.putExtra("sendLocationFastEnd",true);
                        locationIntent.putExtra("from",from);
                        ContextCompat.startForegroundService(context, locationIntent);
                    }
                }

            }
        }

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
