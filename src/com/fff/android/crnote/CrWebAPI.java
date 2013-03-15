package com.fff.android.crnote;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;

import java.security.GeneralSecurityException;

import org.apache.commons.ssl.OpenSSL;
import org.json.JSONObject;

public class CrWebAPI {
	CrNoteApp crApp;
	WSHelper  wsh = null;

	public CrWebAPI(CrNoteApp theApp) {
		this.crApp = theApp;
	}
	
	public String getResponse() {
		return (wsh != null ? wsh.getResponse() : null);
	}

	public String getErrorMsg() {
		return (wsh != null ? wsh.getErrorMsg() : null);
	}

	public int getResponseCode() {
		return (wsh != null ? wsh.getResponseCode() : -1);
	}

	public boolean sendFile(String localfile, String remotefile) {
		/*
		 * Upload: POST /mynote.php?uid=<your_username>&fid=salt^AES(salt++filename,authkey)&rid=<rid> HTTP/1.1
		 * Host: crnote.com
		 * Authorization: Basic <Base64(uid:authkey)>
		 * Content-length: nnn
		 * Content-type: application/octet-stream \r\n xxxxxxxxxxxxxxxxxxxx
		 */

		String authkey = (String) crApp.getTempSettingObject("sha_webauthkey");
		String rid = (String) crApp.getTempSettingObject("rsessionid");
		
		wsh = new WSHelper(CrNoteApp.weburl, CrNoteApp.webuser, authkey);
		wsh.addQueryParam("act", "upload");
		wsh.addQueryParam("rid", rid);
		wsh.addQueryParam("uid", CrNoteApp.webuser);
		wsh.addQueryParam("fid", remotefile);

		// read file contents
		File f = new File(localfile);
		FileInputStream instream;
		try {
			instream = new FileInputStream(f);
		} catch (FileNotFoundException e1) {
			Util.dLog("CrNote-SendFile", "FilenotFoundException: " + e1.getMessage());
			//e1.printStackTrace();
			return false;
		}
		InputStream cipherstream;
		try {
			// Encrypt the file using webkey
			cipherstream = OpenSSL.encrypt(CrNoteApp.cryptoalgo, getOnlineWebKey(), instream, false);
		} catch (IOException e1) {
			wsh.setErrorMsg(e1.getMessage());
			e1.printStackTrace();
			return false;
		} catch (GeneralSecurityException e1) {
			wsh.setErrorMsg(e1.getMessage());
			e1.printStackTrace();
			return false;
		}

		String tempFile = CrNoteApp.onlinefolder + File.separator + Util.genUniqueID() + ".tmp";

		FileOutputStream ostream;
		try {
			ostream = new FileOutputStream(tempFile);
			org.apache.commons.ssl.Util.pipeStream(cipherstream, ostream, false);
			ostream.flush();
			ostream.close();
		} catch (FileNotFoundException e2) {
			Util.dLog("SendFile", "FileNotFound(trying to write to tempFile): " + e2.getMessage());
			//e2.printStackTrace();
			return false;
		} catch (IOException e1) {
			Util.dLog("SendFile", "OpenSSL IOException: " + e1.getMessage());
			//e1.printStackTrace();
			return false;
		}
		
		File tf = new File(tempFile);
		try {
			instream = new FileInputStream(tf);
		} catch (FileNotFoundException e1) {
			Util.dLog("SendFile", "FileNotFound(trying to access tempFile): " + e1.getMessage());
			//e1.printStackTrace();
			return false;
		}
		
		try {
			boolean reqresult = wsh.postFile(instream, tf.length());
			
			// Delete temporary file
			tf.delete();
			
			if (!reqresult) {
				return false;
			}

			String resp;
			int rcode = wsh.getResponseCode();
			if (rcode == 200) {
				resp = wsh.getResponse();
			} else {
				resp = wsh.getErrorMsg();
				Util.dLog("SendFile", resp + " --- " + wsh.getResponse());
				return false;
			}
		} catch (Exception e) {
			Util.dLog("SendFile", "POST Error: " + e.getMessage());
			//e.printStackTrace();
			return false;
		}
		return true;
	}

	
	public boolean sendFile(byte[] filedata, String remotefile) {
		/*
		 * Upload: POST /mynote.php?uid=<your_username>&fid=salt^AES(salt++filename,authkey)&rid=<rid> HTTP/1.1
		 * Host: crnote.com
		 * Authorization: Basic <Base64(uid:authkey)>
		 * Content-length: nnn
		 * Content-type: application/octet-stream \r\n xxxxxxxxxxxxxxxxxxxx
		 */

		String authkey = (String) crApp.getTempSettingObject("sha_webauthkey");
		String rid = (String) crApp.getTempSettingObject("rsessionid");

		wsh = new WSHelper(CrNoteApp.weburl, CrNoteApp.webuser, authkey);
		wsh.addQueryParam("act", "upload");
		wsh.addQueryParam("rid", rid);
		wsh.addQueryParam("uid", CrNoteApp.webuser);
		wsh.addQueryParam("fid", remotefile);

		ByteArrayInputStream instream = new ByteArrayInputStream(filedata);
		InputStream cipherstream;
		try {
			// Encrypt the file using webkey
			cipherstream = OpenSSL.encrypt(CrNoteApp.cryptoalgo, getOnlineWebKey(), instream, false);
		} catch (IOException e1) {
			wsh.setErrorMsg(e1.getMessage());
			e1.printStackTrace();
			return false;
		} catch (GeneralSecurityException e1) {
			wsh.setErrorMsg(e1.getMessage());
			e1.printStackTrace();
			return false;
		}
		
		String tempFile = CrNoteApp.onlinefolder + File.separator + Util.genUniqueID() + ".tmp";

		FileOutputStream ostream;
		try {
			ostream = new FileOutputStream(tempFile);
			org.apache.commons.ssl.Util.pipeStream(cipherstream, ostream, false);
			ostream.flush();
			ostream.close();
		} catch (FileNotFoundException e2) {
			Util.dLog("SendFile", "FileNotFound: " + e2.getMessage());
			//e2.printStackTrace();
			return false;
		} catch (IOException e1) {
			Util.dLog("SendFile", "IOException: " + e1.getMessage());
			//e1.printStackTrace();
			return false;
		}
		
		File tf = new File(tempFile);
		FileInputStream finstream;
		try {
			finstream = new FileInputStream(tf);
		} catch (FileNotFoundException e1) {
			Util.dLog("SendFile", "FileNotFound(trying to access tempFile): " + e1.getMessage());
			//e1.printStackTrace();
			return false;
		}
		
		try {
			boolean reqresult = wsh.postFile(finstream, tf.length());
			tf.delete();
			
			if (!reqresult) {
				return false;
			}

			String resp;
			int rcode = wsh.getResponseCode();
			if (rcode == 200) {
				resp = wsh.getResponse();
			} else {
				resp = wsh.getErrorMsg();
				Util.dLog("SendFile err", resp + " --- " + wsh.getResponse());
				return false;
			}
		} catch (Exception e) {
			Util.dLog("SendFile", "Error: " + e.getMessage());
			//e.printStackTrace();
			return false;
		}
		return true;
	}
	

	
	// Return the list of files mapped to their timestamps
	// The filenames are still encrypted using the sendFile method above so
	// they'll
	// need to be decrypted to extract the real filename.
	public HashMap<String, String> getFiles() {
		/*
		 * GET /mynote.php?uid=<your_username>&act=list&rid=<rid> HTTP/1.1
		 * Host: crnote.com
		 * Authorization: Basic <base64(uid:authkey)>
		 */
		String authkey = (String) crApp.getTempSettingObject("sha_webauthkey");
		String rid = (String) crApp.getTempSettingObject("rsessionid");

		wsh = new WSHelper(CrNoteApp.weburl, CrNoteApp.webuser, authkey);
		wsh.addQueryParam("act", "list");
		wsh.addQueryParam("rid", rid);
		wsh.addQueryParam("uid", CrNoteApp.webuser);

		HashMap<String, String> files = new HashMap<String, String>();

		try {
			boolean reqresult = wsh.get();
			if (!reqresult) {
				return null;
			}

			String resp;
			int rcode = wsh.getResponseCode();
			if (rcode == 200) {
				resp = wsh.getResponse();
				
				if (resp.startsWith("[]")) {
					return null;
				}

				JSONObject jobj = new JSONObject(resp);

				@SuppressWarnings("rawtypes")
				Iterator iter = jobj.keys();
				while (iter.hasNext()) {
					String key = (String) iter.next();
					String value = jobj.getString(key);
					files.put(key, value);
				}

			} else {
				resp = wsh.getErrorMsg();
				Util.dLog("getFiles", "Error: (" + resp + ") " + wsh.getResponse());
				return null;
			}
		} catch (Exception e) {
			Util.dLog("getFiles", "Error trying to get file list from server: " + e.getMessage());
			//e.printStackTrace();
			return null;
		}
		return files;
	}

	// Filename provided is assumed to be encrypted format (per sendFile)
	public byte[] getFile(String name) {
		/*
		 * POST /mynote.php?uid=<your_username>&act=get&rid=<rid> HTTP/1.1
		 * Host: crnote.com
		 * Authorization: Basic <base64(uid:authkey)> \r\n
		 * fid=<B64(OpenSSL_AES(data,key))>
		 */
		String authkey = (String) crApp.getTempSettingObject("sha_webauthkey");
		String rid = (String) crApp.getTempSettingObject("rsessionid");


		// Get registration status
		wsh = new WSHelper(CrNoteApp.weburl, CrNoteApp.webuser, authkey);
		wsh.addQueryParam("act", "get");
		wsh.addQueryParam("rid", rid);
		wsh.addQueryParam("uid", CrNoteApp.webuser);
		wsh.addParam("fid", name);
		try {
			boolean reqresult = wsh.get();
			if (!reqresult) {
				return null;
			}

			String resp;
			int rcode = wsh.getResponseCode();
			if (rcode == 200) {
				// Decrypt the file using webkey
				byte[] data;
				try {
					data = OpenSSL.decrypt(CrNoteApp.cryptoalgo, getOnlineWebKey(), wsh.getBinResponse());
				} catch (IOException e1) {
					wsh.setErrorMsg(e1.getMessage());
					Util.dLog("getFile", "OpenSSL Decrypt Error: " + e1.getMessage());
					//e1.printStackTrace();
					return null;
				} catch (GeneralSecurityException e1) {
					wsh.setErrorMsg(e1.getMessage());
					Util.dLog("getFile", "OpenSSL Decrypt Error: " + e1.getMessage());
					return null;
				}
				return data;
			} else {
				resp = wsh.getErrorMsg();
				Util.dLog("getFile", "Error: (" + resp + ") " + wsh.getResponse());
				return null;
			}
		} catch (Exception e) {
			Util.dLog("getFile", "Error downloading file: " + e.getMessage());
			//e.printStackTrace();
			return null;
		}
	}
	
	// Filename provided is assumed to be encrypted format (per sendFile)
	public boolean getFile(String name, String toLocalStore) {
		/*
		 * POST /mynote.php?uid=<your_username>&act=get&rid=<rid> HTTP/1.1
		 * Host: crnote.com
		 * Authorization: Basic <base64(uid:authkey)> \r\n
		 * fid=<B64(OpenSSL_AES(data,key))>
		 */
		String authkey = (String) crApp.getTempSettingObject("sha_webauthkey");
		String rid = (String) crApp.getTempSettingObject("rsessionid");

		// Get registration status
		wsh = new WSHelper(CrNoteApp.weburl, CrNoteApp.webuser, authkey);
		wsh.addQueryParam("act", "get");
		wsh.addQueryParam("rid", rid);
		wsh.addQueryParam("uid", CrNoteApp.webuser);
		wsh.addParam("fid", name);
		try {
			boolean reqresult = wsh.get();
			if (!reqresult) {
				return false;
			}

			String resp;
			int rcode = wsh.getResponseCode();
			if (rcode == 200) {
				Util.dLog("getFile", "OK");
				
				//Util.dLog("webkey", Hex.encode(CrNoteApp.webkey));
				byte[] src = wsh.getBinResponse();
				//Util.dLog("SRC LEN", ""+src.length);
				byte[] data;
				try {
					data = OpenSSL.decrypt(CrNoteApp.cryptoalgo, getOnlineWebKey(), src);
				} catch (IOException e1) {
					wsh.setErrorMsg(e1.getMessage());
					e1.printStackTrace();
					return false;
				} catch (GeneralSecurityException e1) {
					wsh.setErrorMsg(e1.getMessage());
					e1.printStackTrace();
					return false;
				}
				File f = new File(toLocalStore);
				OutputStream out = new FileOutputStream(f);
				out.write(data);
				out.close();
				return true;
			} else {
				resp = wsh.getErrorMsg();
				Util.dLog("getFile", "Error: (" + resp + ") " + wsh.getResponse());
				return false;
			}
		} catch (Exception e) {
			Util.dLog("getFile", "Error downloading file: " + e.getMessage());
			//e.printStackTrace();
			return false;
		}
	}

	// name is encrypted format per sendFile
	public boolean deleteFile(String name) {
		/*
		 * POST /mynote.php?uid=<your_username>&act=delete&rid=<rid> HTTP/1.1
		 * Host: crnote.com
		 * Authorization: Basic <base64(uid:authkey)> \r\n
		 * fid=<B64(OpenSSL_AES(data,key))>
		 */
		String authkey = (String) crApp.getTempSettingObject("sha_webauthkey");
		String rid = (String) crApp.getTempSettingObject("rsessionid");

		wsh = new WSHelper(CrNoteApp.weburl, CrNoteApp.webuser, authkey);
		wsh.addQueryParam("act", "delete");
		wsh.addQueryParam("rid", rid);
		wsh.addQueryParam("uid", CrNoteApp.webuser);
		wsh.addParam("fid", name);
		try {
			boolean reqresult = wsh.get();
			if (!reqresult) {
				return false;
			}

			String resp;
			int rcode = wsh.getResponseCode();
			if (rcode == 200) {
				resp = wsh.getResponse();
			} else {
				resp = wsh.getErrorMsg();
				Util.dLog("deleteFile", "Error: (" + resp + ") " + wsh.getResponse());
				return false;
			}
		} catch (Exception e) {
			Util.dLog("deleteFile", "Error deleting file: " + e.getMessage());
			//e.printStackTrace();
			return false;
		}
		return true;
	}

	public boolean updateAuth(String newAuth) {
		/*
		 * POST /mynote.php?uid=<your_username>&act=update&rid=<rid> HTTP/1.1
		 * Host: crnote.com
		 * Authorization: Basic <base64(uid:old-authkey)>
		 * Content-length: xxx \r\n
		 * auth=<authkey>
		 */
		String authkey = (String) crApp.getTempSettingObject("sha_webauthkey");
		String rid = (String) crApp.getTempSettingObject("rsessionid");

		wsh = new WSHelper(CrNoteApp.weburl, CrNoteApp.webuser, authkey);
		wsh.addQueryParam("act", "update");
		wsh.addQueryParam("rid", rid);
		wsh.addQueryParam("uid", CrNoteApp.webuser);
		wsh.addParam("auth", newAuth);
		try {
			boolean reqresult = wsh.post();
			if (!reqresult) {
				return false;
			}

			String resp;
			int rcode = wsh.getResponseCode();
			if (rcode == 200) {
				resp = wsh.getResponse();
			} else {
				resp = wsh.getErrorMsg();
				Util.dLog("updateAuth", "Error: (" + resp + ") " + wsh.getResponse());
				return false;
			}
		} catch (Exception e) {
			Util.dLog("updateAuth", "Error updating auth: " + e.getMessage());
			//e.printStackTrace();
			return false;
		}
		return true;
	}

	private char[] getOnlineWebKey() {
		return Util.byteArrayToCharArray(CrNoteApp.webkey);
		//Util.dLog("WEBKEY", Hex.encode(crApp.webkey));
		//return Hex.encode(crApp.webkey).toCharArray();
	}
}
