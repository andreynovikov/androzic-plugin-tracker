/**
 * 
 */
package com.androzic.data;

public class TrackerFootprint extends MapObject {
	
	public long moid;
	public double speed;
	public long time;
	public int battery;
	public int signal;
	
	public TrackerFootprint()
	{
		super();
		speed = 0;
		time = 0;
		moid = Long.MIN_VALUE;
		battery = -1;
		signal = -1;
	}
}
