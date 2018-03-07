// Copyright 2018 Life360, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.life360.batterytestapp;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
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
import com.life360.falx.model.RealtimeMessagingActivity;
import com.life360.falx.monitor.RealtimeMessagingSession;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.app.job.JobInfo.NETWORK_TYPE_ANY;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {
    public static final String LOGTAG = "FalxDebugActivity";

    public static final String MONITOR_LABEL_GPS = "GPS";
    public static final String EVENT_GPS_ON = "gps-on";
    public static final String EVENT_ACTIVITY_DETECTION_ON = "activities-on";
    public static final String MONITOR_LABEL_ACTIVITY_DETECTION = "ActivityDetection";
    public static final String EVENT_WAKELOCK_ACQUIRED = "wakelock-acquired";
    public static final String MONITOR_LABEL_WAKELOCK = "test wakelock";
    public static final String MONITOR_LABEL_WAKELOCK2 = "test wakelock2";

    private GoogleMap mMap;
    private boolean logging;
    private FalxApi falxApi;
    private long sessionStartTime;
    private long wakelockAcquireTime;
    private long wakelock2AcquireTime;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        falxApi = FalxApi.getInstance(MainActivity.this);
        falxApi.enableLogging(true);
        falxApi.addMonitors(FalxApi.MONITOR_APP_STATE | FalxApi.MONITOR_NETWORK | FalxApi.MONITOR_REALTIME_MESSAGING);
        falxApi.addOnOffMonitor(MONITOR_LABEL_GPS, EVENT_GPS_ON);
        falxApi.addOnOffMonitor(MONITOR_LABEL_ACTIVITY_DETECTION, EVENT_ACTIVITY_DETECTION_ON);
        falxApi.addWakelockMonitor(MONITOR_LABEL_WAKELOCK, EVENT_WAKELOCK_ACQUIRED);
        falxApi.addWakelockMonitor(MONITOR_LABEL_WAKELOCK2, EVENT_WAKELOCK_ACQUIRED);

        findViewById(R.id.trigger_stats).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOGTAG, "Test aggregate data query...");

                BatteryStatReporter.sendLogs(MainActivity.this);
            }
        });

        findViewById(R.id.trigger_realtime_event).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                for (int i = 0; i < 100; i++) {
                    Random random = new Random();
                    int bytes = random.nextInt(10000);
                    RealtimeMessagingActivity rtActivity = new RealtimeMessagingActivity(1, bytes, "mqtt");
                    falxApi.realtimeMessageReceived(rtActivity);
                }

            }
        });

        findViewById(R.id.get_events_json).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    showLogs(FalxApi.getInstance(MainActivity.this).eventToJSON("falx_logs_test.log"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.trigger_logging).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logging = !logging;
                FalxApi.getInstance(MainActivity.this).enableLogging(logging);
                if (logging) {
                    Toast.makeText(MainActivity.this, "Falx Logging enabled.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Falx Logging disabled.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        ((Switch) findViewById(R.id.wakelockSwitch)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    wakelockAcquireTime = System.currentTimeMillis();
                    falxApi.wakelockAcquired(MONITOR_LABEL_WAKELOCK, DateUtils.MINUTE_IN_MILLIS);
                } else {
                    Log.d(LOGTAG, "Wakelock held for " + (System.currentTimeMillis() - wakelockAcquireTime) + " ms");
                    falxApi.wakelockReleased(MONITOR_LABEL_WAKELOCK);
                }
            }
        });

        ((Switch) findViewById(R.id.wakelock2Switch)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    wakelock2AcquireTime = System.currentTimeMillis();
                    falxApi.wakelockAcquired(MONITOR_LABEL_WAKELOCK2);
                } else {
                    Log.d(LOGTAG, "Wakelock held for " + (System.currentTimeMillis() - wakelock2AcquireTime) + " ms");
                    falxApi.wakelockReleased(MONITOR_LABEL_WAKELOCK2);
                }
            }
        });

        findViewById(R.id.trigger_deleteall_event).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("rk-dbg", "Deleting all stored events...");
                falxApi.deleteAllEvents();
                Log.e("rk-dbg", "Deletion complete");
            }
        });

        JobScheduler jobScheduler = (JobScheduler) this.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        List<JobInfo> pendingJobs = jobScheduler.getAllPendingJobs();

        boolean foundBatteryJob = false;
        for (JobInfo info : pendingJobs) {
            if (ScheduledJobService.JOB_ID_BATTERY_STATS == info.getId()) {
                foundBatteryJob = true;
                break;
            }
        }

        if (!foundBatteryJob) {
            JobInfo.Builder builder = new JobInfo.Builder(ScheduledJobService.JOB_ID_BATTERY_STATS, new ComponentName(this, ScheduledJobService.class));
            builder.setPeriodic(1 * DateUtils.MINUTE_IN_MILLIS);
            builder.setRequiredNetworkType(NETWORK_TYPE_ANY);
            JobInfo jobInfo = builder.build();

            int schRes = jobScheduler.schedule(jobInfo);
            Log.d(LOGTAG, "Job scheduling result: " + schRes);
        } else {
            Log.d(LOGTAG, "Battery stat reporter job already scheduled");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        sessionStartTime = System.currentTimeMillis();
        falxApi.startSession(this);
        falxApi.turnedOn(MONITOR_LABEL_GPS);
        falxApi.turnedOn(MONITOR_LABEL_ACTIVITY_DETECTION);
    }

    @Override
    protected void onStop() {
        super.onStop();

        falxApi.turnedOff(MONITOR_LABEL_GPS);
        falxApi.turnedOff(MONITOR_LABEL_ACTIVITY_DETECTION);
        falxApi.endSession(this);

        // Test FalxApi.realtimeMessageSessionCompleted()
        long sessionDuration = System.currentTimeMillis() - sessionStartTime;
        HashMap<String, Double> extras = new HashMap<>();
        extras.put("numTopics", 1.0D);
        extras.put("connectTime", 5.0D);
        RealtimeMessagingSession session = new RealtimeMessagingSession(sessionDuration, 1, extras);
        falxApi.realtimeMessageSessionCompleted(session);
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

                Log.d(LOGTAG, "Map long click rev-geocoding: " + latLng.toString());

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

                                    Log.d(LOGTAG, "Rev geocode response: " + address);
                                    Toast.makeText(MainActivity.this, address, Toast.LENGTH_SHORT).show();
                                } else {
                                    try {
                                        Log.e(LOGTAG, "Error in response: " + response.errorBody().string());
                                    } catch (IOException e) {
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Call<GeocodeResponse> call, Throwable t) {
                                Log.e(LOGTAG, "Call failure: " + t.toString());
                            }
                        });
            }
        });

    }

    private void showLogs(@NonNull URI uri) throws IOException {
        if (uri == null) {
            Toast.makeText(this, "file uri is null", Toast.LENGTH_SHORT).show();
            return;
        }
        File file = new File(uri);
        StringBuilder text = new StringBuilder();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
        } catch (IOException e) {
            Log.d("vs-dbg", "file read failed", e.getCause());
        } finally {
            if (br != null) {
                br.close();
            }
        }
        Toast.makeText(this, text.toString(), Toast.LENGTH_LONG).show();

    }
}
