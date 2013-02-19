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

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

public class TrackerList extends ListActivity implements OnSharedPreferenceChangeListener
{
	private static final String TAG = "TrackerList";
	private TrackerDataAccess dataAccess;
	
	private TrackerListAdapter adapter;

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
//		setContentView(R.layout.act_userlist);

		TextView emptyView = (TextView) getListView().getEmptyView();
		if (emptyView != null)
			emptyView.setText(R.string.msg_empty_list);

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		onSharedPreferenceChanged(sharedPreferences, null);
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

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
			String title = cursor.getString(cursor.getColumnIndex(TrackerDataAccess.TITLE));
			String imei = cursor.getString(cursor.getColumnIndex(TrackerDataAccess.IMEI));
		    TextView t = (TextView) view.findViewById(R.id.name);
		    t.setText(title);
		    t = (TextView) view.findViewById(R.id.imei);
		    t.setText(imei);
		}
		 
		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent)
		{
		    return mInflater.inflate(mItemLayout, parent, false);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{

		if (adapter != null)
			adapter.notifyDataSetChanged();
	}
}
