package net.amdroid.noitehoje.ui;

import java.net.URI;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import net.amdroid.noitehoje.R;
import net.amdroid.noitehoje.provider.NoiteHojeProvider;
import net.amdroid.noitehoje.util.AnalyticsUtils;
import net.amdroid.noitehoje.util.UIUtils;

import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.AbstractAction;
import com.markupartist.android.widget.ActionBar.IntentAction;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebView;
import android.util.Log;

public class NoiteHojeEventWeb extends Activity {
	private static final String TAG = "NoiteHojeEventWeb";
	private Context mContext;
	private String contentUri = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.detail_webview);

		mContext = this;

		Intent intent = getIntent();
		contentUri = intent.getStringExtra("content_uri");

		Uri uri = Uri.parse(contentUri);
		ContentResolver cr = getContentResolver();
		Cursor cursor = cr.query(uri, null, null, null, null);

		if (cursor.moveToFirst()) {
			String content = cursor.getString(cursor
					.getColumnIndex(NoiteHojeProvider.KEY_DESCRIPTION));
			String html = UIUtils.readRawTextFile(this, R.raw.html_description);
			html = html.replace("%%%DESCRIPTION%%%", Uri.encode(content));
			WebView webview = (WebView) findViewById(R.id.webDetail);
			webview.setBackgroundColor(0);
			webview.loadData(html, "text/html", "utf-8");
		}

		ActionBar actionBar = (ActionBar) findViewById(R.id.detailwebActionBar);

		if (cursor.getCount() == 1) {
			String title = cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_TITLE));
			String event_id = cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_EVENT_ID));
			String type = cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_EVENT_TYPE));
			if (type.contentEquals("party"))
				actionBar.setHomeAction(new PartyAction());
			else
				actionBar.setHomeAction(new ShowAction());
			AnalyticsUtils.getInstance(mContext).trackPageView("/Android/details/" + event_id + "/" + title);
		} else {
			actionBar.setHomeAction(new IntentAction(this, NoiteHoje
				.createIntent(this), R.drawable.ic_title_home_default));
		}

		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeLogo(R.drawable.ab_logo);
		actionBar.showHomeLogo();
		actionBar.addAction(new IntentAction(this, UIUtils.createShareIntent(cursor),
				R.drawable.ic_title_share_default));
		if (cursor.getInt(cursor.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_AVAILABLE)) != 0)
			actionBar.addAction(new MapAction());
	}

	private class PartyAction extends AbstractAction {
		public PartyAction() {
			super(R.drawable.party);
		}

		@Override
		public void performAction(View view) {
			finish();
		}
	}

	private class ShowAction extends AbstractAction {
		public ShowAction() {
			super(R.drawable.show);
		}

		@Override
		public void performAction(View view) {
			finish();
		}
	}

	private class MapAction extends AbstractAction {
		public MapAction() {
				super(R.drawable.ic_actionbar_maps);
		}

		@Override
		public void performAction(View view) {
			Intent intent = new Intent(mContext, NoiteHojeMap.class);
			intent.putExtra("content_uri", contentUri);
			startActivity(intent);
		}
	}

	private class ExampleAction extends AbstractAction {

		public ExampleAction() {
			super(R.drawable.ic_title_export_default);
		}

		@Override
		public void performAction(View view) {
			Toast.makeText(NoiteHojeEventWeb.this, "Example action",
					Toast.LENGTH_SHORT).show();
		}

	}

}
