package com.familysonar;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class EmergencyPasswordStore {
    private static final String PREFS_NAME = "emergency_location";
    private static final String PASSWORD_HASH = "password_hash";
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 64;

    private EmergencyPasswordStore() {
    }

    static boolean isValid(String password) {
        if (password == null
                || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            return false;
        }
        for (int index = 0; index < password.length(); index++) {
            if (Character.isWhitespace(password.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    static boolean isConfigured(Context context) {
        return getPreferences(context).contains(PASSWORD_HASH);
    }

    static void save(Context context, String password) {
        if (!isValid(password)) {
            throw new IllegalArgumentException("Emergency password does not meet the requirements");
        }
        getPreferences(context)
                .edit()
                .putString(PASSWORD_HASH, hash(password))
                .apply();
    }

    static void clear(Context context) {
        getPreferences(context).edit().remove(PASSWORD_HASH).apply();
    }

    static boolean matches(Context context, String password) {
        if (!isValid(password)) {
            return false;
        }
        String storedHash = getPreferences(context).getString(PASSWORD_HASH, null);
        if (storedHash == null) {
            return false;
        }
        byte[] expected = Base64.decode(storedHash, Base64.NO_WRAP);
        byte[] actual = Base64.decode(hash(password), Base64.NO_WRAP);
        return MessageDigest.isEqual(expected, actual);
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String hash(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.encodeToString(
                    digest.digest(password.getBytes(StandardCharsets.UTF_8)),
                    Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
