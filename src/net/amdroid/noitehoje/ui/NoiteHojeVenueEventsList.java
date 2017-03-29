package net.amdroid.noitehoje.ui;

import net.amdroid.noitehoje.R;
import net.amdroid.noitehoje.provider.NoiteHojeProvider;
import net.amdroid.noitehoje.util.UIUtils;

import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.AbstractAction;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

public class NoiteHojeVenueEventsList extends Activity {
	private static final String TAG = "NoiteHojeVenueEventsList";

	private Context mContext;

	private ActionBar actionBar = null;

	private Cursor mCursor;
	private EventsAdapter adapter;
	private ListView listView;
	private String orderBy = NoiteHojeProvider.KEY_ID;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.venue_list);

		mContext = this;

		Intent intent = getIntent();
		String contentUri = intent.getStringExtra("content_uri");
		String venue_id = intent.getStringExtra("venue_id");

		ActionBar actionBar = (ActionBar) findViewById(R.id.actionbar);
		Uri uri = Uri.parse(contentUri);
		ContentResolver cr = getContentResolver();
		Cursor cursor = cr.query(uri, null, null, null, null);

		if (cursor.moveToFirst()) {
			String type = cursor.getString(cursor.getColumnIndex(NoiteHojeProvider.KEY_EVENT_TYPE));
			if (type.contentEquals("party"))
				actionBar.setHomeAction(new UIUtils.PartyAction(this));
			else
				actionBar.setHomeAction(new UIUtils.ShowAction(this));
		}

		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeLogo(R.drawable.ab_logo);
		actionBar.showHomeLogo();

		//actionBar.addAction(new MapAction());

		cursor.close();

		adapter = new EventsAdapter(this, null, true);
		listView = (ListView) findViewById(R.id.listEvents);
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(listviewOnClick);

		cursor = cr.query(NoiteHojeProvider.CONTENT_URI, null, NoiteHojeProvider.KEY_VENUE_ID + "=?", new String[] { venue_id }, null);
		adapter.changeCursor(cursor);
		adapter.notifyDataSetChanged();
	}

	private OnItemClickListener listviewOnClick = new OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			Intent intent = new Intent(mContext, NoiteHojeEvent.class);
			intent.putExtra("content_uri", (String) view.getTag());
			startActivity(intent);
		}

	};

	private class MapAction extends AbstractAction {

		public MapAction() {
				super(R.drawable.ic_actionbar_maps);
		}

		@Override
		public void performAction(View view) {
			Intent intent = new Intent(mContext, NoiteHojeMap.class);
			intent.putExtra("content_uri", NoiteHojeProvider.CONTENT_URI.toString());
			startActivity(intent);
		}
	}

}
