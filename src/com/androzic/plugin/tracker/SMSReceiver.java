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
	private static final SimpleDateFormat XexunDateFormatter = new SimpleDateFormat("dd/MM/yy HH:mm");

	@Override
	public void onReceive(Context context, Intent intent)
	{
		Log.e(TAG, "SMS received");

		Bundle extras = intent.getExtras();
		if (extras == null)
			return;

		Tracker tracker = new Tracker();

		Object[] pdus = (Object[]) extras.get("pdus");
		for (int i = 0; i < pdus.length; i++)
		{
			SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdus[i]);
			String text = msg.getMessageBody();
			if (text == null)
				continue;

			Log.e(TAG, "SMS: " + text);

			// Xexun TK102/TK103
			// lat:55.950468 long:035.867116 speed: 000.0 24/11/12 08:54 bat:F signal:F imei:358948010446647
			Pattern pattern = Pattern.compile("lat:(\\d+\\.\\d+) long:(\\d+\\.\\d+) speed:\\s?([\\d\\.]+) ([\\d/: ]+) bat:(.+) signal:(.+) imei:(\\d+)");
			Matcher m = pattern.matcher(text);
			if (m.matches())
			{
				String latitude = m.group(1);
				String longitude = m.group(2);
				try
				{
					tracker.speed = Double.parseDouble(m.group(3)) / 3.6;
				}
				catch (NumberFormatException ignore)
				{
				}

				String time = m.group(4);
				try
				{
					Date date = XexunDateFormatter.parse(time);
					tracker.modified = date.getTime();
				}
				catch (Exception e)
				{
					Log.e(TAG, "Date error", e);
				}

				String battery = m.group(5);
				if ("F".equals(battery))
					tracker.battery = Tracker.LEVEL_FULL;
				if ("L".equals(battery))
					tracker.battery = Tracker.LEVEL_LOW;

				String signal = m.group(6);
				if ("F".equals(signal))
					tracker.signal = Tracker.LEVEL_FULL;
				if ("L".equals(signal))
					tracker.signal = Tracker.LEVEL_LOW;

				tracker.imei = m.group(7);

				double coords[] = CoordinateParser.parse(latitude + " " + longitude);
				if (Double.isNaN(coords[0]) || Double.isNaN(coords[1]))
					continue;

				tracker.latitude = coords[0];
				tracker.longitude = coords[1];
			}
		}
		if (tracker.imei != null)
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
			
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

			// Show notification
			boolean notifications = prefs.getBoolean(context.getString(R.string.pref_tracker_notifications), context.getResources().getBoolean(R.bool.def_notifications));
			if (notifications)
			{
				Intent i = new Intent("com.androzic.COORDINATES_RECEIVED");
				i.putExtra("title", tracker.name);
				i.putExtra("sender", tracker.name);
				i.putExtra("origin", context.getApplicationContext().getPackageName());
				i.putExtra("lat", tracker.latitude);
				i.putExtra("lon", tracker.longitude);
				
				String msg = context.getString(R.string.notif_text, tracker.name);
				NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
				builder.setContentTitle(context.getString(R.string.app_name));
				builder.setContentText(msg);
				PendingIntent contentIntent = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_ONE_SHOT);
				builder.setContentIntent(contentIntent);
				builder.setSmallIcon(R.drawable.ic_stat_tracker);
				builder.setTicker(msg);
				builder.setWhen(tracker.modified);
				builder.setDefaults(Notification.DEFAULT_ALL);
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
}
