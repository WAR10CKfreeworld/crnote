package com.fff.android.crnote;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Random;

import org.apache.commons.ssl.OpenSSL;

public class SecureFileStorage {
	private File fileHandler;
	String filename;
	
	public SecureFileStorage() throws Exception {
		filename = CrNoteApp.folder + File.separator + genRandomFileName();
		fileHandler = new File(filename);
	}
	
	public SecureFileStorage(String filename) throws Exception {
		fileHandler = new File(filename);
	}
	
	public SecureFileStorage(File fileH) throws Exception {
		fileHandler = fileH;
	}
	
	// Only call this once the use of this class is no longer needed as this will close the file holder
	// and delete the temporary file.
	public void close() {
		File fh = fileHandler;
		fileHandler = null;

		try {
			if (fh != null) {
				if (fh.exists()) {
					fh.delete();
				}
			}
		} catch (Exception e) {
			Util.dLog("SecureFileStorage", "Delete temp file " + e.getMessage());
		}
	}
	
	private static String genRandomFileName() {
		Random r = new Random();
		String s = "CRTMP_" + r.nextLong();
		return s;
	}
	
	public String getFilename() {
		return filename;
	}

	// Encrypt data and write to filehandler
	public void encrypt(InputStream data) throws IOException, GeneralSecurityException {
		char[] cdata = Util.byteArrayToCharArray(CrNoteApp.webkey);
		InputStream is = OpenSSL.encrypt(CrNoteApp.cryptoalgo, cdata, data, false);
		FileOutputStream fh = new FileOutputStream(fileHandler);
		for (int i = 0; /*i < clen*/; i++) {
			int b = is.read();
			if (b < 0) {
				break;
			}
			fh.write((byte)(b & 0xff));
		}
		fh.close();
	}
	
	public InputStream decrypt() throws IOException, GeneralSecurityException {
		FileInputStream fin = new FileInputStream(fileHandler);
		char[] cdata = Util.byteArrayToCharArray(CrNoteApp.webkey);
		return OpenSSL.decrypt(CrNoteApp.cryptoalgo, cdata, fin);
	}
}
