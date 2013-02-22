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

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import com.androzic.ui.SeekbarPreference;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preferences);
	}

    @Override
	public void onResume()
    {
        super.onResume();
        initSummaries(getPreferenceScreen());
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
	public void onPause()
    {
    	super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);    
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

    		if (pref instanceof PreferenceGroup || pref instanceof PreferenceScreen)
            {
    			initSummaries((PreferenceGroup) pref);
            }
    	}
    }
}
