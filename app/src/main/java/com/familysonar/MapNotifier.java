package com.familysonar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/** Posts a notification when a technical location SMS is received, opening the map. */
final class MapNotifier {

    private static final String CHANNEL_ID = "FindMe.LocationReceived";
    private static final int NOTIFICATION_BASE_ID = 4200;

    private MapNotifier() {
    }

    static void notifyLocationReceived(Context context, String from, String body) {
        createChannel(context);

        String displayName = ContactNameResolver.resolve(context, from);

        Intent mapIntent = new Intent(context, MapActivity.class);
        mapIntent.putExtra(MapActivity.EXTRA_FROM, from);
        mapIntent.putExtra(MapActivity.EXTRA_BODY, body);
        mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode = Math.abs(LocationInboxStore.keyFor(from).hashCode());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, requestCode, mapIntent, flags);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_location)
                .setContentTitle(context.getString(R.string.location_received_title))
                .setContentText(context.getString(R.string.location_received_text, displayName))
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_BASE_ID + requestCode % 1000, notification);
        }

        // Bring the map to the foreground directly when the app is visible; when the
        // app is in the background this is ignored and the (full-screen) notification
        // handles the launch instead.
        try {
            context.startActivity(mapIntent);
        } catch (Exception ignored) {
            // Background activity starts are restricted; the notification remains.
        }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.location_received_channel),
                NotificationManager.IMPORTANCE_HIGH);
        manager.createNotificationChannel(channel);
    }
}
