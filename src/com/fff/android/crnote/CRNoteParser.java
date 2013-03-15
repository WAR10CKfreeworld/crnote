package com.fff.android.crnote;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.ssl.Base64;


public class CRNoteParser {
	
	public static class CRNoteItem {
		public String parent_branch;
		public String data;    // branch-name, leaf data, or meta-description
		public String meta_name; // meta filename
		public String meta_type; // meta content-type
		public byte[] rawdata;   // meta raw data
		public int type;
		
		public String toBranchString() {
			return CRNoteItemToBranchString(this);
		}
		
		public String getMetaName() {
			return meta_name;
		}
		
		public String getMetaType() {
			return meta_type;
		}
		
		public String getMetaDescription() {
			return data;
		}
	}
	
	public static CRNoteItem newCrNoteItem() {
		return new CRNoteItem();
	}
	
	private ArrayList<CRNoteItem> cr_items = new ArrayList<CRNoteItem>();

	public static final String CR_BRANCH_DELIM = ">";
	public static final char CR_BRANCH_DELIM_CH = '>';

	public static final int CR_TYPE_NONE = 0;
	public static final int CR_TYPE_BRANCH = 1;
	public static final int CR_TYPE_LEAF = 2;
	public static final int CR_TYPE_META = 3;  // Meta-type, defined by content-type
	
	public static final int CR_MAX_BRANCH_DEPTH = 50; // way too much
	
	public static final char BRANCH_TAG = '+';
	public static final char LEAF_TAG   = '-';
	public static final char META_TAG   = '~';
	
	public String errmsg;
	
	
	
	public static int itemType(char c) {
		switch (c) {
		case BRANCH_TAG:
			return CR_TYPE_BRANCH;
		case LEAF_TAG:
			return CR_TYPE_LEAF;
		case META_TAG:
			return CR_TYPE_META;
		default:
			return CR_TYPE_NONE;
		}
	}

	
	public CRNoteParser(byte[] src) throws CRNoteException {
		__CRNoteParsedItem__ item = new __CRNoteParsedItem__();
		int i = 0;
		String[] branches = new String[CR_MAX_BRANCH_DEPTH];
		int cur_depth = 0; // 0 = root (considered a branch), all other branches begin at depth 1
		boolean branchDetected = false;

		item.src_start = 0;
		item.src_end = 0;
		while (i < src.length) {
			i = item.src_end;
			
			item.value = null;
			item.type = CR_TYPE_NONE;

			
			CRNoteItem cri = parseCrItem(src, item); // everything but parent_branch assigned here
			if (cri == null) {
				break;
			}
			
			if (item.type == CR_TYPE_BRANCH) {
				branchDetected = true;
				
				if (item.branch_depth > cur_depth+1 || item.branch_depth > CR_MAX_BRANCH_DEPTH) {
					// hmm... this is wrong, branches should always increase by
					// one; not skip them
					throw new CRNoteException("Invalid format, branch level corruption at item " + item.value);
				}

				if (item.branch_depth == cur_depth) {
					// at the same level as the current set of branches
					branches[cur_depth] = null;
				} else {
					// erase everything beyond the branch_depth
					for (int n = item.branch_depth; n < cur_depth; n++) {
						branches[n] = null;
					}
					cur_depth = item.branch_depth;			
				}

				cri.parent_branch = stringArrayToString(branches, cur_depth, CR_BRANCH_DELIM);
				branches[cur_depth] = item.value;				

				cr_items.add(cri);
				//Util.dLog("parser", "Add branch " + cri.data);
			} else if (item.type == CR_TYPE_LEAF || item.type == CR_TYPE_META) {
				// cur_depth used in this iterator could be either root or a branch... algorithm issue
				// (needs to be recoded to make sense) but in the meantime, this is a workaround
				// so that the parser assigns root as parent to leaves that are immediate to it.
				if (branchDetected) {
					cri.parent_branch = stringArrayToString(branches, cur_depth+1, CR_BRANCH_DELIM);
				} else {
					cri.parent_branch = CR_BRANCH_DELIM;
				}

				cr_items.add(cri);
				//Util.dLog("parser", "Add leaf " + cri.data);
			} else {
				// shouldn't get here
				throw new CRNoteException("Invalid format, corruption at item " + item.value);
			}
			
			item.src_start = item.src_end;
		}
			
		// DEBUG
		//__debug_dump__();
	}

	/*
	private void __debug_dump__()
	{
		for (int i = 0; i < cr_items.size(); i++) {
			CRNoteItem cri = cr_items.get(i);
			if (cri.type == CR_TYPE_BRANCH)
				Util.dLog("i@ " + cri.parent_branch, " > " + cri.data);
			else
				Util.dLog("i@ " + cri.parent_branch, " - " + cri.data);
		}
	}
	*/
	
	public static String stringArrayToString(String[] src, int items, String delim) {
		if (items == 0)
			return delim;

		int i = 0;
		if (src[0] == CR_BRANCH_DELIM)
			i = 1;
		
		String s = "";
		for (; i < items; i++) {
			s = s + delim + src[i];
		}

		if (s.length() == 0)
			s = CR_BRANCH_DELIM;
		
		return s;
	}
	
	public static String stringArrayToString(List<String> src, String delim) {
		int items = src.size();
		
		if (items == 0)
			return delim;
		
		int i = 0;
		if (src.get(0) == CR_BRANCH_DELIM)
			i = 1;

		String s = "";
		for (; i < items; i++) {
			s = s + delim + src.get(i);
		}
		
		if (s.length() == 0)
			s = CR_BRANCH_DELIM;

		return s;
	}
	
	public static String CRNoteItemToBranchString(CRNoteItem cri) {
		if (cri.type == CR_TYPE_BRANCH) {
			if (cri.parent_branch.compareTo(CR_BRANCH_DELIM) == 0) {
				return (CR_BRANCH_DELIM + cri.data);
			}
			return (cri.parent_branch + CR_BRANCH_DELIM + cri.data);
		} else {
			return (cri.parent_branch);
		}
	}
	
	// get items at this branch level. Each branch level delimited with '>'.
	// PRE-CONDITION: '>' is not allowed for branch name.
	public ArrayList<CRNoteItem> getItemsAt(String branch) {
		ArrayList<CRNoteItem> sl = new ArrayList<CRNoteItem>();
		int items = 0;
		for (int i = 0; i < cr_items.size(); i++) {
			CRNoteItem cri = cr_items.get(i);
			if (cri.parent_branch.compareTo(branch) == 0) {
				sl.add(cri);
				//Util.dLog("get@"+branch, cri.data);
				items++;
			}
		}

		return sl;
	}
	
	// get all branches
	public ArrayList<CRNoteItem> getAllBranches() {
		ArrayList<CRNoteItem> sl = new ArrayList<CRNoteItem>();
		
		// insert the root branch first
		CRNoteItem cri = new CRNoteItem();
		cri.parent_branch = ">";
		cri.data = "";
		cri.type = CR_TYPE_BRANCH;
		sl.add(cri);
		
		for (int i = 0; i < cr_items.size(); i++) {
			cri = cr_items.get(i);
			if (cri.type == CR_TYPE_BRANCH) {
				sl.add(cri);
			}
		}

		return sl;
	}
	
	// search for items from this branch
	public ArrayList<CRNoteItem> searchItems(String branch, String searchFor, boolean caseSensitive) {
		ArrayList<CRNoteItem> sl = new ArrayList<CRNoteItem>();
		int items = 0;
		if (!caseSensitive) {
			searchFor = searchFor.toLowerCase();
		}
		for (int i = 0; i < cr_items.size(); i++) {
			CRNoteItem cri = cr_items.get(i);
			//Util.dLog("search " + searchFor + "@" + branch, cri.parent_branch + " .... " + cri.data + " ? " + (cri.type == CR_TYPE_BRANCH ? "B" : "L"));
			if (isOffspring(cri.parent_branch, branch)) {
				//Util.dLog(" ... ->", "offspring(" + cri.parent_branch + "," + branch + ")");
				String s = caseSensitive ? cri.data : cri.data.toLowerCase();
				if (s.indexOf(searchFor) >= 0) {
					sl.add(cri);
					//Util.dLog("found@"+branch, cri.data);
					items++;
				}
			}
		}

		return sl;
	}
	
	// -> branch name must be unique within its level (leaves don't need to because they don't have any children to worry about).
	public boolean addItem(String parent, String item, int type) {
		return addItem(parent, item, null, type);
	}
	
	public boolean addItem(String parent, String item, CRNoteItem icri, int type) {		
		int i = 0;
		CRNoteItem cri;
		boolean found_parent = false;
		
		errmsg = null;
		
		if (icri == null && item.contains(CR_BRANCH_DELIM) && type == CR_TYPE_BRANCH) {
			// illegal to have branch name with our delimiter
			return false;
		}
		
		if (parent.equals(CR_BRANCH_DELIM) && (type == CR_TYPE_LEAF || type == CR_TYPE_META)) {
			// add immediately at root
			CRNoteItem ncri = icri != null ? icri : new CRNoteItem();
			ncri.type = type;
			if (icri == null) {
				ncri.data = item;
			}
			ncri.parent_branch = parent;
			cr_items.add(0, ncri);
			return true;
		}
		
		// Find where the parent first exists
		if (!parent.equals(CR_BRANCH_DELIM)) {
			for (i = 0; i < cr_items.size(); i++) {
				cri = cr_items.get(i);
				if (CRNoteItemToBranchString(cri).equals(parent)) {
					found_parent = true;
					//Util.dLog("Add", "Found where the branch starts");
					break;
				}
			}
		}

		if (i < cr_items.size() && (type == CR_TYPE_LEAF || type == CR_TYPE_META)) {
			// add immediately after the branch node
			CRNoteItem ncri = icri != null ? icri : new CRNoteItem();
			ncri.type = type;
			if (icri == null) {
				ncri.data = item;
			}
			ncri.parent_branch = parent;
			cr_items.add(i+1, ncri);
			//Util.dLog("ADD LEAF", "Added @ " + i+1);
			return true;
		}
		
		if (!parent.equals(CR_BRANCH_DELIM) && i >= cr_items.size()) {
			Util.dLog("ADD FAILED", "INVALID PARENT " + parent);
			return false;
		}
		
		if (type == CR_TYPE_LEAF || type == CR_TYPE_META) {
			// leaf is already added above and nothing further needs to be done for it.
			Util.dLog("ADD L/M FAILED", "Parent " + parent + " not found");
			return false;
		} 
		
		if (found_parent) {
			i++;
		} else if (parent.equals(CR_BRANCH_DELIM)) {
			found_parent = true;
			//i = cr_items.size();
		}
		
		if (found_parent) {
			// Find the end of the branch's lineage, i.e. when the node's parent changes
			for (; i < cr_items.size(); i++) {
				cri = cr_items.get(i);
				if (cri.parent_branch.equals(parent) && cri.data.equals(item) && cri.type == type) {
					errmsg = "Item already exists";
					return false;
				}
				
				if (!parent.equals(CR_BRANCH_DELIM)) {
					if (!cri.parent_branch.equals(parent) && !isOffspring(cri.parent_branch, parent)) {
						//Util.dLog("Add", "cri.parent " + cri.parent_branch + " differs");
						break;
					}
				}
			}

		
			// Add the branch to the end of the parent's lineage
			//Util.dLog("ADD BRANCH " + cr_items.size(), "Added " + item + " @ " + i + " parent " + parent);
			CRNoteItem ncri = new CRNoteItem();
			ncri.type = type;
			ncri.data = item;
			ncri.parent_branch = parent;
			cr_items.add(i, ncri);
			
			//__debug_dump__();
			return true;
		}
		return false;
	}
	
	public boolean isOffspring(String offspringsParent, String head) {
		if (head.equals(CR_BRANCH_DELIM) || head.equals(offspringsParent))
			return true;
		
		if (offspringsParent.length() > head.length() &&
				offspringsParent.startsWith(head) &&
				offspringsParent.charAt(head.length()) == CR_BRANCH_DELIM_CH) {
			return true;
		}
		return false;
	}
	
	public void deleteItem(CRNoteItem item) {
		if (item.type == CR_TYPE_LEAF || item.type == CR_TYPE_META) {
			cr_items.remove(item);
		} else {
			String fullName = CRNoteItemToBranchString(item);
			String fullNamePlus = fullName + CR_BRANCH_DELIM;
			
			int i = cr_items.indexOf(item);
			if (i >= 0) {
				cr_items.remove(i);
				while (i < cr_items.size()) {
					CRNoteItem ncri = cr_items.get(i);
					if (ncri.parent_branch.compareTo(fullName) == 0 ||
							ncri.parent_branch.startsWith(fullNamePlus)) {
						cr_items.remove(i);
					} else {
						break; // done
					}
				}
			}
		}
	}
	
	public boolean moveItem(CRNoteItem src, String toParent) {
		if (src.parent_branch.compareTo(toParent) == 0)
			return false;
		
		String fullName = CRNoteItemToBranchString(src);
		if (src.type == CR_TYPE_BRANCH && !src.parent_branch.equals(CR_BRANCH_DELIM)) {
			if (isOffspring(toParent, fullName)) {
				return false; // can't move this branch to its offsprings
			}
		}

		if (src.type == CR_TYPE_BRANCH) {
			if (toParent.compareTo(fullName) == 0)
				return false; // can't move to itself
		}
		
		// Find this particular item. If it is a branch, then extract all its children and move them to a temporary array		
		boolean moveToRoot = toParent.compareTo(CR_BRANCH_DELIM) == 0;
		int srcloc = cr_items.indexOf(src);
		boolean destExists = false;

		
		for (int j = 0; j < cr_items.size(); j++) {
			CRNoteItem ncri = cr_items.get(j);
			if (ncri.type == CR_TYPE_BRANCH &&
					toParent.compareTo(CRNoteItemToBranchString(ncri)) == 0) {
				destExists = true;
				break;
			}
		}
		
		if (!moveToRoot && !destExists) {
			return false;
		}
		
		
		ArrayList<CRNoteItem> crn_a = new ArrayList<CRNoteItem>();
		int nleaves = 0;
		int nbranches = 0;
		if (src.type == CR_TYPE_LEAF || src.type == CR_TYPE_META) {
			cr_items.remove(src);
			src.parent_branch = toParent;
			// Add leaf to start of the parent branch ... see near end of this method()
		} else {
			CRNoteItem ncri = cr_items.get(srcloc);

			int i = srcloc;
			cr_items.remove(i);
			ncri.parent_branch = toParent;
			crn_a.add(ncri);
			String newFullName = CRNoteItemToBranchString(ncri);
			
			// extract all offsprings of this branch, put into temporary array to copy back later
			for (;;) {
				ncri = cr_items.get(i);
				//Util.dLog("move compare offspring", ncri.parent_branch + " -> " + fullName);
				if (isOffspring(ncri.parent_branch, fullName)) {
					cr_items.remove(i);
					if (ncri.type == CR_TYPE_BRANCH) {
						nbranches++;
					} else {
						nleaves++;
					}
					ncri.parent_branch = newFullName;
					crn_a.add(ncri);
					//Util.dLog("Move Offspring", ncri.parent_branch + ">" + ncri.data);
				} else {
					break;
				}
			}
		}
		
		// Find the location of the new parent - start and end

		int bloc_s = -1; // all leaves will be put into bloc_s
		int bloc_e = -1; // all branches will be put into bloc_e
		if (moveToRoot) {
			bloc_s = 0;
			bloc_e = cr_items.size();
		} else {
			String bprefix = toParent + CR_BRANCH_DELIM;
			for (int i = 0; i < cr_items.size(); i++) {
				CRNoteItem ncri = cr_items.get(i);
			
				if (ncri.type == CR_TYPE_BRANCH &&
						toParent.equals(CRNoteItemToBranchString(ncri))) {
				
					// found destination starting point
					if (bloc_s < 0) {
						bloc_s = i+1;
					}
					
					if (src.type == CR_TYPE_LEAF || src.type == CR_TYPE_META)
						break;
					
				} else if (toParent.equals(ncri.parent_branch) || ncri.parent_branch.startsWith(bprefix)) {
					bloc_e = i+1; // insertion of branch should be after everything found for the parent
				} else if (bloc_s > 0) {
					break;
				}
			}
			if (bloc_e < 0) {
				bloc_e = bloc_s;
			}
		}
		
		// Now move items
		if (src.type == CR_TYPE_BRANCH) {
			// Move all branches first to end (bloc_e)
			CRNoteItem ncri;
			while (!crn_a.isEmpty()) {
				ncri = crn_a.remove(0);
				cr_items.add(bloc_e++, ncri);
			}
		} else {
			// leaf/meta - simply move to start of new parent
			cr_items.add(bloc_s, src);
		}
		
		//__debug_dump__();
		
		return true;
	}
	
	public int branchLevel(String branch) {
		int n = 0;
		int i;
		
		for (i = 0; i < branch.length(); i++) {
			if (branch.charAt(i) == CR_BRANCH_DELIM_CH) {
				n++;
			}
		}
		
		return n;
	}

	/*
	// use by the methods below
	private boolean multiOutputConverter(StringBuffer sb, OutputStream os)
	{
		for (int i = 0; i < cr_items.size(); i++) {
			CRNoteItem ncri = cr_items.get(i);
			
			if (ncri.type == CR_TYPE_LEAF) {
				sb.append((new String("- ")).concat(ncri.data).concat("\n"));
			} else if (ncri.type == CR_TYPE_META) {
				sb.append("~ ");
				// ~ name|type|description|encoding|size|xxxxxxxxxxxxxxxxxxxx
				
				sb.append(crStringEncode(ncri.meta_name).concat("|"));
				sb.append(crStringEncode(ncri.meta_type).concat("|"));
				sb.append(crStringEncode(ncri.data).concat("|"));
				if (CrNoteApp.encode_meta_base64) {
					sb.append("base64|");
					String rdata = Base64.encodeBase64String(ncri.rawdata);
					if (rdata.endsWith("\n")) {
						sb.append(Integer.toString(rdata.length()) + "|");
						sb.append(rdata);
					} else {
						sb.append(Integer.toString(rdata.length() + 1) + "|");
						sb.append(rdata);
						sb.append("\n");
					}
				} else {
					// XXX
				}
			} else {
				int bl = branchLevel(CRNoteItemToBranchString(ncri));
				while (bl > 0) {
					sb.append("+");
					bl--;
				}
				sb.append((new String(" ")).concat(ncri.data).concat("\n"));
			}
		}
		if (os != null) {
			try {
				os.write(sb.toString().getBytes());
			} catch (IOException e) {
				Util.dLog("Write failed", e.getMessage());
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}
	
	public StringBuffer toStringBuffer() {
		StringBuffer sb = new StringBuffer();
		multiOutputConverter(sb, null);
		return sb;
	}
	
	public String toString()
	{
		return toStringBuffer().toString();
	}
	
	public boolean writeToOutput(OutputStream os)
	{
		StringBuffer sb = new StringBuffer();
		boolean res = multiOutputConverter(sb, os);
		return res;
	}
	*/

	// get an instance of an InputStream belonging to this object
	public CrNoteInputStream getInputStream() {
		return this.new CrNoteInputStream();
	}
	
	// Output as an InputStream - used during encryption
	public class CrNoteInputStream extends InputStream {
		private int at = 0;
		private byte[] outbuf = null;
		private int atByte = 0;
		//private boolean isNL = false; // is there a newline detected - the next character needs to be a 'space', ie '\n '

		@Override
		public int read() throws IOException {
			if (atByte > 0 && outbuf == null) {
				return (-1);
			}
			
			if (at == cr_items.size() && outbuf != null && atByte >= outbuf.length) {
				return (-1);
			}
			
			if (outbuf != null && atByte == outbuf.length) {
				atByte = 0;
			}
			
			int retval;
			
			if (atByte == 0) {
				// obtain the next cr_item and generate a new sbuffer
				
				CRNoteItem ncri = cr_items.get(at++);
				if (ncri.type == CR_TYPE_LEAF) {
					outbuf = (LEAF_TAG + " " + ncri.data.replace("\n", "\n ") + "\n").getBytes(); // this could possibly exhaust memory... needs to be optimised
				} else if (ncri.type == CR_TYPE_META) {
					ByteArrayOutputStream sb = new ByteArrayOutputStream();
					
					sb.write((META_TAG + " ").getBytes());
					// ~ name|type|description|encoding|size|xxxxxxxxxxxxxxxxxxxx
					
					sb.write(crStringEncode(ncri.meta_name).concat("|").getBytes());
					sb.write(crStringEncode(ncri.meta_type).concat("|").getBytes());
					sb.write(crStringEncode(ncri.data).concat("|").getBytes());
					
					if (CrNoteApp.encode_meta_base64) {
						sb.write("base64|".getBytes());
						String rdata = Base64.encodeBase64String(ncri.rawdata);
						if (rdata.endsWith("\n")) {
							sb.write((Integer.toString(rdata.length()) + "|").getBytes());
							sb.write(rdata.getBytes());
						} else {
							sb.write((Integer.toString(rdata.length() + 1) + "|").getBytes());
							sb.write(rdata.getBytes());
							sb.write("\n".getBytes());
						}
					} else {
						sb.write("raw|".getBytes());
						sb.write((Integer.toString(ncri.rawdata.length + 1) + "|").getBytes());
						sb.write(ncri.rawdata);
						sb.write("\n".getBytes());
					}
					outbuf = sb.toByteArray();
					sb = null;
				} else {
					int bl = branchLevel(CRNoteItemToBranchString(ncri));
					StringBuffer sbuf = new StringBuffer();
					while (bl > 0) {
						sbuf.append(BRANCH_TAG);
						bl--;
					}
					sbuf.append(" " + ncri.data.replace("\n", "\n ") + "\n");
					outbuf = sbuf.toString().getBytes();
				}
				//Util.dLog("is", sb.toString());
			}
			
			retval = 0xff & outbuf[atByte++];
			return retval;
		}
	}
	
	
	///////////////////////////////////////////////////
	// Privates
	///////////////////////////////////////////////////
	
	/*
	 * Meta format: 
	 *   ~ name|type|description|encoding|size|xxxxxxxxxxxxxxxxxxxx
	 *
	 *       all fields up to size are urlencoded
	 *       name = filename
	 *       type = content-type
	 *       description = text
	 *       encodings = raw | base64
	 *       size = bytes (includes all newline delimeters for base64-encoding).
	 *              Next record starts immediately after data.
	 */
	
	private String nextMetaField(byte[] src, int from) throws CRNoteException {
		StringBuffer s = new StringBuffer();
		
		while (from < src.length) {
			if (src[from] == '|') {
				break;
			}
			s.append((char)src[from]);
			from++;
		}

		return s.toString();
	}
	
	private CRNoteItem parseCrMeta(byte[] src, __CRNoteParsedItem__ arg) throws CRNoteException {
		// Parse the Meta format
		
		int i = arg.src_start + 2; // skip "~ "
		if (i >= src.length) {
			throw new CRNoteException("Invalid format; reached end of file");
		}

		CRNoteItem cri = new CRNoteItem();
		
		String s;

		s = nextMetaField(src, i); i += s.length() + 1; cri.meta_name = crStringDecode(s);
		s = nextMetaField(src, i); i += s.length() + 1; cri.meta_type = crStringDecode(s);
		s = nextMetaField(src, i); i += s.length() + 1; cri.data =  crStringDecode(s);

		s = nextMetaField(src, i); i += s.length() + 1; String enc = crStringDecode(s);
		s = nextMetaField(src, i); i += s.length() + 1; String size = crStringDecode(s);
		
		int sz = Integer.parseInt(size);

		if (enc.equals("base64")) {
			byte[] rdata = new byte[sz];
			System.arraycopy(src, i, rdata, 0, sz);

			// decode base64 to raw
			cri.rawdata = Base64.decodeBase64(rdata);
		} else {
			byte[] rdata = new byte[sz-1];
			System.arraycopy(src, i, rdata, 0, sz-1);

			cri.rawdata = rdata;
		}
		cri.type = arg.type;
		arg.src_end = i + sz;
		
		return cri;
	}
	
	private CRNoteItem parseCrItem(byte[] src, __CRNoteParsedItem__ arg) throws CRNoteException {
		ByteArrayOutputStream sb;
		int type = CR_TYPE_NONE;
		
		int i = arg.src_start;

		if (i >= src.length)
			return null;

		if ((char)src[i] == BRANCH_TAG || (char)src[i] == LEAF_TAG || (char)src[i] == META_TAG) {
			type = itemType((char)src[i]);
			arg.type = type;
			
			if (type == CR_TYPE_META) {
				return parseCrMeta(src, arg);
			}

			i++;

			arg.branch_depth = 0;
			if (type == CR_TYPE_BRANCH) {
				// determine branch level
				while (i < src.length && (char)src[i] == BRANCH_TAG) {
					arg.branch_depth++;
					i++;
				}
			}

			if (i >= src.length || (char)src[i] != ' ') {
				if (i < src.length) {
					String errs = new String(src, i, ((i + 50) >= src.length) ? src.length : i + 50);
					throw new CRNoteException("Invalid format at item " + errs);
				} else {
					throw new CRNoteException("Invalid format; reached end of file");
				}
			}
			
			i++; // skip first whitespace
			
			sb = new ByteArrayOutputStream();
			boolean nldetected = false;
			boolean done = false;
			
			byte[] fifo = new byte[1024];
			int fifo_i = 0;
			
			while (i < src.length && !done) {
				
				if (fifo_i >= fifo.length) {
					// Flush fifo to storage buffer
					try {
						sb.write(fifo);
					} catch (IOException e) {
						Util.dLog("parseItem", "Writing fifo to byte array error " + e.getMessage());
					}
					fifo_i = 0;
				}
				
				switch ((char)src[i]) {
				case '\n':
					nldetected = true;
					fifo[fifo_i++] = src[i];
					i++;
					break;
				case ' ':
					if (nldetected) {
						// Skip the leader ' ' if it a  new line was detected, i.e. it continues from previous line
						nldetected = false;
					} else {
						// otherwise we append it here.
						fifo[fifo_i++] = ' ';
					}

					i++;
					break;
				case LEAF_TAG:
				case BRANCH_TAG:
				case META_TAG:
					if (nldetected) {
						// a branch or a leaf tag started on a new line; we're done
						nldetected = false;
						done = true;
					} else {
						fifo[fifo_i++] = src[i];
						i++;
					}
					break;

				default:
					fifo[fifo_i++] = src[i];
					i++;
					nldetected = false;	// for now be lenient on allowing non-key characters as first character on new line
				}
			}

			if (fifo_i > 0) {
				fifo_i--;
				if (fifo[fifo_i] == '\n') {
					fifo_i--;
				}

				sb.write(fifo, 0, fifo_i+1);
			}

			try {
				arg.value = sb.toString("utf-8");
			} catch (Exception e) {
				arg.value = sb.toString();
			}
			arg.src_end = i;
			
			//Util.dLog("parseCrItem", "completed [" + arg.value + "]");
			CRNoteItem cri = new CRNoteItem();
			cri.data = arg.value;
			cri.type = arg.type;
			
			return cri;

		}

		if (i < src.length) {
			String errs = new String(src, i, ((i + 50) >= src.length) ? src.length : i + 50);
			throw new CRNoteException("Invalid format at item " + errs);
		} else {
			throw new CRNoteException("Invalid format; reached end of file");
		}
	}

	// Used by the local parser
	private class __CRNoteParsedItem__ {
		public String value;
		public int type;
		public int branch_depth;

		public int src_start; // where did the item start in the src string
		public int src_end; // where did the item end in the src string
	}

	// this is thrown due to errors in parsing
	public class CRNoteException extends Exception {
		private static final long serialVersionUID = 1L;
		String err;

		public CRNoteException() {
			super();
			err = "unknown";
		}

		public CRNoteException(String e) {
			super(e);
			err = e;
		}

		public String getError() {
			return err;
		}
	}
	
	
	public static String crStringEncode(String src) {
		return src.replace("%", "%25").replace("|", "%7c");
	}

	public static String crStringDecode(String src) {
		return URLDecoder.decode(src);
	}
}
