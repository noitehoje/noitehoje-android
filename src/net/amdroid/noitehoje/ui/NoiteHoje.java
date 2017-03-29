package net.amdroid.noitehoje.ui;

import net.amdroid.noitehoje.Consts;
import net.amdroid.noitehoje.R;
import net.amdroid.noitehoje.provider.NoiteHojeProvider;
import net.amdroid.noitehoje.service.NoiteHojeService;
import net.amdroid.noitehoje.service.NoiteHojeSyncService;
import net.amdroid.noitehoje.util.AnalyticsUtils;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;

import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.AbstractAction;
import com.markupartist.android.widget.ActionBar.Action;
import com.markupartist.android.widget.ActionBar.IntentAction;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

public class NoiteHoje extends Activity {

	private static final String TAG = "NoiteHoje";

	private static final int START_FADEOUT = 1;
	private static final int ENABLE_ORIENTATION = 2;

	private WebServiceReceiver receiver;

	private Context mContext;
	private Intent startIntent;

	private LocationManager locationManager = null;
	private LocationListener wifiLocationListener = null;
	private LocationListener gpsLocationListener = null;
	private Location wifiLocation = null;
	private Location gpsLocation = null;

	private SharedPreferences prefs;

	private ActionBar actionBar = null;
	private QuickAction quickAction = null;
	private ActionItem first = null;
	private ActionItem second = null;
	private Action refreshAction = null;

	private Cursor mCursor;
	private EventsAdapter adapter;
	private ListView listView;
	private String orderBy = NoiteHojeProvider.KEY_ID;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.main);

		/* Action bar for < 3.0 */
		actionBar = (ActionBar) findViewById(R.id.actionbar);
		actionBar.setHomeAction(new IntentAction(this, NoiteHoje.createIntent(this), R.drawable.ic_title_home_default));
		actionBar.setHomeLogo(R.drawable.ab_logo);

		refreshAction = new RefreshAction();
		actionBar.addAction(refreshAction);

		actionBar.addAction(new OrderAction());

		actionBar.addAction(new MapAction());
		actionBar.setProgressBarVisibility(View.GONE);

		/* action itens */
		first = new ActionItem();
		first.setTitle("Data");
		first.setIcon(getResources().getDrawable(R.drawable.ic_quickaction_date));
		first.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				refreshContents(NoiteHojeProvider.KEY_ID);
				quickAction.dismiss();
				listView.setSelection(0);
			}
		});

		second = new ActionItem();
		second.setTitle("Evento");
		second.setIcon(getResources().getDrawable(R.drawable.ic_quickatcion_event));
		second.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				refreshContents(NoiteHojeProvider.KEY_TITLE);
				listView.setSelection(0);
				quickAction.dismiss();
			}
		});

		mContext = this;
		startIntent = getIntent();

		adapter = new EventsAdapter(this, null, true);
		listView = (ListView) findViewById(R.id.listEvents);
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(listviewOnClick);

		prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		startSplash();

		loadContent(false);
	}

	private void startLocation() {
		// Acquire a reference to the system Location Manager
		locationManager = (LocationManager) this
				.getSystemService(Context.LOCATION_SERVICE);

		// Define a listener that responds to wifi location updates
		wifiLocationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				// Called when a new location is found by the network location
				// provider.
				wifiLocation = location;
				locationManager.removeUpdates(this);
				refreshContents(orderBy);
				Log.d(TAG, "Got new WIFI Location: " + wifiLocation.getLatitude() + ", " + wifiLocation.getLongitude());
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
				Log.d(TAG, "Removing WIFI Location Listener");
				locationManager.removeUpdates(this);
			}
		};

		// Define a listener that responds to wifi location updates
		gpsLocationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				// Called when a new location is found by the network location
				// provider.
				gpsLocation = location;
				locationManager.removeUpdates(this);
				refreshContents(orderBy);
				Log.d(TAG, "Got new GPS Location: " + gpsLocation.getLatitude() + ", " + gpsLocation.getLongitude());
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
				Log.d(TAG, "Removing GPS Location Listener");
				locationManager.removeUpdates(this);
			}
		};

		// Register the listener with the Location Manager to receive location
		// updates
		locationManager.requestLocationUpdates(
				LocationManager.NETWORK_PROVIDER, 0, 0, wifiLocationListener);
		locationManager.requestLocationUpdates(
				LocationManager.GPS_PROVIDER, 0, 0, gpsLocationListener);

		Runnable gpsTimeout = new Runnable() {

			@Override
			public void run() {
				Log.d(TAG, "Removing GPS location listener from timeout");
				locationManager.removeUpdates(gpsLocationListener);
			}
		};

		Handler gpsHandler= new Handler();
		gpsHandler.postDelayed(gpsTimeout, 1000 * 120);
	}

	private OnItemClickListener listviewOnClick = new OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			Intent intent = new Intent(mContext, NoiteHojeEvent.class);
			intent.putExtra("content_uri", (String) view.getTag());
			saveContext(position);
			startActivity(intent);
		}

	};

	@Override
	public void onRestart() {
		Log.d(TAG, "onRestart, avoiding splash and use last position");
		Intent i = getIntent();
		i.putExtra("SHOW_SPLASHSCREEN", false);
		i.putExtra("USE_LAST_POSITION", true);
		super.onRestart();
	}

	@Override
	public void onResume() {
		IntentFilter filter;
		filter = new IntentFilter(Consts.WEBSERVICE_RESPONSE);
		receiver = new WebServiceReceiver();
		registerReceiver(receiver, filter);

		startLocation();

		refreshContents(orderBy);
		super.onResume();
	}

	@Override
	public void onPause() {
		if (locationManager != null) {
			if (wifiLocationListener != null)
				locationManager.removeUpdates(wifiLocationListener);
			if (gpsLocationListener != null)
				locationManager.removeUpdates(gpsLocationListener);
		}
		unregisterReceiver(receiver);
		super.onPause();
	}

	public void onConfigurationChanged(Configuration newConfig) {
		// ignore orientation change
		super.onConfigurationChanged(newConfig);
	}

	private void loadContent(boolean force) {
		/*Intent intent = new Intent(this, NoiteHojeService.class);
		Log.d(TAG, "Starting NoiteHojeService");
		startService(intent);*/
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		long timestamp = prefs.getLong("last_update", 0);

		final Intent intent = new Intent(Intent.ACTION_SYNC, null, this,
				NoiteHojeSyncService.class);
		intent.putExtra("last_update", timestamp);
		intent.putExtra("force_refresh", force);
		intent.putExtra(NoiteHojeSyncService.EXTRA_STATUS_RECEIVER,
				new SyncReceiver(new Handler()));
		startService(intent);
	}

	class SyncReceiver extends ResultReceiver {

		public SyncReceiver(Handler handler) {
			super(handler);
		}

		protected void onReceiveResult(int resultCode, Bundle resultData) {
			switch (resultCode) {
			case NoiteHojeSyncService.STATUS_READ_CACHE:
				Log.d(TAG, "READ CACHE");
				refreshContents(orderBy);
				break;
			case NoiteHojeSyncService.STATUS_SYNCING:
				Log.d(TAG, "START SYNC");
				actionBar.hideAction(refreshAction);
				actionBar.setProgressBarVisibility(View.VISIBLE);
				break;
			case NoiteHojeSyncService.STATUS_SYNC_ERROR:
				Log.d(TAG, "Error syncing data");
				actionBar.showAction(refreshAction);
				actionBar.setProgressBarVisibility(View.GONE);
				break;
			case NoiteHojeSyncService.STATUS_SYNC_DONE:
				Log.d(TAG, "SYNC DONE");

				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
				Editor edit = prefs.edit();
				edit.putLong("last_update", System.currentTimeMillis());
				edit.commit();

				refreshContents(orderBy);
				actionBar.showAction(refreshAction);
				actionBar.setProgressBarVisibility(View.GONE);
				break;
			case NoiteHojeSyncService.STATUS_DONE:
				Log.d(TAG, "ALL SYNC DONE");
			default:
				break;
			}
		}
	}

	public void saveContext(int position) {
		Editor edit = prefs.edit();
		Log.d(TAG, "Setting LAST_POSITION to " + position);
		edit.putInt("LAST_POSITION", position);
		edit.commit();
	}

	public void saveContext() {
		Editor edit = prefs.edit();
		int lastPosition = listView.getLastVisiblePosition();
		Log.d(TAG, "Setting LAST_POSITION to " + lastPosition);
		edit.putInt("LAST_POSITION", lastPosition);
		edit.commit();
	}

	private void startSplash() {
		final Handler handler = new Handler() {

			Runnable run_orientation = new Runnable() {
				public void run() {
					Message msg = obtainMessage();
					msg.arg1 = ENABLE_ORIENTATION;
					sendMessage(msg);
				}
			};

			@Override
			public void handleMessage(Message msg) {
				View view = findViewById(R.id.splashLayout);
				if (view == null)
					return;

				if (msg.arg1 == START_FADEOUT) {
					Animation fadeout = AnimationUtils.loadAnimation(mContext,
							R.anim.fade_out);
					view.setVisibility(View.GONE);
					view.startAnimation(fadeout);

					Log.d(TAG, "Start splashscreen fadeout");
					postDelayed(run_orientation, 2000);
				} else if (msg.arg1 == ENABLE_ORIENTATION) {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
				}
			}
		};

		Runnable run = new Runnable() {
			public void run() {
				Message msg = handler.obtainMessage();
				msg.arg1 = START_FADEOUT;
				handler.sendMessage(msg);
			}
		};

		if (Build.VERSION.SDK_INT < 6) {
			Intent startIntent = getIntent();
			if (!startIntent.getBooleanExtra("SHOW_SPLASHSCREEN", true))
				return;

			Log.d(TAG, "Start splashscreen");
			View view = findViewById(R.id.splashLayout);
			view.setVisibility(View.VISIBLE);
			handler.postDelayed(run, 2000);
		} else
			Log.d(TAG, "Android SDK version " + Build.VERSION.SDK_INT + ", splash already showed");
	}

	public static Intent createIntent(Context context) {
		Intent i = new Intent(context, NoiteHoje.class);
		i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		i.putExtra("SHOW_SPLASHSCREEN", false);
		i.putExtra("USE_LAST_POSITION", true);
		return i;
	}

	private class RefreshAction extends AbstractAction {

			public RefreshAction() {
					super(R.drawable.ic_actionbar_refresh);
			}

			@Override
			public void performAction(View view) {
				loadContent(true);
			}
	}

	private class OrderAction extends AbstractAction {

			public OrderAction() {
					super(R.drawable.ic_actionbar_order);
			}

			@Override
			public void performAction(View view) {
				/*Toast.makeText(NoiteHoje.this,
								"Example action", Toast.LENGTH_SHORT).show();*/
				quickAction = new QuickAction(view);

				quickAction.addActionItem(first);
				quickAction.addActionItem(second);
				quickAction.setAnimStyle(QuickAction.ANIM_REFLECT);

				quickAction.show();
			}
	}

	private class MapAction extends AbstractAction {

		public MapAction() {
				super(R.drawable.ic_actionbar_maps);
		}

		@Override
		public void performAction(View view) {
			Intent intent = new Intent(mContext, NoiteHojeMap.class);
			intent.putExtra("content_uri", NoiteHojeProvider.CONTENT_URI.toString());
			saveContext();
			startActivity(intent);
		}
	}

	public void refreshContents(String order) {
		orderBy = order;
		ContentResolver cr = getContentResolver();
		Cursor cursor = cr.query(NoiteHojeProvider.CONTENT_URI, null, null, null, order);
		adapter.changeCursor(cursor);
		adapter.notifyDataSetChanged();
	}

	public class WebServiceReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().contentEquals(Consts.WEBSERVICE_RESPONSE)) {
				refreshContents(NoiteHojeProvider.KEY_ID);
				Log.d(TAG, "WEBSERICE_RESPONSE");
				actionBar.setProgressBarVisibility(View.GONE);
				if (startIntent.getBooleanExtra("USE_LAST_POSITION", false)) {
					Log.d(TAG, "USE_LAST_POSITION = true " + prefs.getInt("LAST_POSITION", -1));
					final int position = prefs.getInt("LAST_POSITION", 0);
					runOnUiThread(new Runnable() { public void run() { listView.setSelection(position); } } );
				}
			}
		}
	}
}
