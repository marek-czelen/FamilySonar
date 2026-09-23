package com.familysonar;

class ConversationPreview {
    final String address;
    final String title;
    final String preview;
    final long dateMillis;
    final int unreadCount;
    final boolean mms;

    ConversationPreview(
            String address,
            String title,
            String preview,
            long dateMillis,
            int unreadCount,
            boolean mms) {
        this.address = address;
        this.title = title;
        this.preview = preview;
        this.dateMillis = dateMillis;
        this.unreadCount = unreadCount;
        this.mms = mms;
    }
}
