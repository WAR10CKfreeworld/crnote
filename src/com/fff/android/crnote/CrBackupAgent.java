package com.fff.android.crnote;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.ssl.Base64;
import org.apache.commons.ssl.util.Hex;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;


public class CrBackupAgent extends BackupAgentHelper {
	// Android Backup Service Key:
	// AEdPqrEAAAAICC8vTof5hLxh8DVgwSWjxQoHjTEGl5yeLk2bnQ

	// An arbitrary string used within the BackupAgentHelper implementation to
	// identify the SharedPreferenceBackupHelper's data.
	static final String MY_PREFS_BACKUP_KEY = "crprefs";
	static final String CRBACKUPAGENT_PREFS = "CR_BACKUP_PREFS";

	// Only backup the webkey used for double encryption - in case the user didn't export
	// his config prior to update.
	public void onCreate() {
		SharedPreferences settings = this.getSharedPreferences(CRBACKUPAGENT_PREFS, MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		if (CrNoteApp.cloudkey != null) {
			editor.putString("ekey", CrNoteApp.cloudkey);
		} else {
			editor.putString("ekey", "");
		}
		editor.putString("folder", CrNoteApp.folder);
		editor.commit();
		
		SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this, CRBACKUPAGENT_PREFS);
		addHelper(MY_PREFS_BACKUP_KEY, helper);
		Util.dLog("BackupAgent", "Backup Agent created.");
	}

	public void onBackup (ParcelFileDescriptor oldState, BackupDataOutput data,
			ParcelFileDescriptor newState) throws IOException {
		super.onBackup(oldState, data, newState);
		
		// Encrypt our settings to SD card using the cloudkey
		String cfg = ConfigHelper.configToString();
		char[] passChars = Util.hexStringToCharArray(CrNoteApp.cloudkey);
		String encData = Util.aesEncToB64(passChars, cfg.getBytes());
		if (encData != null) {
			File f = new File(CrNoteApp.getConfigFile());
			FileOutputStream fout = new FileOutputStream(f);
			fout.write(encData.getBytes());
			fout.flush();
	        fout.close();
		}
		
		Util.dLog("BackupAgent", "onBackup completed");
	}
	
	public void onRestore(BackupDataInput data, int appVersionCode,
			ParcelFileDescriptor newState) throws IOException {
		super.onRestore(data, appVersionCode, newState);
		
		SharedPreferences settings = this.getSharedPreferences(CRBACKUPAGENT_PREFS, MODE_PRIVATE);
		char[] cloudkeyChars = null;
		String cloudkeyHex = null;
		try {
			cloudkeyHex = settings.getString("ekey", "");
			if (cloudkeyHex.length() > 0) {
				cloudkeyChars = Util.hexStringToCharArray(cloudkeyHex);
			}
		} catch (ClassCastException e) {
		}
		
		CrNoteApp.cloudkey = cloudkeyHex;
		CrNoteApp.setTempSettingObject("cloudkeybytes", Util.hexStringToByteArray(cloudkeyHex));
		
		CrNoteApp.folder = settings.getString("folder", CrNoteApp.DEFAULT_FOLDER);
		
		//Util.dLog("onRestore", "called");
		
		// Decrypt our settings from SD card using cloudkey
		File f = new File(CrNoteApp.getConfigFile());
		if (f.exists() && f.length() > 0 && f.length() < (16*1024)) {
			FileInputStream fin = new FileInputStream(f);
			byte[] buf = new byte[(int) f.length()];
			fin.read(buf);
			fin.close();

			String decData = Util.aesDecFromB64(buf, cloudkeyChars);
			if (decData != null) {
				ConfigHelper.stringToConfig(decData);
			}
			
			Util.dLog("BackupAgent", "onRestore completed");
		} else {
			Util.dLog("BackupAgent", "Unable to find configuration file at " + CrNoteApp.getConfigFile());
		}
	}
}
