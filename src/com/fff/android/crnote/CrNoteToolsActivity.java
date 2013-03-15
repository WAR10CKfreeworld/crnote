package com.fff.android.crnote;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class CrNoteToolsActivity extends Activity {
	private final int REQUEST_OPEN_ENCFILE = 0;
	private final int REQUEST_OPEN_DECFILE = 1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.crnote_tools_layout);

		Button encButton = (Button) findViewById(R.id.bEncrypt);
		encButton.setOnClickListener(mEncListener);
		Button decButton = (Button) findViewById(R.id.bDecrypt);
		decButton.setOnClickListener(mDecListener);
		
		TextView tv = (TextView) findViewById(R.id.encdectext);
		tv.setText("Encrypt/Decrypt using your password. Secure Storage Mode is " +
			(CrNoteApp.securestorage ? "enabled." : "disabled."));
		
	}

	private OnClickListener mEncListener = new OnClickListener() {
		public void onClick(View v) {
			Intent intent = new Intent(getBaseContext(), FileDialog.class);
			intent.putExtra(FileDialog.LAUNCH_NO_NEW_FILE, "yes");
			intent.putExtra(FileDialog.START_PATH, CrNoteApp.folder);
			startActivityForResult(intent, REQUEST_OPEN_ENCFILE);
		}
	};

	private OnClickListener mDecListener = new OnClickListener() {
		public void onClick(View v) {
			Intent intent = new Intent(getBaseContext(), FileDialog.class);
			intent.putExtra(FileDialog.LAUNCH_NO_NEW_FILE, "yes");
			intent.putExtra(FileDialog.START_PATH, CrNoteApp.folder);
			startActivityForResult(intent, REQUEST_OPEN_DECFILE);
		}
	};

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
			doEncDecAction(filePath, requestCode);
		} else if (resultCode == Activity.RESULT_CANCELED) {
		}
	}
	
	
	private void doEncDecAction(final String filePath, final int action) {
		LayoutInflater factory = LayoutInflater.from(this);
		
        final View textEntryView = factory.inflate(R.layout.encdec_tool_prompt, null);
        
        final EditText input = (EditText) textEntryView.findViewById(R.id.password_edit);
		input.setInputType(input.getInputType()
				| InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
				| InputType.TYPE_TEXT_VARIATION_FILTER);
		input.setTransformationMethod(new PasswordTransformationMethod());
		
		final EditText filename = (EditText) textEntryView.findViewById(R.id.filename_edit);
		filename.setText(filePath + ".x");
		
		String title = "Encrypt";
		if (action != REQUEST_OPEN_ENCFILE) {
			title = "Decrypt";
		}
		
		File f = new File(filePath);
		
        AlertDialog alert = new AlertDialog.Builder(this)
            //.setIconAttribute(android.R.attr.alertDialogIcon)
            .setTitle(title + " " + f.getName())
            .setView(textEntryView)
            .setPositiveButton(title, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
    				String fpass = input.getText().toString();
    				String toFile = filename.getText().toString();
    				
    				File toF = new File(toFile);
    				if (toF.exists()) {
    					Toast.makeText(getApplicationContext(),
    						"File " + toFile + " already exists, please specify something different",
    						Toast.LENGTH_LONG).show();
    					return;
    				}

    				if (fpass != "") {
    					if (action == REQUEST_OPEN_ENCFILE) {
    						encryptFile(filePath, toFile, fpass);
    					} else if (action == REQUEST_OPEN_DECFILE) {
    						decryptFile(filePath, toFile, fpass);
    					}
    				} else {
    					Toast.makeText(getApplicationContext(),
        					"Password is empty?",
        					Toast.LENGTH_LONG).show();
    				}
                }
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
            })
            .create();

		alert.show();
	}
	
	private void encryptFile(String path, String toFile, String password)
	{
		String newFile = toFile;
		boolean res = FileHelper.doEncryptToFile(path, password, newFile);
		if (!res) {
			Toast.makeText(this, CrNoteApp.appErrString, Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(this, "Encrypted to output file " + newFile, Toast.LENGTH_LONG).show();
		}
	}
	
	private void decryptFile(String path, String toFile, String password)
	{
		String newFile = toFile;
		boolean res = FileHelper.doDecryptToFile(path, password, newFile);
		if (!res) {
			Toast.makeText(this, CrNoteApp.appErrString, Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(this, "Decrypted to output file " + newFile, Toast.LENGTH_LONG).show();
		}
	}
}
