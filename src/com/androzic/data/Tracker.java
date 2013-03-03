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

package com.androzic.data;

public class Tracker extends MapObject
{
	public static final int LEVEL_UNKNOWN = -1;
	public static final int LEVEL_LOW = Integer.MIN_VALUE;
	public static final int LEVEL_FULL = Integer.MAX_VALUE;

	/**
	 * Map object ID (mapping ID to Androzic map objects)
	 */
	public long moid;
	public double speed;
	public long modified;
	public String message;
	/**
	 * Tracker battery level: LEVEL_UNKNOWN, LEVEL_LOW, LEVEL_FULL, 0-100 - percent, > 100 - voltage*100
	 */
	public int battery;
	/**
	 * Tracker signal level: LEVEL_UNKNOWN, LEVEL_LOW, LEVEL_FULL, 0-100 - percent
	 */
	public int signal;
	public String imei;

	public Tracker()
	{
		super();
		speed = 0;
		modified = 0;
		moid = Long.MIN_VALUE;
		battery = LEVEL_UNKNOWN;
		signal = LEVEL_UNKNOWN;
	}
}
