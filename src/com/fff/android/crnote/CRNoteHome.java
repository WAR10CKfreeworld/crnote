package com.fff.android.crnote;

import java.io.File;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.Toast;

public class CRNoteHome extends Activity {
	private ArrayList<String> prev_files = null;
	private PrevFileAdapter pf_adapter;

	private final int REQUEST_OPEN_FILE = 0;
	private final int REQUEST_NEW_FILE = 1;
	private final int REQUEST_ONLINE_FILE = 2;
	private final int REQUEST_OPEN_PREV_FILE = 3;
	private final int REQUEST_LAUNCH_EDITOR = 4;
	private final int REQUEST_SETTINGS = 5;
	private final int REQUEST_REGISTER = 6;

	private String fpass;
	

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_home_menu);

		prev_files = CrNoteApp.file_history;
		
		View v = findViewById(R.id.comButton);
		v.setVisibility(View.VISIBLE);
		
		pf_adapter = new PrevFileAdapter(this,
				R.layout.layout_prev_opened_file_item, prev_files);
		
		ListView pfList = (ListView) findViewById(R.id.prevfilesListView);
		pfList.setAdapter(pf_adapter);
		pfList.setItemsCanFocus(false);

		Button nfButton = (Button) findViewById(R.id.newButton);
		nfButton.setOnClickListener(newfile_onClickHandler);
		Button openfileButton = (Button) findViewById(R.id.openButton);
		openfileButton.setOnClickListener(openfile_onClickHandler);
		Button comButton = (Button) findViewById(R.id.comButton);
		comButton.setOnClickListener(webfile_onClickHandler);
		Button toolsButton = (Button) findViewById(R.id.toolsButton);
		toolsButton.setOnClickListener(tools_onClickHandler);
	}
	
	private OnClickListener openfile_onClickHandler = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Intent intent = new Intent(getBaseContext(), FileDialog.class);
			intent.putExtra(FileDialog.LAUNCH_NO_NEW_FILE, "yes");
			intent.putExtra(FileDialog.START_PATH, CrNoteApp.folder);
			startActivityForResult(intent, REQUEST_OPEN_FILE);
		}
	};

	private OnClickListener newfile_onClickHandler = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Intent intent = new Intent(getBaseContext(), FileDialog.class);
			intent.putExtra(FileDialog.START_PATH, CrNoteApp.folder);
			startActivityForResult(intent, REQUEST_NEW_FILE);
		}
	};
	
	private OnClickListener webfile_onClickHandler = new OnClickListener() {
		@Override
		public void onClick(View v) {
			//Toast.makeText(getApplicationContext(), "Please wait while CrNote syncs file list", Toast.LENGTH_SHORT).show();
			Intent intent = new Intent(getBaseContext(), OnlineFiles.class);
			startActivityForResult(intent, REQUEST_ONLINE_FILE);
		}
	};
	
	private OnClickListener tools_onClickHandler = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Intent intent = new Intent(getBaseContext(), CrNoteToolsActivity.class);
			startActivity(intent);
		}
	};
	
	public void prevfile_onClickCallback(String file) {
		openFile(file);
	}


	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_OPEN_PREV_FILE) {
			// do nothing - already handled by that particular activity
		} else if (requestCode == REQUEST_ONLINE_FILE) {
			// do nothing - already handled by the activity
		} else if (requestCode == REQUEST_SETTINGS || requestCode == REQUEST_REGISTER) {
			// If registered, show Web Files in main screen
		} else if (resultCode == Activity.RESULT_OK) {
			if (requestCode == REQUEST_OPEN_FILE) {
				String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
				openFile(filePath);

				CrNoteApp.addToFileHistory(filePath);
				CrNoteApp.savePrefs();
			} else if (requestCode == REQUEST_NEW_FILE) {
				String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
				int filetype = data.getIntExtra(FileDialog.RESULT_TYPE,
						FileDialog.NEW_FILE_TXT);

				// launch the file editor
				newFile(filePath, filetype);
			} else if (requestCode == REQUEST_LAUNCH_EDITOR) {
				// launched either the crnote or plaintext editor (new or open file)
				// Do nothing... the editors have done everything already, including adding the filename to the file history
			}
			pf_adapter.notifyDataSetChanged();
		} else if (resultCode == Activity.RESULT_CANCELED) {
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.home_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.itemClear:
			CrNoteApp.clearFileHistory();
			CrNoteApp.savePrefs();
			pf_adapter.notifyDataSetChanged();
			return true;
		case R.id.settingsItem:
			startActivityForResult (new Intent(getApplication(), CRNotePreferences.class), REQUEST_SETTINGS);
			return true;
			
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private void openFile(String file) {
		CrNoteApp.addToFileHistory(file);
		CrNoteApp.savePrefs();
		pf_adapter.notifyDataSetChanged();

		getPassword(file);
	}

	private void newFile(String file, int type) {
		// type is either FileDialog.NEW_FILE_TXT or NEW_FILE_CR
		CrNoteApp crApp = (CrNoteApp) getApplication();
		crApp.setTextbuf(null);
		Intent intent;
		if (type == FileDialog.NEW_FILE_CR) {
			intent = new Intent(getApplicationContext(),
					CRNote.class);
			intent.putExtra(CRNote.FILE_NAME, file);
		} else {
			intent = new Intent(getApplicationContext(),
					PlaintextView.class);
			intent.putExtra(PlaintextView.FILE_NAME, file);
		}
		startActivityForResult(intent, REQUEST_LAUNCH_EDITOR);
	}
	
	/*
	private void getPasswordx(final String filePath) {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("Enter file password");

		// Set an EditText view to get user input
		final EditText input = new EditText(this);

		alert.setView(input);
		input.setInputType(input.getInputType()
				| InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
				| InputType.TYPE_TEXT_VARIATION_FILTER);
		input.setTransformationMethod(new PasswordTransformationMethod());

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int whichButton) {
				fpass = input.getText().toString();

				if (fpass != "") {
					
					DecryptTask dt = new DecryptTask();
					dt.setProgressMessage("Decrypting File", "File decryption in progress. Please wait.");
					dt.setOnDecryptTaskEvent(new DecryptTaskEvent() {
						private FileHelper.FileStat fs;
						private String fileparam;
						private String passwdparam;
						
						@Override
						public boolean doDecryptTask(String file, String passwd, byte[] fkey) {
							fs = FileHelper.doDecrypt(file, passwd);
							fileparam = file;
							passwdparam = passwd;
							return (fs != null);
						}

						@Override
						public void onDecryptTaskComplete(boolean resultCode) {
							if (resultCode == false) {
								fileparam = null;
								passwdparam = null;
								fs = null;
								alertDialog("Error", "Decryption error. Your password is incorrect or file is not compatible.");
							} else {
								CrNoteApp crApp = (CrNoteApp) getApplication();
								crApp.setTextbuf(fs.sbuf);
								Intent intent;
								if (fs.isCrNote) {
									intent = new Intent(getApplicationContext(), CRNote.class);
									intent.putExtra(CRNote.FILE_NAME, fileparam);
									intent.putExtra(CRNote.FILE_PASSWD, passwdparam);
								} else {
									intent = new Intent(getApplicationContext(), PlaintextView.class);
									intent.putExtra(PlaintextView.FILE_NAME, fileparam);
									intent.putExtra(PlaintextView.FILE_PASSWD, passwdparam);
								}
								fileparam = null;
								passwdparam = null;
								fs = null;
								startActivityForResult(intent, REQUEST_LAUNCH_EDITOR);
							}
						}
					});
					dt.execute(filePath, fpass);
				}
			}
		});

		alert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int whichButton) {
						fpass = "";
					}
				});

		alert.show();
		// see http://androidsnippets.com/prompt-user-input-with-an-alertdialog
	}
	*/

	private void getPassword(final String filePath) {
		final PatternDialog ptd = new PatternDialog(this, 0, PatternDialog.MODE_PROMPT, CrNoteApp.patternSeed);

		ptd.setTitle("Enter file password/pattern");
		PatternDialog.PatternDialogCallback ptc = new PatternDialog.PatternDialogCallback() {
			@Override
			public void cancelled() {
			}

			@Override
			public void keyObtained(byte[] key) {
				startDecryptTask(null, key, filePath);
				ptd.dismiss();
			}

			@Override
			public void passwordObtained(String password) {
				startDecryptTask(password, null, filePath);
				ptd.dismiss();
			}
		};
		ptd.setCallback(ptc);
		ptd.show();
	}
	
	private void startDecryptTask(String fpass, byte[] fkey, String filename) {
		DecryptTask dt = new DecryptTask();
		dt.setProgressMessage("Decrypting File", "File decryption in progress. Please wait.");
		dt.setOnDecryptTaskEvent(new DecryptTaskEvent() {
			private FileHelper.FileStat fs;
			private String fileparam;
			private String passwdparam;
			private byte[] filekeyparam;

			@Override
			public boolean doDecryptTask(String file, String passwd, byte[] fkey) {
				if (fkey != null) {
					char[] carray = Util.byteArrayToCharArray(fkey);
					fs = FileHelper.doDecrypt(file, carray);
				} else {
					fs = FileHelper.doDecrypt(file, passwd);
				}
				fileparam = file;
				passwdparam = passwd;
				filekeyparam = fkey;
				return (fs != null);
			}

			@Override
			public void onDecryptTaskComplete(boolean resultCode) {
				if (resultCode == false) {
					fileparam = null;
					passwdparam = null;
					filekeyparam = null;
					fs = null;
					alertDialog("Error", "Decryption error. Your password is incorrect or file is not compatible.");
				} else {
					CrNoteApp crApp = (CrNoteApp) getApplication();
					crApp.setTextbuf(fs.sbuf);
					Intent intent;
					if (fs.isCrNote) {
						intent = new Intent(getApplicationContext(), CRNote.class);
						intent.putExtra(CRNote.FILE_NAME, fileparam);
						intent.putExtra(CRNote.FILE_PASSWD, passwdparam);
						intent.putExtra(CRNote.FILE_KEY, filekeyparam);
					} else {
						intent = new Intent(getApplicationContext(), PlaintextView.class);
						intent.putExtra(PlaintextView.FILE_NAME, fileparam);
						intent.putExtra(PlaintextView.FILE_PASSWD, passwdparam);
						intent.putExtra(PlaintextView.FILE_KEY, filekeyparam);
					}
					fileparam = null;
					passwdparam = null;
					fs = null;
					startActivityForResult(intent, REQUEST_LAUNCH_EDITOR);
				}
			}
		});
		dt.execute(filename, fpass, fkey);
	}
	
	private void alertDialog(String title, String msg) {
		CrDialogs.AlertMsg(title, msg, this);
	}
	
	private class DecryptTask extends AsyncTask<Object, Void, Boolean> {
		private ProgressDialog pd;
		DecryptTaskEvent dtevent;
		String progressTitle;
		String progressMessage;
		
		public void setOnDecryptTaskEvent(DecryptTaskEvent dte) {
			dtevent = dte;
		}
		
		public void setProgressMessage(String title, String message) {
			progressTitle = title;
			progressMessage = message;
		}

		@Override
		protected void onPreExecute() {
			pd = ProgressDialog.show(CRNoteHome.this,
					progressTitle, progressMessage, true, false);
		}

		protected Boolean doInBackground(Object... params) {
			boolean result = dtevent.doDecryptTask((String)params[0], (String)params[1], (byte[])params[2]);
			return result;
		}
		
		protected void onPostExecute(Boolean result) {
			try {
				pd.dismiss();
			} catch (Exception e) {
				// view could be dead... due to orientation change; this is a pain.
			}
			dtevent.onDecryptTaskComplete(result);
		}
	}
	
	private interface DecryptTaskEvent
	{
		public boolean doDecryptTask(String file, String passwd, byte[] fkey);
	    public void onDecryptTaskComplete (boolean resultCode);
	}

	// /////////////////////////////////////////////////////////////

	private class PrevFileAdapter extends ArrayAdapter<String> {
		private ArrayList<String> items;

		public PrevFileAdapter(Context context, int textViewResourceId,
				ArrayList<String> items) {
			super(context, textViewResourceId, items);
			this.items = items;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.layout_prev_opened_file_item, null);
			}

			v.setClickable(true);
			v.setFocusable(true);
		     
			String s = items.get(position);
			if (s != null) {
				TextView t = (TextView) v.findViewById(R.id.prevFilenameText);
				if (t != null) {
					if (s.startsWith(CrNoteApp.folder + File.separator))
						t.setText(s.substring(CrNoteApp.folder.length() + 1));
					else
						t.setText(s);
					//t.setMovementMethod(new ScrollingMovementMethod());
					//t.setOnClickListener(v_onClickHandler);
				}
			}
			v.setOnClickListener(v_onClickHandler);
			
			return v;
		}
		
		private OnClickListener v_onClickHandler = new OnClickListener() {
			@Override
			public void onClick(View v) {
				String s = null;
				if (v.getClass() == TextView.class) {
					TextView t = (TextView) v;
					s = new String(t.getText().toString());
				} else {
					TextView t = (TextView) v
							.findViewById(R.id.prevFilenameText);
					if (t != null) {
						s = new String(t.getText().toString());
					}
				}
				if (s != null) {
					if (s.indexOf(File.separatorChar) < 0) {
						s = CrNoteApp.folder + File.separator + s;
					}
					CRNoteHome.this.prevfile_onClickCallback(s);
				}
			}
		};
	}

	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}
}
