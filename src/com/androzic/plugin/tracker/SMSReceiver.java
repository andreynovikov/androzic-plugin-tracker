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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsMessage;
import android.util.Log;

import com.androzic.data.Tracker;
import com.androzic.util.CoordinateParser;

public class SMSReceiver extends BroadcastReceiver
{
	private static final String TAG = "SMSReceiver";

	@SuppressLint("SimpleDateFormat")
	private static final SimpleDateFormat JointechDateFormatter = new SimpleDateFormat("MM-dd HH:mm:ss");
	@SuppressLint("SimpleDateFormat")
	private static final SimpleDateFormat XexunDateFormatter = new SimpleDateFormat("dd/MM/yy HH:mm");
	@SuppressLint("SimpleDateFormat")
	private static final SimpleDateFormat TK102Clone1DateFormatter = new SimpleDateFormat("yy/MM/dd HH:mm");
	
	private static final Pattern realNumber = Pattern.compile("\\d+\\.\\d+");

	@Override
	public void onReceive(Context context, Intent intent)
	{
		String Sender = "";
		Log.e(TAG, "SMS received");

		Bundle extras = intent.getExtras();
		if (extras == null)
			return;

		StringBuilder messageBuilder = new StringBuilder();
		Object[] pdus = (Object[]) extras.get("pdus");
		for (int i = 0; i < pdus.length; i++)
		{
			SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdus[i]);
			String text = msg.getMessageBody();
			Sender = msg.getDisplayOriginatingAddress();
			Log.w(TAG, "Sender: " + Sender);
			if (text == null)
				continue;
			messageBuilder.append(text);
		}
		
		String text = messageBuilder.toString();
		
		Log.i(TAG, "SMS: " + text);
		Tracker tracker = new Tracker();
		if (! parseXexunTK102(text, tracker) &&
			! parseJointechJT600(text, tracker) &&
			! parseTK102Clone1(text, tracker))
			return;
			
		if (tracker.message != null)
		{
			tracker.message = tracker.message.trim();
			if ("".equals(tracker.message))
				tracker.message = null;
		}

		tracker.imei = Sender;//IMEI it is not good for ID. Phone number is better
				
		if (! "".equals(tracker.imei))
		{
			// Save tracker data
			TrackerDataAccess dataAccess = new TrackerDataAccess(context);
			dataAccess.saveTracker(tracker);
			try
			{
				Application application = Application.getApplication();
				application.sendMapObject(dataAccess, tracker);
			}
			catch (RemoteException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			dataAccess.close();

			context.sendBroadcast(new Intent(Application.TRACKER_DATE_RECEIVED_BROADCAST));

			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

			// Show notification
			boolean notifications = prefs.getBoolean(context.getString(R.string.pref_tracker_notifications), context.getResources().getBoolean(R.bool.def_notifications));
			if (notifications)
			{
				Intent i = new Intent("com.androzic.COORDINATES_RECEIVED");
				i.putExtra("title", tracker.message != null ? tracker.message : tracker.name);
				i.putExtra("sender", tracker.name);
				i.putExtra("origin", context.getApplicationContext().getPackageName());
				i.putExtra("lat", tracker.latitude);
				i.putExtra("lon", tracker.longitude);

				String msg = context.getString(R.string.notif_text, tracker.name);
				NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
				builder.setContentTitle(context.getString(R.string.app_name));
				if (tracker.message != null)
					builder.setContentText(tracker.name + ": " + tracker.message);
				else
					builder.setContentText(msg);
				PendingIntent contentIntent = PendingIntent.getBroadcast(context, (int) tracker._id, i, PendingIntent.FLAG_ONE_SHOT);
				builder.setContentIntent(contentIntent);
				builder.setSmallIcon(R.drawable.ic_stat_tracker);
				builder.setTicker(msg);
				builder.setWhen(tracker.modified);
				int defaults = Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND;
				boolean vibrate = prefs.getBoolean(context.getString(R.string.pref_tracker_vibrate), context.getResources().getBoolean(R.bool.def_vibrate));
				if (vibrate)
					defaults |= Notification.DEFAULT_VIBRATE;
				builder.setDefaults(defaults);
				builder.setAutoCancel(true);
				Notification notification = builder.build();
				NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManager.notify((int) tracker._id, notification);
			}

			// Conceal SMS
			boolean concealsms = prefs.getBoolean(context.getString(R.string.pref_tracker_concealsms), context.getResources().getBoolean(R.bool.def_concealsms));
			if (concealsms)
				abortBroadcast();
		}
	}

	private boolean parseTK102Clone1(String text, Tracker tracker)
	{
		// Clone TK-102
		//help me!
		//lat:50.123456 long:39.123456
		//speed:0.00
		//T:13/09/30 10:27
		//bat:100%
		//http://maps.google.com/maps?f=q&q=50.... 
		//
		//lat:50.123456lon:39.123456
		//speed:0.00
		//T:13/09/30 10:27
		//bat:100% 3597100123456789
		//http://maps.google.com/maps?f=q&q=50.... 
				
		//Pattern pattern = Pattern.compile("(.*)?\\s?lat:\\s?([^\\s]+)\\s  long :\\s?([^\\s]+)\\sspeed:\\s?([\\d\\.]+)\\s(?:T:)?([\\d/:\\.\\s]+)\\s(?:bat|F):([^\\s,]+)(?:V,\\d,)?\\s?signal:([^\\s]+)\\s(.*)?\\s?imei:(\\d+)", Pattern.CASE_INSENSITIVE);
		Pattern pattern = Pattern.compile("(.*)?\\s?lat:\\s?([^\\sl]+)\\s?long?:\\s?([^\\s]+)\\s?speed:\\s?([\\d\\.]+)\\s?T:?([\\d/:\\.\\s]+)\\s?bat:([^%]+)%\\s?(\\d+)?(.+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		Matcher m = pattern.matcher(text);
		if (! m.matches())
			return false;

		String latitude = m.group(2);
		String longitude = m.group(3);

		double coords[] = CoordinateParser.parse(latitude + " " + longitude);
		if (Double.isNaN(coords[0]) || Double.isNaN(coords[1]))
			return false;

		tracker.latitude = coords[0];
		tracker.longitude = coords[1];
		
		try
		{
			tracker.speed = Double.parseDouble(m.group(4)) / 3.6;
		}
		catch (NumberFormatException ignore)
		{
		}

		String time = m.group(5);
		try
		{
			Date date = TK102Clone1DateFormatter.parse(time);
			tracker.modified = date.getTime();
		}
		catch (Exception e)
		{
			Log.e(TAG, "Date error", e);
		}

		String battery = m.group(6);
		try
		{
			tracker.battery = Integer.parseInt(battery);
		}
		catch (NumberFormatException ignore)
		{
		}

		String s_imei =  m.group(7);
		
		if ( s_imei != null )	
		   tracker.imei = s_imei;
		else
			tracker.imei = "0000";
		
		String message = m.group(1);
		if (! "".equals(message))
			tracker.message = message;
		
		
		return true;
	}
	
	private boolean parseJointechJT600(String text, Tracker tracker)
	{
		// Jointech JT600
		// jeson,09-28 12:11:02,Speed:32km/h,Battery:80%,GPS:13,STANDARD,
		// http://maps.google.com/?q=22.549737N,114.076685E
		// 3110701703,09-28 12:11:02,Speed:0km/h,Charging,Base Station,STANDARD,Cell ID:4195,LAC:230
		// 3110701703,04-24 22:44:33,Speed:0km/h,Battery:90%,GPS:8,STANDARD,
		// http://maps.google.com/?q=60.010245N,30.288323E
		// ALM,SOS,3110701703,09-28 12:11:02,Speed:32km/h,Battery:80%,GPS:13,STANDARD,http://maps.google.com/?q=22.549737N,114.076685E
		// http://fiddle.re/yv1h6
		Pattern pattern = Pattern.compile("(?:ALM,)?(?:(.*),)?([^,]+),([\\d\\-:\\s]+),(?:Speed:(\\d+)km/h,)?(?:Battery:(\\d+)%|Charging),[^,]+,[^,]+,(?:\\r?\\n)?http://maps\\.google\\.com/\\?q=([^,]+),(.+)");
		Matcher m = pattern.matcher(text);
		if (! m.matches())
			return false;

		String latitude = m.group(6);
		String longitude = m.group(7);

		double coords[] = CoordinateParser.parse(latitude + " " + longitude);
		if (Double.isNaN(coords[0]) || Double.isNaN(coords[1]))
			return false;

		tracker.latitude = coords[0];
		tracker.longitude = coords[1];

		try
		{
			String speed = m.group(4);
			if (speed != null)
				tracker.speed = Double.parseDouble(speed) / 3.6;
		}
		catch (NumberFormatException ignore)
		{
		}

		String time = m.group(3);
		try
		{
			Date date = JointechDateFormatter.parse(time);
			Date now = new Date();
			date.setYear(now.getYear());
			tracker.modified = date.getTime();
		}
		catch (Exception e)
		{
			Log.e(TAG, "Date error", e);
		}

		String battery = m.group(5);
		try
		{
			tracker.battery = Integer.parseInt(battery);
		}
		catch (NumberFormatException ignore)
		{
		}

		tracker.imei = m.group(2);

		String message = m.group(1);
		if (! "".equals(message))
			tracker.message = message;
		
		return true;
	}

	private boolean parseXexunTK102(String text, Tracker tracker)
	{
		// Xexun family and some clones
		// lat: 55.807693 long: 037.730640 speed: 000.0 03/03/13 16:18   bat:F signal:F  imei:358948010446647
		// lat:55.950468 long:035.867116 speed: 000.0 24/11/12 08:54 bat:F signal:F imei:358948010446647
		// lat: 123.345678N long: 0.125621W speed: 001.2 17/07/11 21:34 F:3.92V,1,Signal:F help me imei:123456789012 07 83.8 234 15 006B 24C4
		// lat: 22.566901 long: 114.051258 speed: 0.00 14/08/09 06.54 F:3.85V,1,Signal:F help me imei:354776031555474 05 43.5 460 01 2533 720B
		// help me! lat:123.45678 long:001.23456 speed:090.00 T:17/01/11 15:14 Bat:25% Signal:F imei:1234567
		// http://fiddle.re/fpfa6
		Pattern pattern = Pattern.compile("(.*)?\\s?lat:\\s?([^\\s]+)\\slong:\\s?([^\\s]+)\\sspeed:\\s?([\\d\\.]+)\\s(?:T:)?([\\d/:\\.\\s]+)\\s(?:bat|F):([^\\s,]+)(?:V,\\d,)?\\s?signal:([^\\s]+)\\s(.*)?\\s?imei:(\\d+)", Pattern.CASE_INSENSITIVE);
		Matcher m = pattern.matcher(text);
		if (! m.matches())
			return false;

		String latitude = m.group(2);
		String longitude = m.group(3);

		double coords[] = CoordinateParser.parse(latitude + " " + longitude);
		if (Double.isNaN(coords[0]) || Double.isNaN(coords[1]))
			return false;

		tracker.latitude = coords[0];
		tracker.longitude = coords[1];

		try
		{
			tracker.speed = Double.parseDouble(m.group(4)) / 3.6;
		}
		catch (NumberFormatException ignore)
		{
		}

		String time = m.group(5);
		try
		{
			Date date = XexunDateFormatter.parse(time);
			tracker.modified = date.getTime();
		}
		catch (Exception e)
		{
			Log.e(TAG, "Date error", e);
		}

		String battery = m.group(6);
		if ("F".equals(battery))
			tracker.battery = Tracker.LEVEL_FULL;
		if ("L".equals(battery))
			tracker.battery = Tracker.LEVEL_LOW;
		try
		{
			if (battery.endsWith("%"))
				tracker.battery = Integer.parseInt(battery.substring(0, battery.length() - 1));
			if (realNumber.matcher(battery).matches())
				tracker.battery = (int) (Float.parseFloat(battery) * 100);
		}
		catch (NumberFormatException ignore)
		{
		}

		String signal = m.group(7);
		if ("F".equals(signal))
			tracker.signal = Tracker.LEVEL_FULL;
		if ("L".equals(signal) || "0".equals(signal))
			tracker.signal = Tracker.LEVEL_LOW;

		tracker.imei = m.group(9);

		String message = m.group(1);
		if (! "".equals(message))
			tracker.message = message;
		message = m.group(8);
		if (! "".equals(message))
			tracker.message = message;

		return true;
	}
}
