package com.example.pharmafind;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;

public class ActivityMapPicker extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Marker currentMarker;
    private LatLng selectedLatLng = null;
    private TextView tvSelectedCoords;
    private EditText etMapLat, etMapLng;
    private ProgressBar pbMapLoading;
    private android.webkit.WebView webViewMapPicker;
    private boolean isUpdatingFromMap = false;
    private FusedLocationProviderClient fusedLocationClient;

    private class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void onLocationSelected(double lat, double lng) {
            runOnUiThread(() -> {
                selectedLatLng = new LatLng(lat, lng);
                updateCoordsDisplay();
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        View headerBar = findViewById(R.id.headerMapPicker);
        View bottomCard = findViewById(R.id.bottomCardMapPicker);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (headerBar != null) {
                headerBar.setPadding(
                    headerBar.getPaddingLeft(),
                    systemBars.top,
                    headerBar.getPaddingRight(),
                    headerBar.getPaddingBottom()
                );
            }
            if (bottomCard != null && bottomCard.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) bottomCard.getLayoutParams();
                params.bottomMargin = systemBars.bottom + 16;
                bottomCard.setLayoutParams(params);
            }
            return insets;
        });

        ImageButton btnBack = findViewById(R.id.btnBackMapPicker);
        tvSelectedCoords = findViewById(R.id.tvSelectedCoords);
        etMapLat = findViewById(R.id.etMapLat);
        etMapLng = findViewById(R.id.etMapLng);
        pbMapLoading = findViewById(R.id.pbMapLoading);
        webViewMapPicker = findViewById(R.id.webViewMapPicker);
        MaterialButton btnConfirm = findViewById(R.id.btnConfirmMapLocation);
        MaterialButton btnMyGps = findViewById(R.id.btnMyGpsLocation);
        MaterialButton btnToggleMapSource = findViewById(R.id.btnToggleMapSource);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        double initialLat = getIntent().getDoubleExtra("initial_lat", 0.0);
        double initialLng = getIntent().getDoubleExtra("initial_lng", 0.0);
        if (initialLat != 0.0 && initialLng != 0.0) {
            selectedLatLng = new LatLng(initialLat, initialLng);
        } else {
            selectedLatLng = new LatLng(13.9412, 121.1631); // Default Lipa City / Batangas
        }

        setupFallbackWebMap(selectedLatLng.latitude, selectedLatLng.longitude);

        // Check if API key is default placeholder or missing
        boolean isPlaceholderKey = true;
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), android.content.pm.PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            if (bundle != null) {
                String apiKey = bundle.getString("com.google.android.geo.API_KEY");
                if (apiKey != null && !apiKey.contains("YOUR_GOOGLE_MAPS_API_KEY")) {
                    isPlaceholderKey = false;
                }
            }
        } catch (Exception ignored) {}

        // If API key is placeholder, show OpenStreetMap tile view by default so roads/streets display immediately
        if (isPlaceholderKey && webViewMapPicker != null) {
            webViewMapPicker.setVisibility(View.VISIBLE);
        }

        if (btnToggleMapSource != null) {
            btnToggleMapSource.setOnClickListener(v -> {
                if (webViewMapPicker != null) {
                    if (webViewMapPicker.getVisibility() == View.VISIBLE) {
                        webViewMapPicker.setVisibility(View.GONE);
                        Toast.makeText(this, "Switched to Google Maps View", Toast.LENGTH_SHORT).show();
                    } else {
                        webViewMapPicker.setVisibility(View.VISIBLE);
                        Toast.makeText(this, "Switched to OpenStreetMap Tile View", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (pbMapLoading != null) {
            pbMapLoading.postDelayed(() -> {
                if (pbMapLoading != null) {
                    pbMapLoading.setVisibility(View.GONE);
                }
            }, 1000);
        }

        setupTextWatcherForManualCoords();
        updateCoordsDisplay();

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                if (selectedLatLng == null) {
                    Toast.makeText(this, "Please tap on the map to select a location", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent resultIntent = new Intent();
                resultIntent.putExtra("selected_lat", selectedLatLng.latitude);
                resultIntent.putExtra("selected_lng", selectedLatLng.longitude);
                setResult(RESULT_OK, resultIntent);
                finish();
            });
        }

        if (btnMyGps != null) {
            btnMyGps.setOnClickListener(v -> fetchCurrentLocationAndMoveMap());
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragmentPicker);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupFallbackWebMap(double initLat, double initLng) {
        if (webViewMapPicker == null) return;
        webViewMapPicker.getSettings().setJavaScriptEnabled(true);
        webViewMapPicker.getSettings().setDomStorageEnabled(true);
        webViewMapPicker.addJavascriptInterface(new WebAppInterface(), "Android");

        String html = "<!DOCTYPE html>" +
                "<html><head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css' />" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +
                "<style>body, html, #map { height: 100%; margin: 0; padding: 0; }</style>" +
                "</head><body>" +
                "<div id='map'></div>" +
                "<script>" +
                "var map = L.map('map').setView([" + initLat + ", " + initLng + "], 15);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {" +
                "    maxZoom: 19," +
                "    attribution: '© OpenStreetMap'" +
                "}).addTo(map);" +
                "var marker = L.marker([" + initLat + ", " + initLng + "], {draggable: true}).addTo(map);" +
                "function updateMarkerPos(lat, lng) {" +
                "    marker.setLatLng([lat, lng]);" +
                "    map.panTo([lat, lng]);" +
                "}" +
                "marker.on('dragend', function(e) {" +
                "    var pos = marker.getLatLng();" +
                "    if (window.Android) window.Android.onLocationSelected(pos.lat, pos.lng);" +
                "});" +
                "map.on('click', function(e) {" +
                "    marker.setLatLng(e.latlng);" +
                "    if (window.Android) window.Android.onLocationSelected(e.latlng.lat, e.latlng.lng);" +
                "});" +
                "</script></body></html>";

        webViewMapPicker.loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null);
    }

    private void setupTextWatcherForManualCoords() {
        TextWatcher coordsWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingFromMap) return;
                try {
                    String latStr = etMapLat != null ? etMapLat.getText().toString().trim() : "";
                    String lngStr = etMapLng != null ? etMapLng.getText().toString().trim() : "";
                    if (!latStr.isEmpty() && !lngStr.isEmpty()) {
                        double lat = Double.parseDouble(latStr);
                        double lng = Double.parseDouble(lngStr);
                        if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
                            LatLng newPos = new LatLng(lat, lng);
                            selectedLatLng = newPos;
                            updateMarker(newPos);
                            if (mMap != null) {
                                mMap.animateCamera(CameraUpdateFactory.newLatLng(newPos));
                            }
                            if (tvSelectedCoords != null) {
                                tvSelectedCoords.setText(String.format(Locale.US, "Selected Pin: %.6f, %.6f", lat, lng));
                            }
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        };

        if (etMapLat != null) etMapLat.addTextChangedListener(coordsWatcher);
        if (etMapLng != null) etMapLng.addTextChangedListener(coordsWatcher);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        if (pbMapLoading != null) {
            pbMapLoading.setVisibility(View.GONE);
        }

        mMap.setOnMapLoadedCallback(() -> {
            if (pbMapLoading != null) {
                pbMapLoading.setVisibility(View.GONE);
            }
        });

        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        // Default location (Lipa City area / Batangas if no initial location passed)
        LatLng defaultLocation = (selectedLatLng != null) ? selectedLatLng : new LatLng(13.9412, 121.1631);
        selectedLatLng = defaultLocation;
        updateMarker(defaultLocation);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f));

        mMap.setOnMapClickListener(latLng -> {
            selectedLatLng = latLng;
            updateMarker(latLng);
            updateCoordsDisplay();
        });

        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(@NonNull Marker marker) {}

            @Override
            public void onMarkerDrag(@NonNull Marker marker) {}

            @Override
            public void onMarkerDragEnd(@NonNull Marker marker) {
                selectedLatLng = marker.getPosition();
                updateCoordsDisplay();
            }
        });
    }

    private void updateMarker(LatLng latLng) {
        if (currentMarker != null) {
            currentMarker.setPosition(latLng);
        } else if (mMap != null) {
            currentMarker = mMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("Selected Location Pin")
                    .draggable(true));
        }

        if (webViewMapPicker != null) {
            webViewMapPicker.evaluateJavascript(String.format(Locale.US, "javascript:updateMarkerPos(%.6f, %.6f);", latLng.latitude, latLng.longitude), null);
        }
    }

    private void updateCoordsDisplay() {
        if (selectedLatLng == null) return;
        isUpdatingFromMap = true;
        if (tvSelectedCoords != null) {
            tvSelectedCoords.setText(String.format(Locale.US, "Selected Pin: %.6f, %.6f", selectedLatLng.latitude, selectedLatLng.longitude));
        }
        if (etMapLat != null) {
            etMapLat.setText(String.format(Locale.US, "%.6f", selectedLatLng.latitude));
        }
        if (etMapLng != null) {
            etMapLng.setText(String.format(Locale.US, "%.6f", selectedLatLng.longitude));
        }
        isUpdatingFromMap = false;
    }

    private void fetchCurrentLocationAndMoveMap() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                LatLng current = new LatLng(location.getLatitude(), location.getLongitude());
                selectedLatLng = current;
                updateMarker(current);
                updateCoordsDisplay();
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 16f));
                }
            } else {
                Toast.makeText(this, "Could not detect GPS location. Ensure location is enabled.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // After the user taps Allow on the system prompt, show the blue dot and move to their location
    // right away instead of making them tap the GPS button a second time.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 1001) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
            }
            fetchCurrentLocationAndMoveMap();
        } else {
            Toast.makeText(this, "Location permission is needed to find your current location.", Toast.LENGTH_SHORT).show();
        }
    }
}
