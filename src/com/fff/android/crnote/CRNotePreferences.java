package com.fff.android.crnote;

import java.io.File;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;


public class CRNotePreferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	final static int INTENT_REGISTER = 1;
	final static int INTENT_FILEIMPORT_SELECT = 2;
	final static int INTENT_FILEEXPORT_SELECT = 3;
	
	private ListPreference mListEncPreferences;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		PreferenceManager prefMgr = getPreferenceManager();
		prefMgr.setSharedPreferencesName(CrNoteApp.APP_PREFS);
		prefMgr.setSharedPreferencesMode(MODE_PRIVATE);
		
		addPreferencesFromResource(R.xml.prefs);
		
		// Notes: see http://www.kaloer.com/android-preferences for custom preference,
		// ie generation of the keys for our prefs automatically when clicked

		
		Preference registeredto = (Preference) findPreference("webregisteredto");

		Preference pref = (Preference) findPreference("webuser");
		pref.setSummary(CrNoteApp.webuser);
		
		pref = (Preference) findPreference("weburl");
		pref.setSummary(CrNoteApp.weburl);

		final Preference genpref = (Preference) findPreference("generatekeys");
		genpref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				AlertDialog.Builder builder = new AlertDialog.Builder(genpref.getContext());
				builder.setMessage("Are you sure you want to regenerate all keys? Existing keys will be deleted and overwritten. You should export your current settings before continuing.")
				       .setCancelable(false)
				       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				        	   regenerateKeys();
				           }
				       })
				       .setNegativeButton("No", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				                dialog.cancel();
				           }
				       });
				AlertDialog alert = builder.create();
				alert.show();
				return true;
			}
		});
		
		final Preference importkeys = (Preference) findPreference("importkeys");
		importkeys.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(getBaseContext(), FileDialog.class);
				intent.putExtra(FileDialog.LAUNCH_NO_NEW_FILE, "yes");
				intent.putExtra(FileDialog.START_PATH, CrNoteApp.folder);
				startActivityForResult(intent, INTENT_FILEIMPORT_SELECT);
				return true;
			}
		});
		final Preference exportkeys = (Preference) findPreference("exportkeys");
		exportkeys.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				String CFGEXPORT = "crnote_exported_cfg";
				ConfigHelper.exportConfig(CFGEXPORT);
				AlertDialog.Builder builder = new AlertDialog.Builder(exportkeys.getContext());
				builder.setMessage("Config saved to " + CrNoteApp.folder + File.separatorChar + CFGEXPORT)
				       .setCancelable(false)
				       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				        	   dialog.cancel();
				           }
				       });
				AlertDialog alert = builder.create();
				alert.show();

				return true;
			}
		});
		
		CheckBoxPreference advprefopt = (CheckBoxPreference) findPreference("advancedsecurity");
		advprefopt.setChecked(false);
		
		Preference advpref = (Preference) findPreference("advancedsecprefs");
		advpref.setEnabled(false);
		
		Preference tpref = (Preference) findPreference("timeout");
		tpref.setSummary(CrNoteApp.timeout + " seconds before CrNote locks");

		Preference aboutPref = (Preference) findPreference("about");
		aboutPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				aboutDialog();
				return true;
			}
		});
		
		mListEncPreferences = (ListPreference)getPreferenceScreen().findPreference("encModePref");
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == INTENT_REGISTER) {
			refreshScreen();
		} else if (requestCode == INTENT_FILEIMPORT_SELECT && resultCode == Activity.RESULT_OK) {
			String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
			boolean res = ConfigHelper.importConfig(filePath);
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			if (res) {
				builder.setMessage("Successfully imported settings from " + filePath);
			} else {
				builder.setMessage("Failed to load settings from " + filePath);
			}
			builder.setCancelable(false)
			       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   dialog.cancel();
			           }
			       });
			AlertDialog alert = builder.create();
			alert.show();
		}
	}
	
	private void refreshScreen() {
		Preference pref = (Preference) findPreference("webuser");
		pref.setSummary(CrNoteApp.webuser);
	}
	
	private void aboutDialog() {
		String version = "";
		try {
		    PackageInfo manager=getPackageManager().getPackageInfo(getPackageName(), 0);
		    version = " v" + manager.versionName + ", build " + manager.versionCode;
		} catch (NameNotFoundException e) {
		}
		
		Dialog dialog = new Dialog(this);
		dialog.setTitle("CrNote" + version);
		dialog.setContentView(R.layout.about_dialog);
		dialog.show();
	}
	
	private void regenerateKeys() {
		//Log.i("pref", "regenerate keys called");
		CrNoteApp crapp = (CrNoteApp) getApplication();
		crapp.fullReset();
	}
	
    @Override
    protected void onResume() {
        super.onResume();

        // Setup the initial values

        
        // Set up a listener whenever a key changes            
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes            
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);    
    }
    
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("webauthkey")) { // TODO: if the authkey is changed, connect to server to change. If fails, revert the webauthkey to previous.
			//crApp.regenSessionKeys();
		} else if (key.equals("advancedsecurity")) {
			boolean enable = sharedPreferences.getBoolean(key, false);		
			Preference pref = (Preference) findPreference("advancedsecprefs");
			pref.setEnabled(enable);
		} else if (key.equals("backup_count")) {
			int count;
			try {
				count = Integer.parseInt(sharedPreferences.getString("backup_count", "2"));
				if (count > 20) {
					SharedPreferences.Editor editor = sharedPreferences.edit();
					editor.putString(key, "20");
					editor.commit();
				}
			} catch (Exception e) {
				count = CrNoteApp.backup_count;
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(key, ""+count);
				editor.commit();
			}
		} else if (key.equals("timeout")) {
			int timeout;
			try {
				timeout = Integer.parseInt(sharedPreferences.getString("timeout", "" + (5*60)));
				if (timeout < 10) {
					SharedPreferences.Editor editor = sharedPreferences.edit();
					editor.putString(key, "10");
					editor.commit();
				}
			} catch (Exception e) {
				timeout = CrNoteApp.timeout;
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(key, ""+timeout);
				editor.commit();
			}
			Preference pref = (Preference) findPreference(key);
			pref.setSummary(timeout + " seconds before CrNote locks");
		} else if (key.equals("cloudkey")) {
			String s = sharedPreferences.getString(key, "");
			
			if (s == null || s == "") {
				Util.dLog("cloudkey prf", "empty...");
				s = Util.getRandomHexBytes(CrNoteApp.defaultKeySize);
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(key, s);
				editor.commit();
			}
		} else if (key.equals("encModePref")) {
            CrNoteApp.defaultEncMode = Integer.parseInt(mListEncPreferences.getValue()); 
        } else if (key.equals("patternseed")) {
        	String sps = sharedPreferences.getString("patternseed", CrNoteApp.ANDROID_ID);
			if (sps.length() < 2 || sps.length() % 2 != 0) {
	        	Util.dLog("PatternSeed", "Invalid value format");
	        	
	        	String spv = Util.byteArrayToHexString(CrNoteApp.patternSeed);
	        	SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(key, spv);
				editor.commit();
			}
        }
		
		CrNoteApp.readPrefs();
	}	
}


