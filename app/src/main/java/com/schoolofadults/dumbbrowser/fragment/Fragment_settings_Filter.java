package com.schoolofadults.dumbbrowser.fragment;

import android.os.Bundle;

import com.schoolofadults.dumbbrowser.R;
import com.schoolofadults.dumbbrowser.preferences.BasePreferenceFragment;

public class Fragment_settings_Filter extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preference_filter, rootKey);
    }
}