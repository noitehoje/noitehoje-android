package net.amdroid.noitehoje.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.amdroid.noitehoje.Consts;
import net.amdroid.noitehoje.provider.NoiteHojeProvider;
import net.amdroid.noitehoje.util.AnalyticsUtils;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

public class NoiteHojeSyncService extends IntentService {
	private static final String TAG = "NoiteHojeSyncService";

	public static final String EXTRA_STATUS_RECEIVER =
        "net.amdroid.noitehoje.service.STATUS_RECEIVER";

	public static final int STATUS_READ_CACHE = 0;
	public static final int STATUS_SYNCING = 1;
	public static final int STATUS_SYNC_DONE = 2;
	public static final int STATUS_SYNC_ERROR = 3;
	public static final int STATUS_DONE = 4;

	private SimpleDateFormat df;
	private SimpleDateFormat tf;

	private int count;
	private int pages;

	public NoiteHojeSyncService() {
		super(TAG);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		df = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.US);
		tf = new SimpleDateFormat("HH:mm");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.d(TAG, "onHandleIntent(intent=" + intent.toString() + ")");
		final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_STATUS_RECEIVER);
		long timestamp = intent.getLongExtra("last_update", 0);
		boolean forceRefresh = intent.getBooleanExtra("force_refresh", false);
        boolean refresh = needRefresh(timestamp);
        boolean connected = checkNetworkStatus(getApplicationContext());
        boolean check = false;

        ContentResolver cr = getContentResolver();
        String where = NoiteHojeProvider.KEY_DATE_TS + " < ?";
        long expire = System.currentTimeMillis() - (6 * 60 * 60 * 1000);
        int rows = cr.delete(NoiteHojeProvider.CONTENT_URI, where, new String[] { String.valueOf(expire )} );

        Log.d(TAG, "Deleted " + rows + " expired rows from cache");
        AnalyticsUtils.getInstance(this).trackEvent("/Android/API/cache", "delete", "deleted rows", rows);
        receiver.send(STATUS_READ_CACHE, Bundle.EMPTY);
        
        if (forceRefresh && connected)
			check = true;

        if (!check && (!connected || !refresh)) {
        	Log.d(TAG, "connected: " + connected + " - refresh: " + refresh);
        	receiver.send(STATUS_DONE, Bundle.EMPTY);
        	return;
        }

        loadEvents(receiver);
	}

	private void loadEvents(ResultReceiver receiver) {
		final ArrayList<ContentProviderOperation> batch = new ArrayList<ContentProviderOperation>();

		batch.add(ContentProviderOperation.newDelete(NoiteHojeProvider.CONTENT_URI).build());

		receiver.send(STATUS_SYNCING, Bundle.EMPTY);
        count = 0;
        int pages = getEvents(1, batch);
        for (int page = 2; page <= pages; page++) {
            if (getEvents(page, batch) == 0)
                Log.d(TAG, "Error loading page " + page);
        }

        if (count > 0) {
        	ContentResolver cr = getContentResolver();
        	try {
				cr.applyBatch(NoiteHojeProvider.CONTENT_AUTHORITY, batch);
			} catch (RemoteException e) {
				AnalyticsUtils.getInstance(this).trackEvent("/Android/error", "db", "RemoteException", 0);
				e.printStackTrace();
				receiver.send(STATUS_SYNC_ERROR, Bundle.EMPTY);
			} catch (OperationApplicationException e) {
				AnalyticsUtils.getInstance(this).trackEvent("/Android/error", "db", "OperationApplicationException", 0);
				e.printStackTrace();
				receiver.send(STATUS_SYNC_ERROR, Bundle.EMPTY);
			}
        }

        AnalyticsUtils.getInstance(this).trackEvent("/Android/API/cache", "load", "loaded events", count);
        Log.d(TAG, "Notifying " + count + " events");
        Log.d(TAG, "Loaded " + pages + " pages");
        receiver.send(STATUS_SYNC_DONE, Bundle.EMPTY);
	}

	public int getEvents(Integer page, ArrayList<ContentProviderOperation> batch) {
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		int pages = 0;

		nvps.add(new BasicNameValuePair("location", Consts.DEFAULT_CITY));
		nvps.add(new BasicNameValuePair("page", page.toString()));

		String json = postUrl("getevents", nvps);
		if (json == null)
			return 0;

		try {
			JSONObject response = (JSONObject) new JSONTokener(json)
					.nextValue();
			JSONObject attributes = response.getJSONObject("attributes");
			pages = attributes.getInt("totalpages");
			JSONArray events = response.getJSONArray("events");
			parseEvents(events, page, batch);
			Log.d(TAG, "Loaded " + count + " events from page" + page);
		} catch (JSONException e) {
			Log.d(TAG, "Error parsing JSON");
			e.printStackTrace();
			return 0;
		}

		return pages;
	}

	private int parseEvents(JSONArray events, Integer page, ArrayList<ContentProviderOperation> batch) {
		ContentResolver cr = getContentResolver();

		String id = null;
		for (int i = 0; i < events.length(); i++) {
			try {
				JSONObject evt = events.getJSONObject(i);
				
				final ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(NoiteHojeProvider.CONTENT_URI);

				ContentValues values = new ContentValues();

				id = evt.getString("_id");
				values.put(NoiteHojeProvider.KEY_EVENT_ID, id);
				values.put(NoiteHojeProvider.KEY_TITLE, evt.getString("title"));
				/*values.put(NoiteHojeProvider.KEY_CREATED_AT,
						evt.getString("created_at"));*/

				String start_date = evt.getString("start_date");
				values.put(NoiteHojeProvider.KEY_START_DATE, start_date);
				String start_time = evt.getString("start_time");
				values.put(NoiteHojeProvider.KEY_START_TIME, start_time);

				Log.d(TAG, "date: " + start_date + " time: " + start_time);

				Date dt = df.parse(start_date);
				Log.d(TAG, "Event date: " + dt.toString());
				long ts = dt.getTime();
				/* XXX - HACK if the start time is empty, set the timestamp to
				 * 23:59:59
				 */
				if (TextUtils.isEmpty(start_time))
					ts += ((60 * 60 * 24 * 1000) - 1000);
				else {
					Date time = tf.parse(start_time);
					Log.d(TAG, "----> Event time: " + time.toString() + " => " + time.getTime());
					ts += (time.getHours() * 60 * 60 * 1000) + (time.getMinutes() * 60 * 1000);
				}
				Date tmp = new Date(ts);
				Log.d(TAG, "----> Event date: " + tmp.toString());

				values.put(NoiteHojeProvider.KEY_DATE_TS, ts);

				/*String time = evt.optString("time");
				values.put(NoiteHojeProvider.KEY_TIME, date);
				if (date != null) {
					Date dt = df.parse(date);
					values.put(NoiteHojeProvider.KEY_TIME_TS, dt.getTime());
				}*/
				values.put(NoiteHojeProvider.KEY_DESCRIPTION,
						evt.optString("description"));
				values.put(NoiteHojeProvider.KEY_EVENT_TYPE,
						evt.getString("evt_type"));
				values.put(NoiteHojeProvider.KEY_SOURCE,
						evt.getString("source"));
				values.put(NoiteHojeProvider.KEY_PERMALINK, evt.getString("permalink"));

				/* VENUE Fields */
				JSONObject venueObj = evt.getJSONObject("venue");
				values.put(NoiteHojeProvider.KEY_VENUE_ID, venueObj.getString("_id"));
				values.put(NoiteHojeProvider.KEY_VENUE_NAME, venueObj.getString("name"));
				values.put(NoiteHojeProvider.KEY_VENUE_URL, venueObj.optString("url"));
				values.put(NoiteHojeProvider.KEY_VENUE_PHONE, venueObj.optString("phone"));

				/* Location INFO */
				JSONObject locObj = venueObj.getJSONObject("location");
				values.put(NoiteHojeProvider.KEY_LOCATION_ID, locObj.getString("_id"));
				values.put(NoiteHojeProvider.KEY_LOCATION_COUNTRY, locObj.optString("country"));
				values.put(NoiteHojeProvider.KEY_LOCATION_STREET, locObj.optString("street"));
				values.put(NoiteHojeProvider.KEY_LOCATION_CITY, locObj.optString("city"));

				try {
					double geo_lng = locObj.getDouble("geo_lon");
					double geo_lat = locObj.getDouble("geo_lat");
					values.put(NoiteHojeProvider.KEY_LOCATION_LATITUDE, geo_lat);
					values.put(NoiteHojeProvider.KEY_LOCATION_LONGITUDE, geo_lng);
					Log.d(TAG, "Insert Event location: " + geo_lat + ", " + geo_lng);
					Log.d(TAG, "Location available");
					values.put(NoiteHojeProvider.KEY_LOCATION_AVAILABLE, 1);
				} catch (JSONException e) {
					AnalyticsUtils.getInstance(this).trackEvent("/Android/error", "location", "missing location: " + id, 0);
					Log.d(TAG, "Location not available");
					values.put(NoiteHojeProvider.KEY_LOCATION_AVAILABLE, 0);
				}

				builder.withValues(values);
				batch.add(builder.build());
				count++;
			} catch (JSONException e) {
				AnalyticsUtils.getInstance(this).trackEvent("/Android/error", "json", "error parsing " + (id != null ? id : ""), 0);
				Log.d(TAG, "Error parsing JSON Event " + i);
				e.printStackTrace();
				continue;
			} catch (ParseException e) {
				AnalyticsUtils.getInstance(this).trackEvent("/Android/error", "date", "error parsing date " + (id != null ? id : ""), 0);
				Log.d(TAG, "Error parsing DATE for Event " + i);
				e.printStackTrace();
				continue;
			}
		}
		return count;
	}

	private String postUrl(String method, List<NameValuePair> nvps) {
		String urlAddress = "http://noitehoje.com.br/api/" + Consts.API_VER
				+ "/" + Consts.API_KEY + "/" + method;
		String buffer = null;
		String paramString = URLEncodedUtils.format(nvps, "utf-8");

		if (!urlAddress.endsWith("?"))
			urlAddress += "?";
		urlAddress += paramString;

		if (Consts.DEBUG_API)
			Log.d(TAG, "postUrl -> " + urlAddress);

		HttpURLConnection urlConnection = null;
		try {
			// Create a URL for the desired page
			URL url = new URL(urlAddress);

			urlConnection = (HttpURLConnection) url
					.openConnection();
			urlConnection.setRequestProperty("Content-Type",
					"application/json");

			BufferedReader r = new BufferedReader(new InputStreamReader(
					urlConnection.getInputStream()));

			StringBuilder total = new StringBuilder();
			String str;
			while ((str = r.readLine()) != null) {
				total.append(str);
			}

			buffer = total.toString();

			if (Consts.DEBUG_API)
				Log.d(TAG, "postUrl <- " + buffer);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			try {
                int respCode = urlConnection.getResponseCode();
                Log.d(TAG, "HTTP ERROR: " + respCode);
			} catch(IOException ex) {
				e.printStackTrace();
			}
			e.printStackTrace();
		}

		return buffer;
	}

	public static boolean checkNetworkStatus(Context context) {
		ConnectivityManager connMgr = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		TelephonyManager mTelephony = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
		boolean connected;

		NetworkInfo info = connMgr.getActiveNetworkInfo();
		if (info == null) {
			Log.d(TAG, "checkNetworkStatus() -> No active connection");
			return false;
		}

		int netType = info.getType();
		connected = info.isConnected();

		if (netType == ConnectivityManager.TYPE_WIFI) {
			Log.d(TAG, "checkNetworkStatus() -> WIFI "
					+ (connected ? "OK" : "NOK"));
			return connected;
		} else if (netType == ConnectivityManager.TYPE_MOBILE) {
			boolean roam = mTelephony.isNetworkRoaming();
			Log.d(TAG, "checkNetworkStatus() -> 3G "
					+ (connected ? "OK" : "NOK"));
			Log.d(TAG, "checkNetworkStatus() -> 3G "
					+ (roam ? "ROAMING" : "LOCAL"));
			return connected && !roam;
		}

		Log.d(TAG, "checkNetworkStatus() -> Connection type not supported "
				+ info.getTypeName());
		return false;
	}

	private boolean hourGreater(Calendar cal, int hour, int minute) {
		Log.d(TAG, "hourGreater: " + cal.get(Calendar.HOUR_OF_DAY) + " > " + hour);
		Log.d(TAG, "minuteGreater: " + cal.get(Calendar.MINUTE) + " > " + minute);
		if (cal.get(Calendar.HOUR_OF_DAY) == hour)
			return minute > cal.get(Calendar.MINUTE);
		return cal.get(Calendar.HOUR_OF_DAY) > hour;
	}

	private boolean needRefresh(long timestamp) {
		boolean refresh;
		Calendar dateNow = Calendar.getInstance();

		if (timestamp == 0)
			return true;

		Calendar dateRefresh = Calendar.getInstance();
		dateRefresh.setTimeInMillis(timestamp);

		Calendar yesterday = Calendar.getInstance();
		yesterday.add(Calendar.DAY_OF_MONTH, -1);

		Log.d(TAG, "lastRefresh: " + dateRefresh.toString() + " dateNow: " + dateNow.toString());

		Log.d(TAG, "day " + dateRefresh.get(Calendar.DAY_OF_YEAR) + " - " + dateNow.get(Calendar.DAY_OF_YEAR) +
			" year " + dateRefresh.get(Calendar.YEAR) + " - " + dateNow.get(Calendar.YEAR));
		/* check if the last refresh was today */
		if (dateRefresh.get(Calendar.DAY_OF_YEAR) == dateNow.get(Calendar.DAY_OF_YEAR) &&
			dateRefresh.get(Calendar.YEAR) == dateNow.get(Calendar.YEAR)) {
			/* if it was made after 2 am, the data is OK */
			if (hourGreater(dateRefresh, 2, 20))
				refresh = false;
			else {
				/* refresh was made before 2:20 am, but is now after 2:20 am? */
				if (hourGreater(dateNow, 2, 20))
					refresh = true;
				else
					refresh = false;
			}
		} else {
			/* if the last refresh was yesterday after 2am
			 * and now is before 2am, data is OK, the crawlers
			 * did not run yet...*/
			Log.d(TAG, "yesterday2 day " + dateRefresh.get(Calendar.DAY_OF_YEAR) + " - " + yesterday.get(Calendar.DAY_OF_YEAR) +
					" year " + dateRefresh.get(Calendar.YEAR) + " - " + yesterday.get(Calendar.YEAR));
			if (dateRefresh.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
				dateRefresh.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
				hourGreater(dateRefresh, 2, 20) && !hourGreater(dateNow, 2, 20))
				refresh = false;
			else
				refresh = true;
		}

		return refresh;
	}

}
