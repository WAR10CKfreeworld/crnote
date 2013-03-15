package com.fff.android.crnote;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;

import org.apache.commons.ssl.OpenSSL;

import com.fff.android.crnote.IdleThread.IdleTimeoutEvent;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class PlaintextView extends Activity implements SaveChangesDialog.SaveDialogCallback {
	EditText tv;
	TextView fn;
	
	private Button mGoButton;

	public static final String FILE_NAME = "FILE_NAME";
	String filename = "";
	public static final String FILE_PASSWD = "FILE_PASSWD";
	String password = "";
	public static final String FILE_KEY = "FILE_KEY";
	byte [] fkey = null;
	public static final String REMOTE_FILENAME = "REMOTE_FILENAME";
	String remote_filename = null;
	
	
	private final int LOCK_SCREEN = 1;
	
	
	String textBuf;
	boolean contentsChanged = false;
	boolean isNewFile;
	boolean fileBackupAlready = false; // old file has already been backed up within this session
	boolean searchVisible = false;
	int searchPos = 0;
	String searchText = null;
	
	IdleThread idleThread;
	private Handler idleMessageHandler;	

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.textview_window);
		
		mGoButton = (Button) findViewById(R.id.search_go_btn);
		mGoButton.setOnClickListener(mGoButtonClickListener);
			// mGoButton.setOnKeyListener(mButtonsKeyListener);
		Drawable iconLabel = getBaseContext().getResources().getDrawable(/*android.*/R.drawable.ic_btn_search);
		mGoButton.setCompoundDrawablesWithIntrinsicBounds(iconLabel, null, null, null);
		
		
		tv = (EditText) findViewById(R.id.tv_textView1);
		fn = (TextView) findViewById(R.id.tv_filename);

		filename = getIntent().getStringExtra(FILE_NAME);
		if (filename != null) {
			fn.setText(filename);
		}
		password = getIntent().getStringExtra(FILE_PASSWD);
		if (password == null) {
			password = "";
		}
		fkey = getIntent().getByteArrayExtra(FILE_KEY);
		
		remote_filename = getIntent().getStringExtra(REMOTE_FILENAME);

		
		CrNoteApp crApp = (CrNoteApp) getApplication();
		if (crApp.getTextbuf() != null) {
			try {
				textBuf = new String(crApp.getTextbuf(), "utf-8");
			} catch (UnsupportedEncodingException e) {
				Util.dLog("Conversion", "Byte array to UTF8 string error " + e.getLocalizedMessage() + ". Resorting to default String(byte[]) constructor");
				textBuf = new String(crApp.getTextbuf());
			}
		}

		if (textBuf != null) {
			tv.setText(textBuf);
		}
		isNewFile = (textBuf == null);
		
		StateChangeCache lastSettings = (StateChangeCache) getLastNonConfigurationInstance();
		if (lastSettings != null) {
			contentsChanged = lastSettings.changed;
			fileBackupAlready = lastSettings.fileBackupAlready;
		}
		
		View v = (View) findViewById(R.id.textview_searchbar);
		v.setVisibility(View.GONE);
		
		// Monitor text changes...
		tv.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				contentsChanged = true;
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				contentsChanged = true;
			}
		});
		
		idleMessageHandler = new Handler() {
			  @Override
			  public void handleMessage(Message msg) {  
				  lockScreen();
			  }
		  };
		
		idleThread = new IdleThread();
		idleThread.touch();
		idleThread.setOnIdleTimeout(new IdleTimeoutEvent() {
			public void onIdleTimeoutEvent() {
				idleMessageHandler.sendMessage(Message.obtain(idleMessageHandler, 0));
			}
		});
		idleThread.start();
	}
	
	private void lockScreen() {
		lockScreen(true);
	}
	
	private void lockScreen(boolean pauseIdle) {
		if (pauseIdle) {
			idleThread.pause();
		}
		
		// Don't lock screen if password has not been set
		if (password != null && password != "") {
			Intent intent = new Intent(this, LockScreenActivity.class);
			intent.putExtra(LockScreenActivity.FILE_NAME, this.filename);
			intent.putExtra(LockScreenActivity.FILE_PASSWD, this.password);	
			intent.putExtra(LockScreenActivity.FILE_KEY, this.fkey);
			intent.putExtra(LockScreenActivity.FILE_CHANGED, this.contentsChanged);
			startActivityForResult(intent, LOCK_SCREEN);
		}
	}
	
	// Search button clicked
	private OnClickListener mGoButtonClickListener = new OnClickListener() {
		public void onClick(View v) {
			// grab string from search field
			EditText einput = (EditText) findViewById(R.id.search_src_text);
			String searchFor = einput.getText().toString().toLowerCase();
			if (searchText != null && !searchText.equals(searchFor)) {
				// searching for a new text field
				searchText = searchFor;
				searchPos = 0;
			}
			
			EditText editor = (EditText) findViewById(R.id.tv_textView1);
			String inEditor = editor.getText().toString().toLowerCase(); // this is really expensive to memory every time!!!

			int n = inEditor.indexOf(searchFor, searchPos);
			if (n >= 0) {
				searchPos = n+searchFor.length();
				editor.requestFocus();
				editor.setSelection(n, searchPos);
			} else if (searchPos != 0){ // wrap around search
				searchPos = 0;
				n = inEditor.indexOf(searchFor, searchPos);
				if (n >= 0) {
					searchPos = n+searchFor.length();
					editor.requestFocus();
					editor.setSelection(n, searchPos);
				}
			}
		}
	};
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == LOCK_SCREEN) {
			if (resultCode == LockScreenActivity.RESULT_CLOSE_FILE) {
				idleThread.terminate();
				this.finish();
			} else {
				idleThread.unpause();
			}
		}
	}
	
	@Override
	public void onUserInteraction() {
		//Util.dLog("PlaintextView", "onUserInteraction");
		idleThread.touch();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.plaintext_menu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.itemSave:
			promptSave(false);				
			return true;
		case R.id.itemSearch:
			if (!searchVisible) {
				item.setTitle("Hide Search");
				View v = (View) findViewById(R.id.textview_searchbar);
				v.setVisibility(View.VISIBLE);
				searchVisible = true;
				v = (View) findViewById(R.id.search_src_text);
				v.requestFocus();
				searchPos = 0;
			} else {
				item.setTitle("Search");
				EditText einput = (EditText) findViewById(R.id.search_src_text);
				einput.setText("");
				//EditText editor = (EditText) findViewById(R.id.tv_textView1);
				//editor.setSelection(0);
				View v = (View) findViewById(R.id.textview_searchbar);
				v.setVisibility(View.GONE);
				searchVisible = false;
			}
			return true;
		case R.id.itemSettings:
			startActivity(new Intent(getApplication(), CRNotePreferences.class));
			return true;
		case R.id.itemInsert:
			RandomGeneratorDialog rd = new RandomGeneratorDialog(this, R.style.crDialogStyle);
			rd.setOnRandomGenerated(new RandomGeneratorDialog.OnRandomGenerated() {
				@Override
				public void onRandomGenerated(String s) {
					TextEditor editor = (TextEditor) findViewById(R.id.tv_textView1);
					editor.insertTextAtCurosr(s);
					contentsChanged = true;
				}
			});
			rd.show();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	public Object onRetainNonConfigurationInstance() {
		if (idleThread != null) {
			idleThread.terminate();
		}
		return new StateChangeCache(contentsChanged, fileBackupAlready);
	}
	
	private class StateChangeCache {
		public boolean changed;
		public boolean fileBackupAlready;

		StateChangeCache(boolean c, boolean isbackedup) {
			changed = c;
			fileBackupAlready = isbackedup;
		}
	}
	
		
	private boolean saveFile(char[] passcode) {
		boolean res = saveFile(passcode, filename);
		if (res) {
			CrNoteApp.addToFileHistory(filename);
			CrNoteApp.savePrefs();
		}
		return res;
	}
	
	private boolean saveFile(char[] passcode, String toFile) {
		//Util.dLog("saveFile", "called");
		
		ByteArrayInputStream bis = new ByteArrayInputStream (tv.getText().toString().getBytes());
		
		try {
			InputStream cipherStream = OpenSSL.encrypt("AES256", passcode, bis, false);
			InputStream finalIStream = cipherStream;
			if (CrNoteApp.securestorage) {
				finalIStream = OpenSSL.encrypt("AES256", Util.byteArrayToCharArray(CrNoteApp.webkey), cipherStream, false);
			}
			File f = new File(toFile);
			FileOutputStream fout = new FileOutputStream(f);
			org.apache.commons.ssl.Util.pipeStream(finalIStream, fout, false);
			fout.flush();
			fout.close();
		} catch (IOException e) {
			Util.dLog("PlainText-SaveFile", "OpenSSL IOException: " + e.getMessage());
			//e.printStackTrace();
			return false;
		} catch (GeneralSecurityException e) {
			Util.dLog("PlainText-SaveFile", "OpenSSL SecurityException: " + e.getMessage());
			//e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	
	
	private SaveChangesDialog sdialog;
	private boolean sdialog_dismissOnly = true;

	private void promptSave(final boolean withQuit) {
		boolean nopassword = isNewFile || (password.length() == 0 && fkey == null);
		//Util.dLog("promptSave", "contents of file changed");
		sdialog = new SaveChangesDialog(this, R.style.crDialogStyle, nopassword);
		sdialog.setCallback((SaveChangesDialog.SaveDialogCallback) this);

		if (nopassword) {
			sdialog.setBackupVisibility(View.GONE);
			sdialog.setNewInstructions("No password set yet, please set it now");
		}
		
		if (remote_filename != null) {
			sdialog.hideOnlineViews();
			sdialog.setBackupMessage("Make local backup");
		}
		
		// user doesn't want to save, and wants to return to previous activity
		sdialog.setDontSaveButtonVisibility(withQuit ? View.VISIBLE : View.GONE);
		
		// return to the editing view
		sdialog.setCancelButtonText(withQuit ? "Continue Editing" : "Cancel");
		
		// The dialog was dismissed from the callbacks below. Dismiss PlainTextView if nothing else to do
		// as the save option has completed.
		sdialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				dialog.dismiss();
				if (!sdialog_dismissOnly && !contentsChanged && withQuit) {
					idleThread.terminate();
					setResult(RESULT_OK, getIntent());
					CrNoteApp crApp = (CrNoteApp) getApplication();
					crApp.setTextbuf(null);
					PlaintextView.this.finish();
				}
			}
		});

		sdialog.show();
	}
	
	
	// Save dialog onSave callback
	public void onSave(boolean doBackup, boolean doOnlineSync, String cbpassword, byte[] cbkey)
	{
		String localcopy = "";
		boolean success = true;

		// Did user provide a new password or key from save dialog.
		if (cbpassword != null || cbkey != null) {
			password = cbpassword;
			fkey = cbkey;
		}
		
		if (remote_filename == null) {
			// backup
			if (!isNewFile && sdialog.doBackupChecked() && !fileBackupAlready) {
				FileHelper.renameAsBackup(filename, CrNoteApp.backup_count);
				fileBackupAlready = true;
			}

			// save
			if (password != null && password.length() > 0) {
				success = saveFile(password.toCharArray());
			} else if (fkey != null) {
				success = saveFile(Util.byteArrayToCharArray(fkey));
			} else {
				// XXX Shouldn't hit this condition
				Toast.makeText(this, "Major error: no key or password? Cannot save.", Toast.LENGTH_LONG);
				success = false;
			}
			
			if (success) {
				isNewFile = false;
				contentsChanged = false;
			}
		} else {
			// Save a copy locally. The filename provided to use as a param has CRNOTE:// as prefix.
			// TODO - use local path settings.
			localcopy = CrNoteApp.onlinefolder + File.separator + filename.substring("CRNOTE://".length());
			//Util.dLog("Save", "Saving to new file " + localcopy);
			if (sdialog.doBackupChecked()) {
				if (!fileBackupAlready) {
					FileHelper.renameAsBackup(localcopy, CrNoteApp.backup_count);
					fileBackupAlready = true;
				}
				if (password != null && password.length() > 0) {
					saveFile(password.toCharArray(), localcopy);
				} else {
					saveFile(Util.byteArrayToCharArray(fkey), localcopy);
				}
				CrNoteApp.addToFileHistory(localcopy);
				CrNoteApp.savePrefs();
			}
			isNewFile = false;
			contentsChanged = false;
			
			// save local cache
			localcopy = CrNoteApp.onlinefolder + File.separator + filename.substring("CRNOTE://".length());
			if (password != null && password.length() > 0) {
				saveFile(password.toCharArray(), localcopy);
			} else {
				saveFile(Util.byteArrayToCharArray(fkey), localcopy);
			}
		}

		if (sdialog.doOnlineSyncChecked() || remote_filename != null) {
			doOnlineSync(localcopy);
		} else {
			sdialog_dismissOnly = false;
			sdialog.dismiss();
		}
	}
	
    public void onDontSave()
    {
    	sdialog_dismissOnly = false; // terminate both dialog and activity when dialog is dismissed
    	contentsChanged = false;
    	if (sdialog != null) {
    		sdialog.dismiss();
    	}
    }
    
    public void onSaveCancel()
    {
    	sdialog_dismissOnly = true;
    	if (sdialog != null) {
    		sdialog.dismiss();
    	}
    }
    
    public void onErrorMessage(String s)
    {
    	Util.dLog("ONERROR", s);
    	Toast.makeText(this, s, Toast.LENGTH_LONG);
    }
    
	
	private void doOnlineSync(final String localcopy) {
		final CrNoteApp crApp = (CrNoteApp) getApplication();

		RemoteTask rt = new RemoteTask();
		rt.setProgressMessage("Syncing file", "Uploading your file to CrNote server.");
		
		if (remote_filename == null) {
			// file was opened locally on device
			rt.setOnRemoteTaskEvent(new RemoteTaskEvent() {
				@Override
				public boolean doRemoteTask(String local, String remote) {
					OnlineFileListCache oflc = (OnlineFileListCache) crApp.getTempSettingObject("onlinefiles");
					char[] cwkey = Util.byteArrayToCharArray(CrNoteApp.webkey);
					CrWebAPI crwapi = new CrWebAPI(crApp);
					
					String onlyfn = (new File(local)).getName(); // = `$ basename fullfilepath`

					if (oflc == null) {
						// Obtain list of online files
						HashMap<String, String> eflist = crwapi.getFiles();
						if (eflist != null) {
							oflc = new OnlineFileListCache(eflist, cwkey);
						} else {
							oflc = new OnlineFileListCache();
						}
						crApp.setTempSettingObject("onlinefiles", oflc);
					}

					String efn = Util.aesEncToB64(cwkey, onlyfn.getBytes());
					//Util.dLog("EncryptedFN", efn);
					String existing_efn = oflc.addFilenamePair(onlyfn, efn);
					if (existing_efn != null) {
						efn = existing_efn;
					}

					return crwapi.sendFile(local, efn);
				}

				@Override
				public void onRemoteTaskComplete(boolean resultCode) {
					sdialog_dismissOnly = false;
					sdialog.dismiss();
				}
				
			});
			rt.execute(filename, null);
			
		} else {
			// File was opened from server
			rt.setOnRemoteTaskEvent(new RemoteTaskEvent() {
				@Override
				public boolean doRemoteTask(String local, String remote) {
					CrWebAPI crwapi = new CrWebAPI(crApp);
					return crwapi.sendFile(local, remote);
				}

				@Override
				public void onRemoteTaskComplete(boolean resultCode) {	
					isNewFile = false;
					contentsChanged = false;
					sdialog_dismissOnly = false;
					sdialog.dismiss();
				}
			});
			rt.execute(localcopy, remote_filename);
		}
	}
	
	private class RemoteTask extends AsyncTask<String, Void, Boolean> {
		private ProgressDialog pd;
		RemoteTaskEvent rtevent;
		String progressTitle;
		String progressMessage;
		
		public void setOnRemoteTaskEvent(RemoteTaskEvent rte) {
			rtevent = rte;
		}
		
		public void setProgressMessage(String title, String message) {
			progressTitle = title;
			progressMessage = message;
		}

		@Override
		protected void onPreExecute() {
			pd = ProgressDialog.show(PlaintextView.this,
					progressTitle, progressMessage, true, false);
		}

		protected Boolean doInBackground(String... params) {
			boolean result = rtevent.doRemoteTask(params[0], params[1]);
			return result;
		}
		
		protected void onPostExecute(Boolean result) {
			try {
				pd.dismiss();
			} catch (Exception e) {
				// view could be dead... due to orientation change; this is a pain.
			}
			rtevent.onRemoteTaskComplete(result);
		}
	}
	
	
	private interface RemoteTaskEvent
	{
		public boolean doRemoteTask(String local, String remote);
		public void onRemoteTaskComplete (boolean resultCode);
	}
	
	
	// Handle back button press
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		idleThread.touch();
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			if (contentsChanged) {
				promptSave(true);
			} else {
				CrNoteApp crApp = (CrNoteApp) getApplication();
				crApp.setTextbuf(null);
				idleThread.terminate();
				super.onKeyDown(keyCode, event);
			}
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}
}
