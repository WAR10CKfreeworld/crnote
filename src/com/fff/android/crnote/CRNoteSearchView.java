package com.fff.android.crnote;

// TODO: edit meta file

import java.util.ArrayList;


import com.fff.android.crnote.CRNoteParser.CRNoteItem;
import com.fff.android.widgets.ScrollingTextView;

import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class CRNoteSearchView extends ListActivity {
	public static final String FILE_NAME = "FILE_NAME";
	String filename = "";

	public static final String SEARCH_FROM = "SEARCH_FROM";
	String searchFrom = "";

	public static final String SEARCH_STRING = "SEARCH_STRING";
	String searchString = "";
	
	public static final String RESULT_BRANCHSELECTED = "RESULT_BRANCHSELECTED";
	public static final String RESULT_HASCHANGES = "RESULT_HASCHANGES";

	private ArrayList<CRNoteParser.CRNoteItem> search_items;
	CRNoteParser crparser;
	
	CRSearchItemAdapter cri_adapter;
	
	boolean contentsChanged = false;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.crnote_search_view);

		TextView fn = (TextView) findViewById(R.id.cr_tv_filename);
		filename = getIntent().getStringExtra(FILE_NAME);
		if (filename != null) {
			fn.setText(filename);
		}
		
		searchFrom = getIntent().getStringExtra(SEARCH_FROM);
		if (searchFrom == null) {
			searchFrom = ">";

		}
		ScrollingTextView stv = (ScrollingTextView) findViewById(R.id.cr_branchlevel_text);
		stv.setText("Search @" + searchFrom);
		
		searchString = getIntent().getStringExtra(SEARCH_STRING);
		if (searchString == null) {
			// hmm.... this is a failure
			searchString = "";
		}

		ListView crList = getListView();
		crList.setDividerHeight(1);
		crList.setOnItemLongClickListener(new ListView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> av, View v, int pos, long id) {
				//onLongListItemClick(v, pos, id);
				return false;
			}
		});
		
		CrNoteApp crApp = (CrNoteApp) getApplication();
		crparser = (CRNoteParser)crApp.getSharedObject("CRPARSER");  // this should not fail
		
		search_items = crparser.searchItems(searchFrom, searchString, false);
		cri_adapter = new CRSearchItemAdapter(search_items);
		setListAdapter(cri_adapter);
		refreshView();
	}
	
	private void refreshView() {
		cri_adapter.notifyDataSetChanged();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		if (search_items.size() > 0) {
			CRNoteParser.CRNoteItem cri = search_items.get(position);
			if (cri.type == CRNoteParser.CR_TYPE_BRANCH) {
				// branch selected, return to parent
				getIntent().putExtra(RESULT_BRANCHSELECTED,
						CRNoteParser.CRNoteItemToBranchString(cri));
				setResult(RESULT_OK, getIntent());
				finish();
			} else {
				editLeaf(cri);
			}
		}
	}
	
	private void editLeaf(final CRNoteParser.CRNoteItem cri) {
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
				// Log.i("-text-", "afterTextChanged");
				contentsChanged = true;
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				// Log.i("-text-", "beforeTextChanged");
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				// Log.i("-text-", "onTextChanged");
				contentsChanged = true;
			}
		});

		Button button_cancel = (Button) dialog.findViewById(R.id.button_cancel);
		button_cancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
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
				dialog.dismiss();
				refreshView();
			}
		});

		dialog.show();
	}

	private class CRSearchItemAdapter extends BaseAdapter {
		private ArrayList<CRNoteParser.CRNoteItem> items;

		public CRSearchItemAdapter(ArrayList<CRNoteParser.CRNoteItem> items) {
			this.items = items;
		}

		@Override
		public CRNoteItem getItem(int position) {
			return items.get(position);
		}

		@Override
		public int getCount() {
			return items.size();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			CRNoteParser.CRNoteItem i = items.get(position);
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

				v = vi.inflate(R.layout.crnote_search_item, null);
			}

			if (i != null) {
				LinearLayout llb = (LinearLayout) v.findViewById(R.id.branchitem);
				LinearLayout lll = (LinearLayout) v.findViewById(R.id.leafitem);
				if (i.type == CRNoteParser.CR_TYPE_BRANCH) {
					llb.setVisibility(View.VISIBLE);
					lll.setVisibility(View.GONE);
					TextView tv = (TextView) v.findViewById(R.id.branchitem_text);
					tv.setText(CRNoteParser.CRNoteItemToBranchString(i)/*.replace(' ', '_')*/);
				} else {
					llb.setVisibility(View.GONE);
					lll.setVisibility(View.VISIBLE);

					TextView btv = (TextView) v.findViewById(R.id.leafparent_text);
					TextView ltv = (TextView) v.findViewById(R.id.leafitem_text);
					btv.setText(i.parent_branch/*.replace(' ', '_')*/);
					ltv.setText(i.data);
				}
			}

			return v;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}
	}


	// Handle back button press
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			getIntent().putExtra(RESULT_HASCHANGES,	contentsChanged);
			setResult(RESULT_OK, getIntent());
			super.onKeyDown(keyCode, event);
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}
}
