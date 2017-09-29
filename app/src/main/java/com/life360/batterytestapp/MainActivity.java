package com.life360.batterytestapp;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.life360.batterytestapp.google.GeocodeResponse;
import com.life360.batterytestapp.google.GooglePlatform;
import com.life360.falx.FalxApi;

import java.io.IOException;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        FalxApi.getInstance(this).addMonitors(FalxApi.MONITOR_APP_STATE | FalxApi.MONITOR_NETWORK);
    }

    @Override
    protected void onStart() {
        super.onStart();

        FalxApi.getInstance(this).testStoredData();

        FalxApi.getInstance(this).startSession(this);
    }

    @Override
    protected void onStop() {
        super.onStop();

        FalxApi.getInstance(this).endSession(this);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {

                Log.d("rk-dbg", "Map long click rev-geocoding: " + latLng.toString());

                // Try a reverse geocode
                GooglePlatform.getInterface(MainActivity.this)
                        .reverseGeocode(String.format(Locale.US, "%f,%f", latLng.latitude, latLng.longitude), Locale.getDefault().getLanguage())
                        .enqueue(new Callback<GeocodeResponse>() {
                            @Override
                            public void onResponse(Call<GeocodeResponse> call, Response<GeocodeResponse> response) {
                                if (response.isSuccessful()) {
                                    String address = "No address";

                                    GeocodeResponse geocodeResponse = response.body();
                                    if (geocodeResponse.results.size() > 0) {
                                        GeocodeResponse.Results results = geocodeResponse.results.get(0);
                                        address = results.formattedAddress;
                                    }

                                    Log.d("rk-dbg", "Rev geocode response: " + address);
                                    Toast.makeText(MainActivity.this, address, Toast.LENGTH_SHORT).show();
                                } else {
                                    try {
                                        Log.e("rk-dbg", "Error in response: " + response.errorBody().string());
                                    } catch (IOException e) {
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Call<GeocodeResponse> call, Throwable t) {
                                Log.e("rk-dbg", "Call failure: " + t.toString());
                            }
                        });
            }
        });

    }
}
