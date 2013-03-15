package com.fff.android.crnote;

import java.util.Calendar;

public class IdleThread extends Thread {
	private long timeOutMillis = 2 * 60 *1000;
	private long lastTouched = 0;
	private boolean ignoreTimeout = false;
	private boolean doTerminate = false;
	
	private final long MIN_SLEEP = 10000; // anything less than 10 seconds is crazy
	
	public void setTimeout(long millis) {
		timeOutMillis = millis;
	}
	
	public void touch() {
		lastTouched = Calendar.getInstance().getTimeInMillis();
		//Util.dLog("idle", "touched");
	}
	
	public void pause() {
		ignoreTimeout = true;
	}
	
	public void unpause() {
		ignoreTimeout = false;
		touch();
	}
	
	public void terminate() {
		pause();
		doTerminate = true;
		this.interrupt();
	}
	
	private IdleTimeoutEvent evcallback = null;
	public void setOnIdleTimeout(IdleTimeoutEvent evc) {
		evcallback = evc;
	}
	
	@Override
	public void run() {
		setPriority(MIN_PRIORITY);
		while (!doTerminate) {
			timeOutMillis = CrNoteApp.timeout*1000 > MIN_SLEEP ?
					CrNoteApp.timeout*1000 : MIN_SLEEP; // timeout could be changed at any point.
			long ctime = Calendar.getInstance().getTimeInMillis();
			if (ctime - lastTouched >= timeOutMillis) {
				if (!ignoreTimeout) {
					if (evcallback != null) {
						//Util.dLog("IdleThread", "Timeout, locking screen");
						//Util.dLog("IdleThread", "Timeout " + ctime + ", last: " + lastTouched + ", tmillis " + timeOutMillis);
						pause();
						evcallback.onIdleTimeoutEvent();
					}
				} else {
					// sleep for a bit
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {

					}
				}
			} else {
				try {
					if (timeOutMillis - (ctime - lastTouched) > 1000) {
						Thread.sleep(timeOutMillis - (ctime - lastTouched));
					} else {
						Thread.sleep(MIN_SLEEP);
					}
				} catch (InterruptedException e) {
				}
			}
		}
	}
	
	public interface IdleTimeoutEvent
	{
	    public void onIdleTimeoutEvent();
	}

}
