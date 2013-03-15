package com.fff.android.crnote;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.ssl.OpenSSL;

import com.fff.android.crnote.CRNoteParser.CRNoteItem;
import com.fff.android.crnote.IdleThread.IdleTimeoutEvent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;

import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/*
 * 2 file formats supported. First is just plain text.
 * If CrNote sees a random 8 bytes followed by the magic string
 * in the first bytes of the (unencrypted) file, then it is a CrNote format:
 * 
 * + indicates branch
 * - indicates leaf - a data blob. For now it is displayed as is within view.
 * [space] indicates the line follows from the previous entry
 * 
 * -> all lines will have the first char as above followed by a [space]
 * -> branch levels are based on number of +'s
 * -> leaf is part of the branch that was just specified.
 * -> newlines only need '\n'; '\r' is ignored
 * 
 * example contents:
 * 
 * + accounts
 * ++ bank
 * - branch1, account, password, netaddress	<- leaf 1 of "bank"
 * - branch2, account, password, email, notes  <- leaf 2 of "bank"
 * +++ local banks		 <- sub-branch of "bank"
 * - city, keycard, pin	<- leaf of "local banks"
 * ++ cc				   <- sub-branch of "accounts"
 * - cc1-number, pin, secret, login, etc  <- leaf of "cc"
 * 
 * Output will be similar to what is displayed for filesystem listings, but
 * all items editable.
 */

public class CRNote extends ListActivity implements SaveChangesDialog.SaveDialogCallback {
	public static final String FILE_NAME = "FILE_NAME";
	String filename = "";

	public static final String FILE_PASSWD = "FILE_PASSWD";
	String password = "";

	public static final String FILE_KEY = "FILE_KEY";
	byte [] fkey = null;

	public static final String REMOTE_FILENAME = "REMOTE_FILENAME";
	String remote_filename = null;
	
	
	// @formatter:off
	/*
	private String TEST_DATA = "+ Bank Accounts\n"
		+ "++ Commonwealth\n"
		+ "- Savings: BSB 112345, AC 123456789, Name My Name\n"
		+ "- Cheque:  BSB 122222, AC 232323232, Company\n"
		+ "++ Westpac\n"
		+ "- Savings: BSB 323423, AC 1213-1212, Name May Name 2\n"
		+ " This is a string which is continues item on previous line\n"
		+ "+ Bank Logins\n"
		+ "- A note which is shown as leaf in Bank Logins\n"
		+ "++ NetBank\n"
		+ "- UID: 1312323, password: 324234\n"
		+ "++ XBank\n"
		+ "- UID: fake_user, p: DDDDDD\n"
		+ "+++ XBank Branch 2\n"
		+ "- This demonstrates a branch off a subbranch";
	*/
	// @formatter:on

	private CRItemAdapter cri_adapter;

	private ArrayList<CRNoteItem> branch_items;
	
	private final int MOVE_SELECTION = 1; // activity launcher to make a selection of the branch to move item to.
	private final int LAUNCH_SEARCH = 2;
	private final int LOCK_SCREEN = 3;
	private final int REQUEST_OPEN_META = 4;
	private final int REQUEST_EXPORT_META = 5;
	private final int REQUEST_CAPTURE_PHOTO = 6;
	
	CRNoteParser crparser;

	ArrayList<String> branch_history = new ArrayList<String>();

	byte[] textBuf;

	Button newLeafButton;
	Button newBranchButton;

	boolean isNewFile;
	boolean contentsChanged = false; // has the contents of this file changed
										// (i.e. user edited).
	
	boolean fileBackupAlready = false; // old file has already been backed up within this session
	
	boolean searchVisible = false;
	
	IdleThread idleThread;
	private Handler idleMessageHandler;
	
	SecureFileStorage securefile;


	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.crnote_layout);
		
		Button mGoButton = (Button) findViewById(R.id.search_go_btn);
		mGoButton.setOnClickListener(mGoButtonClickListener);
		// mGoButton.setOnKeyListener(mButtonsKeyListener);
		Drawable iconLabel = getBaseContext().getResources().getDrawable(/*
																		 * android.
																		 */R.drawable.ic_btn_search);
		mGoButton.setCompoundDrawablesWithIntrinsicBounds(iconLabel, null, null, null);

		View v = (View) findViewById(R.id.crview_searchbar);
		v.setVisibility(View.GONE);
		
		TextView fn = (TextView) findViewById(R.id.cr_tv_filename);
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
		
		ListView crList = getListView();
		crList.setDividerHeight(1);
		crList.setOnItemLongClickListener(new ListView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> av, View v, int pos, long id) {
				onLongListItemClick(v, pos, id);
				return false;
			}
		});

		newBranchButton = (Button) findViewById(R.id.bNewBranch);
		newBranchButton.setOnClickListener(newBranch_onClickHandler);

		newLeafButton = (Button) findViewById(R.id.bNewLeaf);
		newLeafButton.setOnClickListener(newLeaf_onClickHandler);

		Button insertButton = (Button) findViewById(R.id.bNewInsert);
		insertButton.setOnClickListener(newInsert_onClickHandler);
		
		/*
		 * This doesn't get called anymore because we handle state changes in the
		 * UI orientation (onConfigurationChanged())
		 */
		StateChangeCache lastSettings = (StateChangeCache) getLastNonConfigurationInstance();
		if (lastSettings != null) {
			crparser = lastSettings.theparser;
			contentsChanged = lastSettings.changed;
			branch_history = lastSettings.thebranch;
			fileBackupAlready = lastSettings.fileBackupAlready;
			imageUri = lastSettings.mUri;
			
			String s = branch_history.get(branch_history.size() - 1);
			getBranch(s);
		} else {
			CrNoteApp crApp = (CrNoteApp) getApplication();
			textBuf = crApp.getTextbuf();
			isNewFile = textBuf == null;
			if (!isNewFile) {
				parseCrNote(textBuf);
			} else {
				parseCrNote(new byte[0]);
			}
			// for testing:
			// else parseCrNote(TEST_DATA);
		}
		
		
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
		if (password != null && password != "" || fkey != null) {
			Intent intent = new Intent(this, LockScreenActivity.class);
			intent.putExtra(LockScreenActivity.FILE_NAME, this.filename);
			intent.putExtra(LockScreenActivity.FILE_PASSWD, this.password);
			intent.putExtra(LockScreenActivity.FILE_KEY, this.fkey);
			intent.putExtra(LockScreenActivity.FILE_CHANGED, this.contentsChanged);
			startActivityForResult(intent, LOCK_SCREEN);
		}
	}

	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.crnote_menu, menu);
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
				View v = (View) findViewById(R.id.crview_searchbar);
				v.setVisibility(View.VISIBLE);
				v = (View) findViewById(R.id.search_src_text);
				v.requestFocus();
				searchVisible = true;
			} else {
				item.setTitle("Search");
				View v = (View) findViewById(R.id.crview_searchbar);
				v.setVisibility(View.GONE);
				searchVisible = false;
			}
			return true;
		case R.id.itemSettings:
			// TODO: on preference return, reset idle timer.
			startActivity(new Intent(getApplication(), CRNotePreferences.class));
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private OnClickListener newInsert_onClickHandler = new OnClickListener() {
		public void onClick(View v) {
			startInsertMetaDialog();
		}
	};
	
	InsertMetaDialog insertDialog;
	private void startInsertMetaDialog() {
		if (insertDialog == null) {
			insertDialog = new InsertMetaDialog(this, R.style.crAlertDialogStyle, InsertMetaDialog.MODE_INSERT);
		} else {
			insertDialog.clear();
			insertDialog.resetMode(InsertMetaDialog.MODE_INSERT);
		}
		
		assignInsertDialogButtons();

		insertDialog.show();
	}
	
	private void assignInsertDialogButtons() {
		Button insertButton = insertDialog.getInsertButton();
		insertButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				performMetaInsertion();
			}
		});
		
		Button cancelButton = insertDialog.getCancelButton();
		cancelButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				insertDialog.dismiss();
				insertDialog = null;
			}
		});
		
		Button selectFileButton = insertDialog.getSelectFileButton();
		selectFileButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				onSelectFileButtonClicked();
			}
		});
	}
	
	private Uri imageUri;
	private void onSelectFileButtonClicked() {
		if (insertDialog.isFileSelectEnabled()) {
			//Util.dLog("LAUNCH", "FILE");
			Intent intent = new Intent(getBaseContext(), FileDialog.class);
			intent.putExtra(FileDialog.LAUNCH_NO_NEW_FILE, "yes");
			intent.putExtra(FileDialog.START_PATH, "/sdcard");
			startActivityForResult(intent, REQUEST_OPEN_META);
		} else {
			//Util.dLog("LAUNCH", "CAMERA");
			// Launch camera
			
			//define the file-name to save photo taken by Camera activity
			String fileName = CrNoteApp.DEFAULT_FOLDER + "/crtempphoto.jpg";
			
			imageUri = Uri.fromFile(new File(fileName));
			
			//create new Intent
			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
			startActivityForResult(intent, REQUEST_CAPTURE_PHOTO);
		}
	}

	CRNoteItem metacri;
	private void editMetafile(final CRNoteItem cri) {
		metacri = cri;
		
		if (insertDialog == null) {
			insertDialog = new InsertMetaDialog(this, R.style.crAlertDialogStyle, InsertMetaDialog.MODE_VIEW);
			
			Button updateButton = insertDialog.getInsertButton();
			updateButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					cri.data = insertDialog.getDescription();
					cri.meta_name = insertDialog.getFilename();
					cri.meta_type = insertDialog.getContentType();
					cri.rawdata = insertDialog.getRawData();
					
					String s = branch_history.get(branch_history.size() - 1);				
					contentsChanged = true;
					getBranch(s);
					
					metacri = null;
					
					insertDialog.dismiss();
					insertDialog = null;
				}
			});
			
			Button cancelButton = insertDialog.getCancelButton();
			cancelButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					metacri = null;
					insertDialog.dismiss();
					insertDialog = null;
				}
			});
			
			Button exportButton = insertDialog.getExportButton();
			exportButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					exportMetafile(cri);
				}
			});
		} else {
			insertDialog.clear();
			insertDialog.resetMode(InsertMetaDialog.MODE_VIEW);
		}
		
		insertDialog.setContentType(cri.meta_type);
		insertDialog.setFileName(cri.meta_name);
		insertDialog.setDescription(cri.data);
		insertDialog.setRawData(cri.rawdata);

		insertDialog.show();
	}
	
	private void processOpenMetafile(String filePath) {
		if (insertDialog != null) {
			insertDialog.setFileNameText(filePath);
			insertDialog.setContentType(Util.fileContentType(filePath));
			insertDialog.loadFile();
		} else {
			Util.dLog("cr-insert-meta", "Dialog does not exist?");
		}
	}
	
	private void processInsertCameraShot(String filePath) {
		if (insertDialog != null) {
			insertDialog.setFileNameText(filePath);
			insertDialog.setContentType(Util.fileContentType(filePath));
			insertDialog.loadFile();
		} else {
			Util.dLog("cr-insert-meta", "Dialog does not exist?");
		}
	}

	private void performMetaInsertion() {
		// Insert the meta file at the current branch
		CRNoteItem cri = CRNoteParser.newCrNoteItem();
		cri.data = insertDialog.getDescription();
		cri.meta_name = insertDialog.getFilename();
		cri.meta_type = insertDialog.getContentType();
		cri.rawdata = insertDialog.getRawData();
		
		String s = branch_history.get(branch_history.size() - 1);
		crparser.addItem(s, null, cri, CRNoteParser.CR_TYPE_META);
		
		contentsChanged = true;
		getBranch(s);
		
		insertDialog.dismiss();
		insertDialog = null;
	}
	
	static final String CRNOTEITEMKEY = "crni";
	private void exportMetafile(CRNoteItem cri) {
		Intent intent = new Intent(getBaseContext(), FileDialog.class);
		intent.putExtra(FileDialog.LAUNCH_NO_NEW_FILE, "yes");
		intent.putExtra(FileDialog.ENABLE_SELECT_DIR, "yes");
		intent.putExtra(FileDialog.START_PATH, "/sdcard");
		intent.putExtra(FileDialog.WINDOW_TITLE, "Select Folder");
		startActivityForResult(intent, REQUEST_EXPORT_META);
		CrNoteApp.setSharedObject(CRNOTEITEMKEY, cri);
	}
	
	private void exportMetafileCallback(String path) {
		//Util.dLog("Export", "Export callback");
		CRNoteItem cri = (CRNoteItem)CrNoteApp.getSharedObject(CRNOTEITEMKEY);
		CrNoteApp.unsetSharedObject(CRNOTEITEMKEY);
		
		String fullPath = path + File.separator + Util.getRandomNumber(5) + "." + cri.meta_name;
		boolean res = FileHelper.writeToFile(fullPath, cri.rawdata);
		if (res) {
			Toast.makeText(getApplicationContext(), "Exported successfully to " + fullPath, Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(getApplicationContext(), "Error exporting to " + fullPath, Toast.LENGTH_LONG).show();
		}
	}
	
	
	@Override
	public void onUserInteraction() {
		//Util.dLog("CRNote", "onUserInteraction");
		idleThread.touch();
	}
	
	// Search button clicked
	private OnClickListener mGoButtonClickListener = new OnClickListener() {
		public void onClick(View v) {
			// grab string from search field
			EditText einput = (EditText) findViewById(R.id.search_src_text);
			String searchFor = einput.getText().toString();
			if (searchFor != "") {
				CrNoteApp.setSharedObject("CRPARSER", crparser);
				// launch the branch selection activity
				Intent intent = new Intent(getApplicationContext(),
						CRNoteSearchView.class);
				intent.putExtra(CRNoteSearchView.FILE_NAME, filename);
				intent.putExtra(CRNoteSearchView.SEARCH_FROM, branch_history.get(branch_history.size() - 1));
				intent.putExtra(CRNoteSearchView.SEARCH_STRING, searchFor);		
				startActivityForResult(intent, LAUNCH_SEARCH);
			}
		}
	};
	
	// Handle configuration change of orientation so that we don't have to have the
	// activity restarted.
	public void onConfigurationChanged(Configuration newConfig) {
	    super.onConfigurationChanged(newConfig);

	    // Checks the orientation of the screen
	    /*
	    if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
	    	Util.dLog("orientation", "Landscape");
	    } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
	    	Util.dLog("orientation", "Portrait");
	    }
	    */
	    if (idleThread != null) {
			idleThread.terminate();
		}
	    
	    if (insertDialog != null) {
	    	int mode = insertDialog.mode;
	    	InsertMetaDialog.InsertMetaDialogState imds = null;
	    	if (mode == InsertMetaDialog.MODE_INSERT) {
	    		imds = insertDialog.copyState(); 
	    	}
	    	
	    	try {
	    		insertDialog.dismiss();
	    	} catch (Exception e) {}
	    	insertDialog = null;
	    	System.gc();
	    	
	    	// Recreate insertDialog
	    	if (imds != null) {
	    		startInsertMetaDialog();
	    		insertDialog.restoreState(imds);
		    	imds = null;
		    	System.gc();
	    	} else {
	    		if (metacri != null) {
	    			editMetafile(metacri);
	    		}
	    	}	    	
	    }
	  }

	public Object onRetainNonConfigurationInstance() {
		return new StateChangeCache(crparser, branch_history,
				contentsChanged,
				fileBackupAlready,
				imageUri);
	}

	private class StateChangeCache {
		public CRNoteParser theparser;
		public ArrayList<String> thebranch;
		public boolean changed;
		public boolean fileBackupAlready;
		public Uri mUri;

		StateChangeCache(CRNoteParser p, ArrayList<String> b, boolean c, boolean isbackedup, Uri uri) {
			theparser = p;
			thebranch = b;
			changed = c;
			fileBackupAlready = isbackedup;
			mUri = uri;
		}
	}

	private OnClickListener newBranch_onClickHandler = new OnClickListener() {
		public void onClick(View v) {
			AlertDialog.Builder alert = new AlertDialog.Builder(v.getContext());

			alert.setTitle("Branch name");

			final EditText input = new EditText(v.getContext());

			alert.setView(input);
			input.setInputType(input.getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
					| InputType.TYPE_TEXT_VARIATION_FILTER);
			input.setFilters(new InputFilter[] {branchNameFilter});
			alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					//Util.dLog("BRANCHSIZE", "branch_history " + branch_history.size());
					String s = branch_history.get(branch_history.size() - 1);
					String ts = input.getText().toString();
					if (ts.length() > 0) {
						if (!crparser.addItem(s, ts, CRNoteParser.CR_TYPE_BRANCH) && crparser.errmsg != null) {
							Toast.makeText(getApplicationContext(), crparser.errmsg, Toast.LENGTH_SHORT).show();
							crparser.errmsg = null;
						}
						contentsChanged = true;
						getBranch(s);
					} else {
						Toast.makeText(getApplicationContext(), "Cannot have empty branch name", Toast.LENGTH_SHORT)
								.show();
					}
				}
			});

			alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
				}
			});

			alert.show();
		}
	};
	
	private InputFilter branchNameFilter = new InputFilter() { 
		public CharSequence filter(CharSequence src, int start, int end, Spanned dest, int dstart, int dend) { 
			for (int i = start; i < end; i++) { 
				if (src.charAt(i) == CRNoteParser.CR_BRANCH_DELIM_CH) { 
					return ""; 
				} 
			} 
			return null; 
		} 
	}; 

	/*
	private void _dump_bh_() {
		String s = "";
		for (int i = 0; i < branch_history.size(); i++) {
			s = s + " [" + branch_history.get(i) + "]";
		}
		Util.dLog("BRANCH_HISTORY", s);
	}
	*/

	private String currentBranch() {
		if (branch_history.size() == 0)
			return "";
		return (branch_history.get(branch_history.size() - 1));
	}

	private OnClickListener newLeaf_onClickHandler = new OnClickListener() {
		public void onClick(View v) {
			final Dialog dialog = new Dialog(v.getContext(), R.style.crDialogStyle);
			dialog.setContentView(R.layout.leafeditor_window);

			dialog.setTitle("Edit new note");
			dialog.setCancelable(true);

			TextView tv_branch = (TextView) dialog.findViewById(R.id.tv_branch);
			tv_branch.setText(currentBranch());

			// Monitor text changes...
			final TextEditor texteditor = (TextEditor) dialog.findViewById(R.id.tv_notes);

			texteditor.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View arg0, MotionEvent arg1) {
					idleThread.touch();
					return false;
				}
			});
			
			texteditor.setOnKeyListener(new OnKeyListener() {
				@Override
				public boolean onKey(View arg0, int arg1, KeyEvent arg2) {
					idleThread.touch();
					return false;
				}
			});
			
			Button button_cancel = (Button) dialog.findViewById(R.id.button_cancel);
			button_cancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					dialog.dismiss();
				}
			});
			
			Button button_insert = (Button) dialog.findViewById(R.id.button_rand);
			button_insert.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					RandomGeneratorDialog rd = new RandomGeneratorDialog(CRNote.this, R.style.crDialogStyle);
					rd.setOnRandomGenerated(new RandomGeneratorDialog.OnRandomGenerated() {
						@Override
						public void onRandomGenerated(String s) {
							texteditor.insertTextAtCurosr(s);
							contentsChanged = true;
						}
					});
					rd.show();
				}
			});

			Button button_done = (Button) dialog.findViewById(R.id.button_done);
			button_done.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					//Util.dLog("BRANCHSIZE", "branch_history " + branch_history.size());
					String ts = texteditor.getText().toString();
					if (ts.length() > 0) { // only add if not empty string
						String s = branch_history.get(branch_history.size() - 1);
						if (!crparser.addItem(s, ts, CRNoteParser.CR_TYPE_LEAF)) {
							if (crparser.errmsg != null) {
								Toast.makeText(getApplicationContext(), crparser.errmsg, Toast.LENGTH_SHORT).show();
								crparser.errmsg = null;
							}
						}
						contentsChanged = true;
						getBranch(s);
					}
					dialog.dismiss();
				}
			});

			dialog.show();
		}
	};

	public boolean parseCrNote(byte[] data) {
		try {
			// Initialise view with root branch contents
			crparser = new CRNoteParser(data);
			getBranch(CRNoteParser.CR_BRANCH_DELIM);
		} catch (CRNoteParser.CRNoteException ce) {
			Util.dLog("Exeption", ce.getMessage());
		}
		return true;
	}

	private void getBranch(String branch) {
		// DEBUG: Util.dLog("STRING OF", crparser.toString());
		branch_items = crparser.getItemsAt(branch);

		if (branch_items != null) {
			if (branch_history.size() == 0 || !branch.equals(branch_history.get(branch_history.size() - 1))) {
				branch_history.add(branch);
			}

			// DEBUG
			int has_branches = -1;
			for (int i = 0; i < branch_items.size(); i++) {
				if (branch_items.get(i).type == CRNoteParser.CR_TYPE_BRANCH) {
					has_branches = i;
					break;
				}
				//Util.dLog("CRITEM", branch_items.get(i).data);
			}

			// Move all leaves to the end of the list
			if (has_branches > 0) {
				CRNoteItem cri = branch_items.get(0);
				do {
					cri = branch_items.remove(0);
					branch_items.add(cri);
				} while ((branch_items.get(0).type == CRNoteParser.CR_TYPE_LEAF) ||
						(branch_items.get(0).type == CRNoteParser.CR_TYPE_META));
			}

			// todo: currently using monolithic crnote_branchlist_layout. Hope
			// to make the listview more
			// like separate types for branch vs leaf... -> Branch item has
			// right arrow to open up subbranches.
			cri_adapter = new CRItemAdapter(branch_items);

			cri_adapter.notifyDataSetChanged();

			setListAdapter(cri_adapter);

			TextView tv = (TextView) findViewById(R.id.cr_branchlevel_text);
			tv.setText(branch);
		} else {
			Util.dLog("Branch items null", branch);
		}
	}

	private void editLeaf(final CRNoteItem cri) {
		final Dialog dialog = new Dialog(this, R.style.crDialogStyle);
		dialog.setContentView(R.layout.leafeditor_window);

		dialog.setTitle("Edit note");
		dialog.setCancelable(true);

		TextView tv_branch = (TextView) dialog.findViewById(R.id.tv_branch);
		tv_branch.setText(cri.parent_branch);

		final TextEditor texteditor = (TextEditor) dialog.findViewById(R.id.tv_notes);
		texteditor.setText(cri.data);

		// Monitor text changes...
		texteditor.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				// Util.dLog("-text-", "afterTextChanged");
				contentsChanged = true;
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				// Util.dLog("-text-", "beforeTextChanged");
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				// Util.dLog("-text-", "onTextChanged");
				contentsChanged = true;
			}
		});
		
		texteditor.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View arg0, MotionEvent arg1) {
				idleThread.touch();
				return false;
			}
		});
		
		texteditor.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View arg0, int arg1, KeyEvent arg2) {
				idleThread.touch();
				return false;
			}
		});

		Button button_cancel = (Button) dialog.findViewById(R.id.button_cancel);
		button_cancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		
		Button button_insert = (Button) dialog.findViewById(R.id.button_rand);
		button_insert.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				RandomGeneratorDialog rd = new RandomGeneratorDialog(CRNote.this, R.style.crDialogStyle);
				rd.setOnRandomGenerated(new RandomGeneratorDialog.OnRandomGenerated() {
					@Override
					public void onRandomGenerated(String s) {
						texteditor.insertTextAtCurosr(s);
						contentsChanged = true;
					}
				});
				rd.show();
			}
		});

		Button button_done = (Button) dialog.findViewById(R.id.button_done);
		button_done.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				String ts = texteditor.getText().toString();
				if (ts.length() > 0) {
					cri.data = ts;
				} else {
					// delete the item
					crparser.deleteItem(cri);
				}
				getBranch(cri.parent_branch);
				dialog.dismiss();
			}
		});

		dialog.show();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		if (branch_items.size() > 0) {
			CRNoteItem cri = branch_items.get(position);
			if (cri.type == CRNoteParser.CR_TYPE_BRANCH) {
				getBranch(CRNoteParser.CRNoteItemToBranchString(cri));
			} else if (cri.type == CRNoteParser.CR_TYPE_LEAF) {
				editLeaf(cri);
			} else {
				editMetafile(cri);
			}
		}
	}

	// User touched item for a long time, pop up a menu of options
	protected void onLongListItemClick(View v, int position, long id) {
		//Util.dLog("onlongclick", "onLongListItemClick id=" + id);
		if (branch_items.size() > 0) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			final CharSequence[] items = { "Edit", "Move", /* "Copy", */"Delete" };
			final int pos = position;

			CRNoteItem cri = branch_items.get(position);
			if (cri.type == CRNoteParser.CR_TYPE_BRANCH) {
				builder.setTitle("Branch options");
			} else if (cri.type == CRNoteParser.CR_TYPE_LEAF) {
				builder.setTitle("Note options");
			} else {
				builder.setTitle("Metafile options");
			}

			builder.setItems(items, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					// Toast.makeText(getApplicationContext(), items[item],
					// Toast.LENGTH_SHORT).show();
					switch (item) {
					case 0:
						editSelectedItem(pos);
						break;

					case 1:
						moveSelectedItem(pos);
						break;

					case 2:
						/*
						 * copySelectedItem(pos); break;
						 * 
						 * case 3:
						 */
						deleteSelectedItem(pos);
						break;

					default:
					}
				}
			});
			AlertDialog alert = builder.create();

			alert.show();
		}
	}

	private void editSelectedItem(int position) {
		final CRNoteItem cri = branch_items.get(position);
		if (cri.type == CRNoteParser.CR_TYPE_BRANCH) {
			AlertDialog.Builder alert = new AlertDialog.Builder(getListView().getContext());

			alert.setTitle("Edit branch name");

			final EditText input = new EditText(getListView().getContext());
			alert.setView(input);
			input.setText(cri.data);
			input.setInputType(input.getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
					| InputType.TYPE_TEXT_VARIATION_FILTER);
			input.setFilters(new InputFilter[] {branchNameFilter});
			alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					String ts = input.getText().toString();
					if (ts.length() > 0) {
						cri.data = ts;
						contentsChanged = true;
						getBranch(branch_history.get(branch_history.size() - 1));
					} else {
						Toast.makeText(getApplicationContext(), "Cannot have empty branch name", Toast.LENGTH_SHORT)
								.show();
					}
				}
			});

			alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
				}
			});

			alert.show();
		} else if (cri.type == CRNoteParser.CR_TYPE_LEAF) {
			editLeaf(cri);
		} else {
			editMetafile(cri);
		}
	}

	private void moveSelectedItem(int position) {
		CrNoteApp.setSharedObject("CRPARSER", crparser);
		// launch the branch selection activity
		Intent intent = new Intent(getApplicationContext(),
				CRBranchList.class);
		intent.putExtra(CRBranchList.PROMPT_TEXT, "Select branch to move item");
		CRNoteItem cri = branch_items.get(position);
		if (cri.type == CRNoteParser.CR_TYPE_BRANCH) {
			intent.putExtra(CRBranchList.IGNORE_BRANCH, CRNoteParser.CRNoteItemToBranchString(cri));
		}
		CrNoteApp.setSharedObject("SRCITEM", cri);
		startActivityForResult(intent, MOVE_SELECTION);
	}

	/*
	private void copySelectedItem(int position) {
		// XXX for version 1.1
	}
	*/

	private void deleteSelectedItem(int position) {
		CRNoteItem cri = branch_items.get(position);
		crparser.deleteItem(cri);
		contentsChanged = true;
		getBranch(branch_history.get(branch_history.size() - 1));
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == MOVE_SELECTION) {
				String destBranch = data.getStringExtra(CRBranchList.RESULT_BRANCH);
				//Util.dLog("RESULT", destBranch);

				// Now move it
				CRNoteItem cri = (CRNoteItem) CrNoteApp.getSharedObject("SRCITEM");
				CrNoteApp.unsetSharedObject("SRCITEM");
				CrNoteApp.unsetSharedObject("CRPARSER");
				boolean res = crparser.moveItem(cri, destBranch);
				cri = null;
				//Util.dLog("MoveItem", "success? " + (res ? "true" : "false"));
				if (res == true) {
					contentsChanged = true;
					getBranch(branch_history.get(branch_history.size() - 1));
				}
			} else if (requestCode == LAUNCH_SEARCH) {
				String selBranch = data.getStringExtra(CRNoteSearchView.RESULT_BRANCHSELECTED);
				boolean hasChanges = data.getBooleanExtra(CRNoteSearchView.RESULT_HASCHANGES, false);
				if (selBranch != null) {
					// New branch selected from search view.
					// Refresh the view with the new branch, but also reset the branch_history
					// so that the Back button goes back to parent, and not the previous
					// branch location.
					String [] branchList = selBranch.split(CRNoteParser.CR_BRANCH_DELIM);
					branch_history.clear();
					String curB = "";
					for (String si: branchList) {
						//Util.dLog("APPENDING", "[" + si + "]");
						if (curB.equals(CRNoteParser.CR_BRANCH_DELIM)) {
							curB = curB + si;
						} else {
							curB = curB + CRNoteParser.CR_BRANCH_DELIM + si;
						}
						branch_history.add(curB);
					}
					getBranch(selBranch);
				} else {
					contentsChanged |= hasChanges;
				}
			} else if (requestCode == REQUEST_OPEN_META) {
				//Util.dLog("AC", "OPEN");
				String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
				processOpenMetafile(filePath);
			} else if (requestCode == REQUEST_EXPORT_META) {
				//Util.dLog("AC", "EXPORT");
				String selectedPath = data.getStringExtra(FileDialog.RESULT_PATH);
				exportMetafileCallback(selectedPath);
			} else if (requestCode == REQUEST_CAPTURE_PHOTO) {			
				processInsertCameraShot(imageUri.getPath());
			}
		}
		if (requestCode == LOCK_SCREEN) {
			if (resultCode == LockScreenActivity.RESULT_CLOSE_FILE) {
				idleThread.terminate();
				this.finish();
			} else {
				idleThread.unpause();
			}
		}
		CrNoteApp.clearSharedObjects();
	}
	
	


	private class CRItemAdapter extends BaseAdapter {
		private ArrayList<CRNoteItem> items;

		public CRItemAdapter(ArrayList<CRNoteItem> items) {
			this.items = items;
		}

		public CRNoteItem getItem(int position) {
			return items.get(position);
		}

		public int getCount() {
			return items.size();
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			CRNoteItem i = items.get(position);
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

				v = vi.inflate(R.layout.crnote_branchlist_layout, null);
			}

			if (i != null) {
				ImageView iv = (ImageView) v.findViewById(R.id.crrowimage);
				TextView tv = (TextView) v.findViewById(R.id.crrowtext);
				if (i.type == CRNoteParser.CR_TYPE_BRANCH) {
					iv.setImageResource(R.drawable.crbranch);
					
					iv = (ImageView) v.findViewById(R.id.crbranchitem_next);
					iv.setVisibility(android.view.View.VISIBLE);
					tv.setLines(1);
					tv.setTextSize(20);
					tv.setText(i.data);
				} else if (i.type == CRNoteParser.CR_TYPE_LEAF) {
					iv.setImageResource(R.drawable.crleaf);
					
					iv = (ImageView) v.findViewById(R.id.crbranchitem_next);
					iv.setVisibility(android.view.View.GONE);
					tv.setLines(2);
					tv.setTextSize(14);
					final int MAX_ITEM_LEN = 120;
					int maxi = i.data.length() > MAX_ITEM_LEN ? MAX_ITEM_LEN : i.data.length();
					tv.setText(i.data.substring(0, maxi));
				} else {
					iv.setImageResource(R.drawable.crmeta);
					
					iv = (ImageView) v.findViewById(R.id.crbranchitem_next);
					iv.setVisibility(android.view.View.GONE);
					tv.setLines(2);
					tv.setTextSize(14);
					final int MAX_ITEM_LEN = 120;
					String s = "[" + i.meta_name + "] " + i.data;
					int maxi = s.length() > MAX_ITEM_LEN ? MAX_ITEM_LEN : s.length();
					tv.setText(s.substring(0, maxi));
				}
			}

			return v;
		}

		public long getItemId(int position) {
			return position;
		}
	}

	private boolean saveCRNote(char [] passcode) {
		boolean res = saveCRNote(passcode, filename);
		if (res) {
			CrNoteApp.addToFileHistory(filename);
			CrNoteApp.savePrefs();
		}
		return res;
	}
	
	private boolean saveCRNote(char[] passcode, String toFile) {
		//Util.dLog("saveCRNote", "called " + toFile);
		InputStream is = crparser.getInputStream();
		CrInputStream cis = new CrInputStream(is);

		try {
			InputStream cipherStream = OpenSSL.encrypt(CrNoteApp.cryptoalgo, passcode, cis, false);
			InputStream finalIStream = cipherStream;
			if (CrNoteApp.securestorage) {
				finalIStream = OpenSSL.encrypt(CrNoteApp.cryptoalgo, Util.byteArrayToCharArray(CrNoteApp.webkey), cipherStream, false);
			}
			File f = new File(toFile);
			FileOutputStream fout = new FileOutputStream(f);
			org.apache.commons.ssl.Util.pipeStream(finalIStream, fout, false);
			fout.flush();
			fout.close();
		} catch (IOException e) {
			//e.printStackTrace();
			return false;
		} catch (GeneralSecurityException e) {
			//e.printStackTrace();
			return false;
		}

		return true;
	}

	public class CrInputStream extends InputStream {
		byte[] crheader;
		int crh_i = 0;
		InputStream crparserStream;

		CrInputStream(InputStream crpstream) {
			crheader = Util.generateCrHeader();
			crparserStream = crpstream;
		}

		@Override
		public int read() throws IOException {
			if (crh_i < crheader.length) {
				return crheader[crh_i++];
			} else {
				return crparserStream.read();
			}
		}

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
		
		// The dialog was dismissed from the callbacks below. Dismiss Activity if nothing else to do
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
					CRNote.this.finish();
				}
				sdialog_dismissOnly = true;
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
			if (fkey != null) {
				success = saveCRNote(Util.byteArrayToCharArray(fkey));
			} else if (password != null && password.length() > 0) {
				success = saveCRNote(password.toCharArray()); 
			} else {
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
				
				if (fkey != null) {
					saveCRNote(Util.byteArrayToCharArray(fkey), localcopy);
				} else if (password != null && password.length() > 0) {
					saveCRNote(password.toCharArray(), localcopy);
				}

				CrNoteApp.addToFileHistory(localcopy);
				CrNoteApp.savePrefs();
			}
			isNewFile = false;
			contentsChanged = false;
			
			// save local cache
			localcopy = CrNoteApp.onlinefolder + File.separator + filename.substring("CRNOTE://".length());
			if (fkey != null) {
				saveCRNote(Util.byteArrayToCharArray(fkey), localcopy);
			} else if (password != null && password.length() > 0) {
				saveCRNote(password.toCharArray(), localcopy);
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
    	Util.dLog("ON_ERROR", s);
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
					OnlineFileListCache oflc = (OnlineFileListCache) CrNoteApp.getTempSettingObject("onlinefiles");
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
						CrNoteApp.setTempSettingObject("onlinefiles", oflc);
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
			pd = ProgressDialog.show(CRNote.this,
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
			if (branch_history.size() == 1) {
				if (contentsChanged) {
					promptSave(true);
				} else {
					idleThread.terminate();
					super.onKeyDown(keyCode, event);
				}
			} else {
				branch_history.remove(branch_history.size() - 1);
				String s = branch_history.remove(branch_history.size() - 1); // remove it because it's going to be added again in getbranch
				getBranch(s);
			}
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}
}
