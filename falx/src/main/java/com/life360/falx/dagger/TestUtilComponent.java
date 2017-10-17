package com.life360.falx.dagger;

import com.life360.falx.FalxApi;

import javax.inject.Singleton;

import dagger.Component;

/**
 * Created by remon on 7/17/17.
 */

@Singleton
@Component(modules = {AppModule.class, FakeDateTimeModule.class, TestLoggerModule.class, FakeFalxStoreModule.class})
public interface TestUtilComponent extends UtilComponent {
    void inject(FalxApi api);
}
