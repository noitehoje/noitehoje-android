package net.amdroid.noitehoje.ui;

import net.amdroid.noitehoje.ui.map.BalloonItemizedOverlay;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;

public class EventItemizedOverlay extends BalloonItemizedOverlay<OverlayItem> {

    private ArrayList<OverlayItem> m_overlays = new ArrayList<OverlayItem>();
    private Context c;

    public EventItemizedOverlay(Drawable defaultMarker, MapView mapView) {
        super(boundCenter(defaultMarker), mapView);
        c = mapView.getContext();
    }

    public void addOverlay(OverlayItem overlay) {
        m_overlays.add(overlay);
        populate();
    }

    @Override
    protected OverlayItem createItem(int i) {
        return m_overlays.get(i);
    }

    @Override
    public int size() {
        return m_overlays.size();
    }

    @Override
    protected boolean onBalloonTap(int index, OverlayItem item) {
        Intent intent = new Intent(c, NoiteHojeEvent.class);
        intent.putExtra("content_uri", (String) item.getTitle());

        c.startActivity(intent);
        return true;
    }

}

