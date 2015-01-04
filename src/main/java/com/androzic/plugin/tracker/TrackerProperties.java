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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.androzic.data.Tracker;
import com.androzic.provider.DataContract;

public class TrackerProperties extends Activity
{
	private Tracker tracker;
	private TrackerDataAccess dataAccess;
	private Application application;
	
	private TextView name;
	private String markerValue;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_tracker_properties);

		String sender = getIntent().getStringExtra("sender");
		
		application = Application.getApplication();
		dataAccess = new TrackerDataAccess(this);
		tracker = dataAccess.getTracker(sender);
		
		if (tracker == null)
		{
			finish();
			dataAccess.close();
			return;
		}
		
		name = (TextView) findViewById(R.id.name_text);
		name.setText(tracker.name);

		markerValue = tracker.marker;
		setMarker(tracker.marker);
		
		ImageButton marker = (ImageButton) findViewById(R.id.marker_button);
		marker.setOnClickListener(markerOnClickListener);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
		{
			registerForContextMenu(marker);
		}

		findViewById(R.id.save_button).setOnClickListener(doneOnClickListener);
		findViewById(R.id.cancel_button).setOnClickListener(new OnClickListener() {
			public void onClick(View v)
			{
				finish();
			}
		});
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		dataAccess.close();
	}

	@Override
	protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);
		markerValue = savedInstanceState.getString("marker");
		setMarker(markerValue);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putString("marker", markerValue);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 0 && resultCode == Activity.RESULT_OK)
		{
			markerValue = data.getStringExtra("marker");
			setMarker(markerValue);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.marker_popup, menu);
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.change:
				startActivityForResult(new Intent(DataContract.ACTION_PICK_MARKER), 0);
				break;
			case R.id.remove:
				markerValue = null;
				ImageButton marker = (ImageButton) findViewById(R.id.marker_button);
				marker.setImageDrawable(this.getResources().getDrawable(R.drawable.ic_highlight_remove_white_24dp));
				break;
		}
		return true;
	}

	private OnClickListener markerOnClickListener = new OnClickListener() {
		@SuppressLint("NewApi")
		public void onClick(View v)
		{
			// PopupMenu from compat library does not work with Dialog theme
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			{
				v.showContextMenu();
			}
			else
			{
				PopupMenu popup = new PopupMenu(TrackerProperties.this, v);
				popup.getMenuInflater().inflate(R.menu.marker_popup, popup.getMenu());
				popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					public boolean onMenuItemClick(MenuItem item)
					{
						return onContextItemSelected(item);
					}
				});
				popup.show();
			}
		}
	};

	private OnClickListener doneOnClickListener = new OnClickListener() {
		public void onClick(View v)
		{
			tracker.name = name.getText().toString();
			if ("".equals(tracker.name))
				tracker.name = tracker.sender;
			tracker.marker = markerValue == null ? "__remove__" : markerValue;
			dataAccess.updateTracker(tracker);
			try
			{
				application.sendTrackerOnMap(dataAccess, tracker);
			}
			catch (RemoteException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			setResult(Activity.RESULT_OK);
			finish();
		}
	};
	
	private void setMarker(String marker)
	{
		ImageButton markerButton = (ImageButton) findViewById(R.id.marker_button);
		Bitmap b = application.getMarker(marker);
		if (b != null)
			markerButton.setImageBitmap(b);
		else
			markerButton.setImageDrawable(this.getResources().getDrawable(R.drawable.ic_highlight_remove_white_24dp));
	}
}
