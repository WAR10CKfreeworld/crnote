package com.fff.android.crnote;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


// Caching of list of online files, saves us from having to do requests every time to the server...
public class OnlineFileListCache {
	
	public HashMap<String, String> filemap = new HashMap<String, String>(); // encfile -> file mapping
	public HashMap<String, String> onlinefiles;
	
	public OnlineFileListCache(HashMap<String, String> onlineList, char[] passwd) {
		onlinefiles = onlineList;
		Iterator it = onlineList.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pairs = (Map.Entry)it.next();
			String fn = Util.aesDecFromB64((String)pairs.getKey(), passwd);
			Util.dLog("Onlinefiles", (String)pairs.getKey() + " <--> " + fn);
			filemap.put((String)pairs.getKey(), fn);
		}
	}
	
	public OnlineFileListCache() {
		onlinefiles = new HashMap<String, String>();
	}
	
	public String searchFilename(String fn) {
		Iterator it = filemap.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry pairs = (Map.Entry)it.next();
	        if (pairs.getValue().equals(fn)) {
	        	return (String)pairs.getKey();
	        }
	    }
		return null;
	}
	
	public String getOnlineFileTimestamp(String efn) {
		if (onlinefiles.containsKey(efn)) {
			return (String)onlinefiles.get(efn);
		}
		return null;
	}
	
	
	public String addFilenamePair(String fn, String efn) {
		//Util.dLog("addFilenamePair", "f:" + fn + ", e:"+efn);
		Iterator it = filemap.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry pairs = (Map.Entry)it.next();
	        if (pairs.getValue().equals(fn)) {
	        	return (String)pairs.getKey();
	        }
	    }
	    filemap.put(efn, fn);
	    onlinefiles.put(efn, Long.toString(System.currentTimeMillis()/1000));
		return null;
	}
	
	public boolean deleteFilename(String fn) {
		Iterator it = filemap.entrySet().iterator();
		String fk = null;
	    while (it.hasNext()) {
	        Map.Entry pairs = (Map.Entry)it.next();
	        if (pairs.getValue().equals(fn)) {
	        	fk = (String)pairs.getKey();
	        	break;
	        }
	    }
	    
	    if (fk != null) {
	    	filemap.remove(fk);
	    	onlinefiles.remove(fk);
	    	return true;
	    }
		return false;
	}
	
	public boolean deleteEncodedFilename(String efn) {
		if (filemap.containsKey(efn)) {
			filemap.remove(efn);
			onlinefiles.remove(efn);
			return true;
		}
		return false;
	}
}
