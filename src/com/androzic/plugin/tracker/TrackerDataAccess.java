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
import android.util.Log;

import com.androzic.data.Tracker;

/**
 * This class helps open, create, and upgrade the database file.
 */
class TrackerDataAccess extends SQLiteOpenHelper
{
	private static final String DATABASE_NAME = "tracker.db";
	private static final int DATABASE_VERSION = 3;
	static final String TABLE_TRACKERS = "trackers";
	static final String TABLE_HISTORY = "history";
	private static final String TAG = "TrackerDataAccess";
	/**
	 * ID
	 * <P>
	 * Type: LONG
	 * </P>
	 */
	public static final String _TRACKER_ID = "_tracker_id";
	/**
	 * Map object ID (mapping ID to Androzic map objects)
	 * <P>
	 * Type: LONG
	 * </P>
	 */
	public static final String MOID = "moid";
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
	 * Sender
	 * <P>
	 * Type: TEXT
	 * </P>
	 */
	public static final String SENDER = "sender";
	/**
	 * Icon
	 * <P>
	 * Type: TEXT
	 * </P>
	 */
	public static final String ICON = "icon";
	/**
	 * Latitude
	 * <P>
	 * Type: DOUBLE
	 * </P>
	 */
	public static final String LATITUDE = "latitude";
	/**
	 * Longitude
	 * <P>
	 * Type: DOUBLE
	 * </P>
	 */
	public static final String LONGITUDE = "longitude";
	/**
	 * Speed
	 * <P>
	 * Type: FLOAT
	 * </P>
	 */
	public static final String SPEED = "speed";
	/**
	 * Battery level
	 * <P>
	 * Type: INTEGER
	 * </P>
	 */
	public static final String BATTERY = "battery";
	/**
	 * Signal level
	 * <P>
	 * Type: INTEGER
	 * </P>
	 */
	public static final String SIGNAL = "signal";
	/**
	 * The timestamp for when the note was last modified
	 * <P>
	 * Type: LONG (long from System.curentTimeMillis())
	 * </P>
	 */
	public static final String MODIFIED = "modified";
	// foreign key for tracker history
	public static final String TRACKER_ID = "tracker_id";
	// The timestamp for when the position was received
	public static final String TIME = "time";
	// key for history point
	public static final String _POINT_ID = "_point_id";
	
	private static final String[] trackerColumnsId = new String[] { _TRACKER_ID };
	private static final String[] trackersColumnsAll = new String[] { _TRACKER_ID, MOID, TITLE, ICON, IMEI, SENDER /*, LATITUDE, LONGITUDE, SPEED, BATTERY, SIGNAL, MODIFIED*/ };

	private static final String[] pointColumnsId = new String[] { _POINT_ID };
	private static final String[] pointColumnsAll = new String[] { _POINT_ID, LATITUDE, LONGITUDE, SPEED, BATTERY, SIGNAL, TIME };

	
	TrackerDataAccess(Context context)
	{

		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		
		Log.w(TAG, "Constructor DB_VER " + DATABASE_VERSION);
	}

	public long saveTracker(Tracker tracker)
	{
		if (tracker.modified == 0)
			tracker.modified = System.currentTimeMillis();

		SQLiteDatabase db = getWritableDatabase();

		Tracker dbTracker = getTracker(tracker.sender);
		if (dbTracker != null)
		{
			// Preserve user defined properties
			if ("".equals(tracker.name))
				tracker.name = dbTracker.name;
			if ("".equals(tracker.image))
				tracker.image = dbTracker.image;

			// Preserve map object ID if it is no set
			if (tracker.moid == Long.MIN_VALUE)
				tracker.moid = dbTracker.moid;

			// Copy tracker ID
			tracker._id = dbTracker._id;
		}
		
		// Set default name for new tracker
		if ("".equals(tracker.name))
			tracker.name = tracker.sender;
			
		ContentValues values = new ContentValues();
		values.put(MOID, tracker.moid);
		values.put(TITLE, tracker.name);
		values.put(ICON, tracker.image);
		values.put(IMEI, tracker.imei);
		values.put(SENDER, tracker.sender);
		values.put(LATITUDE, tracker.latitude);
		values.put(LONGITUDE, tracker.longitude);
		values.put(SPEED, tracker.speed);
		values.put(BATTERY, tracker.battery);
		values.put(SIGNAL, tracker.signal);
		values.put(MODIFIED, Long.valueOf(tracker.modified));

		if (tracker._id > 0)
		{
			db.update(TrackerDataAccess.TABLE_TRACKERS, values, _TRACKER_ID + " = ?", new String[] { String.valueOf(tracker._id) });
		}
		else
		{
			tracker._id = db.insert(TrackerDataAccess.TABLE_TRACKERS, null, values);
		}
		return tracker._id;
	}

	public void removeTracker(Tracker tracker)
	{
		SQLiteDatabase db = getWritableDatabase();
		Cursor cursor = db.query(TrackerDataAccess.TABLE_TRACKERS, trackerColumnsId, SENDER + " = ?", new String[] { tracker.sender }, null, null, null);
		if (cursor.getCount() > 0)
		{
			cursor.moveToFirst();
			long id = cursor.getLong(cursor.getColumnIndex(_TRACKER_ID));
			cursor.close();
			db.delete(TrackerDataAccess.TABLE_TRACKERS, _TRACKER_ID + " = ?", new String[] { String.valueOf(id) });
		}
	}

	public Tracker getTracker(String sender)
	{
		Log.w(TAG, "getTracker(String sender)");
		
		SQLiteDatabase db = getReadableDatabase();
		Cursor cursor = db.query(TrackerDataAccess.TABLE_TRACKERS, trackersColumnsAll, SENDER + " = ?", new String[] { sender }, null, null, null);
		if (cursor.getCount() > 0)
		{
			cursor.moveToFirst();
			Tracker tracker = getTracker(cursor);
			cursor.close();
			return tracker;
		}
		return null;
	}

	public Tracker getTracker(Cursor cursor)
	{
		Log.w(TAG, "getTracker(Cursor cursor)");
		
		Tracker tracker = new Tracker();
		tracker._id = cursor.getLong(cursor.getColumnIndex(_TRACKER_ID));
		tracker.moid = cursor.getLong(cursor.getColumnIndex(MOID));
		tracker.latitude = cursor.getDouble(cursor.getColumnIndex(LATITUDE));
		tracker.longitude = cursor.getDouble(cursor.getColumnIndex(LONGITUDE));
		tracker.speed = cursor.getFloat(cursor.getColumnIndex(SPEED));
		tracker.battery = cursor.getInt(cursor.getColumnIndex(BATTERY));
		tracker.signal = cursor.getInt(cursor.getColumnIndex(SIGNAL));
		tracker.name = cursor.getString(cursor.getColumnIndex(TITLE));
		tracker.imei = cursor.getString(cursor.getColumnIndex(IMEI));
		tracker.sender = cursor.getString(cursor.getColumnIndex(SENDER));
		tracker.image = cursor.getString(cursor.getColumnIndex(ICON));
		tracker.modified = cursor.getLong(cursor.getColumnIndex(MODIFIED));
		return tracker;
	}

	public Cursor getTrackers()
	{	
		SQLiteDatabase db = getReadableDatabase();
		
		return db.query(TrackerDataAccess.TABLE_TRACKERS, trackersColumnsAll, null, null, null, null, null);
	}

	@Override
	public void onCreate(SQLiteDatabase db)
	{
		Log.w(TAG, "onCreate");
		
		db.beginTransaction();
		
		db.execSQL("CREATE TABLE " + TABLE_TRACKERS + " (" + _TRACKER_ID + " INTEGER PRIMARY KEY," 
													   + MOID + " INTEGER," 
													   + IMEI + " TEXT," 
													   + SENDER + " TEXT NOT NULL UNIQUE," 
													   + TITLE + " TEXT," 
													   + ICON + " TEXT," 
													   + LATITUDE + " REAL," 
													   + LONGITUDE + " REAL," 
													   + SPEED + " REAL," 
													   + BATTERY + " INTEGER," 
													   + SIGNAL + " INTEGER," 
													   + MODIFIED + " INTEGER" 
											      + ");");
		
		db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" + _POINT_ID + " INTEGER PRIMARY KEY," 
													   + TRACKER_ID + " INTEGER," 
													   + LATITUDE + " REAL," 
													   + LONGITUDE + " REAL," 
													   + SPEED + " REAL," 
													   + BATTERY + " INTEGER," 
													   + SIGNAL + " INTEGER," 
												       + TIME + " INTEGER"
											      + ");");
		
		db.endTransaction();
		
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		Log.e(TAG, " --- onUpgrade database from " + oldVersion
		          + " to " + newVersion + " version --- ");
		
		db.beginTransaction();
		
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRACKERS);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
		onCreate(db);
		
		db.endTransaction();
	}
}
