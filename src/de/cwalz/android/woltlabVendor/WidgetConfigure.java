package de.cwalz.android.woltlabVendor;

import util.TransactionsUtil;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class WidgetConfigure extends Activity {
	private int mAppWidgetId;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.options);
		setResult(RESULT_CANCELED);

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
		    mAppWidgetId = extras.getInt(
		            AppWidgetManager.EXTRA_APPWIDGET_ID, 
		            AppWidgetManager.INVALID_APPWIDGET_ID);
		}
		
		//No valid ID, so bail out.
	    if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
	    	finish();
	    }	    

	    // Check if prefs are already set, finish with RESULT_OK
        SharedPreferences settings = getSharedPreferences(WidgetProvider.PREFS_NAME, 0);
        int vendorID = settings.getInt("vendorID", 0);
        String apiKey = settings.getString("apiKey", "");
        float balance = settings.getFloat("balance", 0);

        if (vendorID != 0 && !apiKey.isEmpty()) {
        	TransactionsUtil.updateBalance(String.valueOf(balance), this);
			Intent resultValue = new Intent();
			resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
			setResult(RESULT_OK, resultValue);
        	finish();
        }    

        // config
		Button button = (Button) findViewById(R.id.save);
		button.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				TextView vendorIDTextView = (TextView) findViewById(R.id.vendorID);
				TextView apiKeyTextView = (TextView) findViewById(R.id.apiKey);
				Spinner currencySpinner = (Spinner) findViewById(R.id.currencySpinner);
				
				int vendorID = !vendorIDTextView.getText().toString().isEmpty() ? Integer.parseInt(vendorIDTextView.getText().toString().trim()) : 0;
				String apiKey = apiKeyTextView.getText().toString().trim();
				String currency = currencySpinner.getSelectedItem().toString();
				
				if (vendorID == 0 || apiKey.isEmpty() || currency.isEmpty()) {
					Toast.makeText(getApplicationContext(), getString(R.string.emptyForm), Toast.LENGTH_LONG).show();
				}
				else {
					// save new values in preferences
					SharedPreferences settings = getApplicationContext().getSharedPreferences(WidgetProvider.PREFS_NAME, 0);
				    SharedPreferences.Editor editor = settings.edit();
				    editor.putInt("vendorID", vendorID);
				    editor.putString("apiKey", apiKey);
				    editor.putString("currency", currency);
				    editor.commit();

				    // Update the widget
					WidgetProvider.forceWidgetUpdate(getApplicationContext());
					
					Intent resultValue = new Intent();
					resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
					setResult(RESULT_OK, resultValue);
					finish();
				}
					
			}
		});
	}

}