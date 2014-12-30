package com.androzic.plugin.tracker;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.support.v4.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.androzic.ui.SeekbarPreference;

public class PreferencesFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preferences);
		setHasOptionsMenu(true);
	}

    @Override
	public void onResume()
    {
        super.onResume();
        initSummaries(getPreferenceScreen());
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
	public void onPause()
    {
    	super.onPause();
	    getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		inflater.inflate(R.menu.help, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.action_help:
				PreferencesHelpDialog dialog = new PreferencesHelpDialog();
				dialog.show(getFragmentManager(), "dialog");
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        Preference pref = findPreference(key);
       	setPrefSummary(pref);
    }

    private void setPrefSummary(Preference pref)
	{
        if (pref instanceof ListPreference)
        {
	        CharSequence summary = ((ListPreference) pref).getEntry();
	        if (summary != null)
	        {
	        	pref.setSummary(summary);
	        }
        }
        else if (pref instanceof EditTextPreference)
        {
	        CharSequence summary = ((EditTextPreference) pref).getText();
	        if (summary != null)
	        {
	        	pref.setSummary(summary);
	        }
        }
        else if (pref instanceof SeekbarPreference)
        {
	        CharSequence summary = ((SeekbarPreference) pref).getText();
	        if (summary != null)
	        {
	        	pref.setSummary(summary);
	        }
        }
	}

	private void initSummaries(PreferenceGroup preference)
    {
    	for (int i=preference.getPreferenceCount()-1; i>=0; i--)
    	{
    		Preference pref = preference.getPreference(i);
           	setPrefSummary(pref);

    		if (pref instanceof PreferenceGroup)
            {
    			initSummaries((PreferenceGroup) pref);
            }
    	}
    }
}
