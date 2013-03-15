package com.fff.android.crnote;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.view.View;
import android.app.Dialog;

public class InsertMetaDialog extends Dialog {
	private byte[] fdata;
	private String fname;
	private String fullPath;
	public int mode;

	public static final int MODE_INSERT = 1; // insert a new meta file mode
	public static final int MODE_VIEW = 2;   // view/edit existing meta file mode

	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        /*
        LayoutParams params = getWindow().getAttributes(); 
        params.width = LayoutParams.FILL_PARENT; 
        getWindow().setAttributes(params);
        
        resetMode(MODE_INSERT);
        */
        //Util.dLog("InsertDialog", "onCreate");
    }
	
	public InsertMetaDialog(Context context, int theme, int mode) {
		super(context, theme);
		setContentView(R.layout.crnote_insert_meta);

        LayoutParams params = getWindow().getAttributes(); 
        params.width = LayoutParams.FILL_PARENT; 
        getWindow().setAttributes(params);
        
		resetMode(mode);
	}
	
	public void resetMode(int mode) {
		this.mode = mode;
		
		Button bExport = (Button) findViewById(R.id.bExport);
		Button bInsert = getInsertButton();
		Button bCancel = getCancelButton();
		
		if (mode == MODE_INSERT) {
			bInsert.setText("Insert");
			bExport.setVisibility(View.GONE);
			bCancel.setText("Cancel");
			bInsert.setEnabled(false);
			
			View lv = findViewById(R.id.layoutSource);
			lv.setVisibility(View.VISIBLE);
			//Util.dLog("MODE", "INSERT");
		} else {
			bInsert.setText("Apply");	
			bExport.setVisibility(View.VISIBLE);
			bCancel.setText("Close");
			bInsert.setEnabled(true);
			
			View lv = findViewById(R.id.layoutSource);
			lv.setVisibility(View.GONE);
			//Util.dLog("MODE", "VIEW");
		}
		
		TextView tfz = (TextView) findViewById(R.id.tFilesz);
		tfz.setText("");
		
		final ToggleButton toggleOpt = (ToggleButton) findViewById(R.id.touchslide);
		toggleOpt.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				toggleSrcOption(isChecked);
			}
		});
	}
	
	public class InsertMetaDialogState {
		public byte[] fdata;
		public String fname;
		public String fullPath;
		public int mode;
		public boolean toggleFileOpt;
		public String desc;
		public String ctype;
	}
	
	public InsertMetaDialogState copyState() {
		InsertMetaDialogState d = new InsertMetaDialogState();
		
		d.fdata = fdata;
		d.fname = fname;
		d.fullPath = fullPath;
		d.mode = mode;
		ToggleButton toggleOpt = (ToggleButton) findViewById(R.id.touchslide);
		d.toggleFileOpt = toggleOpt.isChecked();
		d.desc = getDescription();
		d.ctype = getContentType();
		return d;
	}
	
	public void restoreState(InsertMetaDialogState d) {
		fdata = d.fdata;
		fname = d.fname;
		fullPath = d.fullPath;
		mode = d.mode;
		ToggleButton toggleOpt = (ToggleButton) findViewById(R.id.touchslide);
		toggleOpt.setChecked(d.toggleFileOpt);

		if (fullPath != null && fullPath.length() > 0) {
			setFileNameText(fullPath);
		}
		
		if (d.desc != null && d.desc.length() > 0) {
			setDescription(d.desc);
		}
		
		if (d.ctype != null && d.ctype.length() > 0) {
			setContentType(d.ctype);
		}
		
		if(this.fdata != null && this.fdata.length > 0) {
			loadFile();
		}
	}
	
	private void toggleSrcOption(boolean isCameraOption) {
		if (!isCameraOption) {
			switchFileButton(true);
		} else {
			switchFileButton(false);
		}
	}
	
	public boolean isFileSelectEnabled() {
		ToggleButton toggleOpt = (ToggleButton) findViewById(R.id.touchslide);
		return (!toggleOpt.isChecked());
	}
	
	public void clear() {
		fdata = null;
		fname = null;
		
		ToggleButton toggleOpt = (ToggleButton) findViewById(R.id.touchslide);
		toggleOpt.setChecked(false);
		
		switchFileButton(true);
		setDescription("");
		setContentType("");
		setB64Encode(true);
	}
	
	public void onStop () {
		clear();
		ImageView mImage = (ImageView) findViewById(R.id.imageView);
		mImage.setImageResource(R.drawable.unknownfile);
		mImage = null;
		//Util.dLog("InsertMetaDialog", "stopping");
		super.onStop();
	}

	public void setFileName(String fn) {
		Button bFilename = getSelectFileButton();
		EditText etFilename = getFilenameEdittext();
		
		fullPath = fn;
		File f = new File(fn);
		fname = f.getName();
		bFilename.setText(fname);
		etFilename.setText(fname);
	}
	
	public void setFileNameText(String fn) {
		EditText etFilename = getFilenameEdittext();
		
		fullPath = fn;
		File f = new File(fn);
		fname = f.getName();
		etFilename.setText(fname);
	}

	
	public void setDescription(String desc) {
		EditText et = (EditText) findViewById(R.id.etDescription);
		et.setText(desc);
	}
	
	public void setContentType(String ctype) {
		EditText et = (EditText) findViewById(R.id.etContentType);
		et.setText(ctype);
	}
	
	public void setB64Encode(boolean enc) {
		CheckBox cb = (CheckBox) findViewById(R.id.cbBase64);
		cb.setEnabled(enc);
	}
	
	public void setRawData(byte[] data) {
		this.fdata = null;
		System.gc();
		
		this.fdata = data;
		ImageView mImage = (ImageView) findViewById(R.id.imageView);
		String ctypes = ((EditText) findViewById(R.id.etContentType)).getEditableText().toString();

		TextView tfz = (TextView) findViewById(R.id.tFilesz);
		tfz.setText("File size: " + fdata.length + " bytes");

		if (ctypes != null && ctypes.startsWith("image/")) {
			Bitmap bmp;
			try {
				bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
				mImage.setImageBitmap(bmp);
			} catch (OutOfMemoryError e) {
				Util.dLog("CrNote", "out of memory " + e.getMessage());
			}
			
			bmp = null;
			System.gc();
		} else {
			mImage.setImageResource(R.drawable.unknownfile);
		}
	}
	
	public String getFilename() {
		EditText et = getFilenameEdittext();
		String s = et.getEditableText().toString();
		if (s.length() > 0 && !s.equals(fname)) {
			fname = s;
		}
		return fname;
	}
	
	public String getDescription() {
		EditText et = (EditText) findViewById(R.id.etDescription);
		String s = et.getEditableText().toString();
		return s;
	}
	
	public String getContentType() {
		EditText et = (EditText) findViewById(R.id.etContentType);
		String s = et.getEditableText().toString();
		return s;
	}
	
	public boolean getB64Encode() {
		CheckBox cb = (CheckBox) findViewById(R.id.cbBase64);
		return cb.isChecked();
	}
	
	public byte[] getRawData() {
		return fdata;
	}
		
	public byte[] loadFile() {
		// Loads the file as specified by the filename
		try {
			if (fname != null) {
				File file = new File(fullPath);
				FileInputStream instream = new FileInputStream(file);
				long len = file.length();
				fdata = new byte[(int) len];
				instream.read(fdata);

				ImageView mImage = (ImageView) findViewById(R.id.imageView);
				
				TextView tfz = (TextView) findViewById(R.id.tFilesz);
				tfz.setText("File size: " + fdata.length + " bytes");
				
				if (isImage(fname)) {
					Bitmap bmp = BitmapFactory.decodeByteArray(fdata, 0, fdata.length);
					mImage.setImageBitmap(bmp);
				} else {
					mImage.setImageResource(R.drawable.unknownfile);
				}
				
				Button bInsert = getInsertButton();
				bInsert.setEnabled(true);
				
				return fdata;
			}
		} catch (FileNotFoundException e) {
			Util.dLog("meta-load", "Failed: [" + fullPath + "] " + e.getMessage());
		} catch (IOException e) {
			Util.dLog("meta-load", "Failed read: " + e.getMessage());
		
		}
		return null;
	}
	
	private boolean isImage(String name) {
		String s = name.toLowerCase();

		if (s.endsWith(".jpg") || s.endsWith(".jpeg")) {
			return true;
		}

		if (s.endsWith(".png")) {
			return true;
		}

		if (s.endsWith(".bmp")) {
			return true;
		}
		
		/*
		if (s.endsWith(".gif")) {
			return true;
		}
		*/
		
		return false;
	}
	
	public Button getInsertButton() {
		return (Button) findViewById(R.id.bInsert);
	}
	
	public Button getCancelButton() {
		return (Button) findViewById(R.id.bCancel);
	}
	
	public Button getSelectFileButton() {
		return (Button) findViewById(R.id.bSelectFile);
	}
	
	public EditText getFilenameEdittext() {
		return (EditText) findViewById(R.id.etFilename);
	}

	public void switchFileButton(boolean showFileButton) {
		if (showFileButton) {
			getSelectFileButton().setText("Select File");
		} else {
			getSelectFileButton().setText("Capture Photo");
		}
	}
	
	public Button getExportButton() {
		return (Button) findViewById(R.id.bExport);
	}
}