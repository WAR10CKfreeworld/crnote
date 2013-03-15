package com.fff.android.crnote;

import java.util.ArrayList;

import com.fff.android.crnote.CRNoteParser.CRNoteItem;

import android.app.ListActivity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

// Display list of all branches except for the one specified by IGNORE_BRANCH
public class CRBranchList extends ListActivity {
	public static final String IGNORE_BRANCH = "IGNORE_BRANCH";
	String ignoreBranch;
	
	public static final String PROMPT_TEXT = "PROMPT_TEXT";
	String promptText;
	
	public static final String RESULT_BRANCH = "RESULT_BRANCH";
	
	private CRNoteParser crparser;
	@SuppressWarnings("unused")
	private CRNoteParser.CRNoteItem cri;
	
	private CRItemAdapter cri_adapter;
	
	ArrayList<CRNoteParser.CRNoteItem> allbranches;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.crbranches);

		TextView tv = (TextView) findViewById(R.id.textTitlePrompt);
		promptText = getIntent().getStringExtra(PROMPT_TEXT);
		if (promptText != null) {
			tv.setText(promptText);
		}

		ignoreBranch = getIntent().getStringExtra(IGNORE_BRANCH);

		ListView crList = getListView();
		crList.setDividerHeight(1);
		
		// Populate the list view
		crparser = (CRNoteParser)CrNoteApp.getSharedObject("CRPARSER");  // this should not fail
		cri = (CRNoteParser.CRNoteItem)CrNoteApp.getSharedObject("SRCITEM");
		
		// TODO: filter out the cri and its offsprings.
		allbranches = crparser.getAllBranches();
		
		cri_adapter = new CRItemAdapter(allbranches);
		cri_adapter.notifyDataSetChanged();
		setListAdapter(cri_adapter);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		CRNoteParser.CRNoteItem ncri = allbranches.get(position);
		getIntent().putExtra(RESULT_BRANCH, CRNoteParser.CRNoteItemToBranchString(ncri));
		setResult(RESULT_OK, getIntent());
		finish();
	}
	
	private class CRItemAdapter extends BaseAdapter {
		private ArrayList<CRNoteParser.CRNoteItem> items;

		public CRItemAdapter(ArrayList<CRNoteParser.CRNoteItem> items) {
			this.items = items;
		}

		public CRNoteItem getItem(int position) {
			return items.get(position);
		}

		public int getCount() {
			return items.size();
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			CRNoteParser.CRNoteItem i = items.get(position);
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

				v = vi.inflate(R.layout.crbranchlist_item, null);
			}
			
			if (i != null) {			
				String s = i.toBranchString();
				int color = 0;
				for (int si = 0; si < s.length(); si++) {
					if (s.charAt(si) == CRNoteParser.CR_BRANCH_DELIM_CH) {
						if (color < 0x606060) {
							color += 0x0d0d0d;
						}
					}
				}
				if (color > 0) {
					color -= 0x0a0a0a;
				}
				color |= 0xff000000;
				
				// gets brighter as the levels get deeper. Increment range until max color depth = 0xff5f5f5f
				v.setBackgroundColor(color);

				TextView tv = (TextView) v.findViewById(R.id.crrowtext);
				tv.setText(s);
			}

			return v;
		}

		public long getItemId(int position) {
			return position;
		}
	}
}
