package com.fff.android.crnote;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

public class CrDialogs {
	public static void AlertMsg(String title, String msg, Context context) {
		AlertDialog alertDialog = new AlertDialog.Builder(context).create();
		alertDialog.setTitle(title);
		alertDialog.setMessage(msg);
		alertDialog.setButton("Close", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				return;
			}
		});
		alertDialog.show();
	}
}
