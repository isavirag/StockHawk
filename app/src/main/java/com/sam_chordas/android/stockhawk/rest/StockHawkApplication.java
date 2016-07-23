package com.sam_chordas.android.stockhawk.rest;

import android.app.Application;

import com.facebook.stetho.Stetho;

/**
 * Created by Isa on 7/21/2016.
 */
public class StockHawkApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
       // if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this);
      //  }
    }

}