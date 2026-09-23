package com.familysonar;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Persists the set of archived conversation addresses in SharedPreferences. */
class ArchiveStore {
    private static final String PREFS = "safe_messages_archive";
    private static final String KEY_ARCHIVED = "archived_addresses";

    private final SharedPreferences prefs;

    ArchiveStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    Set<String> getArchived() {
        return new HashSet<>(prefs.getStringSet(KEY_ARCHIVED, new HashSet<>()));
    }

    boolean isArchived(String address) {
        if (address == null) {
            return false;
        }
        return getArchived().contains(normalize(address));
    }

    void archive(String address) {
        if (address == null) {
            return;
        }
        Set<String> archived = getArchived();
        archived.add(normalize(address));
        prefs.edit().putStringSet(KEY_ARCHIVED, archived).apply();
    }

    void unarchive(String address) {
        if (address == null) {
            return;
        }
        Set<String> archived = getArchived();
        archived.remove(normalize(address));
        prefs.edit().putStringSet(KEY_ARCHIVED, archived).apply();
    }

    private static String normalize(String address) {
        return address.trim();
    }
}
