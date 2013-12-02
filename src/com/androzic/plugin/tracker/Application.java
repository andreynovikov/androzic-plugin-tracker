/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013 Andrey Novikov <http://andreynovikov.info/>
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
import java.util.HashSet;
import java.util.Set;

import android.content.ContentProviderClient;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;

import com.androzic.BaseApplication;
import com.androzic.data.Tracker;
import com.androzic.data.TrackerFootprins;
import com.androzic.provider.DataContract;

public class Application extends BaseApplication
{
	final String TAG = "Application";
	
	public static final String TRACKER_DATE_RECEIVED_BROADCAST = "com.androzic.plugin.tracker.TRACKER_DATA_RECEIVED";
	
	int markerColor = Color.BLUE;

	/**
	 * Sends tracker to Androzic map
	 * 
	 * @throws RemoteException
	 */
	void sendTrackerOnMap(TrackerDataAccess dataAccess, Tracker tracker) throws RemoteException
	{
		Log.w(TAG, ">>>> sendTrackerOnMap");
		Log.w(TAG, "     tracker._id = " + tracker._id);
		Log.w(TAG, "     tracker.time = " + tracker.time);
		Log.w(TAG, "     tracker.latitude = " + tracker.latitude);
		Log.w(TAG, "     tracker.longitude = " + tracker.longitude);
		
		ContentProviderClient contentProvider = getContentResolver().acquireContentProviderClient(DataContract.MAPOBJECTS_URI);
		ContentValues values = new ContentValues();
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LATITUDE_COLUMN], tracker.latitude);
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LONGITUDE_COLUMN], tracker.longitude);
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_NAME_COLUMN], tracker.name);
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_IMAGE_COLUMN], tracker.image);
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_BACKCOLOR_COLUMN], markerColor);

		if (tracker.moid <= 0)
		{
			Uri uri = contentProvider.insert(DataContract.MAPOBJECTS_URI, values);
			if (uri != null)
			{
				tracker.moid = ContentUris.parseId(uri);
				dataAccess.updateTracker(tracker);
			}
		}
		else
		{
			Uri uri = ContentUris.withAppendedId(DataContract.MAPOBJECTS_URI, tracker.moid);
			contentProvider.update(uri, values, null, null);
		}
		
		
		Cursor cursor = dataAccess.getTrackerFootprints(tracker._id);
		
		cursor.moveToFirst(); //skip first point
		
		if (cursor.moveToNext())
		{
			TrackerFootprins footprint = new TrackerFootprins();
			
			do
			{
				footprint = dataAccess.getTrackerFootprint(cursor);
				
				Calendar calendar = Calendar.getInstance();
				calendar.setTimeInMillis(footprint.time);
				Date date = calendar.getTime();
				String time = DateFormat.getTimeFormat(this).format(date);
				
				String pointName = tracker.name + " " + time;
			
				values.clear();
				
				values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LATITUDE_COLUMN], footprint.latitude);
				values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LONGITUDE_COLUMN], footprint.longitude);
				values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_NAME_COLUMN], pointName);
				values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_IMAGE_COLUMN], "");
				values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_BACKCOLOR_COLUMN], markerColor);

				if (footprint.moid <= 0)
				{
					Uri uri = contentProvider.insert(DataContract.MAPOBJECTS_URI, values);
					if (uri != null)
					{
						dataAccess.saveFootprintMoid(String.valueOf(footprint._id), String.valueOf(ContentUris.parseId(uri)));
					}
				}
				else
				{
					Uri uri = ContentUris.withAppendedId(DataContract.MAPOBJECTS_URI, footprint.moid);
					contentProvider.update(uri, values, null, null);
				}
				
			}
			while (cursor. moveToNext());
		}
		
		cursor.close();
		
		contentProvider.release();
	}

	/**
	 * Sends all known trackers to Androzic map
	 * 
	 * @throws RemoteException
	 */
	void sendMapObjects() throws RemoteException
	{
		TrackerDataAccess dataAccess = new TrackerDataAccess(this);
		Tracker tracker;
		
		Cursor cursor = dataAccess.getHeadersOfTrackers();
		if (!cursor.moveToFirst())
		{
			dataAccess.close();
			return;
		}
		do
		{
			tracker = dataAccess.getFullInfoTracker(cursor);
			sendTrackerOnMap(dataAccess, tracker);
		}
		while (cursor. moveToNext());
		dataAccess.close();
	}

	/**
	 * Removes tracker from Androzic map
	 * 
	 * @throws RemoteException
	 */
	void removeTrackerFromMap(TrackerDataAccess dataAccess, Tracker tracker) throws RemoteException
	{
		if (tracker.moid <= 0)
			return;
		ContentProviderClient contentProvider = getContentResolver().acquireContentProviderClient(DataContract.MAPOBJECTS_URI);
		Uri uri = ContentUris.withAppendedId(DataContract.MAPOBJECTS_URI, tracker.moid);
		tracker.moid = 0;
		dataAccess.updateTracker(tracker);
		contentProvider.delete(uri, null, null);
		
		Cursor footprintsCursor = dataAccess.getTrackerFootprints(tracker._id);
		if (footprintsCursor.moveToFirst())
		{
			do
			{
				TrackerFootprins footprint = dataAccess.getTrackerFootprint(footprintsCursor);
				if (footprint.moid > 0)
				{
					uri = ContentUris.withAppendedId(DataContract.MAPOBJECTS_URI, footprint.moid);
					contentProvider.delete(uri, null, null);
					
					dataAccess.saveFootprintMoid(footprint._id, 0);
				}
				
				
			}
			while (footprintsCursor.moveToNext());
		}
		
		footprintsCursor.close();

		
		contentProvider.release();
	}

	/**
	 * Removes all previously sent trackers from Androzic map
	 * 
	 * @throws RemoteException
	 */
	void removeMapObjects() throws RemoteException
	{
		TrackerDataAccess dataAccess = new TrackerDataAccess(this);
		Cursor cursor = dataAccess.getHeadersOfTrackers();
		if (!cursor.moveToFirst())
		{
			dataAccess.close();
			return;
		}
		Set<Long> moids = new HashSet<Long>();
		do
		{
			Tracker tracker = dataAccess.getFullInfoTracker(cursor);
			if (tracker.moid > 0)
			{
				moids.add(tracker.moid);
				tracker.moid = 0;
				dataAccess.updateTracker(tracker);
			}
			
			Cursor footprintsCursor = dataAccess.getTrackerFootprints(tracker._id);
			if (footprintsCursor.moveToFirst())
			{
				do
				{
					TrackerFootprins footprint = dataAccess.getTrackerFootprint(footprintsCursor);
					if (footprint.moid > 0)
					{
						moids.add(footprint.moid);
						dataAccess.saveFootprintMoid(footprint._id, 0);
					}
					
					
				}
				while (footprintsCursor.moveToNext());
			}
			
			footprintsCursor.close();
		}
		while (cursor.moveToNext());

		ContentProviderClient contentProvider = getContentResolver().acquireContentProviderClient(DataContract.MAPOBJECTS_URI);
		String[] args = new String[moids.size()];
		int i = 0;
		for (Long moid : moids)
		{
			args[i] = String.valueOf(moid);
			i++;
		}
		contentProvider.delete(DataContract.MAPOBJECTS_URI, DataContract.MAPOBJECT_ID_SELECTION, args);
		contentProvider.release();
	}

	/**
	 * Queries icon bitmap
	 */
	public Bitmap getIcon(String icon)
	{
		Bitmap bitmap = null;

		if (icon == null || "".equals(icon))
			return null;

		// Resolve content provider
		ContentProviderClient client = getContentResolver().acquireContentProviderClient(DataContract.ICONS_URI);
		Uri uri = Uri.withAppendedPath(DataContract.ICONS_URI, icon);
		try
		{
			Cursor cursor = client.query(uri, DataContract.ICON_COLUMNS, null, null, null);
			cursor.moveToFirst();
			byte[] bytes = cursor.getBlob(DataContract.ICON_COLUMN);
			if (bytes != null)
				bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
		}
		catch (RemoteException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		client.release();
		return bitmap;
	}

	@Override
	public String getRootPath()
	{
		throw new java.lang.RuntimeException("getRootPath() not implemented");
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		setInstance(this);
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		markerColor = sharedPreferences.getInt(getString(R.string.pref_tracker_markercolor), getResources().getColor(R.color.marker));
	}
}
