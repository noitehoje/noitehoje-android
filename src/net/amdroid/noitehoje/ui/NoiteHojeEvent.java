package net.amdroid.noitehoje.ui;

import java.net.URI;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

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
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class NoiteHojeEvent extends Activity {

	private static final String TAG = "NoiteHojeEvent";

	private SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.US);
	private SimpleDateFormat tf = new SimpleDateFormat("HH:mm");

	private Context mContext = null;
	private Cursor mCursor = null;
	private String contentUri = null;
	private String venue;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.detail);

		Intent intent = getIntent();
		contentUri = intent.getStringExtra("content_uri");

		mContext = this;

		Uri uri = Uri.parse(contentUri);
		ContentResolver cr = getContentResolver();
		mCursor = cr.query(uri, null, null, null, null);
		String title = null;

		ActionBar actionBar = (ActionBar) findViewById(R.id.detailActionBar);
		actionBar.setHomeAction(new IntentAction(this, NoiteHoje
				.createIntent(this), R.drawable.ic_title_home_default));
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeLogo(R.drawable.ab_logo);
		actionBar.showHomeLogo();

		if (mCursor.moveToFirst()) {
			String content = DatabaseUtils.dumpCurrentRowToString(mCursor);
			Log.d(TAG, "event: " + content);

			TextView what = (TextView) findViewById(R.id.textWhat);
			title = mCursor.getString(mCursor.getColumnIndex(NoiteHojeProvider.KEY_TITLE));
			what.setText(Html.fromHtml(title).toString());

			/*
			 * TODO - check if this event has description if it doesn't have,
			 * make sure this view is not selectble
			 */
			View whatLabel = (View) findViewById(R.id.whatTextLabel);
			if (TextUtils.isEmpty(mCursor.getString(mCursor
					.getColumnIndex(NoiteHojeProvider.KEY_DESCRIPTION)))) {
				whatLabel.setClickable(false);
				ImageView img = (ImageView) findViewById(R.id.imageInfo);
				img.setImageDrawable(mContext.getResources().getDrawable(
						R.drawable.ic_none));
			} else {
				whatLabel.setTag(contentUri);
				whatLabel.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						String uri = (String) v.getTag();
						Intent intent = new Intent(mContext,
								NoiteHojeEventWeb.class);
						intent.putExtra("content_uri", uri);
						startActivity(intent);
					}
				});
			}

			String date = mCursor.getString(mCursor
					.getColumnIndex(NoiteHojeProvider.KEY_START_DATE));
			String time = mCursor.getString(mCursor
					.getColumnIndex(NoiteHojeProvider.KEY_START_TIME));

			TextView when = (TextView) findViewById(R.id.textWhen);
			try {
				Date dt = df.parse(date);

				Calendar cal = Calendar.getInstance();
				cal.setTime(dt);

				String eventDate = cal.get(Calendar.DAY_OF_MONTH) + "/"
						+ (cal.get(Calendar.MONTH) + 1) + "/"
						+ cal.get(Calendar.YEAR);

				DecimalFormat decf = new DecimalFormat("00");
				if (!TextUtils.isEmpty(time)) {
					Date tm = tf.parse(time);
					cal.set(Calendar.HOUR_OF_DAY, tm.getHours());
					cal.set(Calendar.MINUTE, tm.getMinutes());

					eventDate += " - "
							+ decf.format(cal.get(Calendar.HOUR_OF_DAY));
					eventDate += ":" + decf.format(cal.get(Calendar.MINUTE));
				}

				when.setText(eventDate);
			} catch (ParseException e) {
				when.setText("Não informado");
				e.printStackTrace();
			}

			TextView where = (TextView) findViewById(R.id.textWhere);
			String whereStr = mCursor.getString(mCursor
					.getColumnIndex(NoiteHojeProvider.KEY_VENUE_NAME));
			String street = mCursor.getString(mCursor
					.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_STREET));
			if (!TextUtils.isEmpty(street))
				whereStr += "<br>" + street;

			String city = mCursor.getString(mCursor
					.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_CITY));
			if (!TextUtils.isEmpty(city))
				whereStr += "<br>" + city;

			String phone = mCursor.getString(mCursor.getColumnIndex(NoiteHojeProvider.KEY_VENUE_PHONE));
			String url = mCursor.getString(mCursor.getColumnIndex(NoiteHojeProvider.KEY_VENUE_URL));
			/*if (!TextUtils.isEmpty(phone) || !TextUtils.isEmpty(url))
				whereStr += "<br>";*/

			if (!TextUtils.isEmpty(phone) && !phone.contentEquals("null"))
				whereStr += "<br><br>" + phone;
			if (!TextUtils.isEmpty(url))
				whereStr += "<br><br>" + url;

			where.setText(Html.fromHtml(whereStr).toString());

			TextView source = (TextView) findViewById(R.id.textSource);
			source.setText(mCursor.getString(mCursor
					.getColumnIndex(NoiteHojeProvider.KEY_SOURCE)));

			String event_id = mCursor.getString(mCursor.getColumnIndex(NoiteHojeProvider.KEY_EVENT_ID));	
			AnalyticsUtils.getInstance(mContext).trackPageView("/Android/event/" + event_id + "/" + title);

			actionBar.addAction(new IntentAction(this, UIUtils.createShareIntent(mCursor),
					R.drawable.ic_title_share_default));

			if (mCursor.getInt(mCursor.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_AVAILABLE)) != 0)
				actionBar.addAction(new MapAction());

			venue = mCursor.getString(mCursor.getColumnIndex(NoiteHojeProvider.KEY_VENUE_ID));
			if (UIUtils.getDBCount(this, NoiteHojeProvider.KEY_VENUE_ID + "=?", new String[] { venue } ) > 1) {
				View whereLabel = (View) findViewById(R.id.whereTextLabel);
				whereLabel.setClickable(true);
				ImageView more = (ImageView) findViewById(R.id.imageShowMore);
				more.setVisibility(View.VISIBLE);
				whereLabel.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent intent = new Intent(mContext,
								NoiteHojeVenueEventsList.class);
						intent.putExtra("content_uri", contentUri);
						intent.putExtra("venue_id", venue);
						startActivity(intent);
					}
				});
			}
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
			Toast.makeText(NoiteHojeEvent.this, "Example action",
					Toast.LENGTH_SHORT).show();
		}

	}

}
