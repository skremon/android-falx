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
import com.life360.falx.monitor.AppState;
import com.life360.falx.monitor.AppStateListener;
import com.life360.falx.monitor.AppStateMonitor;
import com.life360.falx.monitor.Monitor;
import com.life360.falx.monitor.NetworkMonitor;
import com.life360.falx.monitor_store.AggregatedFalxMonitorEvent;
import com.life360.falx.monitor_store.FalxEventStorable;
import com.life360.falx.network.FalxInterceptor;
import com.life360.falx.util.Logger;

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
                monitors.put(MONITOR_APP_STATE, new AppStateMonitor(utilComponent, appStateObservable()));
            }
        }

        if ((monitorFlags & MONITOR_NETWORK) == MONITOR_NETWORK) {
            if (!monitors.containsKey(MONITOR_NETWORK)) {
                monitors.put(MONITOR_NETWORK, new NetworkMonitor(utilComponent, getNetworkActivityObservable()));
            }
        }
        // todo: and so on

        for (Monitor monitor : monitors.values()) {
            eventStorable.subscribeToEvents(monitor.getEventObservable());
        }
    }

    public void removeAllMonitors() {
        if (!monitors.isEmpty()) {
            monitors.clear();
        }
    }

    public boolean isMonitorActive(int monitorId) {
        return monitors.containsKey(monitorId);
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


    private static volatile FalxApi falxApi = null;


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Inject
    Logger logger;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    List<AppStateListener> appStateListeners;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Inject
    FalxEventStorable eventStorable;

    @Inject
    Context application;        // Application application

    private UtilComponent utilComponent;


    // Maps MonitorId -> Monitor
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected HashMap<Integer, Monitor> monitors = new HashMap<>();

    private FalxInterceptor falxInterceptor;
    private PublishSubject<NetworkActivity> networkActivitySubject = PublishSubject.create();


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

        //eventStorable = new FalxEventStore(new FalxRealm(), context);
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

    private Observable<NetworkActivity> getNetworkActivityObservable() {
        return networkActivitySubject;
    }

    private Observer<NetworkActivity> getNetworkActivityObserver() {
        return networkActivitySubject;
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
     * @param eventName name of event
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
}
