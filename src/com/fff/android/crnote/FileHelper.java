/*
 * This class provides interfaces to assist in file handling
 */
package com.fff.android.crnote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.apache.commons.ssl.OpenSSL;

public class FileHelper {

	static String SALTED = "Salted_";

	public static String dirName(String path) {
		File f = new File(path);
		return f.getParent();
	}

	public static String baseName(String path) {
		File f = new File(path);
		return f.getName();
	}

	// get matching filenames, ie backups
	public static String[] getMatchingFiles(String path) {
		File f = new File(path);
		String fileName = f.getName();
		File dirF = new File(dirName(path));
		if (!dirF.exists())
			return null;

		return dirF.list(new BackupFilenameFilter(fileName));
	}

	public static int latestBackup(String path) {
		String[] files = getMatchingFiles(path);
		int maxi = 0;

		for (String s : files) {
			int i = s.lastIndexOf('.');
			if (i >= 0) {
				try {
					String ext = s.substring(i + 1);
					int n = Integer.parseInt(ext);
					if (n > maxi) {
						maxi = n;
					}
				} catch (Exception e) {
					Util.dLog("exception:", e.getMessage());
				}
			}
		}
		return maxi;
	}

	// rename the file to backup and also delete any excess backup files.
	// Backup files are timestamped to make it easier to figure out which to
	// delete.
	public static String renameAsBackup(String filename, int maxBackups) {
		String dirname = dirName(filename);
		String basename = baseName(filename);
		String[] f = getMatchingFiles(filename);
		if (f.length >= maxBackups) { // the list also includes the original
										// file + backups
			Arrays.sort(f);
			int todelete = f.length - maxBackups;
			for (int i = 0; todelete > 0 && i < f.length; i++) {
				if (!basename.equals(f[i])) {
					File fdel = new File(dirname + File.separator + f[i]);
					fdel.delete();
					todelete--;
				}
			}
		}

		SimpleDateFormat s = new SimpleDateFormat("yyMMddhhmmss");
		String format = s.format(new Date());
		String newfile = filename + "." + format;
		File file = new File(filename);
		file.renameTo(new File(newfile));

		return null;
	}

	public static int deleteBackups(String path) {
		String[] files = getMatchingFiles(path);
		int i = 0;
		for (String s : files) {
			File f = new File(s);
			if (f.exists() && f.isFile()) {
				f.delete();
				i++;
			}
		}
		return i;
	}

	private static class BackupFilenameFilter implements FilenameFilter {
		private String basefilename;

		BackupFilenameFilter(String basename) {
			basefilename = basename;
		}

		// @Overide
		public boolean accept(File dir, String filename) {
			if (filename.equals(basefilename))
				return true;

			String prefix = basefilename + ".";
			if (!filename.startsWith(prefix)) {
				return false;
			}

			try {
				String ext = filename.substring(prefix.length());
				@SuppressWarnings("unused")
				Long n = Long.parseLong(ext);
			} catch (Exception e) {
				return false;
			}
			return true;
		}
	}

	public static class FileStat {
		public byte[] sbuf;
		public boolean isCrNote;

		public String crNoteHeader;
	}
	
	public static FileStat doDecrypt(String file, String fpass) {
		if (fpass == "")
			return null;
		
		return doDecrypt(file, fpass.toCharArray());
	}

	public static FileStat doDecrypt(String file, char[] fpass) {
		if (fpass.length == 0)
			return null;

		FileStat fs = new FileStat();
		fs.isCrNote = false;
		fs.crNoteHeader = null;
		fs.sbuf = null;

		ByteArrayOutputStream sbuf = new ByteArrayOutputStream();

		try {
			FileInputStream instream = new FileInputStream(file);

			// Test if file has been double encrypted
			boolean doubleEnc = false;
			try {
				byte[] tbuf = new byte[16];
				InputStream d1stream;
				d1stream = OpenSSL.decrypt(CrNoteApp.cryptoalgo, Util.byteArrayToCharArray(CrNoteApp.webkey), instream);
				int nread = d1stream.read(tbuf);
				if (nread >= SALTED.length()) {
					String s = new String(tbuf, 0, SALTED.length());
					if (s.compareToIgnoreCase(SALTED) == 0) {
						// YES double encryption, close the file
						doubleEnc = true;
						Util.dLog("DoubleEnc", "File " + file + " is double encrypted");
					} else {
						Util.dLog("DoubleEnc", "File " + file + " is single encrypted");
					}
				}
				d1stream.close();
				instream.close();
			} catch (IOException e1) {
				Util.dLog("OpenSSL.decrypt", "webkey dec IOException: " + e1.getMessage());
			} catch (GeneralSecurityException e1) {
				Util.dLog("OpenSSL.decrypt", "webkey dec GeneralSecurityException: " + e1.getMessage());
			} catch (NullPointerException enull) {
				Util.dLog("OpenSSL.decrypt", "Null pointer exception" + enull.getMessage());
			}

			// Reopen the file for real decryption
			InputStream decstream;
			instream = new FileInputStream(file);
			InputStream finstream = instream;
			if (doubleEnc) {
				try {
					finstream = OpenSSL.decrypt(CrNoteApp.cryptoalgo, Util.byteArrayToCharArray(CrNoteApp.webkey), instream);
				} catch (IOException e1) {
					Util.dLog("OpenSSL.decrypt", "File IO issue " + e1.getMessage());
					return null;
				} catch (GeneralSecurityException e1) {
					Util.dLog("OpenSSL.decrypt", "General security exception" + e1.getMessage());
					return null;
				}
				Util.dLog("-> doubleEnc", "decrypted first layer");
			}

			try {
				decstream = OpenSSL.decrypt(CrNoteApp.cryptoalgo, fpass, finstream);
			} catch (IOException e1) {
				//e1.printStackTrace();
				Util.dLog("OpenSSL.decrypt", "File IO issue " + e1.getMessage());
				return null;
			} catch (GeneralSecurityException e1) {
				Util.dLog("OpenSSL.decrypt", "General security exception" + e1.getMessage());
				return null;
			} catch (NullPointerException enull) {
				Util.dLog("OpenSSL.decrypt", "Null pointer exception" + enull.getMessage());

				return null;
			}

			byte[] buffer = new byte[1024];
			int bytesread = 0;

			for (;;) {
				int n;

				try {
					n = decstream.read(buffer);
				} catch (IOException e) {
					//e.printStackTrace();
					return null;
				} catch (Exception e) {
					//e.printStackTrace();
					return null;
				}
				if (n == -1)
					break;

				if (bytesread == 0 && n > Util.CRNOTE_MAGIC_TOTALLEN) {
					// Compare whether this is a CrNote file or not only
					// at the beginning of the file read.
					int off = Util.getRealDataOffset(buffer);
					if (off > 0) {
						fs.crNoteHeader = new String(buffer, 0, Util.CRNOTE_MAGIC_TOTALLEN);
						fs.isCrNote = true;
					}
					sbuf.write(buffer, off, n - off);
					// Util.dLog("read", s);
				} else {
					sbuf.write(buffer, 0, n);
				}
				bytesread += n;
			}

			try {
				decstream.close();
			} catch (IOException e) {
				Util.dLog("decstream.close", e.toString());
			}
			try {
				instream.close();
			} catch (IOException e) {
				Util.dLog("instream.close", e.toString());
			}
		} catch (java.io.FileNotFoundException e) {
			// do something if the myfilename.txt does not exits
			Util.dLog("FileNotFound", e.toString());
			//e.printStackTrace();
		}

		fs.sbuf = sbuf.toByteArray();//sbuf.toString("utf-8");
		return fs;
	}

	public static FileStat doDecrypt(byte[] data, String fpass) {
		if (fpass == "")
			return null;
		return doDecrypt(data, fpass.toCharArray());
	}
	
	public static FileStat doDecrypt(byte[] data, char[] fpass) {
		if (fpass.length == 0)
			return null;

		FileStat fs = new FileStat();
		fs.isCrNote = false;
		fs.crNoteHeader = null;
		fs.sbuf = null;

		ByteArrayOutputStream sbuf = new ByteArrayOutputStream();

		ByteArrayInputStream instream = new ByteArrayInputStream(data);
		InputStream decstream;

		// Test if file has been double encrypted
		boolean doubleEnc = false;
		try {
			byte[] tbuf = new byte[16];
			InputStream d1stream;
			d1stream = OpenSSL.decrypt(CrNoteApp.cryptoalgo, Util.byteArrayToCharArray(CrNoteApp.webkey), instream);
			int nread = d1stream.read(tbuf);
			if (nread >= SALTED.length()) {
				String s = new String(tbuf, 0, SALTED.length());
				if (s.compareToIgnoreCase(SALTED) == 0) {
					// YES double encryption, close the file
					doubleEnc = true;
					Util.dLog("DoubleEnc", "Data buffer is double encrypted");
				} else {
					Util.dLog("DoubleEnc", "Data buffer is single encrypted");
				}
			}
			d1stream.close();

		} catch (IOException e1) {
			Util.dLog("OpenSSL.decrypt", "webkey dec IOException: " + e1.getMessage());
		} catch (GeneralSecurityException e1) {
			Util.dLog("OpenSSL.decrypt", "webkey dec GeneralSecurityException: " + e1.getMessage());
		} catch (NullPointerException enull) {
			Util.dLog("OpenSSL.decrypt", "Null pointer exception" + enull.getMessage());
		}

		try {
			instream.close();
		} catch (IOException e2) {
			Util.dLog("crnote-doDecrypt", "331: " + e2.getMessage());
			//e2.printStackTrace();
		}

		instream = new ByteArrayInputStream(data);

		try {
			InputStream finstream = instream;
			if (doubleEnc) {
				finstream = OpenSSL.decrypt(CrNoteApp.cryptoalgo, Util.byteArrayToCharArray(CrNoteApp.webkey), instream);
			}
			decstream = OpenSSL.decrypt(CrNoteApp.cryptoalgo, fpass, finstream);

			byte[] buffer = new byte[1024];
			int bytesread = 0;

			for (;;) {
				int n;

				try {
					n = decstream.read(buffer);
				} catch (IOException e) {
					Util.dLog("crnote-doDecrypt", "353: " + e.getMessage());
					//e.printStackTrace();
					return null;
				}
				if (n == -1)
					break;

				if (bytesread == 0 && n > Util.CRNOTE_MAGIC_TOTALLEN) {
					// Compare whether this is a CrNote file or not only
					// at the beginning of the file read.
					int off = Util.getRealDataOffset(buffer);
					if (off > 0) {
						fs.crNoteHeader = new String(buffer, 0, Util.CRNOTE_MAGIC_TOTALLEN);
						fs.isCrNote = true;
					}
					sbuf.write(buffer, off, n - off);
					// Util.dLog("read", s);
				} else {
					sbuf.write(buffer, 0, n);
				}
				bytesread += n;
			}

			try {
				decstream.close();
			} catch (IOException e) {
				Util.dLog("decstream.close", e.toString());
			}
			try {
				instream.close();
			} catch (IOException e) {
				Util.dLog("instream.close", e.toString());
			}
		} catch (IOException e1) {
			Util.dLog("OpenSSL.decrypt", "File IO <data> issue " + e1.getMessage());
			return null;
		} catch (GeneralSecurityException e1) {
			Util.dLog("OpenSSL.decrypt", "General security <data> exception " + e1.getMessage());
			return null;
		} catch (NullPointerException enull) {
			Util.dLog("OpenSSL.decrypt", "Null pointer exception" + enull.getMessage());
			return null;
		}
		
		fs.sbuf = sbuf.toByteArray();//sbuf.toString("utf-8");
		return fs;
	}

	public static boolean doDecryptToFile(String file, String fpass, String outputFile) {
		if (fpass == "") {
			CrNoteApp.appErrString = "No password provided";
			return false;
		}
		
		return doDecryptToFile(file, fpass.toCharArray(), outputFile);
	}
		
	public static boolean doDecryptToFile(String file, char[] fpass, String outputFile) {
		if (fpass.length == 0) {
			CrNoteApp.appErrString = "No password provided";
			return false;
		}
		
		File f_outputFile = new File(outputFile);
		FileOutputStream fout;
		try {
			fout = new FileOutputStream(outputFile);
		} catch (FileNotFoundException e) {
			Util.dLog("doDecryptToFile", "Cannot open output file: " + e.getMessage());
			CrNoteApp.appErrString = "Unable to open output file " + outputFile + ": " + e.getMessage();
			return false;
		}

		try {
			FileInputStream instream = new FileInputStream(file);

			// Test if file has been double encrypted
			boolean doubleEnc = false;
			try {
				byte[] tbuf = new byte[16];
				InputStream d1stream;
				d1stream = OpenSSL.decrypt(CrNoteApp.cryptoalgo, Util.byteArrayToCharArray(CrNoteApp.webkey), instream);
				int nread = d1stream.read(tbuf);
				if (nread >= SALTED.length()) {
					String s = new String(tbuf, 0, SALTED.length());
					if (s.compareToIgnoreCase(SALTED) == 0) {
						// YES double encryption, close the file
						doubleEnc = true;
						Util.dLog("DoubleEnc", "File " + file + " is double encrypted");
					} else {
						Util.dLog("DoubleEnc", "File " + file + " is single encrypted");
					}
				}
				d1stream.close();
				instream.close();
			} catch (IOException e1) {
				Util.dLog("OpenSSL.decrypt", "webkey dec IOException: " + e1.getMessage());
			} catch (GeneralSecurityException e1) {
				Util.dLog("OpenSSL.decrypt", "webkey dec GeneralSecurityException: " + e1.getMessage());
			} catch (NullPointerException enull) {
				Util.dLog("OpenSSL.decrypt", "Null pointer exception" + enull.getMessage());
			}

			// Reopen the file for real decryption
			InputStream decstream;
			instream = new FileInputStream(file);
			InputStream finstream = instream;
			if (doubleEnc) {
				try {
					finstream = OpenSSL.decrypt(CrNoteApp.cryptoalgo, Util.byteArrayToCharArray(CrNoteApp.webkey), instream);
				} catch (IOException e1) {
					Util.dLog("OpenSSL.decrypt", "File IO issue " + e1.getMessage());
					try {
						fout.close();
					} catch (IOException e) {
					}
					CrNoteApp.appErrString = "Decryption error: " + e1.getMessage();
					
					try { if (f_outputFile.exists()) f_outputFile.delete(); } catch (Exception fex) {}
					return false;
				} catch (GeneralSecurityException e1) {
					Util.dLog("OpenSSL.decrypt", "General security exception" + e1.getMessage());
					try {
						fout.close();
					} catch (IOException e) {
					}
					CrNoteApp.appErrString = "Decryption error: " + e1.getMessage();
					
					try { if (f_outputFile.exists()) f_outputFile.delete(); } catch (Exception fex) {}
					return false;
				}
				Util.dLog("-> doubleEnc", "decrypted first layer");
			}

			try {
				decstream = OpenSSL.decrypt(CrNoteApp.cryptoalgo, fpass, finstream);
				org.apache.commons.ssl.Util.pipeStream(decstream, fout, false);
				fout.flush();
			} catch (IOException e1) {
				try {
					fout.close();
				} catch (IOException e) {
					Util.dLog("fout.close", e.toString());
				}
				//e1.printStackTrace();
				Util.dLog("OpenSSL.decrypt", "File IO issue " + e1.getMessage());
				CrNoteApp.appErrString = "Decryption error: " + e1.getMessage();
				
				try { if (f_outputFile.exists()) f_outputFile.delete(); } catch (Exception fex) {}
				return false;
			} catch (GeneralSecurityException e1) {
				try {
					fout.close();
				} catch (IOException e) {
					Util.dLog("fout.close", e.toString());
				}
				Util.dLog("OpenSSL.decrypt", "General security exception" + e1.getMessage());
				CrNoteApp.appErrString = "Decryption error: " + e1.getMessage();
				
				try { if (f_outputFile.exists()) f_outputFile.delete(); } catch (Exception fex) {}
				return false;
			} catch (NullPointerException enull) {
				try {
					fout.close();
				} catch (IOException e) {
					Util.dLog("fout.close", e.toString());
				}
				Util.dLog("OpenSSL.decrypt", "Null pointer exception" + enull.getMessage());
				CrNoteApp.appErrString = "Decryption error: (is your file a valid format?) " + enull.getMessage();
				
				try { if (f_outputFile.exists()) f_outputFile.delete(); } catch (Exception fex) {}
				return false;
			}

			try {
				fout.close();
			} catch (IOException e) {
				Util.dLog("fout.close", e.toString());
			}

			try {
				decstream.close();
			} catch (IOException e) {
				Util.dLog("decstream.close", e.toString());
			}
			try {
				instream.close();
			} catch (IOException e) {
				Util.dLog("instream.close", e.toString());
			}
		} catch (java.io.FileNotFoundException e) {
			try { if (f_outputFile.exists()) f_outputFile.delete(); } catch (Exception fex) {}
			
			// do something if the myfilename.txt does not exits
			Util.dLog("FileNotFound", e.toString());
			//e.printStackTrace();
		}

		CrNoteApp.appErrString = null;
		return true;
	}

	public static boolean doEncryptToFile(String file, String fpass, String outputFile) {
		if (fpass == "") {
			CrNoteApp.appErrString = "No password provided";
			return false;
		}
		
		return doEncryptToFile(file, fpass.toCharArray(), outputFile);
	}
	
	public static boolean doEncryptToFile(String file, char[] fpass, String outputFile) {
		if (fpass.length == 0) {
			CrNoteApp.appErrString = "No password provided";
			return false;
		}

		File f_outputFile = new File(outputFile);
		
		FileOutputStream fout;
		try {
			fout = new FileOutputStream(outputFile);
		} catch (FileNotFoundException e) {
			Util.dLog("doEncryptToFile", "Cannot open output file: " + e.getMessage());
			CrNoteApp.appErrString = "Unable to open output file " + outputFile + ": " + e.getMessage();
			return false;
		}

		InputStream instream;
		InputStream encstream;
		InputStream finalstream;
		try {
			instream = new FileInputStream(file);
			encstream = OpenSSL.encrypt(CrNoteApp.cryptoalgo, fpass, instream, false);
			if (CrNoteApp.securestorage) {
				finalstream = OpenSSL.encrypt(CrNoteApp.cryptoalgo, Util.byteArrayToCharArray(CrNoteApp.webkey), encstream, false);
			} else {
				finalstream = encstream;
			}
			org.apache.commons.ssl.Util.pipeStream(finalstream, fout, false);
			fout.flush();
		} catch (IOException e) {
			try {
				fout.close();
			} catch (IOException ef) {
			}
			Util.dLog("doEncryptToFile", "OpenSSL IOException " + e.getMessage());
			CrNoteApp.appErrString = "Encryption error: " + e.getMessage();
			// e.printStackTrace();
			try { if (f_outputFile.exists()) f_outputFile.delete(); } catch (Exception fex) {}
			return false;
		} catch (GeneralSecurityException e) {
			try {
				fout.close();
			} catch (IOException ef) {
			}
			Util.dLog("doEncryptToFile", "OpenSSL SecurityException " + e.getMessage());
			CrNoteApp.appErrString = "Encryption error: " + e.getMessage();
			// e.printStackTrace();
			try { if (f_outputFile.exists()) f_outputFile.delete(); } catch (Exception fex) {}
			return false;
		}

		try {
			fout.close();
		} catch (IOException e) {
			Util.dLog("fout.close", e.toString());
		}

		try {
			if (encstream != null) {
				encstream.close();
			}
		} catch (IOException e) {
			Util.dLog("decstream.close", e.toString());
		}
		try {
			if (instream != null) {
				instream.close();
			}
		} catch (IOException e) {
			Util.dLog("instream.close", e.toString());
		}

		return true;
	}
	
	public static boolean writeToFile(String path, byte[] data) {
		File f_outputFile = new File(path);
		
		FileOutputStream fout;
		try {
			fout = new FileOutputStream(f_outputFile);
		} catch (FileNotFoundException e) {
			Util.dLog("writeToFile", "Cannot open output file: " + e.getMessage());
			CrNoteApp.appErrString = "Unable to access file " + path + ": " + e.getMessage();
			return false;
		}
		
		boolean err = false;
		try {
			fout.write(data);
		} catch (IOException e) {
			Util.dLog("writeToFile", "Cannot open output file: " + e.getMessage());
			CrNoteApp.appErrString = "Unable to access file " + path + ": " + e.getMessage();
			err = true;
		}
		
		try {
			fout.close();
		} catch (IOException e) {
		}
		
		return !err;
	}
}
