package com.familysonar;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a located contact on an OpenStreetMap map: the exact GPS point delivered
 * in the technical SMS (with its accuracy circle) and an approximate position
 * estimated from cell towers via {@link OpenCellIdClient} (with a confidence radius).
 */
public class MapActivity extends AppCompatActivity {

    public static final String EXTRA_FROM = "com.familysonar.extra.MAP_FROM";
    public static final String EXTRA_BODY = "com.familysonar.extra.MAP_BODY";

    private MapView map;
    private TextView subtitle;
    private TextView btsLegendLabel;

    private TechnicalLocationSms.Data data;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this,
                getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_map);

        map = findViewById(R.id.map);
        subtitle = findViewById(R.id.mapSubtitle);
        btsLegendLabel = findViewById(R.id.btsLegendLabel);
        findViewById(R.id.backButton).setOnClickListener(view -> finish());

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(16.0);

        String from = getIntent().getStringExtra(EXTRA_FROM);
        String body = getIntent().getStringExtra(EXTRA_BODY);
        if (body == null && from != null) {
            body = LocationInboxStore.bodyFor(this, from);
        }

        TextView title = findViewById(R.id.mapTitle);
        if (from != null) {
            title.setText(ContactNameResolver.resolve(this, from));
        }

        data = TechnicalLocationSms.parse(body);
        if (data == null) {
            subtitle.setText(R.string.map_no_location);
            return;
        }

        showGpsLocation();
        estimateFromCells();
    }

    private void showGpsLocation() {
        GeoPoint point = new GeoPoint(data.lat, data.lon);
        map.getController().setCenter(point);
        int accuracy = data.accuracy > 0 ? data.accuracy : 30;

        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(getString(R.string.map_gps_label));
        marker.setSnippet(getString(R.string.map_pin_gps_details, accuracy));
        configureMarkerInfoWindow(marker);
        map.getOverlays().add(marker);

        addCircle(point, accuracy,
                ContextCompat.getColor(this, R.color.safe_primary), 0x332D6CDF);

        if (data.accuracy > 0) {
            subtitle.setText(getString(R.string.map_gps_accuracy, data.accuracy));
        } else {
            subtitle.setText(R.string.map_gps_label);
        }

        List<GeoPoint> bounds = new ArrayList<>();
        bounds.add(point);
        zoomTo(bounds);
    }

    private void estimateFromCells() {
        if (data.cells.isEmpty()) {
            return;
        }
        final String apiKey = OpenCellIdKeyStore.get(this);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            btsLegendLabel.setText(R.string.map_bts_label);
            appendSubtitle(getString(R.string.map_no_opencellid_key));
            return;
        }

        new Thread(() -> {
            final OpenCellIdClient.Estimate estimate =
                    OpenCellIdClient.estimate(data.cells, apiKey);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (estimate == null) {
                    appendSubtitle(getString(R.string.map_bts_unavailable));
                    return;
                }
                showBtsEstimate(estimate);
            });
        }).start();
    }

    private void showBtsEstimate(OpenCellIdClient.Estimate estimate) {
        GeoPoint point = new GeoPoint(estimate.lat, estimate.lon);

        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setTitle(getString(R.string.map_bts_label));
        marker.setSnippet(getString(R.string.map_pin_bts_details,
                Math.round(estimate.radiusMeters), estimate.usedCells));
        configureMarkerInfoWindow(marker);
        map.getOverlays().add(marker);

        addCircle(point, estimate.radiusMeters,
                ContextCompat.getColor(this, R.color.safe_secondary), 0x33F97316);

        int radius = (int) Math.round(estimate.radiusMeters);
        appendSubtitle(getString(R.string.map_confidence, radius));

        List<GeoPoint> bounds = new ArrayList<>();
        bounds.add(new GeoPoint(data.lat, data.lon));
        bounds.add(point);
        // Include the extent of the confidence circle so it stays visible.
        bounds.add(point.destinationPoint(estimate.radiusMeters, 0));
        bounds.add(point.destinationPoint(estimate.radiusMeters, 90));
        bounds.add(point.destinationPoint(estimate.radiusMeters, 180));
        bounds.add(point.destinationPoint(estimate.radiusMeters, 270));
        zoomTo(bounds);
        map.invalidate();
    }

    private void configureMarkerInfoWindow(Marker marker) {
        marker.setInfoWindow(new LocationMarkerInfoWindow(map));
        marker.setOnMarkerClickListener((clickedMarker, mapView) -> {
            clickedMarker.showInfoWindow();
            mapView.getController().animateTo(clickedMarker.getPosition());
            return true;
        });
    }

    private void addCircle(GeoPoint center, double radiusMeters, int strokeColor, int fillColor) {
        Polygon circle = new Polygon(map);
        circle.setPoints(Polygon.pointsAsCircle(center, radiusMeters));
        circle.getOutlinePaint().setColor(strokeColor);
        circle.getOutlinePaint().setStrokeWidth(4f);
        circle.getFillPaint().setColor(fillColor);
        circle.getFillPaint().setStyle(android.graphics.Paint.Style.FILL);
        map.getOverlays().add(circle);
        map.invalidate();
    }

    private void zoomTo(List<GeoPoint> points) {
        if (points.isEmpty()) {
            return;
        }
        map.post(() -> {
            if (points.size() == 1) {
                map.getController().setZoom(16.0);
                map.getController().setCenter(points.get(0));
                return;
            }
            BoundingBox box = BoundingBox.fromGeoPoints(points);
            map.zoomToBoundingBox(box, false, 100);
        });
    }

    private void appendSubtitle(String text) {
        CharSequence current = subtitle.getText();
        if (current == null || current.length() == 0) {
            subtitle.setText(text);
        } else {
            subtitle.setText(current + " · " + text);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) {
            map.onPause();
        }
    }
}
