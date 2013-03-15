/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.fff.android.crnote;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;



public class LockPatternUtils {
	final static int HASH_ROUNDS = 500; // number of hash rounds on each cell
	final static int HASH_INNER_ROUNDS = 10;
	
    /**
     * Deserialize a pattern.
     * @param string The pattern serialized with {@link #patternToString}
     * @return The pattern.
     */
    public static List<LockPatternView.Cell> stringToPattern(String string) {
        List<LockPatternView.Cell> result = Lists.newArrayList();

        final byte[] bytes = string.getBytes();
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            result.add(LockPatternView.Cell.of(b / 4, b % 4));
        }
        return result;
    }

    /**
     * Serialize a pattern.
     * @param pattern The pattern.
     * @return The pattern in string form.
     */
    public static String patternToString(List<LockPatternView.Cell> pattern) {
        if (pattern == null) {
            return "";
        }
        final int patternSize = pattern.size();

        byte[] res = new byte[patternSize];
        for (int i = 0; i < patternSize; i++) {
            LockPatternView.Cell cell = pattern.get(i);
            res[i] = (byte) (cell.getRow() * 4 + cell.getColumn());
        }
        return new String(res);
    }
    
    /**
     * Generate a hash key from the given pattern.
     * @param pattern The pattern.
     * @param seed Seed to initialise the hash sequence with.
     * @return The 512-bit hash key.
     */
    public static byte[]  patternToKey(List<LockPatternView.Cell> pattern, byte[] seed) {
    	if (pattern == null) {
    		return null;
    	}
    	final int patternSize = pattern.size();
    	
    	byte[] res = new byte[512/8];
    	for (int i = 0; i < patternSize; i++) {
    		LockPatternView.Cell cell = pattern.get(i);
    		int location = (cell.getRow() * 4 + cell.getColumn());
    		
    		/*
    		 * Determine the hash for this round
    		 */
    		MessageDigest md;
    		MessageDigest md5;
    		try {
				md = MessageDigest.getInstance("SHA-512");
				md5 = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				Util.dLog("patternToKey", e.getMessage());
				return null;
			}
			
			/*
			 * Each round is seeded with the MD5(seed ++ previous round's hash)
			 */
			md5.update(seed);
			
			md.update(md5.digest(res));
			for (int x = 0; x < HASH_ROUNDS; x++) {
				res = md.digest(res);
			}
			
			for (int n = 0; n <= location; n++) {
				for (int x = 0; x < HASH_INNER_ROUNDS; x++) {
					res = md.digest(res);
				}
			}
    	}
    	return res;
    }
}
