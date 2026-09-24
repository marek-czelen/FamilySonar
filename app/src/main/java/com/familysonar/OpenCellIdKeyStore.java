package com.familysonar;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the user's OpenCellID API key used for cell-tower location estimates. */
final class OpenCellIdKeyStore {

    private static final String PREFS = "opencellid_settings";
    private static final String KEY = "api_key";

    /**
     * Shared built-in OpenCellID key used for now. Per-user keys will be added later;
     * {@link #get(Context)} already prefers a stored key when one is present.
     */
    private static final String DEFAULT_KEY = "pk.ea7bf3fb367978af9d4134469fc8cbb3";

    private OpenCellIdKeyStore() {
    }

    static void save(Context context, String apiKey) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, apiKey == null ? "" : apiKey.trim())
                .apply();
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY)
                .apply();
    }

    static String get(Context context) {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "");
        return stored != null && !stored.trim().isEmpty() ? stored.trim() : DEFAULT_KEY;
    }

    static boolean isConfigured(Context context) {
        return true;
    }
}
