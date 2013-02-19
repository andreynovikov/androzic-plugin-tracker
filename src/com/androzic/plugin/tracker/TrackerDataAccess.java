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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.androzic.data.Tracker;

/**
 * This class helps open, create, and upgrade the database file.
 */
class TrackerDataAccess extends SQLiteOpenHelper
{
	private static final String DATABASE_NAME = "tracker.db";
	private static final int DATABASE_VERSION = 1;
	static final String TABLE_NAME = "trackers";

	/**
	 * ID
	 * <P>
	 * Type: LONG
	 * </P>
	 */
	public static final String _ID = "_id";
	/**
	 * Title
	 * <P>
	 * Type: TEXT
	 * </P>
	 */
	public static final String TITLE = "title";
	/**
	 * IMEI
	 * <P>
	 * Type: TEXT
	 * </P>
	 */
	public static final String IMEI = "imei";
	/**
	 * Latitude
	 * <P>
	 * Type: FLOAT
	 * </P>
	 */
	public static final String LATITUDE = "latitude";
	/**
	 * LONGITUDE
	 * <P>
	 * Type: FLOAT
	 * </P>
	 */
	public static final String LONGITUDE = "longitude";
	/**
	 * The timestamp for when the note was last modified
	 * <P>
	 * Type: INTEGER (long from System.curentTimeMillis())
	 * </P>
	 */
	public static final String MODIFIED_DATE = "modified";

	private static final String[] columnsId = new String[] { _ID };
	private static final String[] columnsAll = new String[] { _ID, TITLE, IMEI, LATITUDE, LONGITUDE, MODIFIED_DATE };

	TrackerDataAccess(Context context)
	{
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	public long saveTracker(Tracker tracker)
	{
		if (tracker.time == 0)
			tracker.time = System.currentTimeMillis();

		SQLiteDatabase db = getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put(TITLE, tracker.name);
		values.put(IMEI, tracker.imei);
		values.put(LATITUDE, tracker.latitude);
		values.put(LONGITUDE, tracker.longitude);
		values.put(MODIFIED_DATE, Long.valueOf(tracker.time));

		long id;

		Cursor cursor = db.query(TrackerDataAccess.TABLE_NAME, columnsId, IMEI + " = ?", new String[] { tracker.imei }, null, null, null);
		if (cursor.getCount() > 0)
		{
			cursor.moveToFirst();
			id = cursor.getLong(cursor.getColumnIndex(_ID));
			cursor.close();
			db.update(TrackerDataAccess.TABLE_NAME, values, _ID + " = ?", new String[] { String.valueOf(id) });
		}
		else
		{
			id = db.insert(TrackerDataAccess.TABLE_NAME, null, values);
		}
		tracker._id = id;
		return id;
	}

	public Tracker getTracker(Cursor cursor)
	{
		Tracker tracker = new Tracker();
		tracker._id = cursor.getLong(cursor.getColumnIndex(_ID));
		tracker.latitude = cursor.getDouble(cursor.getColumnIndex(LATITUDE));
		tracker.longitude = cursor.getDouble(cursor.getColumnIndex(LONGITUDE));
		tracker.name = cursor.getString(cursor.getColumnIndex(TITLE));
		tracker.imei = cursor.getString(cursor.getColumnIndex(IMEI));
		tracker.time = cursor.getLong(cursor.getColumnIndex(MODIFIED_DATE));
		return tracker;
	}

	public Cursor getTrackers()
	{
		SQLiteDatabase db = getReadableDatabase();
		return db.query(TrackerDataAccess.TABLE_NAME, columnsAll, null, null, null, null, null);
	}

	@Override
	public void onCreate(SQLiteDatabase db)
	{
		db.execSQL("CREATE TABLE " + TABLE_NAME + " (" + _ID + " INTEGER PRIMARY KEY," + IMEI + " TEXT," + TITLE + " TEXT," + LATITUDE + " FLOAT," + LONGITUDE + " FLOAT," + MODIFIED_DATE + " INTEGER" + ");");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
		onCreate(db);
	}
}
