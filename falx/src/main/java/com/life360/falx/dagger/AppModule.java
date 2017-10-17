package com.life360.falx.dagger;

import android.app.Application;
import android.content.Context;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Created by remon on 7/17/17.
 */

@Module
public class AppModule {

    Context appContext;

    public AppModule(Context application) {
        appContext = application;
    }

    @Provides
    @Singleton
    Context providesApplication() {
        return appContext;
    }
}