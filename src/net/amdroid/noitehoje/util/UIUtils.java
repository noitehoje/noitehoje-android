package net.amdroid.noitehoje.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import net.amdroid.noitehoje.R;
import net.amdroid.noitehoje.provider.NoiteHojeProvider;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ParseException;
import android.net.Uri;
import android.test.IsolatedContext;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.FloatMath;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.markupartist.android.widget.ActionBar.AbstractAction;

public class UIUtils {
	private static final String TAG = "NoiteHojeUIUtils";

	private static final int SECONDS_PER_DAY = 60 * 60 * 24;

	/**
	 * Returns the number of days between two dates. The time part of the days
	 * is ignored in this calculation, so 2007-01-01 13:00 and 2007-01-02 05:00
	 * have one day inbetween.
	 */
	public static long daysBetween(Date firstDate, Date secondDate) {
		// We only use the date part of the given dates
		long firstSeconds = truncateToDate(firstDate).getTime() / 1000;
		long secondSeconds = truncateToDate(secondDate).getTime() / 1000;
		// Just taking the difference of the millis.
		// These will not be exactly multiples of 24*60*60, since there
		// might be daylight saving time somewhere inbetween. However, we can
		// say that by adding a half day and rounding down afterwards, we always
		// get the full days.
		long difference = secondSeconds - firstSeconds;
		// Adding half a day
		if (difference >= 0) {
			difference += SECONDS_PER_DAY / 2; // plus half a day in seconds
		} else {
			difference -= SECONDS_PER_DAY / 2; // minus half a day in seconds
		}
		// Rounding down to days
		difference /= SECONDS_PER_DAY;

		return difference;
	}

	/**
	 * Truncates a date to the date part alone.
	 */
	@SuppressWarnings("deprecation")
	public static Date truncateToDate(Date d) {
		if (d instanceof java.sql.Date) {
			return d; // java.sql.Date is already truncated to date. And raises
						// an
						// Exception if we try to set hours, minutes or seconds.
		}
		d = (Date) d.clone();
		d.setHours(0);
		d.setMinutes(0);
		d.setSeconds(0);
		d.setTime(((d.getTime() / 1000) * 1000));
		return d;
	}

	public static String partyTime(String type, String time, long ts) {
		String startTime = "";
		String typeStr = "Show";

		if (type.contentEquals("party"))
			typeStr = "Festa";

		/* Check if we have start time */
		if (TextUtils.isEmpty(time)) {
			if (DateUtils.isToday(ts) || ts < System.currentTimeMillis())
				startTime = typeStr + " hoje";
			else {
				long days = UIUtils.daysBetween(
						new Date(System.currentTimeMillis()), new Date(ts));
				if (days == 1) {
					startTime = typeStr + " amanhã";
				} else {
					startTime = typeStr + " em " + days + " dias";
				}
			}
		} else {
			/* XXX - UGLY... needs to fix this.. */
			if (DateUtils.isToday(ts) || ts < System.currentTimeMillis()) {
				if (System.currentTimeMillis() >= ts)
					startTime = typeStr + " agora";
				else {
					Date now = new Date(System.currentTimeMillis());
					Date event = new Date(ts);

					int hours = event.getHours() - now.getHours();
					if (hours == 0)
						startTime = typeStr + " agora";
					else
						startTime = typeStr + " hoje";
						//startTime = typeStr + " em " + hours + " horas";
				}
			} else {
				long days = UIUtils.daysBetween(
						new Date(System.currentTimeMillis()), new Date(ts));
				if (days == 1) {
					startTime = typeStr + " amanhã";
				} else {
					startTime = typeStr + " em " + days + " dias";
				}
			}
		}

		return startTime;
	}

	private static final int TWO_MINUTES = 1000 * 60 * 2;

	/** Determines whether one Location reading is better than the current Location fix
	  * @param location  The new Location that you want to evaluate
	  * @param currentBestLocation  The current Location fix, to which you want to compare the new one
	  */
	private static boolean isBetterLocation(Location location, Location currentBestLocation) {
	    if (currentBestLocation == null) {
	        // A new location is always better than no location
	        return true;
	    }

	    // Check whether the new location fix is newer or older
	    long timeDelta = location.getTime() - currentBestLocation.getTime();
	    boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
	    boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
	    boolean isNewer = timeDelta > 0;

	    // If it's been more than two minutes since the current location, use the new location
	    // because the user has likely moved
	    if (isSignificantlyNewer) {
	        return true;
	    // If the new location is more than two minutes older, it must be worse
	    } else if (isSignificantlyOlder) {
	        return false;
	    }

	    // Check whether the new location fix is more or less accurate
	    int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
	    boolean isLessAccurate = accuracyDelta > 0;
	    boolean isMoreAccurate = accuracyDelta < 0;
	    boolean isSignificantlyLessAccurate = accuracyDelta > 200;

	    // Check if the old and new location are from the same provider
	    boolean isFromSameProvider = isSameProvider(location.getProvider(),
	            currentBestLocation.getProvider());

	    // Determine location quality using a combination of timeliness and accuracy
	    if (isMoreAccurate) {
	        return true;
	    } else if (isNewer && !isLessAccurate) {
	        return true;
	    } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
	        return true;
	    }
	    return false;
	}

	/** Checks whether two providers are the same */
	private static boolean isSameProvider(String provider1, String provider2) {
	    if (provider1 == null) {
	      return provider2 == null;
	    }
	    return provider1.equals(provider2);
	}

	public static Location getCurrentLocation(Context context) {
		LocationManager locationManager = (LocationManager) context
				.getSystemService(Context.LOCATION_SERVICE);
		String locationProvider = LocationManager.NETWORK_PROVIDER;
		// Or use LocationManager.GPS_PROVIDER

		Location wifiLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

		Location lastKnownLocation = null;
		if (wifiLocation != null && gpsLocation != null) {
			if (isBetterLocation(gpsLocation, wifiLocation))
				lastKnownLocation = gpsLocation;
			else
				lastKnownLocation = wifiLocation;
		} else if (wifiLocation != null)
			lastKnownLocation = wifiLocation;
		else if (gpsLocation != null)
			lastKnownLocation = gpsLocation;

		if (lastKnownLocation != null)
			Log.d(TAG, "lastKnownLocation lat: " + lastKnownLocation.getLatitude()
					+ " lng: " + lastKnownLocation.getLongitude() + " - " +
					lastKnownLocation.getProvider());

		return lastKnownLocation;
	}

	public static double distanceMeters(double d, double e, float lat_b,
			float lng_b) {
		float pk = (float) (180 / 3.14169);

		float a1 = (float) (d / pk);
		float a2 = (float) (e / pk);
		float b1 = lat_b / pk;
		float b2 = lng_b / pk;

		float t1 = FloatMath.cos(a1) * FloatMath.cos(a2) * FloatMath.cos(b1)
				* FloatMath.cos(b2);
		float t2 = FloatMath.cos(a1) * FloatMath.sin(a2) * FloatMath.cos(b1)
				* FloatMath.sin(b2);
		float t3 = FloatMath.sin(a1) * FloatMath.sin(b1);
		double tt = Math.acos(t1 + t2 + t3);

		return 6366000 * tt;
	}

	public static Location newLocation(double d, double e) {
		Location loc = new Location(LocationManager.GPS_PROVIDER);
		loc.setLatitude(d / 1E6);
		loc.setLongitude(e/ 1E6);

		Log.d(TAG, "newLocation lat: " + loc.getLatitude()
				+ " lng: " + loc.getLongitude());

		return loc;
	}

	public static String readRawTextFile(Context ctx, int resId)
	{
		InputStream inputStream = ctx.getResources().openRawResource(resId);

		InputStreamReader inputreader = new InputStreamReader(inputStream);
		BufferedReader buffreader = new BufferedReader(inputreader);
		String line;
		StringBuilder text = new StringBuilder();

		try {
			while (( line = buffreader.readLine()) != null) {
				text.append(line);
				text.append('\n');
			}
		} catch (IOException e) {
			return null;
		}
		return text.toString();
	}

	public static String breakString(String string, int size) {
		String ret;
		if (string.length() >= size) {
			char[] dest = new char[size];
//			TextUtils.getChars(string, 0, size-1, dest, 0);
			string.getChars(0, size -2, dest, 0);

			if (string.length() > size) {
				dest[size-3] = '.'; dest[size-2] = '.'; dest[size-1] = '.';
			}
			ret = new String(dest);
		} else
			ret = string;

		Log.d(TAG, "breakString: " + string + " - " + ret);
		return ret;
	}

	public static String shareString(Cursor cursor) {
		String tmp = Html.fromHtml(cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_TITLE))).toString();
		String title = breakString(tmp, 25);

		tmp = Html.fromHtml(cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_VENUE_NAME))).toString();
		String venue = breakString(tmp, 23);

		String date = null;
		tmp = cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_START_DATE));
		try {
			final SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.US);
			Date dt = df.parse(tmp);
			Calendar cal = Calendar.getInstance();
            cal.setTime(dt);

            date = cal.get(Calendar.DAY_OF_MONTH) + "/"
                    + (cal.get(Calendar.MONTH) + 1) + "/"
                    + cal.get(Calendar.YEAR);

		} catch (java.text.ParseException e) {
			date = "não informado";
			e.printStackTrace();
		}

		tmp = Html.fromHtml(cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_CITY))).toString();
		String city = breakString(tmp, 20);

		String url = cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_PERMALINK));

		String tweet = title + " - " + date + ", " + venue + " @ " + city + ", " + url +
						" - #NoiteHoje";
		return tweet;
	}

	public static int getDBCount(Context context, String where, String[] whereValues) {
		ContentResolver cr = context.getContentResolver();
		Cursor cursor = cr.query(NoiteHojeProvider.CONTENT_URI, null, where, whereValues, null);
		int count = 0;
		if (cursor.moveToFirst())
			count = cursor.getCount();
		cursor.close();
		Log.d(TAG, "getDBCount: " + count);
		return count;
	}

	public static Intent createShareIntent(Cursor cursor) {
		final Intent intent = new Intent(Intent.ACTION_SEND);
		String text = shareString(cursor);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_TEXT, text);
		return Intent.createChooser(intent, "Share");
	}

	public static Intent createNavigationIntent(double lat, double lng) {
		final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("act=android.intent.action.VIEW dat=google.navigation:ll=" + lat + "," + lng));
		return intent;
	}

	public static class PartyAction extends AbstractAction {
		private Activity mActivity;

		public PartyAction(Activity activity) {
			super(R.drawable.party);
			mActivity = activity;
		}

		@Override
		public void performAction(View view) {
			mActivity.finish();
		}
	}

	public static class ShowAction extends AbstractAction {
		private Activity mActivity;

		public ShowAction(Activity activity) {
			super(R.drawable.show);
			mActivity = activity;
		}

		@Override
		public void performAction(View view) {
			mActivity.finish();
		}
	}

}
