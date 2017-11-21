package com.life360.falx;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.life360.falx.dagger.AppModule;
import com.life360.falx.dagger.DaggerUtilComponent;
import com.life360.falx.dagger.DateTimeModule;
import com.life360.falx.dagger.FalxStoreModule;
import com.life360.falx.dagger.LoggerModule;
import com.life360.falx.dagger.UtilComponent;
import com.life360.falx.model.NetworkActivity;
import com.life360.falx.model.RealtimeMessagingActivity;
import com.life360.falx.monitor.AppState;
import com.life360.falx.monitor.AppStateListener;
import com.life360.falx.monitor.AppStateMonitor;
import com.life360.falx.monitor.Monitor;
import com.life360.falx.monitor.NetworkMonitor;
import com.life360.falx.monitor.OnOffMonitor;
import com.life360.falx.monitor.OnOffStateListener;
import com.life360.falx.monitor.RealtimeMessagingMonitor;
import com.life360.falx.monitor.RealtimeMessagingSession;
import com.life360.falx.monitor_store.AggregatedFalxMonitorEvent;
import com.life360.falx.monitor_store.FalxEventStorable;
import com.life360.falx.network.FalxInterceptor;
import com.life360.falx.util.Logger;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.functions.Cancellable;
import io.reactivex.subjects.PublishSubject;

/**
 * Created by remon on 6/27/17.
 */
public class FalxApi {

    public static final int MONITOR_APP_STATE = 0x01;
    public static final int MONITOR_NETWORK = 0x02;
    public static final int MONITOR_REALTIME_MESSAGING = 0x03;

    // .. and so on

    public static FalxApi getInstance(Context context) {
        if (falxApi == null) {
            synchronized (FalxApi.class) {
                if (falxApi == null) {
                    falxApi = new FalxApi(context);
                }
            }
        }

        return falxApi;
    }

    /**
     * Add 1 or more Monitors using a integer to specify which monitors to add.
     * The monitor flags are specified by integer constants MONITOR_*
     * If a monitor was added before it will remain added, and only one insteance of a monitor
     * shall exist with the FalxApi singleton.
     *
     * @param monitorFlags
     */
    public void addMonitors(int monitorFlags) {

        if ((monitorFlags & MONITOR_APP_STATE) == MONITOR_APP_STATE) {
            if (!monitors.containsKey(MONITOR_APP_STATE)) {
                AppStateMonitor appStateMonitor = new AppStateMonitor(utilComponent, appStateObservable());
                monitors.put(MONITOR_APP_STATE, appStateMonitor);
                eventStorable.subscribeToEvents(appStateMonitor.getEventObservable());
            }
        }

        if ((monitorFlags & MONITOR_NETWORK) == MONITOR_NETWORK) {
            if (!monitors.containsKey(MONITOR_NETWORK)) {
                NetworkMonitor networkMonitor = new NetworkMonitor(utilComponent, getNetworkActivityObservable());
                monitors.put(MONITOR_NETWORK, networkMonitor);
                eventStorable.subscribeToEvents(networkMonitor.getEventObservable());
            }
        }

        if ((monitorFlags & MONITOR_REALTIME_MESSAGING) == MONITOR_REALTIME_MESSAGING) {
            if (!monitors.containsKey(MONITOR_REALTIME_MESSAGING)) {
                RealtimeMessagingMonitor realtimeMessagingMonitor = new RealtimeMessagingMonitor(utilComponent, getRealtimeMessagingObservable(), getRealtimeMessagingSessionObservable());
                monitors.put(MONITOR_REALTIME_MESSAGING, realtimeMessagingMonitor);
                eventStorable.subscribeToEvents(realtimeMessagingMonitor.getEventObservable());
            }
        }
        // todo: and so on
    }

    public void addOnOffMonitor(@NonNull String monitorLabel, @NonNull String metricName) {
        if (!onOffMonitors.containsKey(monitorLabel)) {
            OnOffMonitor monitor = new OnOffMonitor(utilComponent, getOnOffObservable(monitorLabel), metricName, monitorLabel);
            onOffMonitors.put(monitorLabel, monitor);
            eventStorable.subscribeToEvents(monitor.getEventObservable());
        }
    }

    public void removeAllMonitors() {
        if (!monitors.isEmpty()) {

            for (Monitor monitor : monitors.values()) {
                monitor.stop();
            }

            monitors.clear();
        }
        if (!onOffMonitors.isEmpty()) {

            for (Monitor monitor : onOffMonitors.values()) {
                monitor.stop();
            }

            onOffMonitors.clear();
        }

        eventStorable.clearSubscriptions();
    }

    public boolean isMonitorActive(int monitorId) {
        return monitors.containsKey(monitorId);
    }
    public boolean isMonitorActive(@NonNull String monitorLabel) {
        return onOffMonitors.containsKey(monitorLabel);
    }

    /**
     * Marks start of a session, call when a Activity comes to the foreground (Activity.onStart)
     *
     * @param activity
     * @return
     */
    public boolean startSession(Activity activity) {
        onAppStateForeground();
        return true;
    }

    /**
     * * Marks start of a session, call when a Activity is removed from the foreground (Activity.onStop)
     *
     * @param activity
     * @return
     */
    public boolean endSession(Activity activity) {
        onAppStateBackground();
        return true;
    }

    public void turnedOn(@NonNull String label) {
        if (onOffListeners.containsKey(label)) {
            onOffListeners.get(label).turnedOn();
        }
    }

    public void turnedOff(@NonNull String label) {
        if (onOffListeners.containsKey(label)) {
            onOffListeners.get(label).turnedOff();
        }
    }

    public void turnedOnOff(@NonNull String label, long durationMs) {
        if (onOffListeners.containsKey(label)) {
            onOffListeners.get(label).turnedOnOff(durationMs);
        }
    }

    /**
     * Call to log an event describing the receipt of a real-time message.
     * @param activity
     */
    public void realtimeMessageReceived(final RealtimeMessagingActivity activity) {
        if (isMonitorActive(MONITOR_REALTIME_MESSAGING)) {
            realtimeMessagingObservable.onNext(activity);
        } else {
            logger.e(Logger.TAG, "MONITOR_REALTIME_MESSAGING is not active!");
        }
    }

    /**
     * Call to log an event to log data for a real-time messaging session.
     * @param session
     */
    public void realtimeMessageSessionCompleted(final RealtimeMessagingSession session) {
        if (isMonitorActive(MONITOR_REALTIME_MESSAGING)) {
            realtimeMessagingSessionObservable.onNext(session);
        } else {
            logger.e(Logger.TAG, "MONITOR_REALTIME_MESSAGING is not active!");
        }

    }

    private static volatile FalxApi falxApi = null;


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Inject
    Logger logger;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    List<AppStateListener> appStateListeners;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Inject
    FalxEventStorable eventStorable;

    HashMap<String, OnOffStateListener> onOffListeners;

    @Inject
    Context application;        // Application application

    private UtilComponent utilComponent;

    private boolean loggingEnabled;


    // Maps MonitorId -> Monitor
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected HashMap<Integer, Monitor> monitors = new HashMap<>();
    private HashMap<String, Monitor> onOffMonitors = new HashMap<>();

    private FalxInterceptor falxInterceptor;
    private PublishSubject<NetworkActivity> networkActivitySubject = PublishSubject.create();
    private PublishSubject<RealtimeMessagingActivity> realtimeMessagingObservable = PublishSubject.create();
    private PublishSubject<RealtimeMessagingSession> realtimeMessagingSessionObservable = PublishSubject.create();

    private FalxApi(@NonNull Context context) {
        // Create our UtilComponent module, since it will be only used by FalxApi
        UtilComponent utilComponent = DaggerUtilComponent.builder()
                // list of modules that are part of this component need to be created here too
                .appModule(new AppModule(context.getApplicationContext()))
                .dateTimeModule(new DateTimeModule())
                .loggerModule(new LoggerModule())
                .falxStoreModule(new FalxStoreModule())
                .build();


        init(context, utilComponent);
    }

    /**
     * Test code can pass in a TestUtilsComponent to this special constructor to inject
     * fake objects instead of what is provided by UtilComponent
     *
     * @param context
     * @param utilComponent
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    FalxApi(@NonNull Context context, @NonNull UtilComponent utilComponent) {

        // Note: Call to Realm.init is omitted as we should not use Realm in unit tests.
        init(context, utilComponent);
    }

    private void init(Context context, UtilComponent utilComponent) {
        this.application = context.getApplicationContext();

        // Create our UtilComponent module, since it will be only used by FalxApi
        this.utilComponent = utilComponent;
        this.utilComponent.inject(this);

        appStateListeners = new ArrayList<>();
        onOffListeners = new HashMap<>();
        onOffMonitors = new HashMap<>();
    }

    public void addAppStateListener(AppStateListener listener) {
        appStateListeners.add(listener);
    }

    public void removeAppStateListener(AppStateListener listener) {
        appStateListeners.remove(listener);
    }

    public void removeAllAppStateListeners() {
        appStateListeners.clear();
    }

    public void enableLogging(boolean enable) {
        logger.setEnabled(enable);
        if (falxInterceptor != null) {
            falxInterceptor.enableLogging(enable);
        }
    }

    @VisibleForTesting
    void onAppStateForeground() {
        for (AppStateListener listener : appStateListeners) {
            listener.onEnterForeground();
        }
    }

    @VisibleForTesting
    void onAppStateBackground() {
        for (AppStateListener listener : appStateListeners) {
            listener.onEnterBackground();
        }
    }

    /**
     * Get a Interceptor that can be added to OkHttpClient.
     *
     * @return a instance of FalxInterceptor that will allow the network monitor to log data about network activity
     */
    public FalxInterceptor getInterceptor() {
        if (falxInterceptor == null) {
            falxInterceptor = new FalxInterceptor(application, getNetworkActivityObserver());
        }

        return falxInterceptor;
    }


    @VisibleForTesting
    Observable<AppState> appStateObservable() {

        return Observable.create(new ObservableOnSubscribe<AppState>() {
            @Override
            public void subscribe(@io.reactivex.annotations.NonNull final ObservableEmitter<AppState> appStateEmitter) throws Exception {

                final AppStateListener appStateListener = new AppStateListener() {
                    @Override
                    public void onEnterBackground() {
                        appStateEmitter.onNext(AppState.BACKGROUND);
                    }

                    @Override
                    public void onEnterForeground() {
                        appStateEmitter.onNext(AppState.FOREGROUND);
                    }
                };

                addAppStateListener(appStateListener);

                appStateEmitter.setCancellable(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        removeAppStateListener(appStateListener);
                    }
                });
            }
        });
    }

    Observable<Boolean> getOnOffObservable(final String monitorLabel) {
        return Observable.create(new ObservableOnSubscribe<Boolean>() {
            @Override
            public void subscribe(final ObservableEmitter<Boolean> emitter) throws Exception {
                OnOffStateListener onOffListener = new OnOffStateListener() {

                    @Override
                    public void turnedOn() {
                        emitter.onNext(true);
                    }

                    @Override
                    public void turnedOff() {
                        emitter.onNext(false);
                    }

                    @Override
                    public void turnedOnOff(long durationMs) {
                        emitter.onNext(true);
                        emitter.onNext(false);
                    }
                };
                onOffListeners.put(monitorLabel, onOffListener);
                emitter.setCancellable(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        onOffListeners.remove(monitorLabel);
                    }
                });
            }
        });
    }

    private Observable<NetworkActivity> getNetworkActivityObservable() {
        return networkActivitySubject;
    }

    private Observer<NetworkActivity> getNetworkActivityObserver() {
        return networkActivitySubject;
    }

    public Observable<RealtimeMessagingActivity> getRealtimeMessagingObservable() {
        return realtimeMessagingObservable;
    }

    public Observable<RealtimeMessagingSession> getRealtimeMessagingSessionObservable() {
        return realtimeMessagingSessionObservable;
    }

    /**
     * get aggregated events for the provided event
     *
     * @param eventName name of event
     * @return list of aggregated Falx monitor events
     */
    public List<AggregatedFalxMonitorEvent> aggregateEvents(String eventName) {
        if (eventStorable != null) {
            return eventStorable.aggregateEvents(eventName);
        }
        return null;
    }

    /**
     * get aggregated events for the provided event, also partial day option is available
     *
     * @param eventName        name of event
     * @param allowPartialDays if true partial day's data also included
     * @return list of aggregated Falx monitor events
     */
    public List<AggregatedFalxMonitorEvent> aggregatedEvents(String eventName, boolean allowPartialDays) {
        if (eventStorable != null) {
            return eventStorable.aggregatedEvents(eventName, allowPartialDays);
        }
        return null;
    }

    /**
     * get all aggregated events
     *
     * @param allowPartialDays if true partial day's data also included
     * @return list of aggregated Falx monitor events
     */
    public List<AggregatedFalxMonitorEvent> allAggregatedEvents(boolean allowPartialDays) {
        if (eventStorable != null) {
            return eventStorable.allAggregatedEvents(allowPartialDays);
        }
        return null;
    }

    /**
     * get JSON file URI which contains all Falx events
     *
     * @param fileName provide name to the JSON file created e.g Falx_Logs_(Users’ Email)_d_(user’s device ID)_u_(user’s user id)
     * @return URI of the newly created file
     */
    public URI eventToJSON(@NonNull String fileName) {
        if (eventStorable != null) {
            return eventStorable.eventToJSONFile(fileName);
        }
        return null;
    }

}
