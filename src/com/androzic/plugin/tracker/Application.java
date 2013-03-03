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

import com.androzic.BaseApplication;
import com.androzic.data.Tracker;
import com.androzic.provider.DataContract;

public class Application extends BaseApplication
{
	public static final String TRACKER_DATE_RECEIVED_BROADCAST = "com.androzic.plugin.tracker.TRACKER_DATA_RECEIVED";
	
	int markerColor = Color.BLUE;

	/**
	 * Sends tracker to Androzic map
	 * 
	 * @throws RemoteException
	 */
	void sendMapObject(TrackerDataAccess dataAccess, Tracker tracker) throws RemoteException
	{
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
				dataAccess.saveTracker(tracker);
			}
		}
		else
		{
			Uri uri = ContentUris.withAppendedId(DataContract.MAPOBJECTS_URI, tracker.moid);
			contentProvider.update(uri, values, null, null);
		}
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
		Cursor cursor = dataAccess.getTrackers();
		if (!cursor.moveToFirst())
		{
			dataAccess.close();
			return;
		}
		do
		{
			Tracker tracker = dataAccess.getTracker(cursor);
			sendMapObject(dataAccess, tracker);
		}
		while (cursor.moveToNext());
		dataAccess.close();
	}

	/**
	 * Removes tracker from Androzic map
	 * 
	 * @throws RemoteException
	 */
	void removeMapObject(TrackerDataAccess dataAccess, Tracker tracker) throws RemoteException
	{
		if (tracker.moid <= 0)
			return;
		ContentProviderClient contentProvider = getContentResolver().acquireContentProviderClient(DataContract.MAPOBJECTS_URI);
		Uri uri = ContentUris.withAppendedId(DataContract.MAPOBJECTS_URI, tracker.moid);
		tracker.moid = 0;
		dataAccess.saveTracker(tracker);
		contentProvider.delete(uri, null, null);
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
		Cursor cursor = dataAccess.getTrackers();
		if (!cursor.moveToFirst())
		{
			dataAccess.close();
			return;
		}
		Set<Long> moids = new HashSet<Long>();
		do
		{
			Tracker tracker = dataAccess.getTracker(cursor);
			if (tracker.moid > 0)
			{
				moids.add(tracker.moid);
				tracker.moid = 0;
				dataAccess.saveTracker(tracker);
			}
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
