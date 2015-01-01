/**
 * 
 */
package com.androzic.data;

public class TrackerFootprints extends MapObject {
	
	public long moid;
	public double speed;
	public long time;
	public int battery;
	public int signal;
	
	public TrackerFootprints()
	{
		super();
		speed = 0;
		time = 0;
		moid = Long.MIN_VALUE;
		battery = -1;
		signal = -1;
	}
}
