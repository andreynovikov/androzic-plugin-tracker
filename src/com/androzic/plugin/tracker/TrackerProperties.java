package com.androzic.plugin.tracker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.androzic.data.Tracker;
import com.androzic.provider.DataContract;

public class TrackerProperties extends Activity
{
	private Tracker tracker;
	private TrackerDataAccess dataAccess;
	private Application application;
	
	private TextView name;
	private String iconValue;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_tracker_properties);

		String sender = getIntent().getStringExtra("sender");
		
		application = Application.getApplication();
		dataAccess = new TrackerDataAccess(this);
		tracker = dataAccess.getTracker(sender);
		
		if (tracker == null)
		{
			finish();
			dataAccess.close();
			return;
		}
		
		name = (TextView) findViewById(R.id.name_text);
		name.setText(tracker.name);

		iconValue = tracker.image;
		setIcon(tracker.image);
		
		ImageButton icon = (ImageButton) findViewById(R.id.icon_button);
		icon.setOnClickListener(iconOnClickListener);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
		{
			registerForContextMenu(icon);
		}

		((Button) findViewById(R.id.done_button)).setOnClickListener(doneOnClickListener);
		((Button) findViewById(R.id.cancel_button)).setOnClickListener(new OnClickListener() {
			public void onClick(View v)
			{
				finish();
			}
		});
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		dataAccess.close();
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);
		iconValue = savedInstanceState.getString("icon");
		setIcon(iconValue);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putString("icon", iconValue);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 0 && resultCode == Activity.RESULT_OK)
		{
			iconValue = data.getStringExtra("icon");
			setIcon(iconValue);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.marker_popup, menu);
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.change:
				startActivityForResult(new Intent(DataContract.ACTION_PICK_ICON), 0);
				break;
			case R.id.remove:
				iconValue = null;
				ImageButton icon = (ImageButton) findViewById(R.id.icon_button);
				icon.setImageDrawable(this.getResources().getDrawable(R.drawable.ic_action_halt));
				break;
		}
		return true;
	}

	private OnClickListener iconOnClickListener = new OnClickListener() {
		@SuppressLint("NewApi")
		public void onClick(View v)
		{
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			{
				v.showContextMenu();
			}
			else
			{
				PopupMenu popup = new PopupMenu(TrackerProperties.this, v);
				popup.getMenuInflater().inflate(R.menu.marker_popup, popup.getMenu());
				popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					public boolean onMenuItemClick(MenuItem item)
					{
						return onContextItemSelected(item);
					}
				});
				popup.show();
			}
		}
	};

	private OnClickListener doneOnClickListener = new OnClickListener() {
		public void onClick(View v)
		{
			tracker.name = name.getText().toString();
			if ("".equals(tracker.name))
				tracker.name = tracker.sender;
			tracker.image = iconValue == null ? "" : iconValue;
			dataAccess.saveTracker(tracker);
			try
			{
				application.sendMapObject(dataAccess, tracker);
			}
			catch (RemoteException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			setResult(Activity.RESULT_OK);
			finish();
		}
	};
	
	private void setIcon(String icon)
	{
		ImageButton iconButton = (ImageButton) findViewById(R.id.icon_button);
		Bitmap b = application.getIcon(icon);
		if (b != null)
			iconButton.setImageBitmap(b);
		else
			iconButton.setImageDrawable(this.getResources().getDrawable(R.drawable.ic_action_halt));
	}
}
