package com.fff.android.crnote;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.UUID;

import android.provider.Settings;
import android.util.Log;

import org.apache.commons.ssl.util.Hex;
import org.apache.commons.ssl.util.UTF8;
import org.apache.commons.ssl.Base64;

public class Util {
	public static final String CRNOTE_MAGIC_START = "_CRNOTE_MAGIC_V";
	public static final String CRNOTE_MAGIC_VER   = "01";
	public static final String CRNOTE_MAGIC_END   = "_$839hifr8793kladsjfwer9iwe$_\n";
	public static final String CRNOTE_MAGIC = CRNOTE_MAGIC_START + CRNOTE_MAGIC_VER + CRNOTE_MAGIC_END;
	public static final int CRNOTE_MAGIC_RANDBYTES = 8;
	public static final int CRNOTE_MAGIC_TOTALLEN = CRNOTE_MAGIC_RANDBYTES + CRNOTE_MAGIC.length();

	public static String byteArrayToHexString(byte[] b) {
		StringBuffer sb = new StringBuffer(b.length * 2);
		for (int i = 0; i < b.length; i++) {
			int v = b[i] & 0xff;
			if (v < 16) {
				sb.append('0');
			}
			sb.append(Integer.toHexString(v));
		}
		return sb.toString().toUpperCase();
	}
	
	private static boolean isMagicString(byte[] src, int offset) {
		int magicLen = CRNOTE_MAGIC.length();
		if (src.length < offset || (src.length - offset) <= magicLen) {
			return false;
		}
		
		if (!CRNOTE_MAGIC_START.equals(new String(src, offset, CRNOTE_MAGIC_START.length()))) {
			return false;
		}
		
		int endStart = CRNOTE_MAGIC_START.length() + CRNOTE_MAGIC_VER.length() + offset;
		if (!CRNOTE_MAGIC_END.equals(new String(src, endStart, CRNOTE_MAGIC_END.length()))) {
			return false;
		}
		
		return true;
	}

	public static byte[] generateCrHeader() {
		byte[] out = new byte[CRNOTE_MAGIC_TOTALLEN];
		try {
			SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");

			sr.nextBytes(out);
		} catch (NoSuchAlgorithmException e) {
			SecureRandom sr = new SecureRandom();
			sr.nextBytes(out);
		}
		for (int x = 0; x < CRNOTE_MAGIC_RANDBYTES; x++) {
			out[x] = (byte)((((int)out[x] & 0xff) % 95) + 32); // restrict to printable ASCII: ' '[32] -> ~[126]
			//dLog("generateCrHeader", "out[x] RAND: " + (char)out[x] + " [" + (int)out[x] + "]");
		}

		byte[] crmagic = CRNOTE_MAGIC.getBytes();
		int i = CRNOTE_MAGIC_RANDBYTES;
		for (int j = 0; j < crmagic.length; j++) {
			out[i] = crmagic[j];
			i++;
		}
		return out;
	}
	
	// Get offset of the data in the string, i.e. if this is a CrNote file, then skip the header
	// and return the index within the string where the data really starts.
	public static int getRealDataOffset(byte [] src) {
		if (isMagicString(src, CRNOTE_MAGIC_RANDBYTES)) {
			return CRNOTE_MAGIC_TOTALLEN;
		}
		return 0;
	}
	
	
	public static byte[] getRandomBytes(int len) {
		byte[] out = new byte[len];
		try {
			SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
			sr.nextBytes(out);
		} catch (NoSuchAlgorithmException e) {
			SecureRandom sr = new SecureRandom();
			sr.nextBytes(out);
		}
		return out;
	}
	
	public static String getRandomNumber(int len) {
		StringBuffer out = new StringBuffer();
		SecureRandom sr;
		try {
			sr = SecureRandom.getInstance("SHA1PRNG");
		} catch (NoSuchAlgorithmException e) {
			sr = new SecureRandom();
		}
		while (out.length() < len) {
			long l = sr.nextLong();
			if (l < 0) {
				l *= -1;
			}
			out.append(l);
		}
		return out.substring(0, len);
	}
	
	public static String getRandomBase64(int len) {
		byte[] v = getRandomBytes(len);
		return Base64.encodeBase64StringNoChunk(v);
	}
	
	public static String getRandomString(int len) {
		byte[] v = getRandomBytes(len);
		
		for (int i = 0; i < v.length; i++) {
			int n = (int)(v[i] & 0xff) % 94;
			n += 33;
			v[i] = (byte)n;
		}
		String s = new String(v);
		return s;
	}
	
	public static String getRandomHexBytes(int len) {
		return Hex.encode(getRandomBytes(len));
	}
	
	public static String getAndroidID() {
		return CrNoteApp.ANDROID_ID;
	}
	
	// Return a unique user ID built in a 64-bit unique key and Base64URL'd
	public static String genUniqueID() {
		// Output: Base64(SHA1(Date + fps + serial + android ID + UUID + 16byte random), 8Bytes)
		String fps = android.os.Build.FINGERPRINT;
		String ser = "";
		String aid = CrNoteApp.ANDROID_ID;

		try {
			Field f = android.os.Build.class.getDeclaredField("SERIAL");
			ser = f.toString();
		} catch (Exception e) {
		}

		long ts = Calendar.getInstance().getTimeInMillis();
		byte[] srand = getRandomBytes(16);
		UUID uuid = UUID.randomUUID();

		String s = ts + '-' + fps + ser + aid + uuid.toString();
		byte[] output = new byte[s.length() + srand.length];
		System.arraycopy(s.getBytes(), 0, output, 0, s.length());
		System.arraycopy(srand, 0, output, s.length(), srand.length);

		MessageDigest sha1;
		try {
			sha1 = MessageDigest.getInstance("SHA1");
			sha1.update(output);
			byte[] digest = new byte[8];
			System.arraycopy(sha1.digest(), 0, digest, 0, digest.length);

			// Use URL encoded base 64 string
			Base64 b64 = new Base64(0, null, true);
			String buffer = b64.encodeToString(digest);
			//Util.dLog("getUniqueID", buffer);
			return buffer;
		} catch (NoSuchAlgorithmException e) {
			Util.dLog("Gen ID", "NoSuchAlgorithmException " + e.getMessage());
			//e.printStackTrace();
			return null;
		}
	}
	
	public static String saltsha1(String salt, byte[] data, String outsep) {
		byte[] b = new byte[salt.getBytes().length + data.length];
		MessageDigest sha1;
		System.arraycopy(salt.getBytes(), 0, b, 0, salt.getBytes().length);
		System.arraycopy(data, 0, b, salt.getBytes().length, data.length);
		try {
			sha1 = MessageDigest.getInstance("SHA1");
			sha1.update(b);
			return (salt + outsep + byteArrayToHexString(sha1.digest()));
		} catch (NoSuchAlgorithmException e) {
			Util.dLog("SHA1", "NoSuchAlgorithmException " + e.getMessage());
			//e.printStackTrace();
		}
		return "";
	}
	
	public static String sha256(byte[] data) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA256");
			sha.update(data);
			return byteArrayToHexString(sha.digest());
		} catch (NoSuchAlgorithmException e) {
			Util.dLog("SHA256", "NoSuchAlgorithmException " + e.getMessage());
			//e.printStackTrace();
		}
		return "";
	}

	public static char[] hexStringToCharArray(String hexString) {
		int len = hexString.length()/2;
		char [] res = new char[len];
		for (int i = 0; i < len; i++) {
			res[i] = (char)(0xff & ((
					Character.digit(hexString.charAt(i*2), 16) << 4) +
					Character.digit(hexString.charAt(i*2 + 1), 16)
					));  
		}
		return res;
	}
	
	public static byte[] hexStringToByteArray(String hexString) {
		int len = hexString.length()/2;
		byte [] res = new byte[len];
		for (int i = 0; i < len; i++) {
			res[i] = (byte)((Character.digit(hexString.charAt(i*2), 16) << 4) +
							Character.digit(hexString.charAt(i*2 + 1), 16));  
		}
		return res;
	}
	
	public static String aesEncToB64(char[] pwd, byte[] data) {
		// B64(OpenSSL_AES(data,key))
		byte[] ctxt;
		try {
			ctxt = org.apache.commons.ssl.OpenSSL.encrypt("AES128", pwd, data, false);
		} catch (IOException e) {
			Util.dLog("AES Enc", "OpenSSL IOException " + e.getMessage());
			//e.printStackTrace();
			return null;
		} catch (GeneralSecurityException e) {
			Util.dLog("AES Enc", "OpenSSL SecurityException " + e.getMessage());
			//e.printStackTrace();
			return null;
		}
		String saltString = "Salted__";
		byte[] stripped = new byte[ctxt.length - saltString.length()];
		System.arraycopy(ctxt, saltString.length(), stripped, 0, stripped.length);
		return Base64.encodeBase64StringNoChunk(stripped);
	}
	
	public static String aesDecFromB64(String data, char[] pwd) {
		return aesDecFromB64(UTF8.toBytes(data), pwd);
	}
	
	public static String aesDecFromB64(byte[] data, char[] pwd) {
		byte[] src = Base64.decodeBase64(data);
		String saltString = "Salted__";
		byte[] salted = new byte[saltString.length() + src.length];
		System.arraycopy(saltString.getBytes(), 0, salted, 0, saltString.length());
		System.arraycopy(src, 0, salted, saltString.length(), src.length);

		byte [] decrypted;
		try {
			decrypted = org.apache.commons.ssl.OpenSSL.decrypt("AES128", pwd, salted);
		} catch (IOException e) {
			Util.dLog("AES Dec", "OpenSSL IOException " + e.getMessage());
			//e.printStackTrace();
			return null;
		} catch (GeneralSecurityException e) {
			Util.dLog("AES Dec", "OpenSSL SecurityException " + e.getMessage());
			//e.printStackTrace();
			return null;
		}
		return new String(decrypted);
	}
	
	public static char[] byteArrayToCharArray(byte[] in) {
		char[] res = new char[in.length];
		
		for (int i = 0; i < in.length; i++) {
			res[i] = (char)(in[i] & 0xff);
		}
		debugDumpCharArray(res);
		return res;
	}
	
	public static void debugDumpCharArray(char[] in) {
		StringBuffer s = new StringBuffer();
		for (char c: in) {
			s.append(Integer.toHexString((int) c) + " ");
		}
		//Util.dLog("HEX", s.toString());
	}
	
	public static void dLog(String tag, String message) {
		Log.d(tag, message);
	}
	
	public static String fileContentType(String filename) {
		String s = filename.toLowerCase();
		
		if (s.endsWith(".jpg") || s.endsWith(".jpeg")) {
			return "image/jpeg";
		}
		
		if (s.endsWith(".png")) {
			return "image/png";
		}
		
		if (s.endsWith(".txt")) {
			return "text/plain";
		}
		
		return "unknown/binary";
	}
}
