package com.fff.android.crnote;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class OnlineFiles extends ListActivity {
	
	OnlineFilesAdapter ofadapter;
	ArrayList<FileInfo> flist;
	
	String cache_onlinefname = null;
	byte[] cache_onlinefilecontents = null;
	
	private final int REQUEST_LAUNCH_EDITOR = 1;
	

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.online_file_list);
		
		ListView crList = getListView();
		crList.setDividerHeight(1);
		crList.setOnItemClickListener(new ListView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> av, View v, int pos, long id) {
				onListItemClick(v, pos, id);
			}
		});
		
		// TODO: if the download is already in progress, don't try another one.
		RemoteTask rt = new RemoteTask();
		rt.setProgressMessage("Contacting server", "Fetching a list of your online files.");
		rt.setOnRemoteTaskEvent(new RemoteTaskEvent() {
			@Override
			public boolean doRemoteTask(String file1, String file2) {
				return refresh(true);
			}

			@Override
			public void onRemoteTaskComplete(boolean resultCode) {
				resetFilesAdapter();
			}
		});
		rt.execute(null, null);
		
		TextView crurl = (TextView) findViewById(R.id.crnoteurl);
		crurl.setText(CrNoteApp.weburl);
	}
	
	private boolean refresh(boolean force) {
		CrNoteApp crApp = (CrNoteApp) getApplication();
		OnlineFileListCache oflc = null;

		oflc = (OnlineFileListCache)crApp.getTempSettingObject("onlinefiles");
		CrWebAPI crwapi = new CrWebAPI(crApp);
		char[] cwkey = Util.byteArrayToCharArray(CrNoteApp.webkey);

		if (oflc == null || force) {
			// Obtain list of online files
			HashMap<String, String> eflist = crwapi.getFiles();
			if (crwapi.getResponseCode() != 200) {
				// Invalid credentials
				CrDialogs.AlertMsg("Connection Failed", "Server responded with " + String.valueOf(crwapi.getResponseCode()) + " error", OnlineFiles.this);
				return false;
			}
			if (eflist != null) {
				oflc = new OnlineFileListCache(eflist, cwkey);
			} else {
				oflc = new OnlineFileListCache();
			}
			crApp.setTempSettingObject("onlinefiles", oflc);
		}

				
		flist = new ArrayList<FileInfo>();
		if (oflc != null) {
			@SuppressWarnings("rawtypes")
			Iterator it = oflc.filemap.entrySet().iterator();
		    while (it.hasNext()) {
		        @SuppressWarnings("rawtypes")
				Map.Entry pairs = (Map.Entry)it.next();
		        String k = (String)pairs.getKey();
		        String v = (String)pairs.getValue();
		        String t = (String)oflc.onlinefiles.get(k);
		        
		        flist.add(new FileInfo(k, v, t));
		    }
		}
		
		ofadapter = new OnlineFilesAdapter(flist);
		return true;
	}
	
	private void resetFilesAdapter() {
		ofadapter.notifyDataSetChanged();

		setListAdapter(ofadapter);
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
			pd = ProgressDialog.show(OnlineFiles.this,
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
		public boolean doRemoteTask(String file1, String file2);
	    public void onRemoteTaskComplete (boolean resultCode);
	}
	
		
	protected void onListItemClick(View v, int position, long id) {
		// Util.dLog("onlongclick", "onLongListItemClick id=" + id);

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		final CharSequence[] items = { "Open", "Download", "Delete" };
		final int pos = position;

		builder.setItems(items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				// Toast.makeText(getApplicationContext(), items[item],
				// Toast.LENGTH_SHORT).show();
				FileInfo fi = flist.get(pos);

				switch (item) {
				case 0:
					// Download the file to buffer and open with appropriate
					// view
					openOnlineFile(fi.getKey(), fi.getValue());
					break;

				case 1:
					// Check if file already exists
					// If yes, prompt to confirm overwrite, rename localfile, or
					// rename downloaded file.
					downloadFile(fi.getKey(), fi.getValue());
					break;

				case 2:
					confirmDelete(fi.getKey(), fi.getValue());
					break;

				default:
				}
			}
		}).setTitle("Remote file tasks");

		AlertDialog alert = builder.create();

		alert.show();
	}
	
	private int confirm_selected = -1;

	private void confirmDelete(final String remoteFile, final String localFile) {
		final String[] options = { "Delete remote file " + localFile + "?" };

		final AlertDialog ad = new AlertDialog.Builder(this)
		.setTitle("Please confirm deletion")
		.setSingleChoiceItems(options, -1, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				confirm_selected = item;
			}
		})
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				if (confirm_selected != -1) {
					deleteTask(remoteFile, localFile);
				}
			}
		})
		.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				dialog.cancel();
			}
		})
		.create();

		ad.show();
	}
	
	private void deleteTask(String remoteFile, String localFile) {
		RemoteTask rt = new RemoteTask();
		rt.setProgressMessage("Contacting server", "Deleting remote file " + localFile);
		CrNoteApp crApp = (CrNoteApp) getApplication();
		final CrWebAPI crwapi = new CrWebAPI(crApp);

		rt.setOnRemoteTaskEvent(new RemoteTaskEvent() {
			@Override
			public boolean doRemoteTask(String file1, String file2) {
				return (crwapi.deleteFile(file1));
			}

			@Override
			public void onRemoteTaskComplete(boolean resultCode) {
				refresh(true);
				resetFilesAdapter();
			}
		});
		rt.execute(remoteFile, null);
	}
	
	
	private void downloadFile(String remoteFile, String localFile) {
		RemoteTask rt = new RemoteTask();
		rt.setProgressMessage("Contacting server", "Downloading file " + localFile);
		CrNoteApp crApp = (CrNoteApp) getApplication();
		final String fullLocalName = CrNoteApp.onlinefolder + File.separator + localFile;
		final CrWebAPI crwapi = new CrWebAPI(crApp);

		rt.setOnRemoteTaskEvent(new RemoteTaskEvent() {
			@Override
			public boolean doRemoteTask(String file1, String file2) {
				return (crwapi.getFile(file1, file2));
			}

			@Override
			public void onRemoteTaskComplete(boolean resultCode) {
				if (resultCode == false) {
					CrDialogs.AlertMsg("Error downloading file", crwapi.getErrorMsg(), OnlineFiles.this);
					return;
				} else {
					CrDialogs.AlertMsg("Download successfully", "File saved as " + fullLocalName, OnlineFiles.this);
				}
			}
		});
		rt.execute(remoteFile, fullLocalName);
	}
	
	private void openOnlineFile(final String onlinefname, final String localname) {
		CrNoteApp crApp = (CrNoteApp) getApplication();
		final String fullLocalName = CrNoteApp.onlinefolder + File.separator + localname;
		if (cache_onlinefname == null || !cache_onlinefname.equals(onlinefname)) {
			RemoteTask rt = new RemoteTask();
			rt.setProgressMessage("Contacting server", "Opening remote file " + localname);
			final CrWebAPI crwapi = new CrWebAPI(crApp);

			// Download to cache
			rt.setOnRemoteTaskEvent(new RemoteTaskEvent() {
				@Override
				public boolean doRemoteTask(String file1, String file2) {
					return (crwapi.getFile(file1, file2));
				}

				@Override
				public void onRemoteTaskComplete(boolean resultCode) {
					if (resultCode == false) {
						CrDialogs.AlertMsg("Error downloading file", crwapi.getErrorMsg(), OnlineFiles.this);
						return;
					} else {
						Util.dLog("Downloaded", "Downloaded to cache as " + fullLocalName);
						cache_onlinefname = onlinefname;
						openFileHelper(onlinefname, localname);
					}
				}
			});
			rt.execute(onlinefname, fullLocalName);
		} else {
			openFileHelper(onlinefname, localname);
		}
	}
	
	private void openFileHelper(final String onlinefname, final String localname) {
		final String fullLocalName = CrNoteApp.onlinefolder + File.separator + localname;

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
				String fpass = input.getText().toString();

				if (fpass != "") {
					FileHelper.FileStat fs = FileHelper.doDecrypt(fullLocalName, fpass);
					CrNoteApp crApp = (CrNoteApp) getApplication();
					
					if (fs == null) {
						alertDialog("Error", "Decryption error. Your password is incorrect");
						return;
					}

					if (crApp == null) {
						Util.dLog("getApplication", "NULL");
					} else {
						crApp.setTextbuf(fs.sbuf);
						Intent intent;
						if (fs.isCrNote) {
							intent = new Intent(getApplicationContext(),
									CRNote.class);
							intent.putExtra(CRNote.FILE_NAME, "CRNOTE://" + localname);
							intent.putExtra(CRNote.FILE_PASSWD, fpass);
							intent.putExtra(CRNote.REMOTE_FILENAME, onlinefname);
						} else {
							intent = new Intent(getApplicationContext(),
									PlaintextView.class);
							intent.putExtra(PlaintextView.FILE_NAME, "CRNOTE://" + localname);
							intent.putExtra(PlaintextView.FILE_PASSWD, fpass);
							intent.putExtra(PlaintextView.REMOTE_FILENAME, onlinefname);
						}
						startActivityForResult(intent, REQUEST_LAUNCH_EDITOR);
					}
				}
			}
		});

		alert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int whichButton) {
					}
				});

		alert.show();
		// see http://androidsnippets.com/prompt-user-input-with-an-alertdialog
	}
	
	private void alertDialog(String title, String msg) {
		CrDialogs.AlertMsg(title, msg, this);
	}
	
	
	////////////////////////////// 
	// UI Helpers
	//////////////////////////////
	
	private class FileInfo {
		public String key;
		public String value;
		public String time;
		public long size;
		
		FileInfo(String key, String value, String timesize) {
			//Util.dLog("FileInfo", key  + " , "  + value + ", " + time);
			this.key = key;
			this.value = value;
			String[] ts = timesize.split("\\|");
			try {
				Date date = new Date(Long.parseLong(ts[0]) * 1000L);
				this.time = (date.toLocaleString());
				this.size = Long.parseLong(ts[1]);
			} catch (Exception e) {
				Util.dLog("API Error", "invalid response for file list " + timesize);
			}
		}
		
		public String getKey() {
			return key;
		}
		
		public String getValue() {
			return value;
		}
		
		public String getTime() {
			return time;
		}
		
		public long getSize() {
			return size;
		}
	}
	
	private class OnlineFilesAdapter extends BaseAdapter {
		private ArrayList<FileInfo> items;

		public OnlineFilesAdapter(ArrayList<FileInfo> items) {
			this.items = items;
		}

		public FileInfo getItem(int position) {
			return items.get(position);
		}

		public int getCount() {
			return items.size();
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			FileInfo i = items.get(position);
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

				v = vi.inflate(R.layout.online_file_row, null);
			}

			if (i != null) {
				TextView efile = (TextView) v.findViewById(R.id.encodedfile);
				TextView dfile = (TextView) v.findViewById(R.id.decodedfile);
				TextView time = (TextView) v.findViewById(R.id.timestamp);
				
				efile.setText(i.getKey());
				dfile.setText(i.getValue());
				long sz = i.getSize()/1024;
				if (sz == 0) {
					sz = 1;
				}
				time.setText("uploaded: " + i.getTime() + " (" + sz + "kb)");
			}

			return v;
		}

		public long getItemId(int position) {
			return position;
		}
	}
}
