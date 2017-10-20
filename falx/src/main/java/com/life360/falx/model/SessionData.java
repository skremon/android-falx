package com.life360.falx.model;

import android.text.format.DateUtils;

import com.life360.falx.monitor.AppState;
import com.life360.falx.monitor.FalxConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by remon on 7/12/17.
 */
public class SessionData {

    public long startTime;          // in milliseconds
    public long endTime;            // in milliseconds
    private String name;

    public SessionData(AppState appState, long startTime, long endTime) {
        this.name = appState.toString();
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Get duration for the session in milliseconds.
     * @return session time in milliseconds, or 0 if the session was not recorded correctly.
     */
    public long getDuration() {
        if (startTime == 0) {
            // If a start time was not set, then we will consider the duration to be 0 milliseconds.
            return 0;
        } else {
            return endTime - startTime;
        }
    }

    public Map<String, Double> getArgumentMap() {
        Map<String, Double> extras = new HashMap<>();

        final long durationMillis = getDuration();
        extras.put(FalxConstants.PROP_DURATION, new Double((double) durationMillis / DateUtils.SECOND_IN_MILLIS));

        return extras;
    }

    @Override
    public String toString() {
        return new StringBuilder(super.toString()).append(" startTime = ").append(startTime).append(" duration = ").append(getDuration() / 1000).toString();
    }

    public String getName() {
        return name;
    }
}
