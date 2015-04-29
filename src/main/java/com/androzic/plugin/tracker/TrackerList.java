/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2015 Andrey Novikov <http://andreynovikov.info/>
 * 
 * This file is part of Androzic application.
 * 
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.plugin.tracker;

import java.util.Calendar;
import java.util.Date;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.internal.view.SupportMenuInflater;
import android.support.v7.internal.view.menu.MenuBuilder;
import android.support.v7.internal.view.menu.MenuPopupHelper;
import android.support.v7.internal.view.menu.MenuPresenter;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.androzic.data.Tracker;
import com.androzic.location.BaseLocationService;
import com.androzic.location.ILocationCallback;
import com.androzic.location.ILocationRemoteService;
import com.androzic.navigation.BaseNavigationService;
import com.androzic.provider.PreferencesContract;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

public class TrackerList extends AppCompatActivity implements AdapterView.OnItemClickListener, MenuBuilder.Callback, MenuPresenter.Callback
{
	private static final String TAG = "TrackerList";
	private TrackerDataAccess dataAccess;

	private ListView listView;
	private TrackerListAdapter adapter;

	private ILocationRemoteService locationService = null;
	private final Location currentLocation = new Location("fake");

	private int coordinatesFormat = 0;
	private double speedFactor = 1;
	private String speedAbbr = "m/s";

	private int selectedPosition;
	private Drawable selectedBackground;
	private int accentColor;

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		Log.w(TAG, ">>>> onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_trackerlist);

		Toolbar toolbar = (Toolbar) findViewById(R.id.action_toolbar);
		setSupportActionBar(toolbar);

		listView = (ListView) findViewById(android.R.id.list);
		TextView emptyView = (TextView) findViewById(android.R.id.empty);
		emptyView.setText(R.string.msg_empty_list);
		listView.setEmptyView(emptyView);

		accentColor = getResources().getColor(R.color.theme_accent_color);

		readAndrozicPreferences();

		// Create database connection
		dataAccess = new TrackerDataAccess(this);
		Cursor cursor = dataAccess.getHeadersOfTrackers();
		Log.w(TAG, "getTrackers() - OK");
		
		// Bind list adapter
		adapter = new TrackerListAdapter(this, cursor);
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(this);

		Log.w(TAG, "Bind - OK");
		
		// Connect to location service
		connect();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		registerReceiver(receiver, new IntentFilter(Application.TRACKER_DATE_RECEIVED_BROADCAST));
	}

	@Override
	public void onPause()
	{
		super.onPause();
		try
		{
			unregisterReceiver(receiver);
		}
		catch (IllegalArgumentException e)
		{
			// ignore - it is thrown if receiver is not registered but it can not be
			// true in normal conditions
		}
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
			case R.id.action_settings:
				startActivity(new Intent(this, Preferences.class));
				return true;
		}
		return false;
	}

	@Override
	public void onItemClick(AdapterView<?> l, View v, int position, long id)
	{
		v.setTag("selected");
		selectedPosition = position;
		selectedBackground = v.getBackground();
		v.setBackgroundColor(accentColor);
		// https://gist.github.com/mediavrog/9345938#file-iconizedmenu-java-L55
		MenuBuilder menu = new MenuBuilder(this);
		menu.setCallback(this);
		MenuPopupHelper popup = new MenuPopupHelper(this, menu, v.findViewById(R.id.name));
		popup.setForceShowIcon(true);
		popup.setCallback(this);
		new SupportMenuInflater(this).inflate(R.menu.tracker_popup, menu);
		popup.show();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		updateData();
	}

	private void connect()
	{
		Intent intent = new Intent(BaseLocationService.ANDROZIC_LOCATION_SERVICE);
		ResolveInfo ri = getPackageManager().resolveService(intent, 0);
		// This generally can not happen because plugin can be run only from parent application
		if (ri == null)
			return;
		ServiceInfo service = ri.serviceInfo;
		intent.setComponent(new ComponentName(service.applicationInfo.packageName, service.name));
		bindService(intent, locationConnection, BIND_AUTO_CREATE);
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
				e.printStackTrace();
			}
			unbindService(locationConnection);
			locationService = null;
		}
	}

	private void updateData()
	{
		if (adapter == null)
			return;
		Cursor cursor = (Cursor) adapter.getItem(selectedPosition);
		// TODO Change to obtaining new cursor
		cursor.requery();
		adapter.notifyDataSetChanged();
	}

	public class TrackerListAdapter extends CursorAdapter
	{
		private static final String TAG = "TrackerList::TrackerListAdapter";
		private LayoutInflater mInflater;
		private int mItemLayout;

		public TrackerListAdapter(Context context, Cursor cursor)
		{
			super(context, cursor);
			
			Log.w(TAG, ">>>> Constructor");
			
			mItemLayout = R.layout.tracker_list_item;
			mInflater = LayoutInflater.from(context);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor)
		{
			Log.w(TAG, ">>>> bindView");
			
			Tracker tracker = dataAccess.getFullInfoTracker(cursor);
			TextView t = (TextView) view.findViewById(R.id.name);
			t.setText(tracker.name);
			Application application = Application.getApplication();
			Bitmap b = application.getMarker(tracker.marker);
			if (b != null)
			{
				Drawable drawable = new BitmapDrawable(getResources(), b);
				drawable.setBounds(0, 0, b.getWidth(), b.getHeight());
				t.setCompoundDrawables(drawable, null, null, null);
				t.setCompoundDrawablePadding(b.getWidth() / 3);
			}
			else
			{
				t.setCompoundDrawables(null, null, null, null);
			}
			t = (TextView) view.findViewById(R.id.sender);
			t.setText(tracker.sender);
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
					double bearing = Geo.bearing(currentLocation.getLatitude(), currentLocation.getLongitude(), tracker.latitude, tracker.longitude);
					distance = StringFormatter.distanceH(dist) + " " + StringFormatter.bearingSimpleH(bearing);
				}
			}
			t = (TextView) view.findViewById(R.id.distance);
			t.setText(distance);
			String battery = "";
			if (tracker.battery == Integer.MAX_VALUE)
				battery = getString(R.string.full);
			if (tracker.battery == Integer.MIN_VALUE)
				battery = getString(R.string.low);
			if (tracker.battery >= 0 && tracker.battery <= 100)
				battery = String.valueOf(tracker.battery) + "%";
			t = (TextView) view.findViewById(R.id.battery);
			if (! "".equals(battery))
				t.setText(String.format("%s: %s", getString(R.string.battery), battery));
			else
				t.setText("");
			String signal = "";
			if (tracker.signal == Integer.MAX_VALUE)
				signal = getString(R.string.full);
			if (tracker.signal == Integer.MIN_VALUE)
				signal = getString(R.string.low);
			if (tracker.signal >= 0 && tracker.signal <= 100)
				signal = String.valueOf(tracker.signal) + "%";
			t = (TextView) view.findViewById(R.id.signal);
			if (! "".equals(signal))
				t.setText(String.format("%s: %s", getString(R.string.signal), signal));
			else
				t.setText("");
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(tracker.time);
			Date date = calendar.getTime();
			String modified = DateFormat.getDateFormat(TrackerList.this).format(date) + " " + DateFormat.getTimeFormat(TrackerList.this).format(date);
			t = (TextView) view.findViewById(R.id.modified);
			t.setText(modified);
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent)
		{
			return mInflater.inflate(mItemLayout, parent, false);
		}
	}

	@Override
	public boolean onMenuItemSelected(MenuBuilder builder, MenuItem item)
	{
		Application application = (Application) getApplication();
		Cursor cursor = (Cursor) adapter.getItem(selectedPosition);
		Tracker tracker = dataAccess.getFullInfoTracker(cursor);

		switch (item.getItemId())
		{
			case R.id.action_view:
				Log.d(TAG, "Passing coordinates to Androzic");
				Intent i = new Intent("com.androzic.CENTER_ON_COORDINATES");
				i.putExtra("lat", tracker.latitude);
				i.putExtra("lon", tracker.longitude);
				sendBroadcast(i);
				return true;
			case R.id.action_navigate:
				Intent intent = new Intent(BaseNavigationService.NAVIGATE_MAPOBJECT_WITH_ID);
				intent.putExtra(BaseNavigationService.EXTRA_ID, tracker.moid);
				// This should not happen but let us check
				if (tracker.moid > 0)
					startService(intent);
				finish();
				return true;
			case R.id.action_edit:
				startActivityForResult(new Intent(TrackerList.this, TrackerProperties.class).putExtra("sender", tracker.sender), 0);
				return true;
			case R.id.action_delete:
				try
				{
					application.removeTrackerFromMap(dataAccess, tracker);
				}
				catch (RemoteException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				dataAccess.removeTracker(tracker);
				updateData();
				return true;
		}
		return false;
	}

	@Override
	public void onMenuModeChange(MenuBuilder builder)
	{
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing)
	{
		selectedPosition = -1;
		if (allMenusAreClosing && listView != null)
		{
			View v = listView.findViewWithTag("selected");
			if (v != null)
			{
				v.setBackgroundDrawable(selectedBackground);
				v.setTag(null);
			}
		}
	}

	@Override
	public boolean onOpenSubMenu(MenuBuilder menu)
	{
		return false;
	}

	private void readAndrozicPreferences()
	{
		// Resolve content provider
		ContentProviderClient client = getContentResolver().acquireContentProviderClient(PreferencesContract.PREFERENCES_URI);

		// Setup preference items we want to read (order is important - it
		// should correlate with the read order later in code)
		int[] fields = new int[] { PreferencesContract.COORDINATES_FORMAT, PreferencesContract.SPEED_FACTOR, PreferencesContract.SPEED_ABBREVIATION, PreferencesContract.DISTANCE_FACTOR,
				PreferencesContract.DISTANCE_ABBREVIATION, PreferencesContract.DISTANCE_SHORT_FACTOR, PreferencesContract.DISTANCE_SHORT_ABBREVIATION };
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

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			if (action.equals(Application.TRACKER_DATE_RECEIVED_BROADCAST))
			{
				updateData();
			}
		}
	};

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
			currentLocation.set(new Location("fake"));
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
