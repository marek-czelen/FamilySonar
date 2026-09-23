package com.familysonar;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.content.ContextCompat;

class NotificationHelper {
    private static final String CHANNEL_ID = "SafeMessages.Messages";

    private NotificationHelper() {
    }

    static void showIncomingMessage(Context context, String address, String body, boolean mms) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        createChannel(context);
        String sender = address == null || address.trim().isEmpty() ? "Unknown" : address;
        String displayName = ContactNameResolver.resolve(context, sender);
        Intent intent = ConversationActivity.createIntent(context, sender)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                sender.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Person user = new Person.Builder().setName(context.getString(R.string.app_name)).build();
        Person senderPerson = new Person.Builder().setName(displayName).build();
        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(user)
                .setConversationTitle(displayName)
                .addMessage(
                        body == null || body.trim().isEmpty()
                                ? context.getString(mms ? R.string.incoming_mms : R.string.app_name)
                                : body,
                        System.currentTimeMillis(),
                        senderPerson);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_message)
                .setContentTitle(displayName)
                .setContentText(body)
                .setStyle(style)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(Math.abs(sender.hashCode()), builder.build());
        }
    }

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.messages_channel),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(context.getString(R.string.messages_channel));
            manager.createNotificationChannel(channel);
        }
    }
}
