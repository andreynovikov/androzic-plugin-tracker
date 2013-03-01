/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.plugin.tracker;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.londatiga.android.QuickAction.OnActionItemClickListener;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.androzic.data.Tracker;
import com.androzic.location.BaseLocationService;
import com.androzic.location.ILocationCallback;
import com.androzic.location.ILocationRemoteService;
import com.androzic.navigation.BaseNavigationService;
import com.androzic.provider.PreferencesContract;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

public class TrackerList extends ListActivity implements OnSharedPreferenceChangeListener
{
	private static final String TAG = "TrackerList";
	private TrackerDataAccess dataAccess;
	
	private TrackerListAdapter adapter;

	private ILocationRemoteService locationService = null;
	private Location currentLocation = new Location("fake");

	private int coordinatesFormat = 0;
	private double speedFactor = 1;
	private String speedAbbr = "m/s";

	private static final int qaTrackerVisible = 1;
	private static final int qaTrackerNavigate = 2;
	private static final int qaTrackerEdit = 3;
	private static final int qaTrackerDelete = 4;
	
    private QuickAction quickAction;
	private int selectedPosition;
	private Drawable selectedBackground;

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list_with_empty_view);

		TextView emptyView = (TextView) getListView().getEmptyView();
		if (emptyView != null)
			emptyView.setText(R.string.msg_empty_list);

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		//TODO Remove if will be not used
		onSharedPreferenceChanged(sharedPreferences, null);
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

		readAndrozicPreferences();

		// Prepare quick actions menu
		Resources resources = getResources();
		quickAction = new QuickAction(this);
		quickAction.addActionItem(new ActionItem(qaTrackerVisible, getString(R.string.menu_view), resources.getDrawable(R.drawable.ic_action_show)));
		quickAction.addActionItem(new ActionItem(qaTrackerNavigate, getString(R.string.menu_navigate), resources.getDrawable(R.drawable.ic_action_directions)));
		quickAction.addActionItem(new ActionItem(qaTrackerEdit, getString(R.string.menu_edit), resources.getDrawable(R.drawable.ic_action_edit)));
		quickAction.addActionItem(new ActionItem(qaTrackerDelete, getString(R.string.menu_delete), resources.getDrawable(R.drawable.ic_action_trash)));

		quickAction.setOnActionItemClickListener(trackerActionItemClickListener);
		quickAction.setOnDismissListener(new PopupWindow.OnDismissListener() {			
			@Override
			public void onDismiss()
			{
				View v = getListView().findViewWithTag("selected");
				if (v != null)
				{
					v.setBackgroundDrawable(selectedBackground);
					v.setTag(null);
				}
			}
		});

		// Create database connection
		dataAccess = new TrackerDataAccess(this);
		Cursor cursor = dataAccess.getTrackers();
		
		// Bind list adapter
		adapter = new TrackerListAdapter(this, cursor);
		setListAdapter(adapter);

		// Connect to location service
		connect();
	}

	@Override
	public void onResume()
	{
		super.onResume();
	}

	@Override
	public void onPause()
	{
		super.onPause();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		
		// Disconnect from location service
		disconnect();
		
		// Close database connection
		adapter.getCursor().close();
		dataAccess.close();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.preferences, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuPreferences:
				startActivity(new Intent(this, Preferences.class));
				return true;
		}
		return false;
	}

	@Override
	protected void onListItemClick(ListView lv, View v, int position, long id)
	{
		v.setTag("selected");
		selectedPosition = position;
		selectedBackground = v.getBackground();
		v.setBackgroundResource(R.drawable.list_selector_background_focus);
		quickAction.show(v);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		updateData();
	}

	private void connect()
	{
		bindService(new Intent(BaseLocationService.ANDROZIC_LOCATION_SERVICE), locationConnection, BIND_AUTO_CREATE);
	}

	private void disconnect()
	{
		if (locationService != null)
		{
			try
			{
				locationService.unregisterCallback(locationCallback);
			}
			catch (RemoteException e)
			{
			}
			unbindService(locationConnection);
			locationService = null;
		}
	}
	
	private void updateData()
	{
		Cursor cursor = (Cursor) adapter.getItem(selectedPosition);
		// TODO Change to obtaining new cursor
		cursor.requery();
		adapter.notifyDataSetChanged();
	}

	public class TrackerListAdapter extends CursorAdapter
	{
		private LayoutInflater mInflater;
		private int mItemLayout;

		public TrackerListAdapter(Context context, Cursor cursor)
		{
			super(context, cursor);
			mItemLayout = R.layout.tracker_list_item;
			mInflater = LayoutInflater.from(context);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor)
		{
			Tracker tracker = dataAccess.getTracker(cursor);
		    TextView t = (TextView) view.findViewById(R.id.name);
		    t.setText(tracker.name);
			Application application = Application.getApplication();
		    Bitmap b = application.getIcon(tracker.image);
		    if (b != null)
		    {
			    Drawable drawable = new BitmapDrawable(getResources(), b);
				drawable.setBounds(0, 0, b.getWidth(), b.getHeight());
		    	t.setCompoundDrawables(drawable, null, null, null);
		    	t.setCompoundDrawablePadding(b.getWidth() / 5);
		    }
		    t = (TextView) view.findViewById(R.id.imei);
		    t.setText(tracker.imei);
			String coordinates = StringFormatter.coordinates(coordinatesFormat, " ", tracker.latitude, tracker.longitude);
		    t = (TextView) view.findViewById(R.id.coordinates);
		    t.setText(coordinates);
		    String speed = String.valueOf(Math.round(tracker.speed * speedFactor)) + " " + speedAbbr;
		    t = (TextView) view.findViewById(R.id.speed);
		    t.setText(speed);
			String distance = "";
			synchronized (currentLocation)
			{
				if (!"fake".equals(currentLocation.getProvider()))
				{
					double dist = Geo.distance(tracker.latitude, tracker.longitude, currentLocation.getLatitude(), currentLocation.getLongitude());
					distance = StringFormatter.distanceH(dist);
				}
			}
		    t = (TextView) view.findViewById(R.id.distance);
		    t.setText(distance);
		    String battery = "";
		    if (tracker.battery == Integer.MAX_VALUE)
		    	battery = getString(R.string.full);
		    if (tracker.battery == Integer.MIN_VALUE)
		    	battery = getString(R.string.low);
		    if (tracker.battery >=0 && tracker.battery <= 100)
		    	battery = String.valueOf(tracker.battery) + "%";
		    t = (TextView) view.findViewById(R.id.battery);
		    t.setText(String.format("%s: %s", getString(R.string.battery), battery));
		    String signal = "";
		    if (tracker.signal == Integer.MAX_VALUE)
		    	signal = getString(R.string.full);
		    if (tracker.signal == Integer.MIN_VALUE)
		    	signal = getString(R.string.low);
		    if (tracker.signal >=0 && tracker.signal <= 100)
		    	signal = String.valueOf(tracker.signal) + "%";
		    t = (TextView) view.findViewById(R.id.signal);
		    t.setText(String.format("%s: %s", getString(R.string.signal), signal));
		    Calendar calendar = Calendar.getInstance();
		    calendar.setTimeInMillis(tracker.modified);
		    Date date = calendar.getTime();
		    String modified = DateFormat.getDateFormat(TrackerList.this).format(date)+" "+DateFormat.getTimeFormat(TrackerList.this).format(date);
		    t = (TextView) view.findViewById(R.id.modified);
		    t.setText(modified);
		}
		 
		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent)
		{
		    return mInflater.inflate(mItemLayout, parent, false);
		}
	}
	
	private OnActionItemClickListener trackerActionItemClickListener = new OnActionItemClickListener(){
		@Override
		public void onItemClick(QuickAction source, int pos, int actionId)
		{
			Application application = (Application) getApplication();
			Cursor cursor = (Cursor) adapter.getItem(selectedPosition);
			Tracker tracker = dataAccess.getTracker(cursor);
	
	    	switch (actionId)
	    	{
	    		case qaTrackerVisible:
	    			Log.d(TAG, "Passing coordinates to Androzic");
	    			Intent i = new Intent("com.androzic.CENTER_ON_COORDINATES");
	    			i.putExtra("lat", tracker.latitude);
	    			i.putExtra("lon", tracker.longitude);
	    			sendBroadcast(i);
					break;
				case qaTrackerNavigate:
					PackageManager packageManager = getPackageManager();
					Intent serviceIntent = new Intent(BaseNavigationService.ANDROZIC_NAVIGATION_SERVICE);
					List<ResolveInfo> services = packageManager.queryIntentServices(serviceIntent, 0);
					if (services.size() > 0)
					{
						ResolveInfo service = services.get(0);
						Intent intent = new Intent();
						intent.setClassName(service.serviceInfo.packageName, service.serviceInfo.name);
						intent.setAction(BaseNavigationService.NAVIGATE_MAPOBJECT_WITH_ID);
						long id = application.getTrackerId(tracker.imei);
						intent.putExtra(BaseNavigationService.EXTRA_ID, id);
						// This should not happen but let us check
						if (id > 0)
							startService(intent);
					}
					finish();
					break;
	    		case qaTrackerEdit:
					startActivityForResult(new Intent(TrackerList.this, TrackerProperties.class).putExtra("imei", tracker.imei), 0);
	    	        break;
	    		case qaTrackerDelete:
	    			dataAccess.removeTracker(tracker);
	    			try
					{
						application.removeMapObject(tracker);
					}
					catch (RemoteException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	    			updateData();
	    			break;
	    	}
		}
	};

	private void readAndrozicPreferences()
	{
		// Resolve content provider
		ContentProviderClient client = getContentResolver().acquireContentProviderClient(PreferencesContract.PREFERENCES_URI);

		// Setup preference items we want to read (order is important - it
		// should correlate with the read order later in code)
		int[] fields = new int[] { PreferencesContract.COORDINATES_FORMAT, PreferencesContract.SPEED_FACTOR, PreferencesContract.SPEED_ABBREVIATION, PreferencesContract.DISTANCE_FACTOR, PreferencesContract.DISTANCE_ABBREVIATION,
				PreferencesContract.DISTANCE_SHORT_FACTOR, PreferencesContract.DISTANCE_SHORT_ABBREVIATION };
		// Convert them to strings
		String[] args = new String[fields.length];
		for (int i = 0; i < fields.length; i++)
		{
			args[i] = String.valueOf(fields[i]);
		}
		try
		{
			// Request data from preferences content provider
			Cursor cursor = client.query(PreferencesContract.PREFERENCES_URI, PreferencesContract.DATA_COLUMNS, PreferencesContract.DATA_SELECTION, args, null);
			cursor.moveToFirst();
			coordinatesFormat = cursor.getInt(PreferencesContract.DATA_COLUMN);
			cursor.moveToNext();
			speedFactor = cursor.getDouble(PreferencesContract.DATA_COLUMN);
			cursor.moveToNext();
			speedAbbr = cursor.getString(PreferencesContract.DATA_COLUMN);
			cursor.moveToNext();
			StringFormatter.distanceFactor = cursor.getDouble(PreferencesContract.DATA_COLUMN);
			cursor.moveToNext();
			StringFormatter.distanceAbbr = cursor.getString(PreferencesContract.DATA_COLUMN);
			cursor.moveToNext();
			StringFormatter.distanceShortFactor = cursor.getDouble(PreferencesContract.DATA_COLUMN);
			cursor.moveToNext();
			StringFormatter.distanceShortAbbr = cursor.getString(PreferencesContract.DATA_COLUMN);
		}
		catch (RemoteException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Notify that the binding is not required anymore
		client.release();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (adapter != null)
			adapter.notifyDataSetChanged();
	}
	
	private ServiceConnection locationConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			locationService = ILocationRemoteService.Stub.asInterface(service);
			try
			{
				locationService.registerCallback(locationCallback);
				Log.d(TAG, "Location service connected");
			}
			catch (RemoteException e)
			{
				e.printStackTrace();
			}
		}

		public void onServiceDisconnected(ComponentName className)
		{
			Log.d(TAG, "Location service disconnected");
			locationService = null;
			currentLocation = new Location("fake");
			if (adapter != null)
				adapter.notifyDataSetChanged();
		}
	};

	private Runnable notifyAdapter = new Runnable() {
		@Override
		public void run()
		{
			if (adapter != null)
				adapter.notifyDataSetChanged();
		}
	};

	private ILocationCallback locationCallback = new ILocationCallback.Stub() {
		@Override
		public void onGpsStatusChanged(String provider, int status, int fsats, int tsats)
		{
		}

		@Override
		public void onLocationChanged(Location loc, boolean continous, boolean geoid, float smoothspeed, float avgspeed)
		{
			Log.d(TAG, "Location arrived");
			synchronized (currentLocation)
			{
				currentLocation.set(loc);
				TrackerList.this.runOnUiThread(notifyAdapter);
			}
		}

		@Override
		public void onProviderChanged(String provider)
		{
		}

		@Override
		public void onProviderDisabled(String provider)
		{
		}

		@Override
		public void onProviderEnabled(String provider)
		{
		}
	};
}
