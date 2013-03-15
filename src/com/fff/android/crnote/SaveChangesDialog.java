package com.fff.android.crnote;

import com.fff.android.crnote.PatternDialog.PatternDialogCallback;

import android.app.Dialog;
import android.content.Context;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SaveChangesDialog extends Dialog {
	private TextView changePwdInstructions;
	private Button ok_button;
	private Button cancel_button;
	private Button no_button;
	private CheckBox dobackup;
	private CheckBox dosync;
	
	private boolean newPasscodeRequired; // User must provide a new password/pattern before clicking the OK button (eg for new files)
	
	private String thePassword;
	private byte[] theKey;
	
	interface SaveDialogCallback {
	    void onSave(boolean doBackup, boolean doOnlineSync, String password, byte[] key);
	    void onDontSave();
	    void onSaveCancel();
	    void onErrorMessage(String s);
	}
	
	private SaveDialogCallback mCallback;
	
	public void setCallback(SaveDialogCallback cb) {
		mCallback = cb;
	}
	
	public SaveChangesDialog(Context context, int theme, boolean newPasscode) {
		super(context, theme);
		
		setContentView(R.layout.save_file_dialog);
        
        setTitle("Save File");
        setCancelable(true);
        
        newPasscodeRequired = newPasscode;
        
        changePwdInstructions = (TextView) findViewById(R.id.passwordInstructionsTextView);
        
        LinearLayout l = (LinearLayout) findViewById(R.id.layoutPasswordOption);
        l.setOnClickListener(changePasswordClicked);

        dobackup = (CheckBox) findViewById(R.id.makeBackupCheckBox);
        dobackup.setChecked(CrNoteApp.autobackup);
        dosync = (CheckBox) findViewById(R.id.onlineSyncCheckBox);
        
        ok_button = (Button) findViewById(R.id.buttonOK);
        ok_button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (newPasscodeRequired && thePassword == null && theKey == null) {
					// Error... user must provide a password and/or pattern
					if (mCallback != null) {
						mCallback.onErrorMessage("You must provide a password and/or pattern");
					}
				} else if (mCallback != null) {
					mCallback.onSave(dobackup.isChecked(), dosync.isChecked(), thePassword, theKey);
				}
			}
		});
        
        cancel_button = (Button) findViewById(R.id.buttonCancel);
        cancel_button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mCallback != null) {
					mCallback.onSaveCancel();
				}
			}
		});
        
        no_button = (Button) findViewById(R.id.buttonNO);
        no_button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mCallback != null) {
					mCallback.onDontSave();
				}
			}
		});
        
        if (newPasscode) {
        	disable_okButton();
        }
	}

	private View.OnClickListener changePasswordClicked = new View.OnClickListener() {         
        @Override
        public void onClick(View v) {
        	// Launch pattern dialog to obtain password
        	
    		final PatternDialog ptd = new PatternDialog(SaveChangesDialog.this.getContext(), R.style.crDialogStyle,
    				PatternDialog.MODE_NEW, CrNoteApp.patternSeed);

    		ptd.setTitle("Enter file password/pattern");
    		PatternDialog.PatternDialogCallback ptc = new PatternDialog.PatternDialogCallback() {
    			@Override
    			public void cancelled() {
    				theKey = null;
    				thePassword = null;
    			}

    			@Override
    			public void keyObtained(byte[] key) {
    				theKey = key;
    				changePwdInstructions.setText("New passcode provided. Touch here to change.");
    				enable_okButton();
    				ptd.dismiss();
    			}

    			@Override
    			public void passwordObtained(String password) {
    				thePassword = password;
    				changePwdInstructions.setText("New password provided. Touch here to change.");
    				enable_okButton();
    				ptd.dismiss();
    			}
    		};
    		
    		theKey = null;
    		thePassword = null;
    		ptd.setCallback(ptc);
    		ptd.show();
        }
    };
	
    public void disable_okButton() {
    	ok_button.setEnabled(false);
    }
    
    public void enable_okButton() {
    	ok_button.setEnabled(true);
    }

	public Button get_okButton() {
		return ok_button;
	}
	
	public Button get_cancelButton() {
		return cancel_button;
	}
	
	public Button get_noButton() {
		return no_button;
	}

	public void setPasswordTitle(String s) {
		TextView tv = (TextView) findViewById(R.id.textSetPassword);
		tv.setText(s);
	}
	
	public void setDontSaveButtonVisibility(int option) {
		no_button.setVisibility(option);
	}
	
	public void setBackupVisibility(int option) {
		LinearLayout l = (LinearLayout) findViewById(R.id.layoutMakeBackup);
		l.setVisibility(option);
	}
	
	public void setBackupMessage(String msg) {
		TextView tv = (TextView) findViewById(R.id.makeBackupText);
		tv.setText(msg);
	}
	
	public void setCancelButtonText(String txt) {
		cancel_button.setText(txt);
	}
	
	public void setNewInstructions(String s) {
		changePwdInstructions.setText(s);
	}
	
	public boolean doBackupChecked() {
		return dobackup.isChecked();
	}
	
	public boolean doOnlineSyncChecked() {
		return dosync.isChecked();
	}
	

	public void hideOnlineViews() {
		// hide online sync option
		LinearLayout l = (LinearLayout) findViewById(R.id.syncLayout);
		l.setVisibility(View.GONE);
	}
}

