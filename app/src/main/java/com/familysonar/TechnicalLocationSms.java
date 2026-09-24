package com.familysonar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds and parses the machine-readable location SMS that accompanies the
 * human-readable {@code ?loc?} response. Format (single logical message, may be
 * delivered as a multipart SMS):
 *
 * <pre>#FMLOC1|LT=&lt;lat&gt;|LN=&lt;lon&gt;|AC=&lt;acc&gt;|TS=&lt;epochMillis&gt;|C=&lt;mcc&gt;,&lt;mnc&gt;,&lt;cid&gt;,&lt;lac&gt;,&lt;dbm&gt;|C=...</pre>
 *
 * <p>Every {@code C} record carries a complete global cell identity and is
 * suitable for an OpenCellID lookup. Cells without a usable identity are
 * deliberately omitted because they cannot determine a location.
 */
final class TechnicalLocationSms {

    static final String PREFIX = "#FMLOC1";

    static final class Cell {
        final int mcc;
        final int mnc;
        final long cid;
        final int lac;
        final int dbm;

        Cell(int mcc, int mnc, long cid, int lac, int dbm) {
            this.mcc = mcc;
            this.mnc = mnc;
            this.cid = cid;
            this.lac = lac;
            this.dbm = dbm;
        }
    }

    static final class Data {
        double lat;
        double lon;
        int accuracy = -1;
        long timestamp;
        final List<Cell> cells = new ArrayList<>();
    }

    private TechnicalLocationSms() {
    }

    static boolean isTechnical(String body) {
        return body != null && body.startsWith(PREFIX);
    }

    static String build(double lat, double lon, int accuracy, long timestamp, List<Cell> cells) {
        StringBuilder sb = new StringBuilder(PREFIX);
        sb.append("|LT=").append(String.format(Locale.US, "%.6f", lat));
        sb.append("|LN=").append(String.format(Locale.US, "%.6f", lon));
        sb.append("|AC=").append(accuracy);
        sb.append("|TS=").append(timestamp);
        if (cells != null) {
            for (Cell c : cells) {
                if (c == null) {
                    continue;
                }
                sb.append("|C=").append(c.mcc).append(',').append(c.mnc).append(',')
                        .append(c.cid).append(',').append(c.lac).append(',').append(c.dbm);
            }
        }
        return sb.toString();
    }

    static Data parse(String body) {
        if (!isTechnical(body)) {
            return null;
        }
        Data data = new Data();
        int legacyMcc = -1;
        int legacyMnc = -1;
        boolean hasLat = false;
        boolean hasLon = false;
        String[] tokens = body.split("\\|");

        // First pass: scalar fields and the shared PLMN (mcc/mnc).
        for (String token : tokens) {
            int eq = token.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = token.substring(0, eq);
            String value = token.substring(eq + 1);
            try {
                if ("LT".equals(key)) {
                    data.lat = Double.parseDouble(value);
                    hasLat = true;
                } else if ("LN".equals(key)) {
                    data.lon = Double.parseDouble(value);
                    hasLon = true;
                } else if ("AC".equals(key)) {
                    data.accuracy = (int) Math.round(Double.parseDouble(value));
                } else if ("TS".equals(key)) {
                    data.timestamp = Long.parseLong(value.trim());
                } else if ("CL".equals(key)) {
                    String[] parts = value.split(",");
                    if (parts.length >= 2) {
                        legacyMcc = parseIntSafe(parts[0]);
                        legacyMnc = parseIntSafe(parts[1]);
                    }
                }
            } catch (NumberFormatException ignored) {
                // Skip malformed field.
            }
        }

        // New records carry their own PLMN; accept the earlier format as well.
        for (String token : tokens) {
            if (!token.startsWith("C=")) {
                continue;
            }
            String[] parts = token.substring(2).split(",");
            if (parts.length >= 5) {
                data.cells.add(new Cell(parseIntSafe(parts[0]), parseIntSafe(parts[1]),
                        parseLongSafe(parts[2]), parseIntSafe(parts[3]), parseIntSafe(parts[4])));
            } else if (parts.length >= 3) {
                data.cells.add(new Cell(legacyMcc, legacyMnc,
                        parseLongSafe(parts[0]), parseIntSafe(parts[1]), parseIntSafe(parts[2])));
            }
        }

        if (!hasLat || !hasLon) {
            return null;
        }
        return data;
    }

    private static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static long parseLongSafe(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }
}
