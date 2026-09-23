package com.familysonar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Scales and compresses an image attachment so a whole MMS fits typical carrier limits
 * (target ~300 KB). Photos are recompressed to JPEG, GIFs are passed through untouched to
 * keep their animation.
 */
final class ImageScaler {
    static final int MAX_BYTES = 300 * 1024;
    private static final int MAX_LONGEST_SIDE = 1024;
    private static final String TAG = "SafeMessagesScaler";

    static final class Result {
        final byte[] data;
        final String contentType;
        final String extension;
        final boolean gif;

        Result(byte[] data, String contentType, String extension, boolean gif) {
            this.data = data;
            this.contentType = contentType;
            this.extension = extension;
            this.gif = gif;
        }
    }

    /**
     * Thrown when the attachment cannot be made to fit the MMS size limit.
     */
    static final class TooLargeException extends Exception {
        TooLargeException(String message) {
            super(message);
        }
    }

    private ImageScaler() {
    }

    static Result scale(Context context, Uri uri, String contentType)
            throws IOException, TooLargeException {
        boolean gif = contentType != null && contentType.toLowerCase().contains("gif");
        if (gif) {
            byte[] original = readAllBytes(context, uri);
            if (original.length > MAX_BYTES) {
                throw new TooLargeException("GIF exceeds MMS size limit");
            }
            return new Result(original, "image/gif", "gif", true);
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        int sampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight);

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = sampleSize;
        Bitmap bitmap;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(input, null, decodeOptions);
        }
        if (bitmap == null) {
            throw new IOException("Unable to decode image attachment");
        }

        bitmap = applyExifOrientation(context, uri, bitmap);
        bitmap = scaleDownIfNeeded(bitmap);

        byte[] jpeg = compressToLimit(bitmap);
        bitmap.recycle();
        if (jpeg == null) {
            throw new TooLargeException("Image could not be compressed below the MMS limit");
        }
        return new Result(jpeg, "image/jpeg", "jpg", false);
    }

    private static int computeSampleSize(int width, int height) {
        int sampleSize = 1;
        int longest = Math.max(width, height);
        while (longest / sampleSize > MAX_LONGEST_SIDE * 2) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private static Bitmap scaleDownIfNeeded(Bitmap bitmap) {
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest <= MAX_LONGEST_SIDE) {
            return bitmap;
        }
        float ratio = (float) MAX_LONGEST_SIDE / (float) longest;
        int targetWidth = Math.max(1, Math.round(bitmap.getWidth() * ratio));
        int targetHeight = Math.max(1, Math.round(bitmap.getHeight() * ratio));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }

    private static byte[] compressToLimit(Bitmap bitmap) {
        for (int quality = 80; quality >= 40; quality -= 10) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            byte[] bytes = out.toByteArray();
            if (bytes.length <= MAX_BYTES) {
                return bytes;
            }
        }
        return null;
    }

    private static Bitmap applyExifOrientation(Context context, Uri uri, Bitmap bitmap) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return bitmap;
            }
            ExifInterface exif = new ExifInterface(input);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.postScale(1, -1);
                    break;
                default:
                    return bitmap;
            }
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (IOException | RuntimeException exception) {
            Log.w(TAG, "Unable to read EXIF orientation", exception);
            return bitmap;
        }
    }

    private static byte[] readAllBytes(Context context, Uri uri) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Unable to open attachment");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    static String guessContentType(Context context, Uri uri) {
        String type = context.getContentResolver().getType(uri);
        if (!TextUtils.isEmpty(type)) {
            return type;
        }
        return "image/*";
    }
}
