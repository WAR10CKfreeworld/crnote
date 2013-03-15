package com.fff.android.crnote;

import java.util.Arrays;
import java.util.List;

import com.fff.android.crnote.LockPatternView.Cell;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class LockScreenActivity extends Activity implements LockPatternView.OnPatternListener {
	public static final String FILE_NAME = "FILE_NAME";
	String filename = "";
	public static final String FILE_PASSWD = "FILE_PASSWD";
	String password = "";
	public static final String FILE_KEY = "FILE_KEY";
	byte[] file_key = null;
	
	public static final String FILE_CHANGED = "FILE_CHANGED";
	boolean file_changed = false;
	
	
	public static int RESULT_CLOSE_FILE = Activity.RESULT_FIRST_USER;
	
	byte[] key_input = null;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.lockscreenlayout);
		
		file_key = getIntent().getByteArrayExtra(FILE_KEY);
		
		filename = getIntent().getStringExtra(FILE_NAME);
		password = getIntent().getStringExtra(FILE_PASSWD);
		
		if (password == null) {
			password = "";
		}
		
		file_changed = getIntent().getBooleanExtra(FILE_CHANGED, false);

		TextView tv = (TextView) findViewById(R.id.tvLockMsg);
		tv.setText("Screen locked. Enter password/pattern for file " + filename);

		EditText etx = (EditText) findViewById(R.id.etPassword);
		etx.setText("");

		Button okButton = (Button) findViewById(R.id.okButton);
		okButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (keyValidator()) {
					setResult(RESULT_OK, getIntent());
					finish();
				}
			}
		});
		
		Button closeButton = (Button) findViewById(R.id.bCloseFile);
		closeButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (file_changed) {
					alertLockOrClose();
				} else {
					setResult(RESULT_CANCELED, getIntent());
					finish();
				}
			}
		});
		
		LockPatternView mpv = (LockPatternView) findViewById(R.id.lockPattern);
		mpv.setOnPatternListener(this);
	}
	
	private void alertLockOrClose() {
		// Show a confirmation dialog
	    Builder confirmationDialogBuilder = new AlertDialog.Builder(this);
	    confirmationDialogBuilder.setMessage("File contains changes. Are you sure you want to close it?");
	    confirmationDialogBuilder.setCancelable(false);
	    confirmationDialogBuilder.setNegativeButton("No", null);
	    confirmationDialogBuilder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	    	@Override
		    public void onClick(DialogInterface dialog, int which) {
	    		// Close the file and activity.
	    		LockScreenActivity.this.finishUp(RESULT_CLOSE_FILE);
	    	}
	    });
	    confirmationDialogBuilder.create().show();
	}
	
	private void finishUp(int result) {
		setResult(result, getIntent());
		this.finish();
	}
	
	private boolean keyValidator() {
		EditText et = (EditText) findViewById(R.id.etPassword);
		String inputText = et.getText().toString();
		
		if (this.file_key == null) {
			// Password based authentication
			if (key_input != null || !inputText.equals(this.password)) {
				Toast.makeText(LockScreenActivity.this, "Invalid input", Toast.LENGTH_SHORT).show();
				return false;
			}
		} else if (key_input == null) {
			Toast.makeText(LockScreenActivity.this, "Invalid input", Toast.LENGTH_SHORT).show();
			return false;
		} else {
			// Combine password + key together and test against the file_key
			byte[] keygen = PatternDialog.passcodeGenerator(inputText, key_input);
			if (!Arrays.equals(keygen, this.file_key)) {
				Toast.makeText(LockScreenActivity.this, "Invalid input", Toast.LENGTH_SHORT).show();
				return false;
			}
		}
		return true;
	}

	// Handle back button press
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			if (keyValidator()) {
				setResult(RESULT_OK, getIntent());
				return super.onKeyDown(keyCode, event);
			}
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}
	
	
	/*
	 * LockPatternView.OnPatternListener methods
	 */
	@Override
	public void onPatternStart() {
	}

	@Override
	public void onPatternCleared() {
		this.key_input = null;
	}

	@Override
	public void onPatternCellAdded(List<Cell> pattern) {
	}

	@Override
	public void onPatternDetected(List<Cell> pattern) {
		/*
		 * Screen is locked. The key is stored in memory within the app settings.
		 */
		byte[] key = LockPatternUtils.patternToKey(pattern, CrNoteApp.patternSeed);
		
		this.key_input = key;
	}
}
