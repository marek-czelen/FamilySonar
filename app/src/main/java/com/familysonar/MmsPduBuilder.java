package com.familysonar;

import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-contained encoder that serialises an M-Send.req MMS PDU (WSP/MMS encoding rules) with a
 * SMIL presentation part, an optional text part and an image part. The output can be handed to
 * {@link android.telephony.SmsManager#sendMultimediaMessage} via a FileProvider Uri.
 */
final class MmsPduBuilder {
    // MMS header field codes (already OR-ed with the 0x80 short-header flag).
    private static final int FIELD_MESSAGE_TYPE = 0x8C;
    private static final int FIELD_TRANSACTION_ID = 0x98;
    private static final int FIELD_MMS_VERSION = 0x8D;
    private static final int FIELD_FROM = 0x89;
    private static final int FIELD_TO = 0x97;
    private static final int FIELD_CONTENT_TYPE = 0x84;

    private static final int MESSAGE_TYPE_SEND_REQ = 0x80;
    private static final int MMS_VERSION_1_2 = 0x92;
    private static final int INSERT_ADDRESS_TOKEN = 0x81;

    // WSP well-known content type: application/vnd.wap.multipart.related.
    private static final int CT_MULTIPART_RELATED = 0xB3;
    // Parameter assigned numbers (deprecated text forms used by AOSP PduComposer).
    private static final int PARAM_TYPE = 0x89;
    private static final int PARAM_START = 0x8A;
    private static final int PARAM_NAME = 0x85;
    private static final int PARAM_CHARSET = 0x81;
    // Content-ID / Content-Location header field codes.
    private static final int HEADER_CONTENT_ID = 0xC0;
    private static final int HEADER_CONTENT_LOCATION = 0x8E;

    private static final int CHARSET_UTF8 = 106;

    private static final String SMIL_CONTENT_ID = "<smil>";
    private static final String SMIL_NAME = "smil.xml";
    private static final String IMAGE_CONTENT_ID = "<image>";
    private static final String TEXT_CONTENT_ID = "<text>";
    private static final String TEXT_NAME = "text.txt";

    private MmsPduBuilder() {
    }

    static byte[] build(
            String recipient,
            String text,
            byte[] imageData,
            String imageContentType,
            String imageFileName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // --- MMS headers ---
        writeByte(out, FIELD_MESSAGE_TYPE);
        writeByte(out, MESSAGE_TYPE_SEND_REQ);

        writeByte(out, FIELD_TRANSACTION_ID);
        writeTextString(out, "T" + Long.toHexString(System.currentTimeMillis()));

        writeByte(out, FIELD_MMS_VERSION);
        writeByte(out, MMS_VERSION_1_2);

        // From: insert-address-token so the MMSC fills in the sender.
        writeByte(out, FIELD_FROM);
        writeValueLength(out, 1);
        writeByte(out, INSERT_ADDRESS_TOKEN);

        writeByte(out, FIELD_TO);
        writeEncodedAddress(out, recipient);

        // Content-Type: application/vnd.wap.multipart.related with type + start params.
        ByteArrayOutputStream ct = new ByteArrayOutputStream();
        writeByte(ct, CT_MULTIPART_RELATED);
        writeByte(ct, PARAM_TYPE);
        writeTextString(ct, "application/smil");
        writeByte(ct, PARAM_START);
        writeTextString(ct, SMIL_CONTENT_ID);
        writeByte(out, FIELD_CONTENT_TYPE);
        writeValueLength(out, ct.size());
        out.write(ct.toByteArray());

        // --- Multipart body ---
        boolean hasText = !TextUtils.isEmpty(text);
        String smil = buildSmil(imageFileName, hasText);

        List<byte[]> parts = new ArrayList<>();
        parts.add(buildPart(
                "application/smil", SMIL_NAME, SMIL_CONTENT_ID, "smil.xml",
                smil.getBytes("UTF-8"), true));
        if (hasText) {
            parts.add(buildPart(
                    "text/plain", TEXT_NAME, TEXT_CONTENT_ID, TEXT_NAME,
                    text.getBytes("UTF-8"), true));
        }
        parts.add(buildPart(
                imageContentType, imageFileName, IMAGE_CONTENT_ID, imageFileName,
                imageData, false));

        writeUintvar(out, parts.size());
        for (byte[] part : parts) {
            out.write(part);
        }
        return out.toByteArray();
    }

    private static String buildSmil(String imageFileName, boolean hasText) {
        StringBuilder smil = new StringBuilder();
        smil.append("<smil><head><layout>");
        smil.append("<root-layout/>");
        smil.append("<region id=\"Image\" left=\"0\" top=\"0\" width=\"100%\" height=\"100%\" fit=\"meet\"/>");
        if (hasText) {
            smil.append("<region id=\"Text\" left=\"0\" top=\"80%\" width=\"100%\" height=\"20%\"/>");
        }
        smil.append("</layout></head><body><par dur=\"5000ms\">");
        smil.append("<img src=\"").append(imageFileName).append("\" region=\"Image\"/>");
        if (hasText) {
            smil.append("<text src=\"").append(TEXT_NAME).append("\" region=\"Text\"/>");
        }
        smil.append("</par></body></smil>");
        return smil.toString();
    }

    private static byte[] buildPart(
            String contentType,
            String name,
            String contentId,
            String contentLocation,
            byte[] data,
            boolean utf8Charset) throws IOException {
        // Content-Type value (general form with value-length so parameters are allowed).
        ByteArrayOutputStream ctBody = new ByteArrayOutputStream();
        writeTextString(ctBody, contentType);
        if (!TextUtils.isEmpty(name)) {
            writeByte(ctBody, PARAM_NAME);
            writeTextString(ctBody, name);
        }
        if (utf8Charset) {
            writeByte(ctBody, PARAM_CHARSET);
            writeByte(ctBody, CHARSET_UTF8 | 0x80);
        }

        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        writeValueLength(headers, ctBody.size());
        headers.write(ctBody.toByteArray());
        // Content-ID
        writeByte(headers, HEADER_CONTENT_ID);
        writeQuotedString(headers, contentId);
        // Content-Location
        writeByte(headers, HEADER_CONTENT_LOCATION);
        writeTextString(headers, contentLocation);

        ByteArrayOutputStream entry = new ByteArrayOutputStream();
        writeUintvar(entry, headers.size());
        writeUintvar(entry, data.length);
        entry.write(headers.toByteArray());
        entry.write(data);
        return entry.toByteArray();
    }

    private static void writeByte(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
    }

    private static void writeTextString(ByteArrayOutputStream out, String value)
            throws UnsupportedEncodingException {
        byte[] bytes = value.getBytes("UTF-8");
        if (bytes.length > 0 && (bytes[0] & 0xFF) > 0x7F) {
            out.write(0x7F);
        }
        out.write(bytes, 0, bytes.length);
        out.write(0x00);
    }

    private static void writeQuotedString(ByteArrayOutputStream out, String value)
            throws UnsupportedEncodingException {
        out.write(0x22); // opening quote
        byte[] bytes = value.getBytes("UTF-8");
        out.write(bytes, 0, bytes.length);
        out.write(0x00);
    }

    private static void writeEncodedAddress(ByteArrayOutputStream out, String address)
            throws UnsupportedEncodingException {
        String value = address;
        if (!value.contains("/TYPE=")) {
            value = value + "/TYPE=PLMN";
        }
        writeTextString(out, value);
    }

    private static void writeValueLength(ByteArrayOutputStream out, long length) {
        if (length <= 30) {
            out.write((int) (length & 0xFF));
        } else {
            out.write(0x1F); // length-quote
            writeUintvar(out, length);
        }
    }

    private static void writeUintvar(ByteArrayOutputStream out, long value) {
        if (value < 0) {
            value = 0;
        }
        int count = 1;
        long temp = value >> 7;
        while (temp != 0) {
            temp >>= 7;
            count++;
        }
        for (int i = count - 1; i >= 0; i--) {
            int b = (int) ((value >> (7 * i)) & 0x7F);
            if (i != 0) {
                b |= 0x80;
            }
            out.write(b);
        }
    }
}
