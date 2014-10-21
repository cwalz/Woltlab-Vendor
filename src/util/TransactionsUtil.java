package util;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import sql.TransactionsDataSource;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import de.cwalz.android.woltlabVendor.ICallback;
import de.cwalz.android.woltlabVendor.R;
import de.cwalz.android.woltlabVendor.WidgetProvider;

public final class TransactionsUtil {
	public final static void update(final Context context, final ICallback callback) {
        // restore preferences
        SharedPreferences settings = context.getSharedPreferences(WidgetProvider.PREFS_NAME, 0);
        final int lastTransactionID = settings.getInt("lastTransactionID", 0);
        float balance = settings.getFloat("balance", 0.f);
        Log.i(WidgetProvider.LOG_TAG, "saved lastTransactionID: " + String.valueOf(lastTransactionID));
        Log.i(WidgetProvider.LOG_TAG, "saved balance: " + String.valueOf(balance));
        
        final int vendorID = settings.getInt("vendorID", 0);
        final String apiKey = settings.getString("apiKey", "");
        
        // return false if configuration is not done yet
        if (vendorID == 0 || apiKey.isEmpty()) {
        	callback.onSuccess(0);
        	Log.i(WidgetProvider.LOG_TAG, "Configuration not done yet, return");
        	Toast.makeText(context, context.getString(R.string.configurationNotDone), Toast.LENGTH_LONG).show();
        	return;
        }
        
    	
    	RequestParams params = new RequestParams();
    	params.put("vendorID", vendorID);
    	params.put("apiKey", apiKey);
    	params.put("lastTransactionID", (lastTransactionID != 0 ? String.valueOf(lastTransactionID) : "-1"));
    	
    	AsyncHttpClient client = new AsyncHttpClient();
    	client.setUserAgent("WoltlabVendor Android/1.0");
    	client.post("https://www.woltlab.com/api/1.1/vendor/transaction/list.json", params, new JsonHttpResponseHandler() {
    	    public void onStart() {
    	    	Log.i(WidgetProvider.LOG_TAG, "Starting POST Request");
    	    	Log.i(WidgetProvider.LOG_TAG, "vendorID: " + vendorID);
    	    	Log.i(WidgetProvider.LOG_TAG, "apiKey: " + apiKey);
    	    	Log.i(WidgetProvider.LOG_TAG, "lastTransactionID: " + lastTransactionID);
    	    }
    	    
    	    @Override
			public void onFailure(int statusCode, Header[] headers,
					String responseString, Throwable throwable) {
				super.onFailure(statusCode, headers, responseString, throwable);
				Log.i("FAILURE", responseString);
				Log.i("FAILURE", String.valueOf(statusCode));
				Log.i("FAUL", throwable.getMessage());
			}
    	    
			public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
				int status = 0;
				int count = 0;
				float newBalance = 0;
				int newLastTransactionID = 0;
				
				try {
					status = response.getInt("status");
					count = response.getInt("count");
				} catch (JSONException e) {}
				
				Log.i(WidgetProvider.LOG_TAG, "status: " + String.valueOf(status));
				Log.i(WidgetProvider.LOG_TAG, "count: " + String.valueOf(count));
				
				switch (status) {
					case 200:
						// check if count is 0. That means we have no new transactions since the last update.
						if (count == 0) {
							callback.onSuccess(0);
							Log.i(WidgetProvider.LOG_TAG, "No new transactions found, returning.");
							return;
						}
						// save lastTransactionID and balance
						try {
							final JSONArray transactions = response.getJSONArray("transactions");
							
							// get the last transaction object
							JSONObject lastTransaction = transactions.getJSONObject(transactions.length()-1);
							
							newBalance = (float) lastTransaction.getDouble("balance");
							newLastTransactionID = lastTransaction.getInt("transactionID");
							
							// save new values in preferences
							SharedPreferences settings = context.getSharedPreferences(WidgetProvider.PREFS_NAME, 0);
						    SharedPreferences.Editor editor = settings.edit();
						    editor.putInt("lastTransactionID", newLastTransactionID);
						    editor.putFloat("balance", newBalance);
						    editor.commit();
						    
							// save transactions to database (separate thread, this will take some time on initial load)
							new Thread() {
							    public void run() {
							    	updateDatabase(transactions, context, callback);
							    }
							}.start();
						      
							Log.i(WidgetProvider.LOG_TAG, "New balance: " + String.valueOf(newBalance));
							Log.i(WidgetProvider.LOG_TAG, "New lastTransactionID: " + String.valueOf(newLastTransactionID));
						} catch (JSONException e) {}
					break;
					
					case 400:
					case 401:
					case 426:
					case 500:
					case 501:
						String errorMessage = "API Error";
						try {
							errorMessage = response.getString("errorMessage");
						} catch (JSONException e) {}
						Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();
						
						//callback
						callback.onFailure(errorMessage);
					break;
				}
			}
    	});		
	}
	
    public static void updateDatabase(JSONArray transactions, Context context, ICallback callback) {
    	int length = transactions.length();
    	
    	TransactionsDataSource datasource = new TransactionsDataSource(context);
        datasource.open();
        datasource.beginTransaction();
    	Log.i(WidgetProvider.LOG_TAG, "starting update Database");
    	try {
	    	for (int i = 0; i < length; i++) {
	    		JSONObject transaction = transactions.getJSONObject(i);
	    		
	    		int fileID = 0;
	    		int woltlabID = 0;
	    		if (!transaction.isNull("fileID")) {
	    			fileID = transaction.getInt("fileID");
	    		}
	    		if (!transaction.isNull("woltlabID")) {
	    			woltlabID = transaction.getInt("woltlabID");
	    		}
	    		int withdrawal = (transaction.getBoolean("withdrawal")) ? 1 : 0;
	    		
	    		datasource.create(transaction.getInt("transactionID"), fileID, transaction.getString("reason"), transaction.getInt("time"), withdrawal, woltlabID, Float.parseFloat(transaction.getString("balance")));
	    	}
	    	datasource.setTransactionSuccessful();
    	} 
    	catch (JSONException e) {
			e.printStackTrace();
		} 
    	finally {
    		datasource.endTransaction();
    	}
    	Log.i(WidgetProvider.LOG_TAG, "finished updating Database");
    	
    	// call callback
		SharedPreferences settings = context.getSharedPreferences(WidgetProvider.PREFS_NAME, 0);
    	callback.onSuccess(settings.getFloat("balance", 0));
    }
    
    public static void updateBalance(String newBalance, Context context) {
		// get currency
		SharedPreferences settings = context.getSharedPreferences(WidgetProvider.PREFS_NAME, 0);
		String currency = settings.getString("currency", "EUR");
		
    	AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
    	int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
		
        for (int appWidgetID : appWidgetIds) {
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget);
            if (newBalance.equals(WidgetProvider.LOADING_STRING))
            	remoteViews.setTextViewText(R.id.balance, newBalance);
            else
            	remoteViews.setTextViewText(R.id.balance, currency + " " + newBalance);
            remoteViews.setOnClickPendingIntent(R.id.layout, getPendingSelfIntent(context, WidgetProvider.ACTION_UPDATE_CLICK));

            appWidgetManager.updateAppWidget(appWidgetID, remoteViews);
        }    	
    }
    
    public static PendingIntent getPendingSelfIntent(Context context, String action) {
        // An explicit intent directed at the current class (the "self").
        Intent intent = new Intent(context, TransactionsUtil.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }
}