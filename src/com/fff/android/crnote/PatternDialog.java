package com.fff.android.crnote;

import java.util.Arrays;
import java.util.List;

import com.fff.android.crnote.LockPatternView.Cell;

import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.app.Dialog;
import android.content.Context;
import android.provider.Settings;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;


public class PatternDialog extends Dialog implements LockPatternView.OnPatternListener {

	
	// dialog creation modes in relation to what password prompt to show
	public final static int MODE_NEW = 0;    // new password is requested
	public final static int MODE_PROMPT = 1; // prompt for existing password
	
	public final static int ENC_PASSWORD = 0;
	public final static int ENC_PASSWORD_AND_PATTERN = 1;
	public final static int ENC_PATTERN = 2;
	
	interface PatternDialogCallback {
	    void cancelled();
	    void keyObtained(byte[] key);
	    void passwordObtained(String password);
	}

	PatternDialogCallback mCallback;
	
	LockPatternView lview;
	
	private int mode = MODE_NEW;
	private byte[] theKey = null;
	private byte[] seed = null;
	
	private byte[] patternInput1 = null;
	private byte[] patternInput2 = null;
	String passwordInput = null;
	
	private int inputPhase = 0;

	public PatternDialog(Context context, int theme, int mode, byte[] seed) {
		super(context, theme);
		setContentView(R.layout.password_dialog);
		getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
		
		this.mode = mode;
		this.seed = seed;

		Button bReset = (Button) findViewById(R.id.bReset);
		bReset.setOnClickListener(mResetOnClick);
		
		Button bNext = (Button) findViewById(R.id.bNext);
		bNext.setOnClickListener(mNextOnClick);
		if (mode == MODE_PROMPT) {
			bNext.setText("OK");
		}
		
		Spinner sopts = (Spinner) findViewById(R.id.spinnerOptions);
	    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
	            context, R.array.encrypt_options_array, android.R.layout.simple_spinner_item);
	    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    sopts.setAdapter(adapter);

		sopts.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View view, int pos, long id) {
				if (pos == ENC_PASSWORD) {
					// hide pattern
					togglePasswordView(true);
					togglePatternView(false);
					setPrompt("Enter password");
				} else if (pos == ENC_PATTERN) {
					togglePasswordView(false);
					togglePatternView(true);
					setPrompt("Draw your pattern below");
				} else {
					togglePasswordView(true);
					togglePatternView(true);
					setPrompt("Enter password and draw pattern");
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
		sopts.setSelection(CrNoteApp.defaultEncMode);
		
		lview = (LockPatternView) findViewById(R.id.lockPattern);
		lview.setOnPatternListener(this);
	}
	
	private void togglePasswordView(boolean enable) {
		View v = findViewById(R.id.etPassword);
		v.setEnabled(enable);
	}
	
	private void togglePatternView(boolean enable) {
		View v = findViewById(R.id.lockPattern);
		v.setVisibility(enable ? View.VISIBLE : View.GONE);
	}

	public void setCallback(PatternDialogCallback cb) {
		mCallback = cb;
	}
	
	public int getEncryptionSelection() {
		Spinner sopts = (Spinner) findViewById(R.id.spinnerOptions);
		return sopts.getSelectedItemPosition();
	}
	
	private void setPrompt(String s) {
		TextView prompt = (TextView) findViewById(R.id.tPrompt);
		prompt.setText(s);
	}
	
	private View.OnClickListener  mResetOnClick = new View.OnClickListener() {
		public void onClick(View v) {
			if (mode == MODE_NEW) {
				Button bNext = (Button) findViewById(R.id.bNext);
				bNext.setText("Next");
			}
			EditText et = (EditText) findViewById(R.id.etPassword);
			et.setText("");
			
			lview.clearPattern();
			
			patternInput1 = null;
			patternInput2 = null;
			passwordInput = null;
			inputPhase = 0;
			
			switch (getEncryptionSelection()) {
			case ENC_PASSWORD:
				setPrompt("Enter password");
				break;
			case ENC_PATTERN:
				setPrompt("Draw your pattern below");
				break;
			case ENC_PASSWORD_AND_PATTERN:
				setPrompt("Enter password and draw pattern");
			}
		}
	};
	
	private View.OnClickListener  mNextOnClick = new View.OnClickListener() {
		public void onClick(View v) {
			EditText et = (EditText) findViewById(R.id.etPassword);
			int encmode = getEncryptionSelection();
			
			if (inputPhase == 0) {
				// First phase of input. Ensure pattern and password are provided
				// per spinner
				boolean passwordMissing = false;
				passwordInput = et.getText().toString();
				if (encmode != ENC_PATTERN && passwordInput.length() == 0) {
					passwordMissing = true;
				}
								
				boolean patternMissing = false;
				if (encmode != ENC_PASSWORD && patternInput1 == null) {
					patternMissing = true;
				}
				
				if (passwordMissing && patternMissing) {
					setPrompt("Password and pattern missing, please input");
				} else if (passwordMissing) {
					setPrompt("Password missing - please input");
				} else if (patternMissing) {
					setPrompt("Pattern missing - please draw");
				} else {
					// All input is provided... move on to next step
					if (mode == MODE_NEW) {
						et.setText("");
						lview.clearPattern();
						inputPhase++;
						setPrompt("Please repeat to confirm");
					} else {
						// Calculate the key and call the callback
						if (encmode != ENC_PASSWORD) {
							byte[] passwordBytes = passwordInput.getBytes();
							int l = patternInput1.length + passwordBytes.length;
							theKey = new byte[l];
							System.arraycopy(passwordBytes, 0, theKey, 0, passwordBytes.length);
							System.arraycopy(patternInput1, 0, theKey, passwordBytes.length, patternInput1.length);

							if (mCallback != null) {
								mCallback.keyObtained(theKey);
							}
						} else {
							if (mCallback != null) {
								mCallback.passwordObtained(passwordInput);
							}
						}
					}
				}
			} else {
				// step 2, verify entered password and/or pattern
				boolean passwordMissing = false;
				boolean passwordErr = false;
				String pText = et.getText().toString();
				if (encmode != ENC_PATTERN) {
					if (pText.length() == 0) {
						passwordMissing = true;
					} else {
						passwordErr = !pText.equals(passwordInput);
					}
				}
				
				boolean patternMissing = false;
				boolean patternErr = false;
				if (encmode != ENC_PASSWORD) {
					if (patternInput1 == null || patternInput2 == null) {
						patternMissing = true;
					} else {
						patternErr = !Arrays.equals(patternInput1, patternInput2);
					}
				}
			
				if (passwordErr || patternErr) {
					et.setText("");
					lview.clearPattern();	
				}
				
				if (passwordErr && patternErr) {
					setPrompt("Password and pattern mismatch, please retry");
				} else if (passwordErr) {
					setPrompt("Password mismatch, please reenter");
				} else if (patternErr) {
					setPrompt("Pattern mistmatch, please retry");
				} else if (passwordMissing && patternMissing) {
					setPrompt("Password and pattern missing, please input");
				} else if (passwordMissing) {
					setPrompt("Password missing - please input");
				} else if (patternMissing) {
					setPrompt("Pattern missing - please draw");
				} else {
					// All input is provided... process is complete.

					// Calculate the key and call the callback
					if (encmode != ENC_PASSWORD) {
						theKey = passcodeGenerator(passwordInput, patternInput1);

						if (mCallback != null) {
							mCallback.keyObtained(theKey);
						}
					} else {
						if (mCallback != null) {
							mCallback.passwordObtained(passwordInput);
						}
					}
				}
			}
		}

	};
	
	public static byte[] passcodeGenerator(String password, byte[] key) {
		if (password != null && password.length() > 0 || key != null) {
			byte[] passwordBytes = password.getBytes();
			int l = key.length + passwordBytes.length;
			byte[] outKey = new byte[l];
			System.arraycopy(passwordBytes, 0, outKey, 0, passwordBytes.length);
			System.arraycopy(key, 0, outKey, passwordBytes.length, key.length);
			return outKey;
		} else {
			return key;
		}
	}
	
	private View.OnClickListener  mCancelClick = new View.OnClickListener() {
		public void onClick(View v) {
			// Close dialog
			if (mCallback != null) {
				mCallback.cancelled();
			}
		}
	};
	
	
	/* LockPatternView callbacks */
	
	@Override
	public void onPatternStart() {
	}

	@Override
	public void onPatternCleared() {
		if (inputPhase == 0) {
			patternInput1 = null;
		} else {
			patternInput2 = null;
		}
	}

	@Override
	public void onPatternCellAdded(List<Cell> pattern) {
	}

	@Override
	public void onPatternDetected(List<Cell> pattern) {
		byte[] key = LockPatternUtils.patternToKey(pattern, this.seed);
		if (inputPhase == 0) {
			patternInput1 = key;
		} else {
			patternInput2 = key;
		}
	}

}
