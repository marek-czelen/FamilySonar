package com.familysonar;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

class MmsSupport {
    private static final int MMS_FROM = 137;
    private static final int MMS_TO = 151;
    private static final int MMS_SENT = 2;

    private MmsSupport() {
    }

    static List<ChatMessage> loadRecentMms(Context context) {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        String[] projection = {
                Telephony.Mms._ID,
                Telephony.Mms.DATE,
                Telephony.Mms.READ,
                Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.SUBJECT
        };
        try (Cursor cursor = context.getContentResolver().query(
                Telephony.Mms.CONTENT_URI,
                projection,
                null,
                null,
                Telephony.Mms.DATE + " DESC LIMIT 50")) {
            if (cursor == null) {
                return messages;
            }
            int idIndex = cursor.getColumnIndexOrThrow(Telephony.Mms._ID);
            int dateIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE);
            int readIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.READ);
            int boxIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX);
            int subjectIndex = cursor.getColumnIndex(Telephony.Mms.SUBJECT);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIndex);
                int box = cursor.getInt(boxIndex);
                boolean outgoing = box == MMS_SENT;
                String address = findAddress(context, id, outgoing ? MMS_TO : MMS_FROM);
                if (TextUtils.isEmpty(address)) {
                    address = "MMS";
                }
                String body = findTextPart(context, id);
                if (TextUtils.isEmpty(body) && subjectIndex >= 0) {
                    body = cursor.getString(subjectIndex);
                }
                if (TextUtils.isEmpty(body)) {
                    body = context.getString(R.string.incoming_mms);
                }
                long date = cursor.getLong(dateIndex);
                if (date > 0 && date < 100000000000L) {
                    date *= 1000L;
                }
                MediaPart mediaPart = findMediaPart(context, id);
                messages.add(new ChatMessage(
                        Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, String.valueOf(id)),
                        address,
                        body,
                        date,
                        box,
                        -1,
                        cursor.getInt(readIndex) == 1,
                        outgoing,
                        true,
                        mediaPart.uri,
                        mediaPart.contentType));
            }
        } catch (SecurityException | IllegalArgumentException exception) {
            android.util.Log.w("SafeMessagesMms", "Unable to read MMS provider", exception);
        }
        return messages;
    }

    private static String findAddress(Context context, long id, int preferredType) {
        Uri uri = Uri.parse("content://mms/" + id + "/addr");
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{"address", "type"},
                null,
                null,
                null)) {
            if (cursor == null) {
                return "";
            }
            String fallback = "";
            while (cursor.moveToNext()) {
                String address = cursor.getString(0);
                int type = cursor.getInt(1);
                if (!TextUtils.isEmpty(address) && !"insert-address-token".equals(address)) {
                    if (type == preferredType) {
                        return address;
                    }
                    fallback = address;
                }
            }
            return fallback;
        } catch (SecurityException | IllegalArgumentException exception) {
            return "";
        }
    }

    private static String findTextPart(Context context, long id) {
        Uri uri = Uri.parse("content://mms/part");
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{"text", "ct"},
                "mid=?",
                new String[]{String.valueOf(id)},
                null)) {
            if (cursor == null) {
                return "";
            }
            StringBuilder text = new StringBuilder();
            while (cursor.moveToNext()) {
                String contentType = cursor.getString(1);
                String partText = cursor.getString(0);
                if ("text/plain".equals(contentType) && !TextUtils.isEmpty(partText)) {
                    if (text.length() > 0) {
                        text.append('\n');
                    }
                    text.append(partText);
                }
            }
            return text.toString();
        } catch (SecurityException | IllegalArgumentException exception) {
            return "";
        }
    }

    private static MediaPart findMediaPart(Context context, long id) {
        Uri uri = Uri.parse("content://mms/part");
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{"_id", "ct"},
                "mid=?",
                new String[]{String.valueOf(id)},
                null)) {
            if (cursor == null) {
                return MediaPart.EMPTY;
            }
            while (cursor.moveToNext()) {
                String contentType = cursor.getString(1);
                if (contentType != null && contentType.startsWith("image/")) {
                    return new MediaPart(
                            Uri.parse("content://mms/part/" + cursor.getLong(0)),
                            contentType);
                }
            }
        } catch (SecurityException | IllegalArgumentException exception) {
            android.util.Log.w("SafeMessagesMms", "Unable to read MMS media part", exception);
        }
        return MediaPart.EMPTY;
    }

    private static class MediaPart {
        static final MediaPart EMPTY = new MediaPart(null, null);
        final Uri uri;
        final String contentType;

        MediaPart(Uri uri, String contentType) {
            this.uri = uri;
            this.contentType = contentType;
        }
    }
}
