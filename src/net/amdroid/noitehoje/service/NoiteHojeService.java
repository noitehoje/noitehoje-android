package net.amdroid.noitehoje.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.acl.LastOwnerException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.amdroid.noitehoje.Consts;
import net.amdroid.noitehoje.EventInfo;
import net.amdroid.noitehoje.EventInfo.Venue;
import net.amdroid.noitehoje.EventInfo.Venue.VenueLocation;
import net.amdroid.noitehoje.provider.NoiteHojeProvider;
import net.amdroid.noitehoje.util.AnalyticsUtils;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.google.android.maps.GeoPoint;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;

public class NoiteHojeService extends Service {
	private static final String TAG = "NoiteHojeService";

	private WebService webService = null;

	//private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	//Thu, 26 May 2011
	private SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.US);
	private SimpleDateFormat tf = new SimpleDateFormat("HH:mm");

	private Context mContext;

	class WebService extends AsyncTask<Object, ContentValues, Integer> {

		private String method;
		private int count;

		@Override
		protected Integer doInBackground(Object... params) {
			int ret = 0;
			method = (String) params[0];

			if (method.contentEquals("getevents")) {
				/*Integer page = (Integer) params[1];

				ret = getEvents(page);*/
				ContentResolver cr = getContentResolver();
				cr.delete(NoiteHojeProvider.CONTENT_URI, null, null);
				AnalyticsUtils.getInstance(mContext).trackEvent("API", "cache", "deleted", 0);

				count = 0;
				int pages = getEvents(1);
				for (int page = 2; page <= pages; page++) {
					if (getEvents(page) == 0)
						Log.d(TAG, "Error loading page " + page);
				}

				Log.d(TAG, "Loaded " + pages + " pages");
			}

			return ret;
		}

		@Override
		protected void onProgressUpdate(ContentValues... values) {
			ContentValues value = values[0];
			ContentResolver cr = getContentResolver();

			Uri ret = cr.insert(NoiteHojeProvider.CONTENT_URI, value);
			if (ret != null) {
				Log.d(TAG, "Just inserted event: " + ret);
			}
			super.onProgressUpdate(values);
		}

		@Override
		protected void onPostExecute(Integer result) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			Editor edit = prefs.edit();

			if (count > 0) {
				edit.putLong("last_update", System.currentTimeMillis());
				edit.commit();
			}

			AnalyticsUtils.getInstance(mContext).trackEvent("API", "cache", "loaded", count);
			Log.d(TAG, "Notifying " + count + " events");
			broadcastDataReady();
			stopSelf();
		}

		public int getEvents(Integer page) {
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
				parseEvents(events, page);
				Log.d(TAG, "Loaded " + count + " events from page" + page);
			} catch (JSONException e) {
				Log.d(TAG, "Error parsing JSON");
				e.printStackTrace();
				return 0;
			}

			return pages;

		}

		/* XXX - FIXME - Review which fields are mandatory and which arent */
		private int parseEvents(JSONArray events, Integer page) {
			ContentResolver cr = getContentResolver();

			String id = null;
			for (int i = 0; i < events.length(); i++) {
				try {
					JSONObject evt = events.getJSONObject(i);

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
						AnalyticsUtils.getInstance(mContext).trackEvent("Error", "location", "missing location: " + id, 0);
						Log.d(TAG, "Location not available");
						values.put(NoiteHojeProvider.KEY_LOCATION_AVAILABLE, 0);
					}

					Uri ret = cr.insert(NoiteHojeProvider.CONTENT_URI, values);
					if (ret != null) {
						count++;
						Log.d(TAG, "Just inserted event: " + ret);
					}
				} catch (JSONException e) {
					AnalyticsUtils.getInstance(mContext).trackEvent("Error", "json", "error parsing " + (id != null ? id : ""), 0);
					Log.d(TAG, "Error parsing JSON Event " + i);
					e.printStackTrace();
					continue;
				} catch (ParseException e) {
					AnalyticsUtils.getInstance(mContext).trackEvent("Error", "date", "error parsing date " + (id != null ? id : ""), 0);
					Log.d(TAG, "Error parsing DATE for Event " + i);
					e.printStackTrace();
					continue;
				}
			}
			return count;
		}

		/*private Venue parseVenue(EventInfo event, JSONObject venueObj) throws JSONException {
			String id = venueObj.getString("_id");
			String name = venueObj.getString("name");
			String url = venueObj.optString("url");
			String phone = venueObj.optString("phone");

			Venue venue = event.new Venue(id, name, url, phone);
			venue.setLocation(parseLocation(venue, venueObj.getJSONObject("location")));

			return venue;
		}

		private VenueLocation parseLocation(Venue venue, JSONObject jsonObject) throws JSONException {
			String id = jsonObject.getString("_id");
			String country = jsonObject.optString("country");
			String street = jsonObject.optString("street");
			String city = jsonObject.optString("city");
			String venue_id = jsonObject.optString("venue_id");
			long geo_lon = jsonObject.optLong("geo_lon");
			long geo_lat = jsonObject.optLong("geo_lat");

			return venue.new VenueLocation(id, country, street, city, geo_lat, geo_lon);
		}*/

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

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mContext = this;
		Log.d(TAG, "NoiteHojeService onCreate()");
	}

	private int handleStart(Intent intent) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		long timestamp = prefs.getLong("last_update", 0);
		boolean refresh = needRefresh(timestamp);
		boolean connected = checkNetworkStatus(getApplicationContext());

		Log.d(TAG, "needRefres " + refresh + " connected " + connected);

		if (connected && refresh &&
			(webService == null || webService.getStatus().equals(AsyncTask.Status.FINISHED))) {
			AnalyticsUtils.getInstance(this).trackEvent("API", "refresh", "", 0);
			webService = new WebService();
			webService.execute("getevents");
			return Service.START_STICKY;
		}

		/* XXX - Ok, there is no connectivity or the last update is still valid...
		 * So we delete every event that is older than NOW + 6 HOURS */
		ContentResolver cr = getContentResolver();
		String where = NoiteHojeProvider.KEY_DATE_TS + " < ?";
		long expire = System.currentTimeMillis() - (6 * 60 * 60 * 1000);
		int rows = cr.delete(NoiteHojeProvider.CONTENT_URI, where, new String[] { String.valueOf(expire )} );

		Log.d(TAG, "Deleted " + rows + " expired rows from cache");
		AnalyticsUtils.getInstance(this).trackEvent("API", "cache", "deleted", rows);

		broadcastDataReady();

		stopSelf();
		return Service.START_NOT_STICKY;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return handleStart(intent);
	}

	@Override
	public void onStart(Intent intent, int startId) {
		handleStart(intent);
	}

	private void broadcastDataReady() {
		Intent intent = new Intent(Consts.WEBSERVICE_RESPONSE);
		sendBroadcast(intent);
	}

	private boolean hourGreater(Calendar cal, int hour, int minute) {
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

		/* check if the last refresh was today */
		if (dateRefresh.get(Calendar.DAY_OF_YEAR) == dateNow.get(Calendar.DAY_OF_YEAR) &&
			dateRefresh.get(Calendar.YEAR) == dateNow.get(Calendar.YEAR)) {
			/* if it was made after 2 am, the data is OK */
			if (hourGreater(dateRefresh, 2, 20))
				refresh = false;
			else {
				/* if the last refresh was yesterday after 2am
				 * and now is before 2am, data is OK, the crawlers
				 * did not run yet...*/
				if (dateRefresh.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
					dateRefresh.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
					hourGreater(dateRefresh, 2, 20) && !hourGreater(dateNow, 2, 20))
					refresh = false;
				else
					refresh = true;
			}
		} else {
			/* if the last refresh was yesterday after 2am
			 * and now is before 2am, data is OK, the crawlers
			 * did not run yet...*/
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
