//package com.lamerman; // http://code.google.com/p/android-file-dialog/ (BSD license)
package com.fff.android.crnote;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.method.NumberKeyListener;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class FileDialog extends ListActivity {

	private static final String ITEM_KEY = "key";
	private static final String ITEM_IMAGE = "image";
	private static final String ITEM_DETAILS = "details";

	public static final String START_PATH = "START_PATH";
	public static final String RESULT_PATH = "RESULT_PATH";
	public static final String RESULT_TYPE = "RESULT_TYPE";
	public static final String LAUNCH_NO_NEW_FILE = "LAUNCH_NO_NEW_FILE";
	public static final String RESTRICT_APP_PATH = "RESTRICT_APP_PATH";
	public static final String ENABLE_SELECT_DIR = "ENABLE_SELECT_DIR";
	public static final String WINDOW_TITLE = "WINDOW_TITLE";
	
	public static final int NEW_FILE_TXT = 1;
	public static final int NEW_FILE_CR = 2;
	
	private int filetype = NEW_FILE_TXT;
	
	private List<String> item = null;
	private List<String> path = null;
	private String root = "/";
	private String startPath;
	private boolean restrictPath; // restrict browsing to only within application store
	private boolean enableDirSelection;
	private TextView myPath;
	private EditText mFileName;
	private ArrayList<HashMap<String, Object>> mList;

	private Button selectButton;
	private Button newTxtButton;
	private Button newCrButton;
	private Button cancelButton;
	private Button createButton;

	private LinearLayout layoutSelect;
	private LinearLayout layoutCreate;
	private InputMethodManager inputManager;
	private String parentPath;
	private String currentPath = root;

	private boolean show_new_button = true;

	private File selectedFile;
	private HashMap<String, Integer> lastPositions = new HashMap<String, Integer>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.file_dialog_main);

		setResult(RESULT_CANCELED, getIntent());

		myPath = (TextView) findViewById(R.id.path);
		mFileName = (EditText) findViewById(R.id.fdEditTextFile);
		
		InputFilter filter = new InputFilter() {
			@Override
			public CharSequence filter(CharSequence source, int start, int end,
					Spanned dest, int dstart, int dend) {
				for (int i = start; i < end; i++) { 
                    if (source.charAt(i) == '|' || source.charAt(i) == '/' || source.charAt(i) == ':') { 
                            return ""; 
                    } 
				} 
				return null;
			}
		};
		mFileName.setFilters(new InputFilter[]{filter});
		
		//mFileName.setKeyListener(new FileNameKeyListener());

		inputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

		String launch_no_new = getIntent().getStringExtra(LAUNCH_NO_NEW_FILE);
		if (launch_no_new != null) {
			show_new_button = false;
		} else {
			show_new_button = true;
		}
		
		String enable_dir = getIntent().getStringExtra(ENABLE_SELECT_DIR);
		if (enable_dir != null && enable_dir.equals("yes")) {
			enableDirSelection = true;
		} else {
			enableDirSelection = false;
		}
		
		String title = getIntent().getStringExtra(WINDOW_TITLE);
		if (title != null) { 
			this.setTitle(getResources().getString(R.string.app_name) + " - " + title);
		}

		selectButton = (Button) findViewById(R.id.fdButtonSelect);
		if (!show_new_button) {
			if (!enableDirSelection) {
				selectButton.setEnabled(false);
				selectButton.setText("Select");
			} else {
				selectButton.setEnabled(true);
				selectButton.setText("Select current folder");
			}
			selectButton.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					if (enableDirSelection) {
						getIntent().putExtra(RESULT_PATH, currentPath);
						setResult(RESULT_OK, getIntent());
						finish();
					}
					
					if (selectedFile != null) {
						getIntent().putExtra(RESULT_PATH,
								selectedFile.getPath());
						setResult(RESULT_OK, getIntent());
						finish();
					}
				}
			});
		} else {
			selectButton.setVisibility(View.GONE);
		}

		newTxtButton = (Button) findViewById(R.id.fdButtonNewText);
		newCrButton = (Button) findViewById(R.id.fdButtonNewCrNote);
		if (show_new_button) {
			newTxtButton.setVisibility(View.VISIBLE);
			newTxtButton.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					layoutSelect.setVisibility(View.GONE);
					layoutCreate.setVisibility(View.VISIBLE);

					mFileName.setText("");
					mFileName.requestFocus();
					
					filetype = NEW_FILE_TXT;
				}
			});
			
			newCrButton.setVisibility(View.VISIBLE);
			newCrButton.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					layoutSelect.setVisibility(View.GONE);
					layoutCreate.setVisibility(View.VISIBLE);

					mFileName.setText("");
					mFileName.requestFocus();
					
					filetype = NEW_FILE_CR;
				}
			});
		} else {
			newTxtButton.setVisibility(View.GONE);
			newCrButton.setVisibility(View.GONE);
		}

		layoutSelect = (LinearLayout) findViewById(R.id.fdLinearLayoutSelect);
		layoutCreate = (LinearLayout) findViewById(R.id.fdLinearLayoutCreate);
		layoutCreate.setVisibility(View.GONE);

		cancelButton = (Button) findViewById(R.id.fdButtonCancel);
		cancelButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				layoutCreate.setVisibility(View.GONE);
				layoutSelect.setVisibility(View.VISIBLE);

				inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
				unselect();
			}

		});
		createButton = (Button) findViewById(R.id.fdButtonCreate);
		createButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String n = mFileName.getText().toString().trim();
				n = n.replace("/", "");
				String newFN = currentPath + File.separator + n;
				
				if (n.length() > 0) {
					Util.dLog("....", "FILE: " + mFileName.getText() + ", "  + CrNoteApp.CONFIG_FILENAME);
					if (CrNoteApp.isConfigFile(newFN)) {
						Toast.makeText(getApplicationContext(), "Error: File name cannot be config file " + CrNoteApp.CONFIG_FILENAME,
								Toast.LENGTH_LONG).show();
					} else {
						File f = new File(newFN);
						if (f.exists()) {
							Toast.makeText(getApplicationContext(), "Error: File \"" + n + "\" exists. Please enter a new name.",
									Toast.LENGTH_LONG).show();
						} else {
							getIntent().putExtra(RESULT_PATH, newFN);
							getIntent().putExtra(RESULT_TYPE, filetype);
							setResult(RESULT_OK, getIntent());
							finish();
						}
					}
				}
			}
		});

		startPath = getIntent().getStringExtra(START_PATH);
		if (startPath != null) {
			getDir(startPath);
		} else {
			startPath = "/";
			getDir(root);
		}
		
		restrictPath = getIntent().getBooleanExtra(RESTRICT_APP_PATH, false);
	}
	
	// Filename restrictor.
	public class FileNameKeyListener extends NumberKeyListener
	{
	    @Override
	    protected char[] getAcceptedChars()
	    {       
	        return new char [] { '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
	                             'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 
	                             'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 
	                             'u', 'v', 'w', 'x', 'y', 'z', 
	                             'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 
	                             'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 
	                             'U', 'V', 'W', 'X', 'Y', 'Z',
	                             '~', '!', '@', '#', '$', '%', '^', '&', '*', '-',
	                             '_', '+', '=', '(', ')', '[', ']', '{', '}', ',',
	                             '.', ' '};
	    }

	    @Override
	    public void clearMetaKeyState(View view, Editable content, int states)
	    {
	    }

	    @Override
	    public int getInputType()
	    {   
	        return InputType.TYPE_CLASS_TEXT;
	    }   
	}

	private void getDir(String dirPath) {

		boolean useAutoSelection = dirPath.length() < currentPath.length();

		Integer position = lastPositions.get(parentPath);

		getDirImpl(dirPath);

		if (position != null && useAutoSelection) {
			getListView().setSelection(position);
		}

	}

	private void getDirImpl(String dirPath) {

		myPath.setText(dirPath);
		currentPath = dirPath;

		item = new ArrayList<String>();
		path = new ArrayList<String>();
		mList = new ArrayList<HashMap<String, Object>>();

		File f = new File(dirPath);
		File[] files = f.listFiles();

		if (!dirPath.equals(root)) {

			item.add(root);
			addItem(root, R.drawable.filedialog_folder, "");
			path.add(root);

			item.add("../");
			addItem("../", R.drawable.filedialog_folder, "");
			path.add(f.getParent());
			parentPath = f.getParent();

		}

		TreeMap<String, String> dirsMap = new TreeMap<String, String>();
		TreeMap<String, String> dirsPathMap = new TreeMap<String, String>();
		TreeMap<String, String> filesMap = new TreeMap<String, String>();
		TreeMap<String, String> filesPathMap = new TreeMap<String, String>();
		for (File file : files) {
			if (file.isDirectory()) {
				String dirName = file.getName();
				dirsMap.put(dirName, dirName);
				dirsPathMap.put(dirName, file.getPath());
			} else {
				filesMap.put(file.getName(), file.getName());
				filesPathMap.put(file.getName(), file.getPath());
			}
		}
		item.addAll(dirsMap.tailMap("").values());
		item.addAll(filesMap.tailMap("").values());
		path.addAll(dirsPathMap.tailMap("").values());
		path.addAll(filesPathMap.tailMap("").values());

		FileItemAdapter fileList = new FileItemAdapter(this, mList,
				R.layout.file_dialog_row,
				new String[] { ITEM_KEY, ITEM_IMAGE, ITEM_DETAILS }, new int[] {
						R.id.fdrowtext, R.id.fdrowimage, R.id.fdrowdetails});

		for (String dir : dirsMap.tailMap("").values()) {
			addItem(dir, R.drawable.filedialog_folder, "");
		}

		for (String file : filesMap.tailMap("").values()) {
			File fd = new File (currentPath + File.separator + file);
			String details = "";
			try {
				Date date = null;
				try {
					date = new Date(fd.lastModified());
				} catch (NumberFormatException e) {
				}

				String len;
				DecimalFormat dec = new DecimalFormat();
				dec.setMaximumFractionDigits(2);
				if (fd.length() < 1024) {
					len = fd.length() + " bytes";
				} else if (fd.length() < (1024*1024)) {
					len = dec.format((double)fd.length()/1024) + "kB";
				} else {
					len = dec.format((double)fd.length()/(1024*1024)) + "MB";
				}
				if (date != null) {
					details = len + " | " + date.toLocaleString();	
				} else {
					details = len;
				}
			} catch (Exception e) {
				Util.dLog("CrFiles", "Unable to get details for " + file);
			}
			addItem(file, R.drawable.file, details);
		}

		fileList.notifyDataSetChanged();

		setListAdapter(fileList);

	}

	private void addItem(String fileName, int imageId, String details) {
		HashMap<String, Object> item = new HashMap<String, Object>();
		item.put(ITEM_KEY, fileName);
		item.put(ITEM_IMAGE, imageId);
		item.put(ITEM_DETAILS, details);
		mList.add(item);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {

		File file = new File(path.get(position));

		if (file.isDirectory()) {
			unselect();
			if (file.canRead()) {
				lastPositions.put(currentPath, position);
				getDir(path.get(position));
			} else {
				new AlertDialog.Builder(this)
						.setIcon(R.drawable.icon)
						.setTitle(
								"[" + file.getName() + "] "
										+ getText(R.string.cant_read_folder))
						.setPositiveButton("OK",
								new DialogInterface.OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {

									}
								}).show();
			}
		} else {
			selectedFile = file;
			v.setSelected(true);
			if (!FileDialog.this.show_new_button) {
				for (int i = 0; i < this.getListView().getChildCount(); i++) {
					View vx = this.getListView().getChildAt(i);
					TextView tx = (TextView) vx.findViewById(R.id.fdrowtext);
					
					if (tx.getText().toString().endsWith("(DO NOT EDIT)")) {
						tx.setTextColor(0xff555555);
					} else if (vx == v) {
						tx.setTextColor(0xffBF8415);
					} else {				
						tx.setTextColor(0xffeeeeee);
					}
				}				
			}
			
			if (!show_new_button && !enableDirSelection) {
				selectButton.setEnabled(true);
			}
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			unselect();

			if (layoutCreate.getVisibility() == View.VISIBLE) {
				layoutCreate.setVisibility(View.GONE);
				layoutSelect.setVisibility(View.VISIBLE);
			} else {
				/*
				if (!restrictPath && !currentPath.equals(root)) {
					getDir(parentPath);
				} else if (restrictPath && !currentPath.equals(startPath)) {
					getDir(parentPath);
				} else {
					return super.onKeyDown(keyCode, event);
				}
				*/
				return super.onKeyDown(keyCode, event);
			}

			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	private void unselect() {
		if (!show_new_button && !enableDirSelection) {
			selectButton.setEnabled(false);
		}
	}
	
	private class FileItemAdapter extends SimpleAdapter {
		public FileItemAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
			super(context, data, resource, from, to);
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View v = super.getView(position, convertView, parent);

			@SuppressWarnings("unchecked")
			HashMap<String, Object> item = (HashMap<String, Object>) getItem(position);

			String fname = (String) item.get(ITEM_KEY);
			//int imageId = (Integer) item.get(ITEM_IMAGE);
			String details = (String) item.get(ITEM_DETAILS);
			
			TextView tx = (TextView) v.findViewById(R.id.fdrowtext);
			tx.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
			tx.setTextColor(0xffeeeeee);
			
			TextView tv = (TextView) v.findViewById(R.id.fdrowdetails);
			if (details == null || details.length() == 0) {
				tv.setVisibility(View.GONE);
				v.setBackgroundColor(0x80141517);
			} else {
				tv.setVisibility(View.VISIBLE);
				v.setBackgroundColor(0);
				
				String filepath = FileDialog.this.currentPath + File.separator + fname;
				if (CrNoteApp.isConfigFile(filepath)) {
					tx.setTextColor(0xff555555);
					tx.setText(fname + " (DO NOT EDIT)");
					tx.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
				} else if (!FileDialog.this.show_new_button && FileDialog.this.selectedFile != null &&
						FileDialog.this.selectedFile.getName().equals(fname)) {
					tx.setTextColor(0xffBF8415);
				}
			}
			return v;
		}
	}
}