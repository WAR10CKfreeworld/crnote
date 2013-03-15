package com.fff.android.crnote;

import android.text.ClipboardManager;
import android.view.KeyEvent;
import android.view.View;
import android.app.Dialog;
import android.content.Context;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class RandomGeneratorDialog extends Dialog {
	OnRandomGenerated onRandomGeneratedCallback;

	public RandomGeneratorDialog(Context context, int theme) {
		super(context, theme);

		setContentView(R.layout.randomgenerator_layout);

		setTitle("Random Generator");
		setCancelable(true);

		Spinner spinner = (Spinner) findViewById(R.id.spinnerOptions);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(context,
				R.array.random_generator_options_array, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(new MyOnItemSelectedListener());

		Button bInsert = (Button) findViewById(R.id.bInsert);
		bInsert.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onRandomGeneratedCallback != null) {
					EditText outET = (EditText) findViewById(R.id.editTextGenerated);
					String s = outET.getEditableText().toString();
					onRandomGeneratedCallback.onRandomGenerated(s);
				}
				dismiss();
			}
		});

		Button bCopy = (Button) findViewById(R.id.bCopy);
		bCopy.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				EditText outET = (EditText) findViewById(R.id.editTextGenerated);
				String s = outET.getEditableText().toString();
				ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
				clipboard.setText(s);
				Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
			}
		});

		Button bCancel = (Button) findViewById(R.id.bCancel);
		bCancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		EditText lenET = (EditText) findViewById(R.id.genLen);
		lenET.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				Spinner spinner = (Spinner) findViewById(R.id.spinnerOptions);
				generateIt(spinner.getSelectedItem().toString());
				return false;
			}
		});

		generateIt("Text");
	}

	public void setOnRandomGenerated(OnRandomGenerated callback) {
		onRandomGeneratedCallback = callback;
	}

	public interface OnRandomGenerated {
		public void onRandomGenerated(String s);
	}

	public class MyOnItemSelectedListener implements OnItemSelectedListener {
		public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
			// Generate random string
			String selection = parent.getItemAtPosition(pos).toString();
			generateIt(selection);
		}

		public void onNothingSelected(AdapterView<?> parent) {
			// Do nothing.
		}
	}

	private void generateIt(String type) {
		EditText genLen = (EditText) findViewById(R.id.genLen);
		EditText outET = (EditText) findViewById(R.id.editTextGenerated);
		String s = genLen.getEditableText().toString();
		int len;
		if (s.length() == 0) {
			outET.setText("");
			return;
		}
		try {
			len = Integer.parseInt(s);
			if (len == 0) {
				return;
			}
		} catch (Exception e) {
			Toast.makeText(getContext(), "Please specify a numeric value for length to generate",
					Toast.LENGTH_LONG).show();
			return;
		}

		String res;
		if (type.equals("Hex")) {
			res = Util.getRandomHexBytes(len);
		} else if (type.equals("Base64")) {
			res = Util.getRandomBase64(len);
		} else if (type.equals("Text")) {
			res = Util.getRandomString(len);
		} else /* if (selection.equals("Number")) */{
			res = Util.getRandomNumber(len);
		}
		//Util.dLog("RAND", res);

		outET.setText(res);
	}
}
