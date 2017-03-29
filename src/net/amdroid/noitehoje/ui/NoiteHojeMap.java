package net.amdroid.noitehoje.ui;

import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;

import net.amdroid.noitehoje.Consts;
import net.amdroid.noitehoje.R;
import net.amdroid.noitehoje.Consts;
import net.amdroid.noitehoje.provider.NoiteHojeProvider;
import net.amdroid.noitehoje.util.UIUtils;
import net.amdroid.noitehoje.util.AnalyticsUtils;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;
import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.AbstractAction;
import com.markupartist.android.widget.ActionBar.IntentAction;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

public class NoiteHojeMap extends MapActivity {
	private static final String TAG = "NoiteHojeMap";

	private Context mContext;
	private Cursor mCursor;
	private MapController mapController;
	private MapView mapView = null;
	GestureDetector mGestureDetector;
	private EventItemizedOverlay itemizedOverlay;

	// private GestureDetector gestureDetector;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (Consts.RELEASE)
			setContentView(R.layout.map);
		else
			setContentView(R.layout.map_debug);

		// actionBar.setHomeAction(new IntentAction(this, NoiteHoje
		// .createIntent(this), R.drawable.ic_title_home_default));
		// actionBar.setDisplayHomeAsUpEnabled(true);
		// actionBar.setHomeLogo(R.drawable.ab_logo);
		// actionBar.showHomeLogo();
		// actionBar.addAction(new IntentAction(this, createShareIntent(),
		// R.drawable.ic_title_share_default));

		Intent intent = getIntent();
		String contentUri = intent.getStringExtra("content_uri");

		mContext = this;

		Uri uri = Uri.parse(contentUri);

		ContentResolver cr = getContentResolver();
		mCursor = cr.query(uri, null, null, null, null);

		ActionBar actionBar = (ActionBar) findViewById(R.id.mapActionBar);
		if (mCursor.moveToFirst() && mCursor.getCount() == 1) {
			String title = mCursor.getString(mCursor
					.getColumnIndex(NoiteHojeProvider.KEY_TITLE));
			String event_id = mCursor.getString(mCursor
					.getColumnIndex(NoiteHojeProvider.KEY_EVENT_ID));
			AnalyticsUtils.getInstance(mContext).trackPageView(
					"/Android/map/" + event_id + "/" + title);

			actionBar.addAction(new IntentAction(this, UIUtils
					.createShareIntent(mCursor),
					R.drawable.ic_title_share_default));
			if (mCursor.getInt(mCursor
					.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_AVAILABLE)) != 0) {
				double lat = mCursor
						.getDouble(mCursor
								.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_LATITUDE));
				double lng = mCursor
						.getDouble(mCursor
								.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_LONGITUDE));
				// actionBar.addAction(new IntentAction(this,
				// UIUtils.createNavigationIntent(lat, lng),
				// R.drawable.ic_title_export_default));
			}
			String type = mCursor.getString(mCursor.getColumnIndex(NoiteHojeProvider.KEY_EVENT_TYPE));
			if (type.contentEquals("party"))
				actionBar.setHomeAction(new PartyAction());
			else
				actionBar.setHomeAction(new ShowAction());
		} else {
			actionBar.setHomeAction(new IntentAction(this, NoiteHoje
					.createIntent(this), R.drawable.ic_title_home_default));
		}
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeLogo(R.drawable.ab_logo);
		actionBar.showHomeLogo();

		ArrayList<EventInfo> items = new ArrayList<EventInfo>();
		if (mCursor.moveToLast()) {
			do {
				if (mCursor
						.getInt(mCursor
								.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_AVAILABLE)) == 0)
					continue;

				String title = mCursor.getString(mCursor
						.getColumnIndex(NoiteHojeProvider.KEY_TITLE));
				String where = mCursor.getString(mCursor
						.getColumnIndex(NoiteHojeProvider.KEY_VENUE_NAME));
				String url = NoiteHojeProvider.CONTENT_URI.toString()
						+ "/"
						+ mCursor.getString(mCursor
								.getColumnIndex(NoiteHojeProvider.KEY_ID));
				Double lat = mCursor
						.getDouble(mCursor
								.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_LATITUDE)) * 1E6;
				Double lng = mCursor
						.getDouble(mCursor
								.getColumnIndex(NoiteHojeProvider.KEY_LOCATION_LONGITUDE)) * 1E6;
				GeoPoint location = new GeoPoint(lat.intValue(), lng.intValue());

				items.add(new EventInfo(title, where, url, location));
			} while (mCursor.moveToPrevious());
		}

		Drawable marker = getResources().getDrawable(R.drawable.map_marker);
		marker.setBounds(0, 0, marker.getIntrinsicWidth(),
				marker.getIntrinsicHeight());

		mapView = (MapView) findViewById(R.id.mapView);
		mapController = mapView.getController();
		mapView.setBuiltInZoomControls(true);

		if (mCursor.getCount() > 1)
			mapController.setZoom(14);
		else
			mapController.setZoom(17);

		/* center map in poa if there are no events... */
		Double lat = -30.027702 * 1E6;
		Double lng = -51.228733 * 1E6;
		GeoPoint poa = new GeoPoint(lat.intValue(), lng.intValue());

		Location current = UIUtils.getCurrentLocation(mContext);
		if (current != null && mCursor.getCount() > 1)
			mapController.setCenter(new GeoPoint(Double.valueOf(
					current.getLatitude() * 1E6).intValue(), Double.valueOf(
					current.getLongitude() * 1E6).intValue()));
		else
			mapController.setCenter(items.size() > 0 ? items.get(0)
					.getLocation() : poa);

		mapView.getOverlays().add(new Overlay() {
			@Override
			public boolean onTouchEvent(MotionEvent e, MapView mapView) {
				mGestureDetector.onTouchEvent(e);
				return super.onTouchEvent(e, mapView);
			}
		});

		mGestureDetector = new GestureDetector(
				new GestureDetector.SimpleOnGestureListener() {
					@Override
					public void onLongPress(MotionEvent e) {
						Log.d(TAG, "Long Press event");
					}

					@Override
					public boolean onDoubleTap(MotionEvent event) {
						Log.d(TAG, "Double Tap event");
						mapView.getController().zoomInFixing(
								(int) event.getX(), (int) event.getY());
						return true;
					}

					@Override
					public boolean onDown(MotionEvent e) {
						return true;
					}
				});

		// mapView.getOverlays().add(new EventsOverlay(marker, items));
		itemizedOverlay = new EventItemizedOverlay(marker, mapView);
		for (EventInfo event : items) {
			itemizedOverlay.addOverlay(new OverlayItem(event.getLocation(),
					event.getUri(), null));
		}
		mapView.getOverlays().add(itemizedOverlay);

		CurrentLocationOverlay me = new CurrentLocationOverlay(mContext,
				mapView);
		GeoPoint center = me.getMyLocation();
		if (center != null && mCursor.getCount() > 1) {
			mapController.setCenter(center);
			mapView.getOverlays().add(me);
			me.enableMyLocation();
		}
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

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	private class EventInfo {
		private String title;
		private String where;
		private String uri;
		GeoPoint location;

		public EventInfo(String title, String where, String uri,
				GeoPoint location) {
			super();
			this.title = title;
			this.where = where;
			this.uri = uri;
			this.location = location;
		}

		public String getTitle() {
			return title;
		}

		public String getWhere() {
			return where;
		}

		public String getUri() {
			return uri;
		}

		public GeoPoint getLocation() {
			return location;
		}
	}

	public class CurrentLocationOverlay extends MyLocationOverlay {

		// TODO: use dynamic calculation?
		private final static int PADDING_ACTIVE_ZOOM = 50;

		private MapController mc;
		private Point currentPoint = new Point();

		private boolean centerOnCurrentLocation = true;

		private int height;
		private int width;

		/**
		 * By default this CurrentLocationOverlay will center on the current
		 * location, if the currentLocation is near the edge, or off the screen.
		 * To dynamically enable/disable this, use
		 * {@link #setCenterOnCurrentLocation(boolean)}.
		 * 
		 * @param context
		 * @param mapView
		 */
		public CurrentLocationOverlay(Context context, MapView mapView) {
			super(context, mapView);
			this.mc = mapView.getController();
		}

		@Override
		protected void drawMyLocation(Canvas canvas, MapView mapView,
				Location lastFix, GeoPoint myLocation, long when) {
			// TODO: find a better way to get height/width once the mapView is
			// layed out correctly
			if (this.height == 0) {
				this.height = mapView.getHeight();
				this.width = mapView.getWidth();
			}
			mapView.getProjection().toPixels(myLocation, currentPoint);
			Paint paint = new Paint();
			paint.setARGB(200, 128, 33, 136);
			paint.setAntiAlias(true);
			int rad = 5;
			RectF oval = new RectF(currentPoint.x - rad, currentPoint.y - rad,
					currentPoint.x + rad, currentPoint.y + rad);
			// canvas.drawBitmap(marker, currentPoint.x, currentPoint.y - 40,
			// null);
			canvas.drawOval(oval, paint);
		}

		@Override
		public synchronized void onLocationChanged(Location location) {
			super.onLocationChanged(location);
			// only move to new position if enabled and we are in an border-area
			if (mc != null && centerOnCurrentLocation
					&& inZoomActiveArea(currentPoint)) {
				mc.animateTo(getMyLocation());
			}
		}

		private boolean inZoomActiveArea(Point currentPoint) {
			if ((currentPoint.x > PADDING_ACTIVE_ZOOM && currentPoint.x < width
					- PADDING_ACTIVE_ZOOM)
					&& (currentPoint.y > PADDING_ACTIVE_ZOOM && currentPoint.y < height
							- PADDING_ACTIVE_ZOOM)) {
				return false;
			}
			return true;
		}

		public void setCenterOnCurrentLocation(boolean centerOnCurrentLocation) {
			this.centerOnCurrentLocation = centerOnCurrentLocation;
		}
	}

	private class EventsOverlay extends ItemizedOverlay<OverlayItem> {
		private ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();
		private ArrayList<EventInfo> events = null;
		private Drawable marker = null;

		public EventsOverlay(Drawable marker, ArrayList<EventInfo> events) {
			super(marker);
			this.marker = marker;
			this.events = events;

			for (EventInfo event : events) {
				items.add(new OverlayItem(event.getLocation(), event.title,
						event.where));
			}

			boundCenterBottom(marker);

			populate();
		}

		@Override
		protected OverlayItem createItem(int i) {
			return (items.get(i));
		}

		@Override
		protected boolean onTap(int i) {
			EventInfo info = events.get(i);
			mapView.getController().animateTo(info.getLocation());
			Toast.makeText(NoiteHojeMap.this, items.get(i).getSnippet(),
					Toast.LENGTH_SHORT).show();

			/*
			 * View view = new View(mContext); MapView.LayoutParams screenLP;
			 * screenLP = new
			 * MapView.LayoutParams(MapView.LayoutParams.WRAP_CONTENT,
			 * MapView.LayoutParams.WRAP_CONTENT, info.getLocation(),
			 * MapView.LayoutParams.MODE_MAP);
			 * 
			 * QuickAction quickAction = new QuickAction(view);
			 * 
			 * ActionItem first = new ActionItem(); first.setTitle("Evento");
			 * first
			 * .setIcon(getResources().getDrawable(R.drawable.ic_quickaction_date
			 * )); first.setOnClickListener(new OnClickListener() {
			 * 
			 * @Override public void onClick(View v) {
			 * 
			 * } });
			 * 
			 * quickAction.addActionItem(first);
			 * quickAction.setAnimStyle(QuickAction.ANIM_GROW_FROM_CENTER);
			 * 
			 * mapView.addView(view, screenLP);
			 * 
			 * quickAction.show();
			 */
			return (true);
		}

		@Override
		public int size() {
			return (items.size());
		}
	}
}
