package com.familysonar;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores the most recent machine-readable location received from each trusted
 * contact so the map can be (re)opened from a notification.
 */
final class LocationInboxStore {

    private static final String PREFS = "location_inbox";
    private static final String LAST_SIGNATURE = "last_signature";

    private LocationInboxStore() {
    }

    /**
     * @return {@code true} when the message is new (not a duplicate delivery of the last one).
     */
    static boolean saveIncoming(Context context, String from, String body) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = keyFor(from);
        String signature = key + "|" + body;
        if (signature.equals(prefs.getString(LAST_SIGNATURE, ""))) {
            return false;
        }
        prefs.edit()
                .putString("body_" + key, body)
                .putString("from_" + key, from == null ? "" : from)
                .putLong("time_" + key, System.currentTimeMillis())
                .putString(LAST_SIGNATURE, signature)
                .apply();
        return true;
    }

    static String bodyFor(Context context, String from) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("body_" + keyFor(from), null);
    }

    static long timeFor(Context context, String from) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong("time_" + keyFor(from), -1L);
    }

    static String keyFor(String from) {
        if (from == null) {
            return "";
        }
        String digits = from.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? from : digits;
    }
}
