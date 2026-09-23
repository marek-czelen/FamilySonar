package com.familysonar;

import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class SmsRepository {
    static final String ACTION_STATUS_CHANGED = "com.familysonar.action.MESSAGE_STATUS_CHANGED";
    static final String ACTION_SMS_SENT = "com.familysonar.action.SMS_SENT";
    static final String ACTION_SMS_DELIVERED = "com.familysonar.action.SMS_DELIVERED";
    static final String EXTRA_MESSAGE_URI = "message_uri";
    static final String EXTRA_ADDRESS = "address";
    static final String EXTRA_BODY = "body";

    static final int TYPE_INBOX = 1;
    static final int TYPE_SENT = 2;
    static final int TYPE_DRAFT = 3;
    static final int TYPE_OUTBOX = 4;
    static final int TYPE_FAILED = 5;
    static final int STATUS_NONE = -1;
    static final int STATUS_COMPLETE = 0;
    static final int STATUS_PENDING = 32;
    static final int STATUS_FAILED = 64;

    static final String ACTION_MMS_SENT = "com.familysonar.action.MMS_SENT";
    static final String EXTRA_PDU_FILE = "pdu_file";
    static final String FILE_PROVIDER_AUTHORITY = "com.familysonar.fileprovider";

    private static final String TAG = "SafeMessagesSms";
    private static final ExecutorService SEND_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;

    /**
     * Callback for background send operations. Methods are always invoked on the main thread.
     */
    interface SendCallback {
        void onSuccess();

        void onError(int messageRes);
    }

    SmsRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    List<ConversationPreview> loadConversationPreviews() {
        Map<String, ConversationAccumulator> grouped = new LinkedHashMap<>();
        for (ChatMessage message : loadAllSmsMessages(null, 500)) {
            addToGrouped(grouped, message);
        }
        for (ChatMessage message : MmsSupport.loadRecentMms(context)) {
            addToGrouped(grouped, message);
        }
        ArrayList<ConversationPreview> previews = new ArrayList<>();
        for (ConversationAccumulator value : grouped.values()) {
            previews.add(value.toPreview());
        }
        Collections.sort(previews, (left, right) -> Long.compare(right.dateMillis, left.dateMillis));
        return previews;
    }

    List<ChatMessage> loadMessagesForAddress(String address) {
        ArrayList<ChatMessage> messages = new ArrayList<>(loadAllSmsMessages(address, 1000));
        for (ChatMessage mms : MmsSupport.loadRecentMms(context)) {
            if (numbersMatch(address, mms.address)) {
                messages.add(mms);
            }
        }
        Collections.sort(messages, Comparator.comparingLong(message -> message.dateMillis));
        return messages;
    }

    /**
     * Sends an SMS or MMS on a background thread. The callback runs on the main thread.
     */
    void sendMessageAsync(String address, String body, Uri mediaUri, SendCallback callback) {
        SEND_EXECUTOR.execute(() -> {
            try {
                if (mediaUri == null) {
                    sendTextMessage(address, body);
                } else {
                    sendMultimediaMessage(address, body, mediaUri);
                }
                postSuccess(callback);
            } catch (SendException exception) {
                Log.w(TAG, "Send failed", exception);
                postError(callback, exception.messageRes);
            } catch (SecurityException exception) {
                Log.w(TAG, "Send failed - permission", exception);
                postError(callback, R.string.default_sms_permission_missing);
            } catch (IllegalArgumentException exception) {
                Log.w(TAG, "Send failed - argument", exception);
                postError(callback, R.string.address_required);
            } catch (RuntimeException exception) {
                Log.w(TAG, "Send failed - runtime", exception);
                postError(callback, R.string.mms_send_failed);
            }
        });
    }

    private void postSuccess(SendCallback callback) {
        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    private void postError(SendCallback callback, int messageRes) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(messageRes));
        }
    }

    Uri sendTextMessage(String address, String body) {
        String destination = address == null ? "" : address.trim();
        String message = body == null ? "" : body.trim();
        if (destination.isEmpty() || message.isEmpty()) {
            throw new IllegalArgumentException("Address and body are required");
        }

        Uri messageUri = insertOutgoingMessage(destination, message);
        ArrayList<String> parts = SmsManager.getDefault().divideMessage(message);
        ArrayList<PendingIntent> sentIntents = new ArrayList<>();
        ArrayList<PendingIntent> deliveryIntents = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            sentIntents.add(createStatusIntent(ACTION_SMS_SENT, messageUri, destination, message, i));
            deliveryIntents.add(createStatusIntent(ACTION_SMS_DELIVERED, messageUri, destination, message, i + 1000));
        }
        SmsManager.getDefault().sendMultipartTextMessage(
                destination,
                null,
                parts,
                sentIntents,
                deliveryIntents);
        return messageUri;
    }

    Uri sendMultimediaMessage(String address, String body, Uri mediaUri) {
        if (TextUtils.isEmpty(address) || mediaUri == null) {
            throw new IllegalArgumentException("MMS address and media are required");
        }

        String sourceType = ImageScaler.guessContentType(context, mediaUri);
        if (TextUtils.isEmpty(sourceType) || !sourceType.startsWith("image/")) {
            throw new SendException(R.string.mms_image_only);
        }

        ImageScaler.Result scaled;
        try {
            scaled = ImageScaler.scale(context, mediaUri, sourceType);
        } catch (ImageScaler.TooLargeException exception) {
            throw new SendException(R.string.mms_attachment_too_large);
        } catch (IOException exception) {
            throw new SendException(R.string.mms_attachment_read_failed);
        }

        Uri mmsUri = storeOutgoingMms(address, body, scaled);
        String fileName = "attachment." + scaled.extension;

        File pduFile;
        try {
            byte[] pdu = MmsPduBuilder.build(
                    address, body, scaled.data, scaled.contentType, fileName);
            pduFile = writePduFile(pdu);
        } catch (IOException exception) {
            throw new SendException(R.string.mms_send_failed);
        }

        Uri fileUri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, pduFile);
        try {
            context.grantUriPermission(
                    "com.android.phone", fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (RuntimeException ignored) {
            // Best effort; some devices do not expose com.android.phone by that name.
        }

        try {
            SmsManager.getDefault().sendMultimediaMessage(
                    context,
                    fileUri,
                    null,
                    null,
                    createMmsSentIntent(mmsUri, pduFile));
        } catch (RuntimeException exception) {
            //noinspection ResultOfMethodCallIgnored
            pduFile.delete();
            throw new SendException(R.string.mms_send_failed);
        }
        return mmsUri;
    }

    private Uri storeOutgoingMms(String address, String body, ImageScaler.Result scaled) {
        ContentValues messageValues = new ContentValues();
        messageValues.put("msg_box", 2);
        messageValues.put("m_type", 128);
        messageValues.put("v", 18);
        messageValues.put("date", System.currentTimeMillis() / 1000L);
        messageValues.put("read", 1);
        messageValues.put("seen", 1);
        messageValues.put("ct_t", "application/vnd.wap.multipart.related");
        messageValues.put("m_cls", "personal");
        messageValues.put("pri", 129);
        messageValues.put("tr_id", "safe-" + System.currentTimeMillis());
        Uri mmsUri = context.getContentResolver().insert(
                Uri.parse("content://mms/sent"),
                messageValues);
        if (mmsUri == null) {
            throw new SendException(R.string.mms_send_failed);
        }

        long messageId = ContentUris.parseId(mmsUri);
        ContentValues addressValues = new ContentValues();
        addressValues.put("address", address);
        addressValues.put("type", 151);
        addressValues.put("charset", 106);
        context.getContentResolver().insert(
                Uri.parse("content://mms/" + messageId + "/addr"),
                addressValues);

        if (!TextUtils.isEmpty(body)) {
            ContentValues textPart = new ContentValues();
            textPart.put("ct", "text/plain");
            textPart.put("chset", 106);
            textPart.put("name", "text.txt");
            textPart.put("cl", "text.txt");
            textPart.put("text", body);
            context.getContentResolver().insert(
                    Uri.parse("content://mms/" + messageId + "/part"),
                    textPart);
        }

        ContentValues mediaPart = new ContentValues();
        mediaPart.put("ct", scaled.contentType);
        mediaPart.put("name", "attachment." + scaled.extension);
        mediaPart.put("fn", "attachment." + scaled.extension);
        mediaPart.put("cid", "<image>");
        mediaPart.put("cl", "attachment." + scaled.extension);
        Uri partUri = context.getContentResolver().insert(
                Uri.parse("content://mms/" + messageId + "/part"),
                mediaPart);
        if (partUri == null) {
            throw new SendException(R.string.mms_send_failed);
        }

        try (OutputStream output = context.getContentResolver().openOutputStream(partUri)) {
            if (output == null) {
                throw new IOException("Unable to open MMS attachment part");
            }
            output.write(scaled.data);
        } catch (IOException exception) {
            throw new SendException(R.string.mms_send_failed);
        }
        return mmsUri;
    }

    private File writePduFile(byte[] pdu) throws IOException {
        File dir = new File(context.getCacheDir(), "mms");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create MMS cache directory");
        }
        File file = new File(dir, "send." + System.currentTimeMillis() + ".pdu");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(pdu);
        }
        return file;
    }

    private PendingIntent createMmsSentIntent(Uri messageUri, File pduFile) {
        Intent intent = new Intent(context, MessageStatusReceiver.class)
                .setAction(ACTION_MMS_SENT)
                .putExtra(EXTRA_MESSAGE_URI, messageUri.toString())
                .putExtra(EXTRA_PDU_FILE, pduFile.getAbsolutePath());
        int requestCode = Math.abs((messageUri.toString() + ACTION_MMS_SENT).hashCode());
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Marks an outgoing MMS row as sent or failed and removes the temporary PDU file.
     */
    void completeMmsSend(Uri messageUri, boolean success, String pduPath) {
        if (!TextUtils.isEmpty(pduPath)) {
            //noinspection ResultOfMethodCallIgnored
            new File(pduPath).delete();
        }
        if (messageUri != null) {
            ContentValues values = new ContentValues();
            values.put("msg_box", success ? 2 : 5);
            try {
                context.getContentResolver().update(messageUri, values, null, null);
            } catch (SecurityException | IllegalArgumentException exception) {
                Log.w(TAG, "Unable to update MMS status", exception);
            }
        }
        context.sendBroadcast(new Intent(ACTION_STATUS_CHANGED).setPackage(context.getPackageName()));
    }

    /**
     * Signals a user-facing send failure with a localized message resource.
     */
    static final class SendException extends RuntimeException {
        final int messageRes;

        SendException(int messageRes) {
            this.messageRes = messageRes;
        }
    }

    /**
     * Persists an incoming SMS into the system provider so it shows up in the conversation.
     * The default SMS app is responsible for writing received messages to the provider.
     */
    Uri storeIncomingSms(String address, String body, long dateMillis) {
        if (TextUtils.isEmpty(address)) {
            return null;
        }
        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.ADDRESS, address);
        values.put(Telephony.Sms.BODY, body == null ? "" : body);
        values.put(Telephony.Sms.DATE, dateMillis > 0 ? dateMillis : System.currentTimeMillis());
        values.put(Telephony.Sms.READ, 0);
        values.put(Telephony.Sms.SEEN, 0);
        values.put(Telephony.Sms.TYPE, TYPE_INBOX);
        try {
            Uri inserted = context.getContentResolver()
                    .insert(Telephony.Sms.Inbox.CONTENT_URI, values);
            context.sendBroadcast(new Intent(ACTION_STATUS_CHANGED)
                    .setPackage(context.getPackageName()));
            return inserted;
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.w(TAG, "Unable to store incoming SMS", exception);
            return null;
        }
    }

    /** Deletes every SMS and MMS row belonging to the given conversation address. */
    void deleteConversation(String address) {
        if (TextUtils.isEmpty(address)) {
            return;
        }
        try {
            context.getContentResolver().delete(
                    Telephony.Sms.CONTENT_URI,
                    Telephony.Sms.ADDRESS + "=?",
                    new String[]{address});
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.w(TAG, "Unable to delete SMS conversation", exception);
        }
        context.sendBroadcast(new Intent(ACTION_STATUS_CHANGED)
                .setPackage(context.getPackageName()));
    }

    void markConversationRead(String address) {
        if (TextUtils.isEmpty(address)) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.READ, 1);
        values.put(Telephony.Sms.SEEN, 1);
        try {
            context.getContentResolver().update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    Telephony.Sms.ADDRESS + "=? AND " + Telephony.Sms.READ + "=0",
                    new String[]{address});
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.w(TAG, "Unable to mark SMS as read", exception);
        }
    }

    void updateMessageStatus(Uri messageUri, boolean deliveredAction, boolean success) {
        if (messageUri == null) {
            return;
        }
        ContentValues values = new ContentValues();
        if (deliveredAction) {
            values.put(Telephony.Sms.STATUS, success ? STATUS_COMPLETE : STATUS_FAILED);
        } else if (success) {
            values.put(Telephony.Sms.TYPE, TYPE_SENT);
            values.put(Telephony.Sms.STATUS, STATUS_NONE);
        } else {
            values.put(Telephony.Sms.TYPE, TYPE_FAILED);
            values.put(Telephony.Sms.STATUS, STATUS_FAILED);
        }
        try {
            context.getContentResolver().update(messageUri, values, null, null);
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.w(TAG, "Unable to update SMS status", exception);
        }
        context.sendBroadcast(new Intent(ACTION_STATUS_CHANGED).setPackage(context.getPackageName()));
    }

    static String formatDate(Context context, long millis) {
        if (millis <= 0) {
            return "";
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
                .format(millis);
    }

    static boolean numbersMatch(String left, String right) {
        if (TextUtils.isEmpty(left) || TextUtils.isEmpty(right)) {
            return false;
        }
        return PhoneNumberUtils.compare(left, right) || left.equals(right);
    }

    private void addToGrouped(Map<String, ConversationAccumulator> grouped, ChatMessage message) {
        String key = TextUtils.isEmpty(message.address) ? "Unknown" : message.address;
        ConversationAccumulator accumulator = grouped.get(key);
        if (accumulator == null) {
            accumulator = new ConversationAccumulator(key);
            grouped.put(key, accumulator);
        }
        accumulator.accept(message);
    }

    private List<ChatMessage> loadAllSmsMessages(String addressFilter, int limit) {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        String[] projection = {
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ,
                Telephony.Sms.TYPE,
                Telephony.Sms.STATUS
        };
        String selection = null;
        String[] selectionArgs = null;
        if (!TextUtils.isEmpty(addressFilter)) {
            selection = Telephony.Sms.ADDRESS + "=?";
            selectionArgs = new String[]{addressFilter};
        }
        try (Cursor cursor = context.getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                Telephony.Sms.DATE + " DESC LIMIT " + limit)) {
            if (cursor == null) {
                return messages;
            }
            int idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID);
            int addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);
            int bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY);
            int dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE);
            int readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ);
            int typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE);
            int statusIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIndex);
                String address = cursor.getString(addressIndex);
                int type = cursor.getInt(typeIndex);
                boolean outgoing = type == TYPE_SENT || type == TYPE_OUTBOX
                        || type == TYPE_FAILED || type == TYPE_DRAFT;
                messages.add(new ChatMessage(
                        ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id),
                        address,
                        cursor.getString(bodyIndex),
                        cursor.getLong(dateIndex),
                        type,
                        cursor.getInt(statusIndex),
                        cursor.getInt(readIndex) == 1,
                        outgoing,
                        false));
            }
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.w(TAG, "Unable to read SMS provider", exception);
        }
        return messages;
    }

    private Uri insertOutgoingMessage(String address, String body) {
        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.ADDRESS, address);
        values.put(Telephony.Sms.BODY, body);
        values.put(Telephony.Sms.DATE, System.currentTimeMillis());
        values.put(Telephony.Sms.READ, 1);
        values.put(Telephony.Sms.SEEN, 1);
        values.put(Telephony.Sms.TYPE, TYPE_OUTBOX);
        values.put(Telephony.Sms.STATUS, STATUS_PENDING);
        Uri inserted = context.getContentResolver().insert(Telephony.Sms.CONTENT_URI, values);
        if (inserted == null) {
            throw new IllegalStateException("SMS provider did not return a row URI");
        }
        return inserted;
    }

    private PendingIntent createStatusIntent(
            String action,
            Uri messageUri,
            String address,
            String body,
            int requestOffset) {
        Intent intent = new Intent(context, MessageStatusReceiver.class)
                .setAction(action)
                .putExtra(EXTRA_MESSAGE_URI, messageUri.toString())
                .putExtra(EXTRA_ADDRESS, address)
                .putExtra(EXTRA_BODY, body);
        int requestCode = Math.abs((messageUri.toString() + action).hashCode()) + requestOffset;
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static class ConversationAccumulator {
        private final String address;
        private String preview = "";
        private long dateMillis = 0L;
        private int unreadCount = 0;
        private boolean latestMms = false;

        ConversationAccumulator(String address) {
            this.address = address;
        }

        void accept(ChatMessage message) {
            if (message.dateMillis >= dateMillis) {
                preview = message.mms ? "MMS · " + safe(message.body) : safe(message.body);
                dateMillis = message.dateMillis;
                latestMms = message.mms;
            }
            if (!message.outgoing && !message.read) {
                unreadCount++;
            }
        }

        ConversationPreview toPreview() {
            return new ConversationPreview(address, address, preview, dateMillis, unreadCount, latestMms);
        }

        private String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
