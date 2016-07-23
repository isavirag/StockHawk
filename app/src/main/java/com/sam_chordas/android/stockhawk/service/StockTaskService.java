package com.sam_chordas.android.stockhawk.service;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Created by sam_chordas on 9/30/15.
 * The GCMTask service is primarily for periodic tasks. However, OnRunTask can be called directly
 * and is used for the initialization and adding task as well.
 */
public class StockTaskService extends GcmTaskService{
  private String LOG_TAG = StockTaskService.class.getSimpleName();

  private OkHttpClient client = new OkHttpClient();
  private Context mContext;
  private StringBuilder mStoredSymbols = new StringBuilder();
  private boolean isUpdate;

  public StockTaskService(){}

  public StockTaskService(Context context){
    mContext = context;
  }

  String fetchData(String url) throws IOException{
    Request request = new Request.Builder()
        .url(url)
        .build();

    Response response = client.newCall(request).execute();

    Log.d("URL Request", "fetchData: " + request.toString());
    Log.d("URL Response", "fetchData: " + response.toString());
    return response.body().string();
  }

  @Override
  public int onRunTask(TaskParams params){
    Cursor initQueryCursor;
    String historyUrl = null;

    if (mContext == null){
      mContext = this;
    }
    StringBuilder urlStringBuilder = new StringBuilder();
    try{
      // Base URL for the Yahoo query
      urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
      urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.quotes where symbol "
        + "in (", "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    if (params.getTag().equals("init") || params.getTag().equals("periodic")){
      isUpdate = true;
      //Get the list of current distinct symbols in the db
      initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
          new String[] { "Distinct " + QuoteColumns.SYMBOL }, null,
          null, null);
      //TODO: Make this code readable
      if (initQueryCursor == null || initQueryCursor.getCount() == 0){
        // Init task. Populates DB with quotes for the symbols seen below
        try {
          urlStringBuilder.append(
              URLEncoder.encode("\"YHOO\",\"AAPL\",\"GOOG\",\"MSFT\")", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
        //If it's not at init, get the current Stored Symbols from the DB and add them all to the URL
      } else {
        //dumpCursor prints the cursor contents to system, can delete this?
        DatabaseUtils.dumpCursor(initQueryCursor);
        initQueryCursor.moveToFirst();
        for (int i = 0; i < initQueryCursor.getCount(); i++){
          mStoredSymbols.append("\""+
              initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol"))+"\",");
          initQueryCursor.moveToNext();
        }
        //TODO: what is this doing?
        mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");
        try {
          urlStringBuilder.append(URLEncoder.encode(mStoredSymbols.toString(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
      }
    } else if (params.getTag().equals("add")){
      isUpdate = false;
      // get symbol from params.getExtra and build query
      String stockInput = params.getExtras().getString("symbol");

      try {
        historyUrl = buildHistoryStockUrl(stockInput);
        //Add the current query url
        urlStringBuilder.append(URLEncoder.encode("\""+stockInput+"\")", "UTF-8"));

      } catch (UnsupportedEncodingException e){
        e.printStackTrace();
      }
    }
    // finalize the URL for the API query.
    urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
        + "org%2Falltableswithkeys&callback=");


    int result = GcmNetworkManager.RESULT_FAILURE;

    if (!TextUtils.isEmpty(urlStringBuilder.toString())) {
      result = fetchAndStoreData(urlStringBuilder.toString());
    }
    if (historyUrl != null) {
      result = fetchAndStoreData(historyUrl);
    }
    return result;
  }

  private int fetchAndStoreData(String urlString) {
    try {
      String getResponse = fetchData(urlString);
      if (getResponse != null) {
        Log.d("URL", "onRunTask: " + urlString);
        try {
          ContentValues contentValues = new ContentValues();
          // update ISCURRENT to 0 (false) so new data is current
          if (isUpdate) {
            contentValues.put(QuoteColumns.ISCURRENT, 0);
            mContext.getContentResolver().update(QuoteProvider.Quotes.CONTENT_URI, contentValues,
                null, null);
          }
          //Check if the result is Null here
          ArrayList<ContentProviderOperation> resultArray =  Utils.quoteJsonToContentVals(mContext, getResponse);
          if (resultArray != null) {
            mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY,
                resultArray);
            return GcmNetworkManager.RESULT_SUCCESS;
          }
        } catch (RemoteException | OperationApplicationException e) {
          Log.e(LOG_TAG, "Error applying batch insert", e);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return GcmNetworkManager.RESULT_FAILURE;
  }

  private String buildHistoryStockUrl(String stockInput){

    StringBuilder urlStringBuilderHistory = new StringBuilder();

    //Calculate today's and 6mos prior datestamp for historical data fetching
    Calendar cal = Calendar.getInstance();
    Date now = cal.getTime();
    String nowString = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now);
    cal.add(Calendar.MONTH, -6);
    Date previous = cal.getTime();
    String prevSixMonthDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(previous);

   try {
     urlStringBuilderHistory.append("https://query.yahooapis.com/v1/public/yql?q=");
     urlStringBuilderHistory.append(URLEncoder.encode("select * from yahoo.finance.historicaldata where " +
          "symbol = \""+stockInput+"\" and startDate = \""+prevSixMonthDate+"\" and endDate = \""+nowString+"\"" , "UTF-8"));
      urlStringBuilderHistory.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
          + "org%2Falltableswithkeys&callback=");

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }

    return urlStringBuilderHistory.toString();
  }
}
