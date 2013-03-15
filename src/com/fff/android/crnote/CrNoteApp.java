package com.fff.android.crnote;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.apache.commons.ssl.util.Hex;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;
import android.provider.Settings;

public class CrNoteApp extends Application {
	private byte[] textbuf; // this buffer is intended to be the storage for the file i/o
	
	public static final String APP_PREFS = "CRNOTE_APP_PREF";
	
	private static HashMap<String,Object> sharedObjMap = new HashMap<String,Object>();
	
	// Temporary settings which exist throughout the lifetime of the application
	private static HashMap<String,Object> tempAppSettings = new HashMap<String,Object>();
	
	static SharedPreferences settings;
	
	public static final String DEFAULT_FOLDER = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "crnote";
	public static final String CONFIG_FILENAME = "crnote.cfg";
	public static final int MAX_MEGAPIXELS = 10000;
	
	public static String ANDROID_ID;

	@Override
	public void onCreate()
	{
		textbuf = null;
		super.onCreate();
		
		ANDROID_ID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

		settings = getSharedPreferences(APP_PREFS, MODE_PRIVATE);
		readPrefs();
		
		boolean c = false;
		
		// If these have not been created, we create them now.
		if (webkey == null) {
			webkey = Util.getRandomBytes(defaultKeySize);
			c = true;
		}
		if (webuser == null || webuser == "") {
			webuser = Util.genUniqueID();
			c = true;
		}
		if (webauthkey == null || webauthkey == "") {
			webauthkey = Util.getRandomHexBytes(defaultKeySize);
			c = true;
		}
		if (cloudkey == null || cloudkey == "") {
			cloudkey = Util.getRandomHexBytes(defaultKeySize);
			c = true;
		}
		
		if (patternSeed == null) {
			patternSeed = Util.hexStringToByteArray(ANDROID_ID);
			c = true;
		}
		
		regenSessionKeys();
		
		if (c) {
			savePrefs();
		}

        try
        {
        	appVersionName = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
        	appVersionCode = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionCode;
        	androidVersion = android.os.Build.VERSION.RELEASE;
        }
        catch (NameNotFoundException e)
        {
        	appVersionName = "0.0.1";
        	appVersionCode = 1;
        }
	}
	
	// Temporary session keys during the life of this application's runtime.
	public static void regenSessionKeys() {
		String authkey = Util.sha256(webauthkey.getBytes());
    	setTempSettingObject("sha_webauthkey", authkey);
    	setTempSettingObject("rsessionid", Util.saltsha1(Util.getRandomHexBytes(4), authkey.getBytes(), ":"));
    	setTempSettingObject("cloudkeybytes", Util.hexStringToByteArray(cloudkey));
	}
	
	public void fullReset() {
		// cloudkey = Util.getRandomHexBytes(defaultKeySize);
		webkey = Util.getRandomBytes(defaultKeySize); // 256-bit key
		webuser = Util.genUniqueID();
		webauthkey = Util.getRandomHexBytes(defaultKeySize);
		patternSeed = Util.hexStringToByteArray(ANDROID_ID);
		regenSessionKeys();
		savePrefs();
	}
	
	public byte[] getTextbuf() {
		return textbuf;
	}

	public void setTextbuf(byte[] s) {
		textbuf = s;
	}

	public void delTextbuf() {
		textbuf = null;
	}
	
	public static Object getSharedObject(String key) {
		return sharedObjMap.get(key);
	}
	
	public static void setSharedObject(String key, Object o) {
		sharedObjMap.put(key, o);
	}
	
	public static void unsetSharedObject(String key) {
		sharedObjMap.remove(key);
	}
	
	public static void clearSharedObjects() {
		sharedObjMap.clear();
	}
	
	
	public static Object getTempSettingObject(String key) {
		return tempAppSettings.get(key);
	}
	
	public static void setTempSettingObject(String key, Object o) {
		tempAppSettings.put(key, o);
	}
	
	public static void unsetTempSettingObject(String key) {
		tempAppSettings.remove(key);
	}

	public static void readPrefs() {
		try {
			timeout = Integer.parseInt(settings.getString("timeout", "" + (5*60)));
		} catch (ClassCastException e) {
			timeout = 5*60;
		}
		
		try {
			autosave = settings.getBoolean("autosave", true);
		} catch (ClassCastException e) {
			autosave = true;
		}
		
		String p = DEFAULT_FOLDER;
		//Util.dLog("extStorage", p);
		try {
			folder = settings.getString("folder", p);
			File f = new File(folder);
			if (!f.exists() && !f.isDirectory()) {
				folder = p;
				f.mkdirs();
			}
			onlinefolder = settings.getString("onlinefolder", folder + File.separator + "online");
			File of = new File (onlinefolder);
			if (!of.exists()) {
				of.mkdirs();
			}
		} catch (ClassCastException e) {
			folder = p;
			onlinefolder = folder + File.separator + "online";
		} catch (SecurityException  e) {
			Util.dLog("Settings", "Security settings when attempting to create folder " + e.getMessage());
		}
		
		try {
			backup_count = Integer.parseInt(settings.getString("backup_count", "" + 5));
		} catch (ClassCastException e) {
			backup_count = 5;
		}
		
		try {
			autobackup = settings.getBoolean("autobackup", true);
		} catch (ClassCastException e) {
			autobackup = true;
		}
		
		try {
			masterpass = settings.getBoolean("masterpass", false);
		} catch (ClassCastException e) {
			masterpass = false;
		}
		
		try {
			encode_meta_base64 = settings.getBoolean("encode_meta_base64", false);
		} catch (ClassCastException e) {
			encode_meta_base64 = true;
		}
		
		try {
			String opt = settings.getString("megapixels", "");
			if (opt == null || opt.length() == 0) {
				megapixels = MAX_MEGAPIXELS;
			} else {
				megapixels = Integer.parseInt(opt);
			}
		} catch (ClassCastException e) {
			megapixels = MAX_MEGAPIXELS;
		}
		
		
		try {
			String webkey_s = settings.getString("webkey", "");
			if (webkey_s.length() > 0) {
				webkey = Hex.decode(webkey_s);
			}
		} catch (ClassCastException e) {
			webkey = null;
		}
		
		try {
			securestorage = settings.getBoolean("securestorage", false);
		} catch (ClassCastException e) {
			securestorage = false;
		}
		
	
		try {
			cloudkey = settings.getString("cloudkey", "");
			webuser = settings.getString("webuser", "");
			webauthkey = settings.getString("webauthkey", "");
			weburl = settings.getString("weburl", "https://crnote.com/mynote");
			//weburl = settings.getString("weburl", "http://devel033nt.crnote.com:8990/mynote");
			//weburl = settings.getString("weburl", "http://192.168.1.50:8990/mynote.php");
		} catch (ClassCastException e) {
			Util.dLog("settings err", e.toString());
		}
		
		try {
			String sps = settings.getString("patternseed", ANDROID_ID);
			if (sps.length() < 2 || sps.length() % 2 != 0) {
				patternSeed = Util.hexStringToByteArray(ANDROID_ID);
			} else {
				patternSeed = Util.hexStringToByteArray(settings.getString("patternseed", ANDROID_ID));
			}
		} catch (ClassCastException e) {
			Util.dLog("Settings err", "PatternSeed value: " + e.toString());
			patternSeed = Util.hexStringToByteArray(ANDROID_ID);
			
		}
			
		
		defaultEncMode = 0;
		try {
			defaultEncMode = Integer.valueOf(settings.getString("encModePref", "0"));
		} catch (ClassCastException e) {
			Util.dLog("settings err", e.toString());
		} catch (Exception e1) {
			Util.dLog("settings err", e1.toString());
		}
		
		//Log.i("SETTINGS", "timeout: " + timeout + ", autosave: " + autosave + ", backup_count: " + backup_count + ", webuser: " + webuser);

		// File history is always in the order of latest to oldest
		for (int i = 0; i < 10; i++) {
			try {
				String s = settings.getString("file_history_" + i, "");
				if (s == "") {
					break;
				}
				File fs = new File(s);
				if (fs.exists()) {
					addToFileHistory(s);
				}
			} catch (ClassCastException e) {
			}
		}
		
		regenSessionKeys();
	}
	
	public static void savePrefs() {
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("timeout", "" + timeout);
		editor.putBoolean("autosave", autosave);
		editor.putString("backup_count", "" + backup_count);
		editor.putBoolean("autobackup", autobackup);
		editor.putBoolean("masterpass", masterpass);
		editor.putBoolean("encode_meta_base64", encode_meta_base64);

		int i;
		for (i = 0; i < file_history.size(); i++) {
			editor.putString("file_history_" + i, file_history.get(i));
		}
		for (; i < 10; i++) {
			try {
				editor.remove("file_history_" + i);
			} catch (Exception e) {}
		}
		
		editor.putBoolean("securestorage", securestorage);
		
		if (webkey != null) {
			editor.putString("webkey", Hex.encode(webkey));
		}
		editor.putString("webuser", webuser);
		editor.putString("webauthkey", webauthkey);
		editor.putString("cloudkey", cloudkey);
		editor.putString("weburl", weburl);
		
		editor.putString("folder", folder);
		editor.putString("onlinefolder", onlinefolder);
		
		editor.putString("encModePref", String.valueOf(defaultEncMode));
		
		editor.putString("patternseed", Util.byteArrayToHexString(patternSeed));
		
		editor.commit();
	}
	
	public static void generateKeys() {
		// regenerate keys, this is a little bit dangerous because it overwrites current settings so prompt the user to confirm
		webkey = Util.getRandomBytes(defaultKeySize); // 256-bit key
		webuser = Util.genUniqueID();
		webauthkey = Util.getRandomHexBytes(defaultKeySize);
		cloudkey = Util.getRandomHexBytes(defaultKeySize);
		savePrefs();
	}
	
	public static void addToFileHistory(String s) {
		int found = -1;
		for (int i = 0; i < file_history.size(); i++) {
			if (s.equals(file_history.get(i))) {
				//Util.dLog("CrNoteApp", "addToFileHistory " + s + " found");
				found = i;
				break;
			}
		}
		
		if (found >= 0) {
			file_history.remove(found);
			file_history.add(0, s);
		} else {
			file_history.add(0, s);
		}
	}
	
	public static void clearFileHistory() {
		file_history.clear();
	}
	

	public static String getConfigFile() {
		String fn = folder + File.separator + CONFIG_FILENAME;
		return fn;
	}
	
	public static boolean isConfigFile(String path) {
		String MOUNTPATH = "/mnt";
		
		if (folder.startsWith(MOUNTPATH)) {
			if (path.startsWith(MOUNTPATH)) {
				return path.equals(getConfigFile());
			}
			return path.equals(getConfigFile().substring(MOUNTPATH.length()));
		} else {
			if (path.startsWith(MOUNTPATH)) {
				return path.substring(MOUNTPATH.length()).equals(getConfigFile());
			}
			return path.equals(getConfigFile());
		}
	}
	
	
	//
	// Shared globals and settings
	//
	public static String appErrString;
	
	// seconds to timeout and flush all memory
	public static int timeout;
	
	// automatically saves changes each time its made
	public static boolean autosave;
	public static String folder;
	public static String onlinefolder; // local cache of online files
	
	// backups to keep
	public static int backup_count;
	public static boolean autobackup;	// backup each time file is saved
	public static boolean securestorage; // double-encryption on sdcard (using the webkey after password)
	
	// use a global password for all files
	public static boolean masterpass;
	
	// file options (misc)
	public static boolean encode_meta_base64 = true;
	
	public static byte[] patternSeed;
	public static int defaultEncMode = 0;
	
	public static int megapixels = MAX_MEGAPIXELS;
	
	// cloud encryption key - the key used to encrypt the file before sending to the web/cloud
	public static byte[] webkey = null;
	public static String webuser;         // online user id
	public static String webauthkey;      // authorisation key for online service
	public static String weburl;          // url of crnote online api
	
	public static String cloudkey;        // Google cloud backup key - used to encrypt our local settings
	
	
	// algorithms used within this app.
	// AES is for encrypting file contents, and used to encrypt the name of the file when uploading online
	// SHA is used for hashing webauthkey
	public static String hashalgo = "SHA-256";
	public static String cryptoalgo = "AES256";
	public static int    defaultKeySize = 256/8; // default encryption key size
	
	public static ArrayList<String> file_history = new ArrayList<String>();
	
	public static int appVersionCode;
	public static String appVersionName;
	public static String androidVersion;
	
	// crnote.com SSL certificate
	public static String crnotePublicModulus = "00d13f11efa05e9232a540cde536fc207c20ba42790a66d744849d9b6440a920853f0e92244d90e5f262239c07ad329b52cc8f1c37994239e80dd0e5bfb7ac394bacee6313b38bdc08bf9c2ea2b12261acd41330111598d5454d264f739de7b0844ec4b7a9b05038aa953f3319b8c612c0d5171ee1f21caeb5b3ae788a3e471ac8d7b37c58d376f03d4b0fe2eb38c8d95b38dc29eab6c2176b77adbbbf2685f99447a93214410109465f09c4fbd913ff1dda25155ad0905554aa93fd2e6f4f60acbc9f397e6c467a945f6ddde129e461fba189e4f8d30b62484ac3913bfd825ddb7ae289f569fe7c6d07bf4c62569b30f9702bde048242bc3f8ab0650dc2fcadeb";
	public static String crnotePublicExponent = "65537";
	public static String crnoteSSLSerial = "4e93a80a14286298c27ff35969fbb748";
}
