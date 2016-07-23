package com.sam_chordas.android.stockhawk.rest;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by sam_chordas on 10/8/15.
 */
public class Utils {

  private static String LOG_TAG = Utils.class.getSimpleName();

  public static boolean showPercent = true;
  private static Boolean getHistory;
  public static final String ACTION_INVALID_BID_STATUS = "INVALID_STOCK_STATUS";
  public static final String INVALID_BID_KEY = "invalid stock";

  public static ArrayList<ContentProviderOperation> quoteJsonToContentVals(Context context, String JSON){
    ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();
    JSONObject jsonObject;
    JSONArray resultsArray;
    Log.d("HERE", "quoteJsonToContentVals: I AM HERE 1");
    try{
      jsonObject = new JSONObject(JSON);
      if (jsonObject != null && jsonObject.length() != 0){
        Log.d("HERE", "quoteJsonToContentVals: I AM HERE 2");
        jsonObject = jsonObject.getJSONObject("query");
        String timeStamp = jsonObject.getString("created");

        int count = Integer.parseInt(jsonObject.getString("count"));
        //Count is 0 in the case that an invalid stock name is entered in the historical search
        if(count == 0){
          return null;
        }
        //If count ==1, it means that we are adding a new stock symbol (current bid)
        else if (count == 1){
          jsonObject = jsonObject.getJSONObject("results")
              .getJSONObject("quote");
          ContentProviderOperation operation = buildBatchOperation(jsonObject, timeStamp);
          // check if it is null for Invalid stock symbol user input
          if(operation != null) {
            batchOperations.add(operation);
          }
          else {
            int invalidBid = 1;
            Intent resultIntent = new Intent(ACTION_INVALID_BID_STATUS);
            resultIntent.putExtra(INVALID_BID_KEY, invalidBid);
            context.sendBroadcast(resultIntent);
            return null;
          }
        } else {
          resultsArray = jsonObject.getJSONObject("results").getJSONArray("quote");

          if (resultsArray != null && resultsArray.length() != 0){
            for (int i = 0; i < resultsArray.length(); i++){
              jsonObject = resultsArray.getJSONObject(i);
              ContentProviderOperation operation = buildBatchOperation(jsonObject, timeStamp);
              if(operation != null){
                batchOperations.add(operation);
              }
            }
          }
        }
      }
    } catch (JSONException e){
      Log.e(LOG_TAG, "String to JSON failed: " + e);
    }
    Log.d("HERE", "quoteJsonToContentVals: I AM HERE 8");
    return batchOperations;
  }

  public static String truncateBidPrice(String bidPrice){
    bidPrice = String.format(Locale.US, "%.2f", Float.parseFloat(bidPrice));
    return bidPrice;
  }

  public static String truncateChange(String change, boolean isPercentChange){
    String weight = change.substring(0,1);
    String ampersand = "";
    if (isPercentChange){
      ampersand = change.substring(change.length() - 1, change.length());
      change = change.substring(0, change.length() - 1);
    }
    change = change.substring(1, change.length());
    double round = (double) Math.round(Double.parseDouble(change) * 100) / 100;
    change = String.format(Locale.US, "%.2f", round);
    StringBuilder changeBuffer = new StringBuilder(change);
    changeBuffer.insert(0, weight);
    changeBuffer.append(ampersand);
    change = changeBuffer.toString();
    return change;
  }

  public static ContentProviderOperation buildBatchOperation(JSONObject jsonObject, String timeStamp){
    ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(
        QuoteProvider.Quotes.CONTENT_URI);
    Log.d("TEST", "buildBatchOperation: I AM HERE");

    try {
      //CHECK if value is null --> meaning the stock name provided is invalid
      if(!jsonObject.isNull("Bid")){
        getHistory = true;
        Log.d("HERE", "quoteJsonToContentVals: I AM HERE 9");
        String change = jsonObject.getString("Change");
        builder.withValue(QuoteColumns.SYMBOL, jsonObject.getString("symbol"));
        builder.withValue(QuoteColumns.BIDPRICE, truncateBidPrice(jsonObject.getString("Bid")));
        builder.withValue(QuoteColumns.PERCENT_CHANGE, truncateChange(
                jsonObject.getString("ChangeinPercent"), true));
        builder.withValue(QuoteColumns.CHANGE, truncateChange(change, false));
        builder.withValue(QuoteColumns.CREATED, timeStamp);
        builder.withValue(QuoteColumns.ISCURRENT, 1);

      }else if(getHistory && !jsonObject.isNull("Close")){
        Log.d("HERE", "quoteJsonToContentVals: I AM HERE 9-2");
        builder.withValue(QuoteColumns.SYMBOL, jsonObject.getString("Symbol"));
        builder.withValue(QuoteColumns.BIDPRICE, truncateBidPrice(jsonObject.getString("Close")));
        builder.withValue(QuoteColumns.PERCENT_CHANGE, "+0%");
        builder.withValue(QuoteColumns.CHANGE, "+0");
        builder.withValue(QuoteColumns.CREATED, jsonObject.getString("Date"));
        builder.withValue(QuoteColumns.ISCURRENT, 0);

      }else {
        getHistory = false;
        //TODO: Return error somehow
        Log.d("HERE", "quoteJsonToContentVals: I AM HERE 10");
        return null;
      }
    } catch (JSONException e){
      e.printStackTrace();
    }
    return builder.build();
  }
}
