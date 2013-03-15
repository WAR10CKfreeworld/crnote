package com.fff.android.crnote;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.ssl.util.Hex;

/*
 * Config import/export helper.
 * Config format is simply key=var pairs
 */
public class ConfigHelper {
	public static boolean importConfig(String fname) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(fname));
			String line;

			while ((line = br.readLine()) != null) {
				//Util.dLog("importConfig", "line(pre): " + line);
				int idx = line.indexOf('=');
				if (idx > 0) {
					String key = line.substring(0, idx);
					String val;
					if (idx == line.length()) {
						continue;
					} else {
						val = line.substring(idx+1, line.length());
					}

					if (val == null || val.length() == 0) {
						continue;
					}
					
					if (key.equals("timeout")) {
						try {
							CrNoteApp.timeout = Integer.parseInt(val);
						} catch (Exception e) { CrNoteApp.timeout = 5*60; }
					} else if (key.equals("autosave")) {
						CrNoteApp.autosave = val.equals("true");
					} else if (key.equals("backup_count")) {
						try {
							CrNoteApp.backup_count = Integer.parseInt(val);
						} catch (Exception e) { CrNoteApp.backup_count = 5; }
					} else if (key.equals("autobackup")) {
						CrNoteApp.autobackup = val.equals("true");
					} else if (key.equals("masterpass")) {
						CrNoteApp.masterpass = val.equals("true");
					} else if (key.equals("encode_meta_base64")) {
						CrNoteApp.encode_meta_base64 = val.equals("true");
					} else if (key.equals("securestorage")) {
						CrNoteApp.securestorage = val.equals("true");
					} else if (key.equals("fileenckey")) {
						CrNoteApp.webkey = Hex.decode(val);
					} else if (key.equals("webuser")) {
						CrNoteApp.webuser = val;
					} else if (key.equals("webauthkey")) {
						CrNoteApp.webauthkey = val;
					} else if (key.equals("cloudkey")) {
						CrNoteApp.cloudkey = val;
					} else if (key.equals("weburl")) {
						CrNoteApp.weburl = val;
					} else if (key.equals("patternseed")) {
						CrNoteApp.patternSeed = Util.hexStringToByteArray(val);
					}
				}
			}
			br.close();
			CrNoteApp.savePrefs();
			CrNoteApp.regenSessionKeys();
		} catch (IOException e) {
			Util.dLog("importConfig", "Error: " + e.getMessage());
			return false;
		} catch (Exception e1) {
			Util.dLog("importCOnfig", "Error: " + e1.getMessage());
			return false;
		}
		return true;
	}
	
	public static boolean exportConfig(String fname) {
		File root = new File(CrNoteApp.folder);
		if (root.canWrite()) {
			File f = new File(root, fname);
			FileWriter fw;
			try {
				fw = new FileWriter(f);
				BufferedWriter out = new BufferedWriter(fw);
				
				
				out.write("timeout=" + CrNoteApp.timeout + "\n");
				out.write("autosave=" + (CrNoteApp.autosave ? "true" : "false") + "\n");
				out.write("backup_count=" + CrNoteApp.backup_count + "\n");

				out.write("autobackup=" + (CrNoteApp.autobackup ? "true" : "false") + "\n");
				//out.write("masterpass=" + (CrNoteApp.masterpass ? "true" : "false") + "\n");
				out.write("encode_meta_base64=" + (CrNoteApp.encode_meta_base64 ? "true" : "false") + "\n");
				out.write("securestorage=" + (CrNoteApp.securestorage ? "true" : "false") + "\n");
				out.write("fileenckey=" + Hex.encode(CrNoteApp.webkey) + "\n");
				out.write("webuser=" + CrNoteApp.webuser + "\n");
				out.write("webauthkey=" + CrNoteApp.webauthkey + "\n");
				out.write("cloudkey=" + CrNoteApp.cloudkey + "\n");
				out.write("weburl=" + CrNoteApp.weburl + "\n");
				out.write("patternseed=" + Util.byteArrayToHexString(CrNoteApp.patternSeed) + "\n");

				out.close();
				return true;
			} catch (IOException e) {
				Util.dLog("exportConfig", "Error: " + e.getMessage());
			}
		}
		return false;
	}
	
	public static String configToString() {
		StringBuffer sb = new StringBuffer();
		sb.append("timeout=" + CrNoteApp.timeout + "\n");
		sb.append("autosave=" + (CrNoteApp.autosave ? "true" : "false") + "\n");
		sb.append("backup_count=" + CrNoteApp.backup_count + "\n");

		sb.append("autobackup=" + (CrNoteApp.autobackup ? "true" : "false") + "\n");
		sb.append("masterpass=" + (CrNoteApp.masterpass ? "true" : "false") + "\n");
		sb.append("encode_meta_base64=" + (CrNoteApp.encode_meta_base64 ? "true" : "false") + "\n");
		sb.append("securestorage=" + (CrNoteApp.securestorage ? "true" : "false") + "\n");
		sb.append("fileenckey=" + Hex.encode(CrNoteApp.webkey) + "\n");
		sb.append("webuser=" + CrNoteApp.webuser + "\n");
		sb.append("webauthkey=" + CrNoteApp.webauthkey + "\n");
		sb.append("weburl=" + CrNoteApp.weburl + "\n");
		sb.append("cloudkey=" + CrNoteApp.cloudkey + "\n");
		sb.append("patternseed=" + Util.byteArrayToHexString(CrNoteApp.patternSeed) + "\n");
		
		return sb.toString();
	}
	
	public static boolean stringToConfig(String input) {
		String[] slist = input.split("\n");
		for (int i = 0; i < slist.length; i++) {
			String line = slist[i];
			
			if (line.length() <= 1 ) {
				continue;
			}
			
			int idx = line.indexOf('=');
			if (idx > 0) {
				String key = line.substring(0, idx);
				String val;
				if (idx == line.length()) {
					continue;
				} else {
					val = line.substring(idx+1, line.length());
				}

				if (val == null || val.length() == 0) {
					continue;
				}
				
				if (key.equals("timeout")) {
					try {
						CrNoteApp.timeout = Integer.parseInt(val);
					} catch (Exception e) { CrNoteApp.timeout = 5*60; }
				} else if (key.equals("autosave")) {
					CrNoteApp.autosave = val.equals("true");
				} else if (key.equals("backup_count")) {
					try {
						CrNoteApp.backup_count = Integer.parseInt(val);
					} catch (Exception e) { CrNoteApp.backup_count = 5; }
				} else if (key.equals("autobackup")) {
					CrNoteApp.autobackup = val.equals("true");
				} else if (key.equals("masterpass")) {
					CrNoteApp.masterpass = val.equals("true");
				} else if (key.equals("encode_meta_base64")) {
					CrNoteApp.encode_meta_base64 = val.equals("true");
				} else if (key.equals("securestorage")) {
					CrNoteApp.securestorage = val.equals("true");
				} else if (key.equals("fileenckey")) {
					CrNoteApp.webkey = Hex.decode(val);
				} else if (key.equals("webuser")) {
					CrNoteApp.webuser = val;
				} else if (key.equals("webauthkey")) {
					CrNoteApp.webauthkey = val;
				} else if (key.equals("weburl")) {
					CrNoteApp.weburl = val;
				} else if (key.equals("cloudkey")) {
					CrNoteApp.cloudkey = val;
				} else if (key.equals("patternseed")) {
					try {
						CrNoteApp.patternSeed = Util.hexStringToByteArray(val);
					} catch (Exception e1) {
						Util.dLog("stringToConfig", "Error: " + e1.getMessage());
						return false;
					}
				} else {
					return false;
				}
			}
		}
		CrNoteApp.savePrefs();
		CrNoteApp.regenSessionKeys();
		return true;
	}
}
