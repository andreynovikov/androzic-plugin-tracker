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

import com.androzic.data.TrackerFootprins;
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
	public static final String _TRACKER_ID = "_id";
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
		
		Log.w(TAG, ">>>> Constructor DB_VER " + DATABASE_VERSION);
	}

	public long updateTracker(Tracker tracker)
	{
		Log.w(TAG, ">>>> updateTracker(" + tracker.sender + ")");
		
		ContentValues values = new ContentValues();
		SQLiteDatabase db = getWritableDatabase();
		
		if (tracker.time == 0)
			tracker.time = System.currentTimeMillis();

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
			
		values.clear();
		
		values.put(MOID, tracker.moid);
		values.put(TITLE, tracker.name);
		values.put(ICON, tracker.image);
		values.put(IMEI, tracker.imei);
		values.put(SENDER, tracker.sender);
				
		if (dbTracker == null)
		{
			tracker._id = db.insert(TABLE_TRACKERS, null, values);			
		}
		else if (tracker.time >= dbTracker.time )
		{
			tracker._id = dbTracker._id;
			
			db.update(TABLE_TRACKERS, values, _TRACKER_ID + " = ?", new String[] { String.valueOf(dbTracker._id) });
		}
		
		
		if (tracker._id != -1 
		    && ( dbTracker == null || ( dbTracker != null && tracker.time != dbTracker.time )) )
		{
			values.clear();
			values.put(TRACKER_ID, tracker._id);
			values.put(LATITUDE, tracker.latitude);
			values.put(LONGITUDE, tracker.longitude);
			values.put(SPEED, tracker.speed);
			values.put(BATTERY, tracker.battery);
			values.put(SIGNAL, tracker.signal);
			values.put(TIME, Long.valueOf(tracker.time));
			
			db.insert(TABLE_HISTORY, null, values);
		}
		
		if( dbTracker != null && ( tracker.time < dbTracker.time ) )
		{
			tracker = dbTracker;
		}
		
		Log.w(TAG, ">>>> updateTracker tracker.time = " + tracker.time);
		return tracker._id;
	}


	public void removeTracker(Tracker tracker)
	{
		Log.w(TAG, ">>>> removeTracker(" + tracker.sender + ")");
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
		Log.w(TAG, ">>>> getTracker(" + sender + ")");
		
		SQLiteDatabase db = getReadableDatabase();
		
		Cursor cursor = db.query(TABLE_TRACKERS, null, SENDER + " = ?", new String[] { sender }, null, null, null);
				
		if (cursor.getCount() > 0)
		{
			cursor.moveToFirst();
			Tracker tracker = getFullInfoTracker(cursor);
			cursor.close();
			return tracker;
		}
		return null;
	}

	public Tracker getFullInfoTracker(Cursor cursor)
	{
		Log.w(TAG, ">>>> getFullInfoTracker(Cursor cursor)");
		
		SQLiteDatabase db = getReadableDatabase();
		
		Cursor historyCur = db.query(TABLE_HISTORY, null, TRACKER_ID + " = ?", new String[] { cursor.getString(cursor.getColumnIndex(_TRACKER_ID)) }, null, null, TIME + " DESC");
		
		if (historyCur.getCount() == 0)
		{
			return null;
		}		
		
		historyCur.moveToFirst();
			
		Tracker tracker = new Tracker();
		
		tracker._id = cursor.getLong(cursor.getColumnIndex(_TRACKER_ID));
		tracker.moid = cursor.getLong(cursor.getColumnIndex(MOID));
		tracker.name = cursor.getString(cursor.getColumnIndex(TITLE));
		tracker.imei = cursor.getString(cursor.getColumnIndex(IMEI));
		tracker.sender = cursor.getString(cursor.getColumnIndex(SENDER));
		tracker.image = cursor.getString(cursor.getColumnIndex(ICON));
		
		
		tracker.latitude = historyCur.getDouble(historyCur.getColumnIndex(LATITUDE));
		tracker.longitude = historyCur.getDouble(historyCur.getColumnIndex(LONGITUDE));
		tracker.speed = historyCur.getFloat(historyCur.getColumnIndex(SPEED));
		tracker.battery = historyCur.getInt(historyCur.getColumnIndex(BATTERY));
		tracker.signal = historyCur.getInt(historyCur.getColumnIndex(SIGNAL));
		tracker.time = historyCur.getLong(historyCur.getColumnIndex(TIME));
		
		return tracker;
	}

	public TrackerFootprins getTrackerFootprint(Cursor cursor)
	{
		Log.w(TAG, ">>>> getTrackerFootprint(Cursor cursor)");
		
		TrackerFootprins point = new TrackerFootprins();
		
		if (cursor.getCount() == 0)
		{
			return null;
		}
		
		point._id = cursor.getLong(cursor.getColumnIndex(_POINT_ID));
		point.moid = cursor.getLong(cursor.getColumnIndex(MOID));
		point.latitude = cursor.getDouble(cursor.getColumnIndex(LATITUDE));
		point.longitude = cursor.getDouble(cursor.getColumnIndex(LONGITUDE));
		point.speed = cursor.getFloat(cursor.getColumnIndex(SPEED));
		point.battery = cursor.getInt(cursor.getColumnIndex(BATTERY));
		point.signal = cursor.getInt(cursor.getColumnIndex(SIGNAL));
		point.time = cursor.getLong(cursor.getColumnIndex(TIME));
		
		return point;
	}
	
	
	public Cursor getHeadersOfTrackers()
	{	
		Log.w(TAG, ">>>> getHeadersOfTrackers()");
		
		SQLiteDatabase db = getReadableDatabase();
		
		return db.query(TrackerDataAccess.TABLE_TRACKERS, null , null, null, null, null, null);
	}

	public Cursor getTrackerFootprints(long trackerId)
	{	
		Log.w(TAG, ">>>> getTrackerFootprints()");
		
		SQLiteDatabase db = getReadableDatabase();
		
		return db.query(TrackerDataAccess.TABLE_HISTORY, null , TRACKER_ID + " = ?" , new String[] {String.valueOf(trackerId)}, null, null, TIME + " DESC");
	}
	
	public int saveFootprintMoid(String footprintId, String moid)
	{
		Log.w(TAG, ">>>> saveFootprintMoid(" + footprintId + "," + moid + ")");
		
		SQLiteDatabase db = getReadableDatabase();
		
		ContentValues values = new ContentValues();
		
		values.put(MOID, moid);
		
		return db.update(TABLE_HISTORY, values, _POINT_ID + " = ?", new String[] { footprintId });
	}
	
	public int saveFootprintMoid(long footprintId, long moid)
	{
		return saveFootprintMoid( String.valueOf(footprintId), String.valueOf(moid)); 
	}
	
	public int clearFootprintMoids(String trackerId)
	{
		Log.w(TAG, ">>>> clearFootprintMoids(" + trackerId + ")");
		
		SQLiteDatabase db = getReadableDatabase();
		
		ContentValues values = new ContentValues();
		
		values.put(MOID, 0);
		
		return db.update(TABLE_HISTORY, values, TRACKER_ID + " = ?", new String[] { trackerId });
	}
	
	@Override
	public void onCreate(SQLiteDatabase db)
	{
		Log.w(TAG, ">>>> onCreate");
		
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRACKERS);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
		
		db.execSQL("PRAGMA foreign_keys = ON;");
		
		db.execSQL("CREATE TABLE " + TABLE_TRACKERS + " (" + _TRACKER_ID + " INTEGER PRIMARY KEY," 
													   + MOID + " INTEGER," 
													   + IMEI + " TEXT," 
													   + SENDER + " TEXT NOT NULL UNIQUE," 
													   + TITLE + " TEXT," 
													   + ICON + " TEXT"  
											      + ");");
		
		db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" + _POINT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
													   + TRACKER_ID + " INTEGER NOT NULL," 
													   + MOID + " INTEGER,"
													   + LATITUDE + " REAL," 
													   + LONGITUDE + " REAL," 
													   + SPEED + " REAL," 
													   + BATTERY + " INTEGER," 
													   + SIGNAL + " INTEGER," 
												       + TIME + " INTEGER," 
												       + "FOREIGN KEY (" + TRACKER_ID + ") REFERENCES " + TABLE_TRACKERS +"(" + _TRACKER_ID + ") ON DELETE CASCADE"
											      + ");");
		
		
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
