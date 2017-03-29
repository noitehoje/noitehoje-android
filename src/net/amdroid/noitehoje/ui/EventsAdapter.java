package net.amdroid.noitehoje.ui;

import java.text.DecimalFormat;
import java.util.Date;

import net.amdroid.noitehoje.R;
import net.amdroid.noitehoje.provider.NoiteHojeProvider;
import net.amdroid.noitehoje.util.AnalyticsUtils;
import net.amdroid.noitehoje.util.UIUtils;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;


public class EventsAdapter extends CursorAdapter {

	private static final String TAG = "NoiteHojeEventsAdapter";

	private Cursor mCursor;
	private Context mContext;
	private final LayoutInflater mInflater;

	public EventsAdapter(Context context, Cursor cursor, boolean autoRequery) {
		super(context, cursor, autoRequery);
		mContext = context;
		mCursor = cursor;

		mInflater = LayoutInflater.from(context);
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		TextView text = (TextView) view.findViewById(R.id.eventName);
		String name = cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_TITLE));
		text.setText(Html.fromHtml(name).toString());

		text = (TextView) view.findViewById(R.id.eventDate);
		String when = UIUtils.partyTime(cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_EVENT_TYPE)),
				cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_START_TIME)),
				cursor.getLong(cursor.getColumnIndex(NoiteHojeProvider.KEY_DATE_TS)));
		text.setText(when);

		text = (TextView) view.findViewById(R.id.eventCity);
		text.setText(cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_CITY)));

		text = (TextView) view.findViewById(R.id.eventDistance);
		Location current = UIUtils.getCurrentLocation(mContext);
		if (current != null && cursor.getInt(cursor.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_AVAILABLE)) != 0) {
			Location event = new Location("event");
			event.setLatitude(cursor.getDouble(cursor.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_LATITUDE)));
			event.setLongitude(cursor.getDouble(cursor.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_LONGITUDE)));

			Log.d(TAG, "Current location: lat " + current.getLatitude() + " lng: " + current.getLongitude());
			Log.d(TAG, "Event location: lat " + cursor.getDouble(cursor.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_LATITUDE)) + " lng: " + cursor.getDouble(cursor.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_LONGITUDE)));

			float dist = current.distanceTo(event);
			Log.d(TAG, "Distance: " + dist);
			/*double dist = UIUtils.distanceMeters(current.getLatitude(), current.getLongitude(),
					cursor.getLong(cursor.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_LATITUDE)),
					cursor.getLong(cursor.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_LONGITUDE)));*/
			DecimalFormat distance = null;
			if (dist > 1000) {
				dist = dist / 1000;
				distance = new DecimalFormat("#0.0km daqui");
			} else
				distance = new DecimalFormat("#0.0m daqui");

			text.setText(distance.format(dist));
		} else
			text.setText("");

		text = (TextView) view.findViewById(R.id.eventWhere);
		String where = cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_VENUE_NAME));
		text.setText(Html.fromHtml(where).toString());

		String uri = NoiteHojeProvider.CONTENT_URI + "/" + cursor.getLong(cursor.getColumnIndex(NoiteHojeProvider.KEY_ID));
		view.setTag(uri);
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		return mInflater.inflate(R.layout.event_item, parent, false);
	}

}
