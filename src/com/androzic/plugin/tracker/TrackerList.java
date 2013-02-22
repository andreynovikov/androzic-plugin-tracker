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

import android.app.ListActivity;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
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
import android.widget.TextView;

import com.androzic.data.Tracker;
import com.androzic.location.ILocationRemoteService;
import com.androzic.provider.PreferencesContract;
import com.androzic.util.StringFormatter;

public class TrackerList extends ListActivity implements OnSharedPreferenceChangeListener
{
	private static final String TAG = "TrackerList";
	private TrackerDataAccess dataAccess;
	
	private TrackerListAdapter adapter;

	private ILocationRemoteService locationService = null;

	private int coordinatesFormat = 0;
	private double speedFactor = 1;
	private String speedAbbr = "m/s";

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		TextView emptyView = (TextView) getListView().getEmptyView();
		if (emptyView != null)
			emptyView.setText(R.string.msg_empty_list);

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		onSharedPreferenceChanged(sharedPreferences, null);
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

		readAndrozicPreferences();

		dataAccess = new TrackerDataAccess(this);
		Cursor cursor = dataAccess.getTrackers();
		
		adapter = new TrackerListAdapter(this, cursor);
		setListAdapter(adapter);
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
	protected void onListItemClick(ListView l, View v, int position, long id)
	{
		Cursor cursor = (Cursor) adapter.getItem(position);
		Tracker tracker = dataAccess.getTracker(cursor);
		Log.d(TAG, "Passing coordinates to Androzic");
		Intent i = new Intent("com.androzic.CENTER_ON_COORDINATES");
		i.putExtra("lat", tracker.latitude);
		i.putExtra("lon", tracker.longitude);
		sendBroadcast(i);
	}
/*
	private void connect()
	{
		bindService(new Intent(this, SharingService.class), sharingConnection, 0);
	}

	private void disconnect()
	{
		if (sharingService != null)
		{
			unregisterReceiver(sharingReceiver);
			unbindService(sharingConnection);
			sharingService = null;
		}
	}
*/
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
		    t = (TextView) view.findViewById(R.id.imei);
		    t.setText(tracker.imei);
			String coordinates = StringFormatter.coordinates(coordinatesFormat, " ", tracker.latitude, tracker.longitude);
		    t = (TextView) view.findViewById(R.id.coordinates);
		    t.setText(coordinates);
		    String speed = String.valueOf(Math.round(tracker.speed * speedFactor)) + " " + speedAbbr;
		    t = (TextView) view.findViewById(R.id.speed);
		    t.setText(speed);
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
}
