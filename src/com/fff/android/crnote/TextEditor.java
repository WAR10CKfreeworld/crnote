package com.fff.android.crnote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Editable;
import android.util.AttributeSet;
import android.widget.EditText;

public class TextEditor extends EditText {
	// Major bug in Android 2.3 and less textview cursor where it doesn't set the cursor position correctly on first touch of the textview.
	/*
	private boolean buggyCursorVersion = true;
	private boolean selChanged = false;
	private boolean onTouchSelChanged = false;
	*/

	private Rect mRect;
	private Paint mPaint;

	
	// we need this constructor for LayoutInflater
	public TextEditor(Context context, AttributeSet attrs) {
		super(context, attrs);

		mRect = new Rect();
		mPaint = new Paint();
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setColor(0x80506C70);
		
		// Check Android version and set buggyCursorVersion (onTouchEvent doesn't position cursor correctly) 
		/*
		Build.VERSION v = new Build.VERSION();
		*/
		
		/*
		this.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				return true;
			}
		});
		*/
	}

	/*
	private int[] selPos = null;
	private int selPosi = 0;

	@Override
	protected void onSelectionChanged(int selStart, int selEnd) {
		if (selPos == null) {
			selPos = new int[2];
			selPosi = 0;
		}

		if (selStart == selEnd && !onTouchSelChanged) {
			selPos[selPosi] = selStart;
			//Log.i("SELCH" + this, "start: " + selStart + ", posi: " + selPosi + " { " + selPos[0] + ", " + selPos[1] + "}");
			selPosi = (selPosi == 0 ? 1 : 0);
			selChanged = true;
		}

		super.onSelectionChanged(selStart, selEnd);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		boolean superes = super.onTouchEvent(event);

		int action = event.getAction();
		if (action == MotionEvent.ACTION_UP) {
			if (selChanged) {
				onTouchSelChanged = true;
				this.setSelection(selPos[selPosi]);
			}
		} else {
			onTouchSelChanged = false;
		}

		return superes;
	}
	*/

	@Override
	protected void onDraw(Canvas canvas) {
		int count = getHeight()/getLineHeight() + 1;
		if (count < getLineCount()) {
			count = getLineCount();
		}
		
		Rect r = mRect;
		Paint paint = mPaint;

		int baseline = getLineBounds(0, r);
        for (int i = 0; i < count; i++) {
            canvas.drawLine(r.left, baseline + 3, r.right, baseline + 3, paint);
            baseline += getLineHeight();
        }
		
		super.onDraw(canvas);
	}
	
	public void insertTextAtCurosr(String s) {
		Editable ed = this.getEditableText();
		ed.replace(getSelectionStart(), getSelectionStart(), s);
	}
}
