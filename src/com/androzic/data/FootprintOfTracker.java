/**
 * 
 */
package com.androzic.data;

public class FootprintOfTracker extends MapObject {
	
	public long moid;
	public double speed;
	public long time;
	public int battery;
	public int signal;
	
	public FootprintOfTracker()
	{
		super();
		speed = 0;
		time = 0;
		moid = Long.MIN_VALUE;
		battery = -1;
		signal = -1;
	}
}
