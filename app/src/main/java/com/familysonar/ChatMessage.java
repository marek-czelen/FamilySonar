package com.familysonar;

import android.net.Uri;

class ChatMessage {
    final Uri uri;
    final String address;
    final String body;
    final long dateMillis;
    final int type;
    final int status;
    final boolean read;
    final boolean outgoing;
    final boolean mms;
    final Uri mediaUri;
    final String mediaType;

    ChatMessage(
            Uri uri,
            String address,
            String body,
            long dateMillis,
            int type,
            int status,
            boolean read,
            boolean outgoing,
            boolean mms) {
        this(uri, address, body, dateMillis, type, status, read, outgoing, mms, null, null);
    }

    ChatMessage(
            Uri uri,
            String address,
            String body,
            long dateMillis,
            int type,
            int status,
            boolean read,
            boolean outgoing,
            boolean mms,
            Uri mediaUri,
            String mediaType) {
        this.uri = uri;
        this.address = address;
        this.body = body;
        this.dateMillis = dateMillis;
        this.type = type;
        this.status = status;
        this.read = read;
        this.outgoing = outgoing;
        this.mms = mms;
        this.mediaUri = mediaUri;
        this.mediaType = mediaType;
    }
}
