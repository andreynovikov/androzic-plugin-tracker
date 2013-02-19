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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.androzic.data.Tracker;
import com.androzic.util.CoordinateParser;

public class SMSReceiver extends BroadcastReceiver
{
	private static final String TAG = "SMSReceiver";
	
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
					tracker.speed = Double.parseDouble(m.group(3));
				}
				catch (NumberFormatException ignore)
				{
				}
				//tracker.time = m.group(4);
				//tracker.battery = m.group(5);
				//tracker.signal = m.group(6);
				tracker.imei = m.group(7);
				tracker.name = tracker.imei + tracker.imei;

				double coords[] = CoordinateParser.parse(latitude + " " + longitude);
				if (Double.isNaN(coords[0]) || Double.isNaN(coords[1]))
					continue;
				
				tracker.latitude = coords[0];
				tracker.longitude = coords[1];
				
				Log.w(TAG, "IMEI: " + tracker.imei);
				Log.w(TAG, "LAT: " + tracker.latitude);
				Log.w(TAG, "LON: " + tracker.longitude);
			}
		}
		if (tracker.imei != null)
		{
			TrackerDataAccess dataAccess = new TrackerDataAccess(context);
			dataAccess.saveTracker(tracker);
			dataAccess.close();
			abortBroadcast();
		}
	}
}
