package com.familysonar;

import android.view.View;
import android.widget.TextView;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

/** Binds a marker's title and details to FindMe's map callout. */
final class LocationMarkerInfoWindow extends InfoWindow {

    LocationMarkerInfoWindow(MapView mapView) {
        super(R.layout.map_marker_info_window, mapView);
    }

    @Override
    public void onOpen(Object item) {
        Marker marker = (Marker) item;
        View view = getView();
        ((TextView) view.findViewById(R.id.mapMarkerTitle)).setText(marker.getTitle());
        ((TextView) view.findViewById(R.id.mapMarkerDetails)).setText(marker.getSnippet());
    }

    @Override
    public void onClose() {
        // No state to release.
    }
}
