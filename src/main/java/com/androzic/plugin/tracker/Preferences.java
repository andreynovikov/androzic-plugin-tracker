package com.androzic.plugin.tracker;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

public class Preferences extends AppCompatActivity
{
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_preferences);

		Toolbar toolbar = (Toolbar) findViewById(R.id.action_toolbar);
		setSupportActionBar(toolbar);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		// Should be set here, if set in onCreate() it gets overwritten somewhere later
		getSupportActionBar().setTitle(R.string.menu_preferences);
		getSupportActionBar().setSubtitle(R.string.pref_tracker_title);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle action buttons
		switch (item.getItemId())
		{
			case android.R.id.home:
				finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
}
