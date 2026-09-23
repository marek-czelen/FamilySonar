package com.familysonar;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves phone numbers to contact display names via {@link ContactsContract.PhoneLookup},
 * caching results in memory to avoid repeated provider queries. Falls back to a nicely formatted
 * number when there is no contact or the READ_CONTACTS permission is missing.
 */
final class ContactNameResolver {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private ContactNameResolver() {
    }

    /**
     * Returns the contact display name for the number, or a formatted fallback number.
     */
    static String resolve(Context context, String number) {
        if (TextUtils.isEmpty(number)) {
            return context.getString(R.string.unknown_sender);
        }
        String trimmed = number.trim();
        String cached = CACHE.get(trimmed);
        if (cached != null) {
            return cached;
        }

        String resolved = lookupName(context, trimmed);
        if (TextUtils.isEmpty(resolved)) {
            resolved = formatNumber(trimmed);
        }
        CACHE.put(trimmed, resolved);
        return resolved;
    }

    static void clearCache() {
        CACHE.clear();
    }

    private static String lookupName(Context context, String number) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(
                uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }
            }
        } catch (SecurityException | IllegalArgumentException exception) {
            return null;
        }
        return null;
    }

    private static String formatNumber(String number) {
        String formatted = PhoneNumberUtils.formatNumber(number, Locale.getDefault().getCountry());
        return TextUtils.isEmpty(formatted) ? number : formatted;
    }
}
