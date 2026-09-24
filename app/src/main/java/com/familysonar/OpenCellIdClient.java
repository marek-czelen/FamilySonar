package com.familysonar;

import android.location.Location;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Estimates an approximate position from serving/neighbour cells using the
 * OpenCellID database (https://www.opencellid.org/). Cell positions are combined
 * with a weight derived from the reported signal strength and a confidence radius
 * is derived from the individual tower ranges and their spread.
 *
 * <p>All methods perform blocking network I/O and must be called off the main thread.
 */
final class OpenCellIdClient {

    private static final String ENDPOINT = "https://opencellid.org/cell/get";
    private static final int TIMEOUT_MILLIS = 12_000;
    private static final double DEFAULT_RANGE_METERS = 1000.0;

    static final class Estimate {
        final double lat;
        final double lon;
        final double radiusMeters;
        final int usedCells;

        Estimate(double lat, double lon, double radiusMeters, int usedCells) {
            this.lat = lat;
            this.lon = lon;
            this.radiusMeters = radiusMeters;
            this.usedCells = usedCells;
        }
    }

    private OpenCellIdClient() {
    }

    /** Rejects cells that carry "unavailable" sentinel identifiers that cannot be located. */
    private static boolean isUsable(TechnicalLocationSms.Cell cell) {
        return cell.mcc > 0 && cell.mnc >= 0
                && cell.lac > 0 && cell.lac != 0xFFFF && cell.lac != Integer.MAX_VALUE
                && cell.cid > 0 && cell.cid != 0x0FFFFFFFL && cell.cid != Integer.MAX_VALUE;
    }

    static Estimate estimate(List<TechnicalLocationSms.Cell> cells, String apiKey) {
        if (cells == null || cells.isEmpty() || apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }

        List<double[]> towers = new ArrayList<>(); // {lat, lon, range, weight, dbm}
        double weightSum = 0.0;
        double latSum = 0.0;
        double lonSum = 0.0;

        for (TechnicalLocationSms.Cell cell : cells) {
            if (!isUsable(cell)) {
                continue;
            }
            double[] tower = queryCell(cell, apiKey);
            if (tower == null) {
                continue;
            }
            double weight = signalWeight(cell.dbm);
            towers.add(new double[]{tower[0], tower[1], tower[2], weight, cell.dbm});
            latSum += tower[0] * weight;
            lonSum += tower[1] * weight;
            weightSum += weight;
        }

        if (towers.isEmpty() || weightSum <= 0.0) {
            return null;
        }

        double lat = latSum / weightSum;
        double lon = lonSum / weightSum;

        double rangeSum = 0.0;
        double maxSpread = 0.0;
        float[] distance = new float[1];
        for (double[] tower : towers) {
            rangeSum += tower[2] > 0 ? tower[2] : DEFAULT_RANGE_METERS;
            Location.distanceBetween(lat, lon, tower[0], tower[1], distance);
            maxSpread = Math.max(maxSpread, distance[0]);
        }
        double averageRange = rangeSum / towers.size();

        double radius;
        if (towers.size() == 1) {
            // A single cell only pins down the tower position; the phone can be
            // anywhere within the cell coverage. Size the radius from the reported
            // tower-position uncertainty plus a signal-derived phone-to-tower distance.
            double towerRange = towers.get(0)[2] > 0 ? towers.get(0)[2] : DEFAULT_RANGE_METERS;
            radius = towerRange + signalDistanceMeters((int) towers.get(0)[4]);
        } else {
            // With several towers the weighted centroid is meaningful; the spread
            // of the towers bounds the confidence region.
            radius = Math.max(averageRange, maxSpread);
        }
        if (radius <= 0.0) {
            radius = DEFAULT_RANGE_METERS;
        }

        return new Estimate(lat, lon, radius, towers.size());
    }

    /**
     * Rough phone-to-tower distance (metres) from the reported signal level using a
     * log-distance path-loss inversion. Intentionally conservative so the confidence
     * region reflects the large uncertainty of single-cell positioning.
     */
    private static double signalDistanceMeters(int dbm) {
        int rsrp = dbm == Integer.MAX_VALUE ? -110 : Math.max(-140, Math.min(-50, dbm));
        double referenceDistance = 50.0;   // metres near the cell
        double referenceLevel = -60.0;      // dBm near the cell
        double pathLossExponent = 3.2;      // typical urban
        double distance = referenceDistance
                * Math.pow(10.0, (referenceLevel - rsrp) / (10.0 * pathLossExponent));
        return Math.max(150.0, Math.min(distance, 15000.0));
    }

    /** @return {@code {lat, lon, range}} or {@code null} when the cell is unknown. */
    private static double[] queryCell(TechnicalLocationSms.Cell cell, String apiKey) {
        HttpURLConnection connection = null;
        try {
            String query = String.format(Locale.US,
                    "%s?key=%s&mcc=%d&mnc=%d&lac=%d&cellid=%d&format=json",
                    ENDPOINT, apiKey.trim(), cell.mcc, cell.mnc, cell.lac, cell.cid);
            connection = (HttpURLConnection) new URL(query).openConnection();
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestMethod("GET");

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (stream == null) {
                return null;
            }
            String response = readAll(stream);
            JSONObject json = new JSONObject(response);
            if (!json.has("lat") || !json.has("lon")) {
                return null;
            }
            double lat = json.optDouble("lat", 0.0);
            double lon = json.optDouble("lon", 0.0);
            if (lat == 0.0 && lon == 0.0) {
                return null;
            }
            double range = json.optDouble("range", DEFAULT_RANGE_METERS);
            return new double[]{lat, lon, range};
        } catch (Exception exception) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static double signalWeight(int dbm) {
        // Map typical dBm (-120 weak .. -50 strong) onto a positive weight.
        int normalized = dbm == Integer.MAX_VALUE ? -120 : dbm;
        return Math.max(1.0, normalized + 130.0);
    }

    private static String readAll(InputStream stream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}
