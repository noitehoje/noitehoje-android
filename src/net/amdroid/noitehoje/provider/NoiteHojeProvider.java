package net.amdroid.noitehoje.provider;

import java.util.ArrayList;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class NoiteHojeProvider extends ContentProvider {

	public static final String CONTENT_AUTHORITY = "net.amdroid.provider.noitehoje";

	public static final Uri CONTENT_URI = Uri.parse("content://net.amdroid.provider.noitehoje/events");

	// NoiteHoje database
	private SQLiteDatabase noitehojeDB;
	private static final String TAG = "NoiteHojeProvider";
	private static final String DATABASE_NAME = "noitehoje.db";
	private static final int DATABASE_VERSION = 2;
	private static final String EVENTS_TABLE = "events";

	// Event Column names
	public static final String KEY_ID = "_id";
	public static final String KEY_EVENT_ID = "eventid";
	public static final String KEY_CREATED_AT = "created_at";
	public static final String KEY_START_DATE = "start_date";
	public static final String KEY_START_TIME = "start_time";
	public static final String KEY_DATE_TS = "date_ts";
	public static final String KEY_DESCRIPTION = "description";
	public static final String KEY_EVENT_TYPE = "evt_type";
	public static final String KEY_SOURCE = "source";
	public static final String KEY_TITLE = "title";
	public static final String KEY_PERMALINK = "permalink";

	// Venue Column names
	public static final String KEY_VENUE_ID = "venue_id";
	public static final String KEY_VENUE_NAME = "venue_name";
	public static final String KEY_VENUE_URL = "venue_url";
	public static final String KEY_VENUE_PHONE = "venue_phone";

	// Location Column names
	public static final String KEY_LOCATION_ID = "location_id";
	public static final String KEY_LOCATION_COUNTRY = "location_country";
	public static final String KEY_LOCATION_STREET = "location_street";
	public static final String KEY_LOCATION_CITY = "location_city";
	//public static final String KEY_LOCATION_VENUEID = "location_venueid";
	public static final String KEY_LOCATION_AVAILABLE = "location_available";
	public static final String KEY_LOCATION_LATITUDE = "location_latitude";
	public static final String KEY_LOCATION_LONGITUDE = "location_longitude";

	private static class noitehojeDatabaseHelper extends SQLiteOpenHelper {

		private static final String DATABASE_CREATE =
			"create table " + EVENTS_TABLE + " (" +
			KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
			KEY_EVENT_ID + " TEXT UNIQUE NOT NULL, " +
			KEY_START_DATE + " TEXT, " +
			KEY_START_TIME + " TEXT, " +
			KEY_DATE_TS + " INTEGER, " +
			KEY_DESCRIPTION + " TEXT, " +
			KEY_EVENT_TYPE + " TEXT, " +
			KEY_SOURCE + " TEXT, " +
			KEY_TITLE + " TEXT NOT NULL, " +
			KEY_PERMALINK + " TEXT NOT NULL, " +
			KEY_VENUE_ID + " TEXT NOT NULL, " +
			KEY_VENUE_NAME + " TEXT NOT NULL, " +
			KEY_VENUE_PHONE + " TEXT, " +
			KEY_VENUE_URL + " TEXT, " +
			KEY_LOCATION_ID + " TEXT, " +
			KEY_LOCATION_COUNTRY + " TEXT, " +
			KEY_LOCATION_CITY + " TEXT, " +
			KEY_LOCATION_STREET + " TEXT, " +
			KEY_LOCATION_AVAILABLE + " INTEGER, " +
			KEY_LOCATION_LATITUDE + " REAL, " +
			KEY_LOCATION_LONGITUDE + " REAL);";

		public noitehojeDatabaseHelper(Context context, String name,
				CursorFactory factory, int version) {
			super(context, name, factory, version);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.d(TAG, "Creating table: " + DATABASE_CREATE);
			db.execSQL(DATABASE_CREATE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.d(TAG, "Upgrading database from version " + oldVersion +
					" to " + newVersion);
			db.execSQL("DROP TABLE IF EXISTS " + EVENTS_TABLE);
			onCreate(db);
		}
	}

	private static final int EVENTS = 1;
	private static final int EVENT_ID = 2;

	private static final UriMatcher uriMatcher;

	static {
		uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		uriMatcher.addURI("net.amdroid.provider.noitehoje", "events", EVENTS);
		uriMatcher.addURI("net.amdroid.provider.noitehoje", "events/#", EVENT_ID);
	}

	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		int count;

		switch (uriMatcher.match(uri)) {
		case EVENTS:
			count = noitehojeDB.delete(EVENTS_TABLE, where, whereArgs);
			break;
		case EVENT_ID:
			String segment = uri.getPathSegments().get(1);
			count = noitehojeDB.delete(EVENTS_TABLE, KEY_ID + "=" +
										segment +
										 (!TextUtils.isEmpty(where) ? " AND (" +
										  where + ")" : ""), whereArgs);
			break;
		default: throw new IllegalArgumentException("Unknown URI: " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	@Override
	public String getType(Uri uri) {
		switch (uriMatcher.match(uri)) {
		case EVENTS: return "vnd.android.cursor.dir/vnd.amdroid.noitehoje";
		case EVENT_ID: return "vnd.android.cursor.item/vnd.amdroid.noitehoje";
		default: throw new IllegalArgumentException("Unsupported URI: " + uri);
		}
	}

	@Override
	public Uri insert(Uri _uri, ContentValues values) {
		long rowID = noitehojeDB.insert(EVENTS_TABLE, KEY_DESCRIPTION, values);

		Log.d(TAG, "Insert: " + values.toString());
		
		if (rowID > 0) {
			Uri uri = ContentUris.withAppendedId(CONTENT_URI, rowID);
			getContext().getContentResolver().notifyChange(uri, null);
			return uri;
		}
		throw new SQLException("Failed to insert row into " + _uri);
	}

	@Override
	public ContentProviderResult[] applyBatch(
			ArrayList<ContentProviderOperation> operations)
			throws OperationApplicationException {
		noitehojeDB.beginTransaction();
		try {
			final int numOperations = operations.size();
			final ContentProviderResult[] results = new ContentProviderResult[numOperations];
			for (int i = 0; i < numOperations; i++) {
				results[i] = operations.get(i).apply(this, results, i);
				Log.d(TAG, "Just inserted event " + results[i]);
			}
			noitehojeDB.setTransactionSuccessful();
			return results;
		} finally {
			noitehojeDB.endTransaction();
		}
	}

	@Override
	public boolean onCreate() {
		Context context = getContext();
		
		noitehojeDatabaseHelper dbHelper = new noitehojeDatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
		noitehojeDB = dbHelper.getWritableDatabase();
		return (noitehojeDB == null) ? false : true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		qb.setTables(EVENTS_TABLE);

		switch (uriMatcher.match(uri)) {
		case EVENT_ID:
			qb.appendWhere(KEY_ID + "=" + uri.getPathSegments().get(1));
		default: break;
		}

		String orderBy;
		if (TextUtils.isEmpty(sortOrder)) {
			orderBy = KEY_DATE_TS;
		} else {
			orderBy = sortOrder;
		}

		Cursor c = qb.query(noitehojeDB, projection,
							selection, selectionArgs,
							null, null,
							orderBy);

		c.setNotificationUri(getContext().getContentResolver(), uri);

		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Update not supported right now...
		return 0;
	}

}
