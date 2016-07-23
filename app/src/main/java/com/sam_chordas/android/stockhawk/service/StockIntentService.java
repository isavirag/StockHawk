package com.sam_chordas.android.stockhawk.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.TaskParams;

/**
 * Created by sam_chordas on 10/1/15.
 */
public class StockIntentService extends IntentService {

  private int result = 1;

  public static final String ACTION_FETCH_SYMBOL_RESULT = "FETCH_SYMBOL_RESULT";
  public static final String RESULT_KEY = "result key";

  public StockIntentService(){
    super(StockIntentService.class.getName());
  }

  public StockIntentService(String name) {
    super(name);
  }

  @Override protected void onHandleIntent(Intent intent) {
    Log.d(StockIntentService.class.getSimpleName(), "Stock Intent Service");
    StockTaskService stockTaskService = new StockTaskService(this);
    Bundle args = new Bundle();
    if (intent.getStringExtra("tag").equals("add")){
      args.putString("symbol", intent.getStringExtra("symbol"));
    }
    // We can call OnRunTask from the intent service to force it to run immediately instead of
    // scheduling a task.
    result = stockTaskService.onRunTask(new TaskParams(intent.getStringExtra("tag"), args));

    Intent resultIntent = new Intent(ACTION_FETCH_SYMBOL_RESULT);
    resultIntent.putExtra(RESULT_KEY, result);
    sendBroadcast(resultIntent);
  }
}
